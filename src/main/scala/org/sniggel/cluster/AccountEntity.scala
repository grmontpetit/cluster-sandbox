package org.sniggel.cluster

import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.SideEffect
import akka.persistence.typed.scaladsl.{Effect, PersistentBehavior, PersistentBehaviors}
import akka.persistence.typed.scaladsl.PersistentBehaviors.CommandHandler
import java.io.Serializable

object AccountEntity {

  // Sharding
  val ShardingTypeName = EntityTypeKey[Command]("account-entity")

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
    println("starting Account Entity")
    PersistentBehaviors
      .receive(PersistenceId + id, State(), commandHandler(), eventHandler())
  }

  def commandHandler(): CommandHandler[Command, Event, State] = {
    case (_, State(accounts), CreateAccountCommand(username, password, replyTo)) =>
      // Check state to see if the account already exist
      accounts.get(username)
        .fold(persistAndReply(username, password, replyTo)) { _ =>
          Effect
            .none
            .andThen(SideEffect[State](_ => replyTo ! CreateAccountConflictReply))
        }
    case (_, State(_), TriggerSomething) =>
      println(s"Received a command on my entity: $EntityId")
      Effect.none
    case (_, State(_), PassivateAccount) =>
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
