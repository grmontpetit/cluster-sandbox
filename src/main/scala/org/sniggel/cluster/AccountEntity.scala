package org.sniggel.cluster

import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.SideEffect
import akka.persistence.typed.scaladsl.{Effect, PersistentBehaviors}
import akka.persistence.typed.scaladsl.PersistentBehaviors.CommandHandler
import java.io.Serializable

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import org.apache.logging.log4j.scala.Logging

object AccountEntity extends Logging {

  // Service Key
  final val AccountServiceKey: ServiceKey[Command] = ServiceKey[Command]("account-entity")

  // Sharding
  final val ShardingTypeName: EntityTypeKey[Command] = EntityTypeKey[Command]("account-entity")

  // Persistent ID
  final val PersistenceId = "AccountEntity"
  final val EntityId = PersistenceId

  // Types
  type Username = String
  type Password = String
  type ReplyTo = ActorRef[Reply]

  // Commands
  sealed trait Command extends Serializable
  final case object TriggerSomething extends Command
  final case class CreateAccountCommand(username: Username,
                                        password: Password,
                                        replyTo: ReplyTo) extends Command
  final case object PassivateAccount extends Command

  // Events
  sealed trait Event
  final case class AccountCreatedEvent(username: Username,
                                       password: Password) extends Event

  // Replies
  sealed trait Reply
  final case object CreateAccountSuccessReply extends Reply
  final case object CreateAccountConflictReply extends Reply

  // State
  final case class Account(username: Username,
                           password: Password)
  final case class State(accounts: Map[Username, Account] = Map.empty[Username, Account])

  def apply(): String => Behavior[Command] = { id =>
    logger.info(s"Setting up AccountEntity $id")
    Behaviors.setup { context =>
      logger.info(s"Registering AccountEntity with Receptionist $AccountServiceKey")
      context.system.receptionist ! Receptionist.Register(AccountServiceKey, context.self)
      PersistentBehaviors
        .receive[Command, Event, State](PersistenceId + id, State(),
        commandHandler(),
        eventHandler())
    }
  }

  def commandHandler(): CommandHandler[Command, Event, State] = {
    case (state, CreateAccountCommand(username, password, replyTo)) =>
      // Check state to see if the account already exist
      state.accounts.get(username)
        .fold(persistAndReply(username, password, replyTo)) { _ =>
          Effect
            .none
            .andThen(SideEffect[State](_ => replyTo ! CreateAccountConflictReply))
        }
    case (_, TriggerSomething) =>
      logger.info(s"Received a command on my entity: $EntityId")
      Effect.none
    case (_, PassivateAccount) =>
      Effect.stop
  }

  private def persistAndReply(username: Username,
                              password: Password,
                              replyTo: ReplyTo): Effect[Event, State] = {
    val accountCreatedEvent = AccountCreatedEvent(username, password)
    Effect
      // Persist the event
      .persist(accountCreatedEvent)
      // Reply to the Actor that sent the command
      .andThen(SideEffect[State](_ => replyTo ! CreateAccountSuccessReply))
  }

  def eventHandler(): (State, Event) => State = {
    case (State(accounts), AccountCreatedEvent(username, password)) =>
      // Append to the Entity State
      State(accounts + (username -> Account(username, password)))
  }

}
