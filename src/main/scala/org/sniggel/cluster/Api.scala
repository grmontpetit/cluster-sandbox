package org.sniggel.cluster

import java.net.{InetAddress, InetSocketAddress}

import akka.actor.CoordinatedShutdown.Reason
import akka.actor.typed.ActorRef
import akka.actor.{ActorSystem, Scheduler}
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.persistence.query.scaladsl.EventsByPersistenceIdQuery
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import io.circe.generic.auto._
import io.circe.syntax._
import org.apache.logging.log4j.scala.Logging
import org.sniggel.cluster.AccountEntity._
import pureconfig.loadConfigOrThrow

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future}

object Api extends Logging {

  final case class Config(clusterName: String, listenPort: Int, bindHostname: String)

  final case class SignUp(username: String, password: String, nickname: String)

  final object BindFailure extends Reason

  def apply(accounts: EntityRef[AccountEntity.Command])
           (implicit untypedSystem: ActorSystem,
            mat: Materializer,
            readJournal: EventsByPersistenceIdQuery,
            context: ExecutionContext): Unit = {

    val config = loadConfigOrThrow[Config]("cluster-sandbox")

    implicit val scheduler: Scheduler = untypedSystem.scheduler
    val askTimeout: FiniteDuration = 5.seconds

    logger.info(s"Starting Akka Http on port ${config.listenPort}")
    Http()
      .bind(interface = config.bindHostname, port = config.listenPort).to(Sink.foreach{ connection =>
      connection.handleWithAsyncHandler(handler(askTimeout, accounts, connection.remoteAddress))
    }).run()
  }

  def handler(askTimeout: FiniteDuration,
              accounts: EntityRef[AccountEntity.Command],
              remoteAddress: InetSocketAddress)
             (implicit mat: Materializer,
              context: ExecutionContext): HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/api"), _, _, _) => handleApi()
    case HttpRequest(HttpMethods.POST, Uri.Path("/api/accounts"), _, entity, _) => handleSignUp(askTimeout, accounts, entity)
    case HttpRequest(HttpMethods.GET, Uri.Path("/api/ping"), _, _, _) => handlePing(askTimeout, accounts, remoteAddress)
    case HttpRequest(HttpMethods.GET, Uri.Path("/api/state"), _, _, _) => handleState(askTimeout, accounts)
  }

  def handleApi(): Future[HttpResponse] = Future.successful(HttpResponse(status = StatusCodes.OK))

  def handleSignUp(askTimeout: FiniteDuration,
                   accounts: EntityRef[AccountEntity.Command],
                   entity: RequestEntity)
                  (implicit mat: Materializer,
                   context: ExecutionContext): Future[HttpResponse] = {
    implicit val timeout: Timeout = askTimeout
    Unmarshal(entity)
      .to[SignUp]
      .flatMap(a => accounts ? createAccount(a.username, a.password, a.nickname))
      .map(_ => HttpResponse(status = StatusCodes.Created))
  }

  def handlePing(askTimeout: FiniteDuration,
                 accounts: EntityRef[AccountEntity.Command],
                 remoteAddress: InetSocketAddress)
                (implicit mat: Materializer,
                 context: ExecutionContext): Future[HttpResponse] = {
    implicit val timeout: Timeout = askTimeout
    val ask = accounts ? ping(Some(remoteAddress.getAddress))
    ask.map{
      case p: Pong => HttpResponse(status = StatusCodes.OK,
        entity = HttpEntity(p.asJson.noSpaces)
          .withContentType(ContentTypes.`application/json`))
      case _ => HttpResponse(status = StatusCodes.BadRequest)
    }
  }

  def handleState(askTimeout: FiniteDuration,
                  accounts: EntityRef[AccountEntity.Command])
                 (implicit mat: Materializer,
                  context: ExecutionContext): Future[HttpResponse] = {
    implicit val timeout: Timeout = askTimeout
    val ask = accounts ? state()
    ask.map {
      case s: State => HttpResponse(status = StatusCodes.OK,
        entity = HttpEntity(s.asJson.noSpaces)
          .withContentType(ContentTypes.`application/json`))
      case _ => HttpResponse(status = StatusCodes.BadRequest)
    }
  }

  private def createAccount(username: String, password: String, nickname: String)
                           (replyTo: ActorRef[Reply]): CreateAccountCommand =
    CreateAccountCommand(username, password, nickname, replyTo)

  private def ping(ipAddress: Option[InetAddress])
                  (replyTo: ActorRef[Reply]): Ping = Ping(System.currentTimeMillis, ipAddress.map(_.getHostAddress), replyTo)

  private def state()
                   (replyTo: ActorRef[Reply]): GetStateCommand =
    GetStateCommand(replyTo)
}
