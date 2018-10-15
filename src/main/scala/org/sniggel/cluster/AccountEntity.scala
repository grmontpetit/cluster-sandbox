package org.sniggel.cluster

import java.io.Serializable
import java.net.InetAddress
import java.util.UUID

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.Cluster
import akka.cluster.ddata._
import akka.cluster.ddata.typed.scaladsl.Replicator
import akka.cluster.sharding.ShardRegion.EntityId
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.SideEffect
import akka.persistence.typed.scaladsl.PersistentBehaviors.CommandHandler
import akka.persistence.typed.scaladsl.{Effect, PersistentBehaviors}
import org.apache.logging.log4j.scala.Logging

object AccountEntity extends Logging {

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

  // Data Replication - Data replication should only be used with entities in separate data-centers
  @SerialVersionUID(1L)
  val ReplicatorKey = AccountStateReplicationKey(PersistenceId)

  // Data Replicator events
  private sealed trait InternalMsg extends Command
  private final case class InternalUpdateResponse[A <: ReplicatedData](rsp: Replicator.UpdateResponse[A]) extends InternalMsg
  private final case class InternalGetResponse[A <: ReplicatedData](rsp: Replicator.GetResponse[A]) extends InternalMsg

  private object AccountStateReplication {
    def empty: ORSet[Event] = ORSet.empty[Event]
  }

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
  final case object CreateAccountSuccessReply extends Reply
  final case object CreateAccountConflictReply extends Reply
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
      // The ddata types still need the implicit untyped Cluster.
      // We will look into another solution for that.
      implicit val cluster: Cluster = akka.cluster.Cluster(context.system.toUntyped)
      val replicator: ActorRef[Replicator.Command] =
        DistributedData(context.system.toUntyped).replicator

      // use message adapters to map the externaStatel messages (replies) to the message types
      // that this actor can handle (see InternalMsg).
      // DData Replicator messages will be sent to this message adapter
      val updateResponseAdapter: ActorRef[Replicator.UpdateResponse[ORSet[Event]]] =
        context.messageAdapter(InternalUpdateResponse.apply)
      val getResponseAdapter: ActorRef[Replicator.GetResponse[ORSet[Event]]] =
        context.messageAdapter(InternalGetResponse.apply)

      context.system.receptionist ! Receptionist.Register(AccountServiceKey, context.self)
      logger.info(s"Registered AccountEntity with Receptionist $AccountServiceKey")
      logger.info(s"Creating persistent behavior persistenceId=$PersistenceId id=$id")
      PersistentBehaviors
        .receive[Command, Event, State](PersistenceId, State(),
        commandHandler(replicator, updateResponseAdapter, getResponseAdapter),
        eventHandler(replicator, updateResponseAdapter, getResponseAdapter))
    }
  }

  def commandHandler(replicator: ActorRef[Replicator.Command],
                     updateResponseAdapter: ActorRef[Replicator.UpdateResponse[ORSet[Event]]],
                     getResponseAdapter: ActorRef[Replicator.GetResponse[ORSet[Event]]]): CommandHandler[Command, Event, State] = {
    case (state, CreateAccountCommand(username, password, nickname, replyTo)) =>
      logger.info(s"Received a CreateAccountCommand for username: $username")
      // Check state to see if the account already exist
      state.accounts.get(username)
        .fold(persistAndReply(username, password, nickname, replyTo)) { _ =>
          Effect
            .none
            .andThen(SideEffect[State](_ => replyTo ! CreateAccountConflictReply))
        }
    case (_, Ping(timestamp, ipAddress, replyTo)) =>
      logger.info(s"Received a Ping command on my entity: $EntityId")
      val pingedEvent = Pinged(timestamp, ipAddress.getOrElse(InetAddress.getLoopbackAddress).toString)
      Effect
        .persist(pingedEvent)
        .andThen(SideEffect[State](_ =>
          replicator ! Replicator.Update(key = ReplicatorKey,
                                         initial = AccountStateReplication.empty,
                                         writeConsistency = Replicator.WriteLocal,
                                         replyTo = updateResponseAdapter)(x => x)))
        .andThen(SideEffect[State](_ => replyTo ! Pong(timestamp, "PONG", EntityId)))
    case (state, GetStateCommand(replyTo)) =>
      logger.info(s"Received a GetStateCommand on my entity $EntityId")
      Effect
        .none
        .andThen(SideEffect[State](_ => replicator ! Replicator.Get(key = ReplicatorKey,
                                                                    consistency = Replicator.ReadLocal,
                                                                    replyTo = getResponseAdapter,
                                                                    request = None)))
        .andThen(SideEffect[State](_ => replyTo ! state))
    case (s, PassivateAccount) =>
      logger.info(s"Passivating account state $s")
      Effect.stop

    case (_, internal: InternalMsg) => internal match {
      case InternalUpdateResponse(_) =>
        logger.info("Received a InternalUpdateResponse.")
        Effect.none
      case InternalGetResponse(rsp @ Replicator.GetSuccess(ReplicatorKey, Some(replyTo: ActorRef[Command] @unchecked))) =>
        val value: Set[Event] = rsp.get(ReplicatorKey).elements
        logger.info(s"Received an InternalGetResponse ${value.mkString}")
        Effect.none
      case InternalGetResponse(_) =>
        logger.info("Received an InternalGetResponse.")
        // not dealing with failures
        Effect.none
    }
  }

  private def persistAndReply(username: Username,
                              password: Password,
                              nickname: Nickname,
                              replyTo: ReplyTo): Effect[Event, State] = {
    val id = UUID.randomUUID
    val accountCreatedEvent = AccountCreatedEvent(id, username, password, nickname)
    logger.info(s"Persisting event $accountCreatedEvent, replyTo: ${replyTo.path}")
    Effect
      // Persist the event
      .persist(accountCreatedEvent)
      // Reply to the Actor that sent the command
      .andThen(SideEffect[State](_ => replyTo ! CreateAccountSuccessReply))
  }

  def eventHandler(replicator: ActorRef[Replicator.Command],
                   updateResponseAdapter: ActorRef[Replicator.UpdateResponse[ORSet[Event]]],
                   getResponseAdapter: ActorRef[Replicator.GetResponse[ORSet[Event]]]): (State, Event) => State = {
    case (State(accounts, pings), AccountCreatedEvent(id, username, password, nickname)) =>
      logger.info(s"Updating current state with new account $username")
      // Append to the Entity State
      State(accounts + (username -> Account(id, username, password, nickname)), pings)
    case (State(accounts, pings), Pinged(timestamp, ip)) =>
      State(accounts, pings :+ PingData(timestamp, ip))
  }

}
