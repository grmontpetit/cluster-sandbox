package org.sniggel.cluster

import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.SideEffect
import akka.persistence.typed.scaladsl.{Effect, PersistentBehaviors}
import akka.persistence.typed.scaladsl.PersistentBehaviors.CommandHandler
import java.io.Serializable
import java.net.InetAddress

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.ShardRegion.EntityId
import org.apache.logging.log4j.scala.Logging

object AccountEntity extends Logging {

  // Types
  type PersistenceId = String
  type Username = String
  type Password = String
  type Nickname = String
  type IpAddress = String
  type ReplyTo = ActorRef[Reply]

  // Service Key
  final val AccountServiceKey: ServiceKey[Command] = ServiceKey[Command]("accounts")

  // Sharding
  final val ShardingTypeName: EntityTypeKey[Command] = EntityTypeKey[Command]("accounts")

  // Persistent ID
  final val PersistenceId: PersistenceId= "accounts"
  final val EntityId: EntityId = PersistenceId

  // Commands
  sealed trait Command extends Serializable
  final case class Ping(ipAddress: Option[IpAddress],
                        ReplyTo: ReplyTo) extends Command
  final case class CreateAccountCommand(username: Username,
                                        password: Password,
                                        nickname: Nickname,
                                        replyTo: ReplyTo) extends Command
  final case class GetStateCommand(replyTo: ReplyTo) extends Command
  final case object PassivateAccount extends Command

  // Events
  sealed trait Event
  final case class AccountCreatedEvent(username: Username,
                                       password: Password,
                                       nickname: Nickname) extends Event
                          // circe doesn't support InetAddress or Option TODO add custom json formats
  final case class Pinged(ip: IpAddress) extends Event

  // Replies
  sealed trait Reply
  final case object CreateAccountSuccessReply extends Reply
  final case object CreateAccountConflictReply extends Reply
  final case class Pong(pong: String = "PONG",
                        entityId: EntityId) extends Reply

  // State
  final case class Account(username: Username,
                           password: Password,
                           nickname: Nickname)
  final case class State(accounts: Map[Username, Account] = Map.empty[Username, Account],
                         pings: List[IpAddress] = List.empty[IpAddress]) extends Reply

  def apply(): String => Behavior[Command] = { id =>
    logger.info(s"Setting up $id")
    Behaviors.setup { context =>
      context.system.receptionist ! Receptionist.Register(AccountServiceKey, context.self)
      logger.info(s"Registered AccountEntity with Receptionist $AccountServiceKey")
      logger.info(s"Creating persistent behavior persistenceId=$PersistenceId id=$id")
      PersistentBehaviors
        .receive[Command, Event, State](PersistenceId, State(),
        commandHandler(),
        eventHandler())
    }
  }

  def commandHandler(): CommandHandler[Command, Event, State] = {
    case (state, CreateAccountCommand(username, password, nickname, replyTo)) =>
      logger.info(s"Received a CreateAccountCommand for username: $username")
      // Check state to see if the account already exist
      state.accounts.get(username)
        .fold(persistAndReply(username, password, nickname, replyTo)) { _ =>
          Effect
            .none
            .andThen(SideEffect[State](_ => replyTo ! CreateAccountConflictReply))
        }
    case (_, Ping(ipAddress, replyTo)) =>
      logger.info(s"Received a Ping command on my entity: $EntityId")
      val pingedEvent = Pinged(ipAddress.getOrElse(InetAddress.getLoopbackAddress).toString)
      Effect
        .persist(pingedEvent)
        .andThen(SideEffect[State](_ => replyTo ! Pong("PONG", EntityId)))
    case (state, GetStateCommand(replyTo)) =>
      logger.info(s"Received a GetStateCommand on my entity $EntityId")
      Effect
        .none
        .andThen(SideEffect[State](_ => replyTo ! state))
    case (s, PassivateAccount) =>
      logger.info(s"Passivating account state $s")
      Effect.stop
  }

  private def persistAndReply(username: Username,
                              password: Password,
                              nickname: Nickname,
                              replyTo: ReplyTo): Effect[Event, State] = {
    val accountCreatedEvent = AccountCreatedEvent(username, password, nickname)
    logger.info(s"Persisting event $accountCreatedEvent, replyTo: ${replyTo.path}")
    Effect
      // Persist the event
      .persist(accountCreatedEvent)
      // Reply to the Actor that sent the command
      .andThen(SideEffect[State](_ => replyTo ! CreateAccountSuccessReply))
  }

  def eventHandler(): (State, Event) => State = {
    case (State(accounts, pings), AccountCreatedEvent(username, password, nickname)) =>
      logger.info(s"Updating current state with new account $username")
      // Append to the Entity State
      State(accounts + (username -> Account(username, password, nickname)), pings)
    case (State(accounts, pings), Pinged(ip)) =>
      State(accounts, pings :+ ip)
  }

}
