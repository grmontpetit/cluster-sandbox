package org.sniggel.cluster

import java.util.UUID

import akka.Done
import akka.actor.Scheduler
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.query.EventEnvelope
import akka.persistence.query.scaladsl.EventsByPersistenceIdQuery
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import org.apache.logging.log4j.scala.Logging
import pureconfig.loadConfigOrThrow

import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal
import scala.util.matching.Regex

object Authenticator extends Logging {

  // Config
  final case class Config(clusterName: String,
                          listenPort: Int,
                          bindHostname: String,
                          askTimeout: FiniteDuration,
                          usernameRegex: Regex,
                          passwordRegex: Regex)
  val config: Config = loadConfigOrThrow[Config]("cluster-sandbox")

  // Types

  // Commands
  sealed trait Command
  final case class AddCredentials(seqNo: Long,
                                  id: UUID,
                                  username: String,
                                  passwordHash: String,
                                  replyTo: ActorRef[Done]) extends Command
  final case class Authenticate(username: String,
                                password: String,
                                replyTo: ActorRef[Reply]) extends Command
  private final case class HandleProjectionComplete(timestamp: Long) extends Command
  final case object Stop extends Command

  // Replies
  sealed trait Reply
  final case class InvalidCredentials(timestamp: Long) extends Reply
  final case class Authenticated(username: String) extends Reply

  def apply()(implicit mat: Materializer,
              readJournal: EventsByPersistenceIdQuery): Behavior[Command] =
    Behaviors.setup { context =>
      implicit val scheduler: Scheduler = context.system.scheduler
      runProjection(0, context.self, config.askTimeout)
      Authenticator(0, Map.empty, config.askTimeout)
    }

  def apply(lastSeqNo: Long,
            credentials: Map[String, String],
            askTimeout: FiniteDuration)
           (implicit mat: Materializer,
            scheduler: Scheduler,
            readJournal: EventsByPersistenceIdQuery): Behavior[Command] =
    Behaviors.receive {
      case (_, Authenticate(username, password, replyTo)) =>
        val isValid =
          credentials
            .get(username)
            .map(verifyPassword(password))
            .fold(false)(identity)
        if (!isValid) {
          logger.warn(s"Invalid credentials for username $username!")
          replyTo ! InvalidCredentials(System.currentTimeMillis())
        } else
          replyTo ! Authenticated(username)
        Behaviors.same

      case (_, AddCredentials(seqNo, _, username, passwordHash, replyTo)) =>
        logger.info(s"Credentials for username $username added")
        replyTo ! Done
        Authenticator(seqNo, credentials + (username -> passwordHash), askTimeout)

      case (context, HandleProjectionComplete(_)) =>
        runProjection(lastSeqNo, context.self, askTimeout)
        Behaviors.same
      case (_, Stop) =>
        Behaviors.stopped
    }

  def authenticate(username: String, password: String)
                  (replyTo: ActorRef[Reply]): Authenticate = Authenticate(username, password, replyTo)

  private def runProjection(lastSeqNo: Long,
                            authenticator: ActorRef[Command],
                            askTimeout: FiniteDuration)
                           (implicit mat: Materializer,
                            scheduler: Scheduler,
                            readJournal: EventsByPersistenceIdQuery) = {
    implicit val timeout: Timeout = askTimeout

    readJournal
      .eventsByPersistenceId(AccountEntity.PersistenceId, lastSeqNo + 1, Long.MaxValue)
      .collect {
        case EventEnvelope(_, _, seqNo, AccountEntity.AccountCreatedEvent(id, username, passwordHash, _)) =>
          Authenticator.AddCredentials(seqNo, id, username, passwordHash, _: ActorRef[Done])
      }
      .mapAsync(1)(authenticator ? _)
      .runWith(Sink.onComplete { cause =>
        logger.warn(s"Projection of Accounts events completed unexpectedly: $cause")
        authenticator ! HandleProjectionComplete(System.currentTimeMillis())
      })
  }

  private def verifyPassword(password: String)(passwordHash: String) =
    try Passwords.verifyPassword(password, passwordHash)
    catch { case NonFatal(_) => false }
}
