package org.sniggel.cluster

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.Done
import akka.actor.CoordinatedShutdown.{PhaseServiceRequestsDone, PhaseServiceUnbind, Reason}
import akka.actor.typed.receptionist.Receptionist
import akka.actor.{ActorSystem, CoordinatedShutdown, Scheduler}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.server.{Directives, Route}
import akka.pattern.after
import akka.persistence.query.scaladsl.EventsByPersistenceIdQuery
import akka.stream.Materializer
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.{Failure, Success}
import org.apache.logging.log4j.scala.Logging

object Api extends Logging {

  import akka.actor.typed.scaladsl.adapter._

  final object BindFailure extends Reason

  def apply()
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
      .bindAndHandle(route(askTimeout, eventsMaxIdle), "0.0.0.0", 9000)
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
            eventsMaxIdle: FiniteDuration)
           (implicit scheduler: Scheduler,
            readJournal: EventsByPersistenceIdQuery): Route = {
    implicit val timeout: Timeout = askTimeout
    import Directives._
    pathPrefix("api") {
      pathEnd {
        get {
          complete {
            OK
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
}
