package org.sniggel.cluster

import java.io.Serializable
import java.net.InetAddress
import java.util.UUID

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.Cluster
import akka.cluster.ddata._
import akka.cluster.sharding.ShardRegion.EntityId
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.SideEffect
import akka.persistence.typed.scaladsl.PersistentBehaviors.CommandHandler
import akka.persistence.typed.scaladsl.{Effect, PersistentBehaviors}
import org.apache.logging.log4j.scala.Logging
import pureconfig.loadConfigOrThrow

import scala.concurrent.duration.FiniteDuration
import scala.util.matching.Regex

object AccountEntity extends Logging {

  // Config
  final case class Config(clusterName: String,
                          listenPort: Int,
                          bindHostname: String,
                          askTimeout: FiniteDuration,
                          usernameRegex: Regex,
                          passwordRegex: Regex)
  val config: Config = loadConfigOrThrow[Config]("cluster-sandbox")

  // Types
  type PersistenceId = String
  type Username = String
  type Password = String
  type Nickname = String
  type IpAddress = String
  type UserId = UUID
  type ReplyTo = ActorRef[Reply]

  // Service Key
  final val AccountServiceKey: ServiceKey[Command] = ServiceKey[Command]("accounts")

  // Sharding
  final val ShardingTypeName: EntityTypeKey[Command] = EntityTypeKey[Command]("accounts")

  // Persistent ID
  final val PersistenceId: PersistenceId= "accounts"
  final val EntityId: EntityId = PersistenceId

  // We use the same key as the persistence Id key
  final case class AccountStateReplicationKey(_id: PersistenceId)
    extends Key[ORSet[Event]](_id) with ReplicatedDataSerialization

  // Commands
  sealed trait Command extends Serializable
  final case class Ping(timestamp: Long,
                        ipAddress: Option[IpAddress],
                        ReplyTo: ReplyTo) extends Command
  final case class CreateAccountCommand(username: Username,
                                        password: Password,
                                        nickname: Nickname,
                                        replyTo: ReplyTo) extends Command
  final case class GetStateCommand(replyTo: ReplyTo) extends Command
  final case object PassivateAccount extends Command

  // Events
  sealed trait Event
  final case class AccountCreatedEvent(id: UserId,
                                       username: Username,
                                       password: Password,
                                       nickname: Nickname) extends Event
  final case class Pinged(timestamp: Long, ip: IpAddress) extends Event

  // Replies
  sealed trait Reply
  final case class UsernameInvalid(timestamp: Long) extends Reply
  final case class PasswordInvalid(timestamp: Long) extends Reply
  final case class UsernameTaken(timestamp: Long)   extends Reply
  final case class CreateAccountSuccessReply(timestamp: Long) extends Reply
  final case class CreateAccountConflictReply(timestamp: Long) extends Reply
  final case class Pong(timestamp: Long,
                        pong: String = "PONG",
                        entityId: EntityId) extends Reply

  // State
  final case class Account(id: UUID,
                           username: Username,
                           password: Password,
                           nickname: Nickname)
  final case class PingData(timestamp: Long, ip: IpAddress)
  final case class State(accounts: Map[Username, Account] = Map.empty[Username, Account],
                         pings: List[PingData] = List.empty[PingData]) extends Reply

  def apply(): String => Behavior[Command] = { id =>
    import akka.actor.typed.scaladsl.adapter._
    logger.info(s"Setting up $id")
    Behaviors.setup { context =>

      implicit val cluster: Cluster = akka.cluster.Cluster(context.system.toUntyped)


      logger.info(s"Registering AccountEntity with Receptionist $AccountServiceKey")
      context.system.receptionist ! Receptionist.Register(AccountServiceKey, context.self)

      logger.info(s"Creating persistent behavior persistenceId=$PersistenceId id=$id")

      PersistentBehaviors
        .receive[Command, Event, State](PersistenceId, State(),
        commandHandler,
        eventHandler)
    }
  }

  def commandHandler: CommandHandler[Command, Event, State] = {
    case (state, CreateAccountCommand(username, password, nickname, replyTo)) =>
      logger.info(s"Received a CreateAccountCommand for username: $username")
      if (!config.usernameRegex.pattern.matcher(username).matches) {
        replyTo ! UsernameInvalid(System.currentTimeMillis())
        Effect.none
      } else if (!config.passwordRegex.pattern.matcher(password).matches) {
        replyTo ! PasswordInvalid(System.currentTimeMillis())
        Effect.none
      } else if (state.accounts.get(username).isDefined) {
        replyTo ! UsernameTaken(System.currentTimeMillis())
        Effect.none
      } else {
        // Check state to see if the account already exist
        state.accounts.get(username)
          .fold(persistAndReply(username, password, nickname, replyTo)) { _ =>
            Effect
              .none
              .andThen(SideEffect[State](_ => replyTo ! CreateAccountConflictReply(System.currentTimeMillis())))
          }
      }
    case (_, Ping(timestamp, ipAddress, replyTo)) =>
      logger.info(s"Received a Ping command on my entity: $EntityId")
      val pingedEvent = Pinged(timestamp, ipAddress.getOrElse(InetAddress.getLoopbackAddress).toString)
      Effect
        .persist(pingedEvent)
        .andThen(SideEffect[State](_ => replyTo ! Pong(timestamp, "PONG", EntityId)))
    case (state, GetStateCommand(replyTo)) =>
      logger.info(s"Received a GetStateCommand on my entity $EntityId")
      Effect
        .none
        .andThen(SideEffect[State](_ => replyTo ! state))
    case (s, PassivateAccount) =>
      logger.info(s"Passivating account state $s")
      Effect.stop
    case x: Any =>
      logger.info(s"Received an Unknown message of type ?: $x")
      Effect.none
    case _ =>
      logger.info(s"Received an Unknown message")
      Effect.none
  }

  private def persistAndReply(username: Username,
                              password: Password,
                              nickname: Nickname,
                              replyTo: ReplyTo): Effect[Event, State] = {
    val id = UUID.randomUUID
    val accountCreatedEvent = AccountCreatedEvent(id, username, Passwords.createHash(password), nickname)
    Effect
      // Persist the event
      .persist(accountCreatedEvent)
      // Reply to the Actor that sent the command
      .andThen(SideEffect[State](_ => replyTo ! CreateAccountSuccessReply(System.currentTimeMillis())))
  }

  def eventHandler: (State, Event) => State = {
    case (State(accounts, pings), AccountCreatedEvent(id, username, password, nickname)) =>
      logger.info(s"Updating current state with new account $username")
      // Append to the Entity State
      State(accounts + (username -> Account(id, username, password, nickname)), pings)
    case (State(accounts, pings), Pinged(timestamp, ip)) =>
      State(accounts, pings :+ PingData(timestamp, ip))
  }

}
