package org.sniggel.cluster

import akka.Done
import akka.actor.CoordinatedShutdown.{PhaseServiceRequestsDone, PhaseServiceUnbind, Reason}
import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.{ActorSystem, CoordinatedShutdown, Scheduler}
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.{Conflict, Created, OK}
import akka.http.scaladsl.server.{Directives, Route}
import akka.pattern.after
import akka.persistence.query.scaladsl.EventsByPersistenceIdQuery
import akka.stream.Materializer
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import org.apache.logging.log4j.scala.Logging
import org.sniggel.cluster.AccountEntity.{CreateAccountCommand, Reply, Ping}

import scala.concurrent.Future
import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.{Failure, Success}

object Api extends Logging {

  import akka.actor.typed.scaladsl.adapter._

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
    import io.circe.generic.auto._

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
                }
            }
          }
        }
      }~
      pathPrefix("trigger") {
        pathEnd {
          get {
            complete {
              accounts ! Ping
              OK
            }
          }
        }
      }
    }
  }

  def receptionist(context: akka.actor.ActorContext)
                  (implicit timeout: Timeout,
                   scheduler: Scheduler): Future[Receptionist.Listing] = {
    val receptionist = context.system.toTyped.receptionist
    val receptionistCommand: Receptionist.Command = Receptionist.find(AccountEntity.AccountServiceKey, context.self)

    receptionist ? (_ => receptionistCommand)
  }

  private def createAccount(username: String, password: String, nickname: String)
                           (replyTo: ActorRef[Reply]): CreateAccountCommand =
    CreateAccountCommand(username, password, nickname, replyTo)
}
