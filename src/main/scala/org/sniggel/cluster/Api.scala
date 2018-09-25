package org.sniggel.cluster

import akka.Done
import akka.actor.CoordinatedShutdown.{PhaseServiceRequestsDone, PhaseServiceUnbind, Reason}
import akka.actor.typed.ActorRef
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

object Api {

  final object BindFailure extends Reason

  def apply(accountEntity: ActorRef[AccountEntity.Command])
           (implicit untypedSystem: ActorSystem,
            mat: Materializer,
            readJournal: EventsByPersistenceIdQuery): Unit = {

    import untypedSystem.dispatcher

    implicit val scheduler: Scheduler = untypedSystem.scheduler
    val shutdown = CoordinatedShutdown(untypedSystem)
    val requestsDoneAfter: FiniteDuration = 5.seconds
    val askTimeout: FiniteDuration = 5.seconds
    val eventsMaxIdle: FiniteDuration = 10.seconds

    Http()
      .bindAndHandle(route(accountEntity, askTimeout, eventsMaxIdle), "0.0.0.0", 9000)
      .onComplete {
        case Failure(cause) =>
          println(s"Failure to start Http server, reason: ${cause.getCause}")
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

  def route(accountEntity: ActorRef[AccountEntity.Command],
            askTimeout: FiniteDuration,
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
}
