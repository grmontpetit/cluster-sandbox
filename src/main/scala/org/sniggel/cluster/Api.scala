/*
Copyright (c) 2018-2019 Gabriel Robitaille-Montpetit

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/

package org.sniggel.cluster

import java.net.{InetAddress, InetSocketAddress}

import akka.actor.CoordinatedShutdown.Reason
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.{ActorSystem, Scheduler}
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.persistence.query.scaladsl.EventsByPersistenceIdQuery
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import com.softwaremill.session.SessionOptions.{ oneOff, usingCookies }
import com.softwaremill.session.{ SessionConfig, SessionDirectives, SessionManager }
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import io.circe.generic.auto._
import io.circe.syntax._
import org.apache.logging.log4j.scala.Logging
import org.sniggel.cluster.AccountEntity._
import org.sniggel.cluster.Authenticator.{Authenticate, Authenticated, InvalidCredentials}
import pureconfig.loadConfigOrThrow

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.matching.Regex

object Api extends Logging {

  final case class Config(clusterName: String,
                          listenPort: Int,
                          bindHostname: String,
                          askTimeout: FiniteDuration,
                          usernameRegex: Regex,
                          passwordRegex: Regex)
  val config: Config = loadConfigOrThrow[Config]("cluster-sandbox")

  final case class SignUp(username: String, password: String, nickname: String)
  final case class SignIn(username: String, password: String)

  final object BindFailure extends Reason

  def apply(accounts: EntityRef[AccountEntity.Command],
            authenticator: ActorRef[Authenticator.Command])
           (implicit untypedSystem: ActorSystem,
            mat: Materializer,
            readJournal: EventsByPersistenceIdQuery,
            context: ExecutionContext): Unit = {

    implicit val scheduler: Scheduler = untypedSystem.scheduler

    logger.info(s"Starting Akka Http on port ${config.listenPort}")
    Http()
      .bind(interface = config.bindHostname, port = config.listenPort).to(Sink.foreach{ connection =>
      connection.handleWithAsyncHandler(handler(config.askTimeout, accounts, connection.remoteAddress, authenticator))
    }).run()
  }

  def handler(askTimeout: FiniteDuration,
              accounts: EntityRef[AccountEntity.Command],
              remoteAddress: InetSocketAddress,
              authenticator: ActorRef[Authenticator.Command])
             (implicit mat: Materializer,
              context: ExecutionContext,
              scheduler: Scheduler): HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/api"), _, _, _) => handleApi()
    case HttpRequest(HttpMethods.POST, Uri.Path("/api/accounts"), _, entity, _) => handleSignUp(askTimeout, accounts, entity)
    case HttpRequest(HttpMethods.GET, Uri.Path("/api/ping"), _, _, _) => handlePing(askTimeout, accounts, remoteAddress)
    case HttpRequest(HttpMethods.GET, Uri.Path("/api/state"), _, _, _) => handleState(askTimeout, accounts)
    case HttpRequest(HttpMethods.POST, Uri.Path("/api/sessions"), _, entity, _) => handleSession(askTimeout, authenticator, entity)
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
      .flatMap(a => accounts ? createAccount(a.username, a.password, a.nickname)).map {
        case UsernameInvalid(_) => HttpResponse(status = StatusCodes.BadRequest)
        case PasswordInvalid(_) => HttpResponse(status = StatusCodes.BadRequest)
        case UsernameTaken(_) => HttpResponse(status = StatusCodes.Conflict)
        case CreateAccountSuccessReply(_) => HttpResponse(status = StatusCodes.Created)
        case _ => HttpResponse(status = StatusCodes.InternalServerError)
      }
  }

  def handleSession(askTimeout: FiniteDuration,
                    authenticator: ActorRef[Authenticator.Command],
                    entity: RequestEntity)
                (implicit mat: Materializer,
                 context: ExecutionContext,
                 scheduler: Scheduler): Future[HttpResponse] = {
    implicit val timeout: Timeout = askTimeout
    implicit val sessions: SessionManager[String] = new SessionManager(SessionConfig.fromConfig())
    import SessionDirectives._
    Unmarshal(entity)
      .to[SignIn]
      .flatMap(a => authenticator ? authenticate(a.username, a.password))
      .map {
        case Authenticated(username) =>
          setSession(oneOff, usingCookies, username)
          HttpResponse(status = StatusCodes.OK)
        case InvalidCredentials(_) => HttpResponse(status = StatusCodes.Unauthorized)
        case _ => HttpResponse(status = StatusCodes.Unauthorized)
      }
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
                   (replyTo: ActorRef[AccountEntity.Reply]): GetStateCommand =
    GetStateCommand(replyTo)

  private def authenticate(username: String, password: String)
                          (replyTo: ActorRef[Authenticator.Reply]): Authenticate =
    Authenticate(username, password, replyTo)
}
