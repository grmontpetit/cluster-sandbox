package org.sniggel.cluster

import java.io.NotSerializableException
import java.util.UUID

import akka.actor.ExtendedActorSystem
import akka.actor.typed.ActorRefResolver
import akka.serialization.{BaseSerializer, SerializerWithStringManifest}
import org.sniggel.cluster.AccountEntity._

class AccountEntitySerializer(val system: ExtendedActorSystem)
  extends SerializerWithStringManifest with BaseSerializer {

  import akka.actor.typed.scaladsl.adapter._

  private final val resolver = ActorRefResolver(system.toTyped)

  // Commands
  private final val PingManifest = "ac"
  private final val CreateAccountCommandManifest = "ba"
  private final val GetStateCommandManifest = "ab"

  // Events
  private final val AccountCreatedEventManifest = "bb"
  private final val PingedManifest = "bc"

  // Replies
  private final val UserNameInvalidManifest = "da"
  private final val PasswordInvalidManifest = "db"
  private final val UsernameTakenManifest = "dc"
  private final val CreateAccountSuccessReplyManifest = "dd"
  private final val CreateAccountConflictReplyManifest = "de"
  private final val PongManifest = "ca"

  // State
  private final val StateManifest = "aa"

  override def manifest(msg: AnyRef): String = msg match {
    // Commands
    case _: Ping => PingManifest
    case _: CreateAccountCommand => CreateAccountCommandManifest
    case _: GetStateCommand => GetStateCommandManifest

    // Events
    case _: AccountCreatedEvent=> AccountCreatedEventManifest
    case _: Pinged => PingedManifest

    // Replies
    case _: UsernameInvalid => UserNameInvalidManifest
    case _: PasswordInvalid => PasswordInvalidManifest
    case _: UsernameTaken => UsernameTakenManifest
    case _: CreateAccountSuccessReply => CreateAccountSuccessReplyManifest
    case _: CreateAccountConflictReply => CreateAccountConflictReplyManifest
    case _: Pong => PongManifest

    // State
    case _: State => StateManifest
    case _ =>
      throw new IllegalArgumentException(s"Can't serialize object of type ${msg.getClass} in [${getClass.getName}]")
  }

  override def toBinary(msg: AnyRef): Array[Byte] = msg match {
    // Commands
    case a: Ping => pingToBinary(a)
    case a: CreateAccountCommand => createAccountCommandToBinary(a)
    case a: GetStateCommand => getStateCommandToBinary(a)

    // Events
    case a : AccountCreatedEvent => accountCreatedEventToBinary(a)
    case a : Pinged => pingedToBinary(a)

    // Replies
    case a: UsernameInvalid => usernameInvalidToBinary(a)
    case a: PasswordInvalid => passwordInvalidToBinary(a)
    case a: UsernameTaken => usernameTakenToBinary(a)
    case a: CreateAccountSuccessReply => createAccountSuccessReplyToBinary(a)
    case a: CreateAccountConflictReply => createAccountConflictReplyToBinary(a)
    case a: Pong => pongToBinary(a)

    // State
    case a: State => accountStateToBinary(a)
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {

    // Commands
    case PingManifest => pingFromBinary(bytes)
    case CreateAccountCommandManifest => createAccountFromBinary(bytes)
    case GetStateCommandManifest => getStateCommandFromBinary(bytes)

    // Events
    case AccountCreatedEventManifest => accountCreatedFromBinary(bytes)
    case PingedManifest => pingedFromBinary(bytes)

    // Replies
    case UserNameInvalidManifest => usernameInvalidFromBinary(bytes)
    case PasswordInvalidManifest => passwordInvalidFromBinary(bytes)
    case UsernameTakenManifest => usernameTakenFromBinary(bytes)
    case CreateAccountSuccessReplyManifest => createAccountSuccessReplyFromBinary(bytes)
    case CreateAccountConflictReplyManifest => createAccountConflictFromBinary(bytes)
    case PongManifest => pongFromBinary(bytes)

    // State
    case StateManifest => accountStateFromBinary(bytes)

    case _ =>
      throw new NotSerializableException(
        s"Unimplemented deserialization of message with manifest [$manifest] in [${getClass.getName}]")
  }

  /** to binary **/

  // Commands
  private def pingToBinary(ping: Ping): Array[Byte] = {
    val builder = protobuf.AccountEntityMessages.Ping.newBuilder()
    builder
      .setTimestamp(ping.timestamp)
      .setIpaddress(ping.ipAddress.getOrElse("unknown"))
      .setReplyto(resolver.toSerializationFormat(ping.ReplyTo))
      .build
      .toByteArray
  }

  private def createAccountCommandToBinary(command: CreateAccountCommand): Array[Byte] = {
    val builder = protobuf.AccountEntityMessages.CreateAccountCommand.newBuilder()
    builder
      .setUsername(command.username)
      .setPassword(command.password)
      .setNickname(command.nickname)
      .setReplyTo(resolver.toSerializationFormat(command.replyTo))
      .build
      .toByteArray
  }

  private def getStateCommandToBinary(state: GetStateCommand): Array[Byte] = {
    val builder = protobuf.AccountEntityMessages.GetStateCommand.newBuilder()
    builder
      .setReplyTo(resolver.toSerializationFormat(state.replyTo))
      .build
      .toByteArray
  }

  // Events
  private def accountCreatedEventToBinary(event: AccountCreatedEvent): Array[Byte] = {
    val builder = protobuf.AccountEntityMessages.AccountCreatedEvent.newBuilder()
    builder
      .setId(event.id.toString)
      .setUsername(event.username)
      .setPassword(event.password)
      .setNickname(event.nickname)
      .build
      .toByteArray
  }

  private def pingedToBinary(pinged: Pinged): Array[Byte] = {
    val builder = protobuf.AccountEntityMessages.Pinged.newBuilder()
    builder
      .setTimestamp(pinged.timestamp.toString)
      .setIp(pinged.ip)
      .build
      .toByteArray
  }

  // Replies
  private def usernameInvalidToBinary(reply: UsernameInvalid): Array[Byte] = {
    val builder = protobuf.AccountEntityMessages.UsernameInvalid.newBuilder()
    builder
      .setTimestamp(reply.timestamp)
      .build
      .toByteArray
  }

  private def passwordInvalidToBinary(reply: PasswordInvalid): Array[Byte] = {
    val builder = protobuf.AccountEntityMessages.PasswordInvalid.newBuilder()
    builder
      .setTimestamp(reply.timestamp)
      .build
      .toByteArray
  }

  private def usernameTakenToBinary(reply: UsernameTaken): Array[Byte] = {
    val builder = protobuf.AccountEntityMessages.UsernameTaken.newBuilder()
    builder
      .setTimestamp(reply.timestamp)
      .build
      .toByteArray
  }

  private def createAccountSuccessReplyToBinary(reply: CreateAccountSuccessReply): Array[Byte] = {
    val builder = protobuf.AccountEntityMessages.CreateAccountSuccessReply.newBuilder()
    builder
      .setTimestamp(reply.timestamp)
      .build
      .toByteArray
  }

  private def createAccountConflictReplyToBinary(reply: CreateAccountConflictReply): Array[Byte] = {
    val builder = protobuf.AccountEntityMessages.CreateAccountConflictReply.newBuilder()
    builder
      .setTimestamp(reply.timestamp)
      .build
      .toByteArray
  }

  // State
  private def accountStateToBinary(state: State): Array[Byte] = {
    import scala.collection.JavaConverters._
    val accounts: Iterable[protobuf.AccountEntityMessages.Account] = state.accounts.map(m => {
      protobuf.AccountEntityMessages.Account.newBuilder()
        .setId(m._2.id.toString)
        .setUsername(m._2.username)
        .setPassword(m._2.password)
        .setNickname(m._2.nickname)
        .build
    })
    val pings: Iterable[protobuf.AccountEntityMessages.PingData] = state.pings.map(p => {
      protobuf.AccountEntityMessages.PingData.newBuilder()
        .setTimestamp(p.timestamp.toString)
        .setIp(p.ip)
        .build
    })
    protobuf.AccountEntityMessages.State.newBuilder()
      .addAllAccounts(accounts.asJava)
      .addAllPing(pings.asJava)
      .build
      .toByteArray
  }

  private def pongToBinary(pong: Pong): Array[Byte] = {
    val builder = protobuf.AccountEntityMessages.Pong.newBuilder()
    builder
      .setTimestamp(pong.timestamp)
      .setEntityId(pong.entityId)
      .setPong(pong.pong)
      .build
      .toByteArray
  }

  /** from binary **/

  // Commands
  private def pingFromBinary(bytes: Array[Byte]): Ping = {
    val a = protobuf.AccountEntityMessages.Ping.parseFrom(bytes)
    Ping(a.getTimestamp, Some(a.getIpaddress), resolver.resolveActorRef(a.getReplyto))
  }

  private def createAccountFromBinary(bytes: Array[Byte]): CreateAccountCommand = {
    val a = protobuf.AccountEntityMessages.CreateAccountCommand.parseFrom(bytes)
    CreateAccountCommand(a.getUsername, a.getPassword, a.getNickname, resolver.resolveActorRef(a.getReplyTo))
  }

  private def getStateCommandFromBinary(bytes: Array[Byte]): GetStateCommand = {
    val a = protobuf.AccountEntityMessages.GetStateCommand.parseFrom(bytes)
    GetStateCommand(resolver.resolveActorRef(a.getReplyTo))
  }

  // Events
  private def accountCreatedFromBinary(bytes: Array[Byte]): AccountCreatedEvent = {
    val a = protobuf.AccountEntityMessages.AccountCreatedEvent.parseFrom(bytes)
    AccountCreatedEvent(UUID.fromString(a.getId), a.getUsername, a.getPassword, a.getNickname)
  }

  private def pingedFromBinary(bytes: Array[Byte]): Pinged = {
    val a = protobuf.AccountEntityMessages.Pinged.parseFrom(bytes)
    Pinged(a.getTimestamp.toLong, a.getIp)
  }

  // Replies
  private def usernameInvalidFromBinary(bytes: Array[Byte]): UsernameInvalid = {
    val a = protobuf.AccountEntityMessages.UsernameInvalid.parseFrom(bytes)
    UsernameInvalid(a.getTimestamp)
  }

  private def passwordInvalidFromBinary(bytes: Array[Byte]): PasswordInvalid = {
    val a = protobuf.AccountEntityMessages.PasswordInvalid.parseFrom(bytes)
    PasswordInvalid(a.getTimestamp)
  }

  private def usernameTakenFromBinary(bytes: Array[Byte]): UsernameTaken = {
    val a = protobuf.AccountEntityMessages.UsernameTaken.parseFrom(bytes)
    UsernameTaken(a.getTimestamp)
  }

  private def createAccountSuccessReplyFromBinary(bytes: Array[Byte]): CreateAccountSuccessReply = {
    val a = protobuf.AccountEntityMessages.CreateAccountSuccessReply.parseFrom(bytes)
    CreateAccountSuccessReply(a.getTimestamp)
  }

  private def createAccountConflictFromBinary(bytes: Array[Byte]): CreateAccountConflictReply = {
    val a = protobuf.AccountEntityMessages.CreateAccountConflictReply.parseFrom(bytes)
    CreateAccountConflictReply(a.getTimestamp)
  }

  private def pongFromBinary(bytes: Array[Byte]): Pong = {
    val a = protobuf.AccountEntityMessages.Pong.parseFrom(bytes)
    Pong(a.getTimestamp, a.getPong, a.getEntityId)
  }

  // State
  private def accountStateFromBinary(bytes: Array[Byte]): State = {
    import scala.collection.JavaConverters._
    val a = protobuf.AccountEntityMessages.State.parseFrom(bytes)
    val accounts: List[Account] = a.getAccountsList.asScala.toList.map(a =>
      Account(UUID.fromString(a.getId), a.getUsername, a.getPassword, a.getNickname))
    val pings: List[PingData] = a.getPingList.asScala.map(d => PingData(d.getTimestamp.toLong, d.getIp)).toList
    State(accounts.map(a => a.username -> a).toMap, pings)
  }


}
