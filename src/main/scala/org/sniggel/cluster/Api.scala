package org.sniggel.cluster

import java.net.InetAddress
import java.nio.charset.StandardCharsets.UTF_8

import akka.Done
import akka.actor.CoordinatedShutdown.{PhaseServiceRequestsDone, PhaseServiceUnbind, Reason}
import akka.actor.typed.ActorRef
import akka.actor.{ActorSystem, CoordinatedShutdown, Scheduler}
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling
import akka.http.scaladsl.model.MediaTypes.`text/event-stream`
import akka.http.scaladsl.model.StatusCodes.{BadRequest, Conflict, Created, OK}
import akka.http.scaladsl.model.headers.`Last-Event-ID`
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.model.{HttpEntity, HttpResponse}
import akka.http.scaladsl.server.{Directives, Route}
import akka.pattern.after
import akka.persistence.query.EventEnvelope
import akka.persistence.query.scaladsl.EventsByPersistenceIdQuery
import akka.stream.Materializer
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import org.apache.logging.log4j.scala.Logging
import org.sniggel.cluster.AccountEntity.{CreateAccountCommand, GetStateCommand, Ping, Reply}

import scala.concurrent.Future
import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.{Failure, Success}

object Api extends Logging {

  final case class SignUp(username: String, password: String, nickname: String)

  final object BindFailure extends Reason

  def apply(accounts: EntityRef[AccountEntity.Command])
           (implicit untypedSystem: ActorSystem,
            mat: Materializer,
            readJournal: EventsByPersistenceIdQuery): Unit = {

    import untypedSystem.dispatcher

    implicit val scheduler: Scheduler = untypedSystem.scheduler
    val shutdown = CoordinatedShutdown(untypedSystem)
    val requestsDoneAfter: FiniteDuration = 5.seconds
    val askTimeout: FiniteDuration = 5.seconds
    val eventsMaxIdle: FiniteDuration = 10.seconds

    logger.info(s"Starting Akka Http on port 9000")
    Http()
      .bindAndHandle(route(askTimeout, eventsMaxIdle, accounts), "0.0.0.0", 9000)
      .onComplete {
        case Failure(cause) =>
          logger.error(s"Failure to start Http server, reason: ${cause.getCause}")
          CoordinatedShutdown(untypedSystem).run(BindFailure)
        case Success(binding) =>
          shutdown.addTask(PhaseServiceUnbind, "api.unbind") { () =>
            binding.unbind()
          }
          shutdown.addTask(PhaseServiceRequestsDone, "api.requests-done") { () =>
            after(requestsDoneAfter, untypedSystem.scheduler)(Future.successful(Done))
          }
      }
  }

  def route(askTimeout: FiniteDuration,
            eventsMaxIdle: FiniteDuration,
            accounts: EntityRef[AccountEntity.Command])
           (implicit scheduler: Scheduler,
            readJournal: EventsByPersistenceIdQuery): Route = {
    implicit val timeout: Timeout = askTimeout
    import Directives._
    import ErrorAccumulatingCirceSupport._
    import EventStreamMarshalling._
    import io.circe.generic.auto._
    import io.circe.syntax._

    pathPrefix("api") {
      pathEnd {
        get {
          complete {
            OK
          }
        }
      } ~
      pathPrefix("accounts") {
        import AccountEntity._
        pathEnd {
          post {
            entity(as[SignUp]) {
              case SignUp(username, password, nickname) =>
                onSuccess(accounts ? createAccount(username, password, nickname)) {
                  case CreateAccountSuccessReply =>
                    complete(Created)
                  case CreateAccountConflictReply =>
                    complete(Conflict)
                  case _ =>
                    complete(BadRequest)
                }
            }
          }
        }
      }~
      pathPrefix("ping") {
        import AccountEntity._
        pathEnd {
          get {
            extractClientIP { ip =>
              onSuccess(accounts ? ping(ip.toOption)) {
                case p: Pong =>
                  complete(p)
                case _ =>
                  complete(BadRequest)
              }
            }
          }
        }
      }~
      pathPrefix("state") {
        import AccountEntity._
        pathEnd {
          get {
            onSuccess(accounts ? state()) {
              case s: State =>
                complete(s)
              case _ =>
                complete(BadRequest)
            }
          }
        }
      }~
      // FIXME stream not working properly
      pathPrefix("eventstream") {
        get {
          optionalHeaderValueByName(`Last-Event-ID`.name) { lastEventId =>
            try {
              val fromSeqNo = lastEventId.getOrElse("-1").trim.toLong + 1
              complete {
                readJournal
                  .eventsByPersistenceId(AccountEntity.PersistenceId, fromSeqNo, Long.MaxValue)
                  .collect {
                    case EventEnvelope(_, _, seqNo, ac: AccountEntity.AccountCreatedEvent) =>
                      ServerSentEvent(ac.asJson.noSpaces,
                                      "account-created",
                                      seqNo.toString)
                    case EventEnvelope(_, _, seqNo, p: AccountEntity.Pinged) =>
                      ServerSentEvent(p.asJson.noSpaces,
                                      "pinged",
                                      seqNo.toString)
                  }.keepAlive(eventsMaxIdle, () => ServerSentEvent.heartbeat)
              }
            } catch {
              case _: NumberFormatException =>
                complete(
                  HttpResponse(
                    BadRequest,
                    entity = HttpEntity(`text/event-stream`,
                                        "Last-Event-Id must be numeric!".getBytes(UTF_8))
                  )
                )
            }
          }
        }
      }
    }
  }

  private def createAccount(username: String, password: String, nickname: String)
                           (replyTo: ActorRef[Reply]): CreateAccountCommand =
    CreateAccountCommand(username, password, nickname, replyTo)

  private def ping(ipAddress: Option[InetAddress])
                  (replyTo: ActorRef[Reply]): Ping = Ping(System.currentTimeMillis, ipAddress.map(_.toString), replyTo)

  private def state()
                   (replyTo: ActorRef[Reply]): GetStateCommand =
    GetStateCommand(replyTo)
}
