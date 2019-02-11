package org.sniggel.cluster

import java.io.NotSerializableException
import java.util.UUID

import akka.actor.ExtendedActorSystem
import akka.actor.typed.ActorRefResolver
import akka.serialization.{BaseSerializer, SerializerWithStringManifest}
import org.sniggel.cluster.AccountEntity._

class ClusterSerializer(val system: ExtendedActorSystem)
  extends SerializerWithStringManifest with BaseSerializer {

  import akka.actor.typed.scaladsl.adapter._

  private final val resolver = ActorRefResolver(system.toTyped)

  private final val StateManifest = "aa"
  private final val GetStateCommandManifest = "ab"
  private final val PingManifest = "ac"
  private final val CreateAccountCommandManifest = "ba"
  private final val AccountCreatedEventManifest = "bb"
  private final val PingedManifest = "bc"
  private final val PongManifest = "ca"
  private final val CreateAccountSuccessReplyManifest = "cb"

  override def manifest(msg: AnyRef): String = msg match {
    case _: State => StateManifest
    case _: GetStateCommand => GetStateCommandManifest
    case _: Ping => PingManifest
    case _: CreateAccountCommand => CreateAccountCommandManifest
    case _: AccountCreatedEvent=> AccountCreatedEventManifest
    case _: Pinged => PingedManifest
    case _: Pong => PongManifest
    case _: CreateAccountSuccessReply => CreateAccountSuccessReplyManifest
    case _ =>
      throw new IllegalArgumentException(s"Can't serialize object of type ${msg.getClass} in [${getClass.getName}]")
  }

  override def toBinary(msg: AnyRef): Array[Byte] = msg match {
    case a: State =>
      accountStateToBinary(a)
    case a: GetStateCommand =>
      getStateCommandToBinary(a)
    case a: Ping =>
      pingToBinary(a)
    case a: CreateAccountCommand =>
      createAccountCommandToBinary(a)
    case a : AccountCreatedEvent=>
      accountCreatedEventToBinary(a)
    case a : Pinged =>
      pingedToBinary(a)
    case a: Pong =>
      pongToBinary(a)
    case a: CreateAccountSuccessReply =>
      createAccountSuccessReplyToBinary(a)
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {
    case StateManifest => accountStateFromBinary(bytes)
    case GetStateCommandManifest => getStateCommandFromBinary(bytes)
    case PingManifest => pingFromBinary(bytes)
    case CreateAccountCommandManifest => createAccountFromBinary(bytes)
    case AccountCreatedEventManifest => accountCreatedFromBinary(bytes)
    case PingedManifest => pingedFromBinary(bytes)
    case PongManifest => pongFromBinary(bytes)
    case CreateAccountSuccessReplyManifest => createAccountSuccessReplyFromBinary(bytes)
    case _ =>
      throw new NotSerializableException(
        s"Unimplemented deserialization of message with manifest [$manifest] in [${getClass.getName}]")
  }

  // to binary
  private def accountStateToBinary(state: State): Array[Byte] = {
    import scala.collection.JavaConverters._
    val accounts: Iterable[protobuf.ClusterSandboxMessages.Account] = state.accounts.map(m => {
      protobuf.ClusterSandboxMessages.Account.newBuilder()
        .setId(m._2.id.toString)
        .setUsername(m._2.username)
        .setPassword(m._2.passwordHash)
        .setNickname(m._2.nickname)
        .build
    })
    val pings: Iterable[protobuf.ClusterSandboxMessages.PingData] = state.pings.map(p => {
      protobuf.ClusterSandboxMessages.PingData.newBuilder()
        .setTimestamp(p.timestamp.toString)
        .setIp(p.ip)
        .build
    })
    protobuf.ClusterSandboxMessages.State.newBuilder()
      .addAllAccounts(accounts.asJava)
      .addAllPing(pings.asJava)
      .build
      .toByteArray
  }

  private def getStateCommandToBinary(state: GetStateCommand): Array[Byte] = {
    val builder = protobuf.ClusterSandboxMessages.GetStateCommand.newBuilder()
    builder
      .setReplyTo(resolver.toSerializationFormat(state.replyTo))
      .build
      .toByteArray
  }

  private def pingToBinary(ping: Ping): Array[Byte] = {
    val builder = protobuf.ClusterSandboxMessages.Ping.newBuilder()
    builder
      .setTimestamp(ping.timestamp)
      .setIpaddress(ping.ipAddress.getOrElse("unknown"))
      .setReplyto(resolver.toSerializationFormat(ping.ReplyTo))
      .build
      .toByteArray
  }

  private def createAccountCommandToBinary(command: CreateAccountCommand): Array[Byte] = {
    val builder = protobuf.ClusterSandboxMessages.CreateAccountCommand.newBuilder()
    builder
      .setUsername(command.username)
      .setPassword(command.passwordHash)
      .setNickname(command.nickname)
      .setReplyTo(resolver.toSerializationFormat(command.replyTo))
      .build
      .toByteArray
  }

  private def accountCreatedEventToBinary(event: AccountCreatedEvent): Array[Byte] = {
    val builder = protobuf.ClusterSandboxMessages.AccountCreatedEvent.newBuilder()
    builder
      .setId(event.id.toString)
      .setUsername(event.username)
      .setPassword(event.passwordHash)
      .setNickname(event.nickname)
      .build
      .toByteArray
  }

  private def pingedToBinary(pinged: Pinged): Array[Byte] = {
    val builder = protobuf.ClusterSandboxMessages.Pinged.newBuilder()
    builder
      .setTimestamp(pinged.timestamp.toString)
      .setIp(pinged.ip)
      .build
      .toByteArray
  }

  private def pongToBinary(pong: Pong): Array[Byte] = {
    val builder = protobuf.ClusterSandboxMessages.Pong.newBuilder()
    builder
      .setTimestamp(pong.timestamp)
      .setEntityId(pong.entityId)
      .setPong(pong.pong)
      .build
      .toByteArray
  }

  private def createAccountSuccessReplyToBinary(reply: CreateAccountSuccessReply): Array[Byte] = {
    val builder = protobuf.ClusterSandboxMessages.Pong.newBuilder()
    builder
      .setTimestamp(reply.timestamp)
      .build
      .toByteArray
  }

  // from binary
  private def accountStateFromBinary(bytes: Array[Byte]): State = {
    import scala.collection.JavaConverters._
    val a = protobuf.ClusterSandboxMessages.State.parseFrom(bytes)
    val accounts: List[Account] = a.getAccountsList.asScala.toList.map(a =>
      Account(UUID.fromString(a.getId), a.getUsername, a.getPassword, a.getNickname))
    val pings: List[PingData] = a.getPingList.asScala.map(d => PingData(d.getTimestamp.toLong, d.getIp)).toList
    State(accounts.map(a => a.username -> a).toMap, pings)
  }

  private def getStateCommandFromBinary(bytes: Array[Byte]): GetStateCommand = {
    val a = protobuf.ClusterSandboxMessages.GetStateCommand.parseFrom(bytes)
    GetStateCommand(resolver.resolveActorRef(a.getReplyTo))
  }

  private def pingFromBinary(bytes: Array[Byte]): Ping = {
    val a = protobuf.ClusterSandboxMessages.Ping.parseFrom(bytes)
    Ping(a.getTimestamp, Some(a.getIpaddress), resolver.resolveActorRef(a.getReplyto))
  }

  private def createAccountFromBinary(bytes: Array[Byte]): CreateAccountCommand = {
    val a = protobuf.ClusterSandboxMessages.CreateAccountCommand.parseFrom(bytes)
    CreateAccountCommand(a.getUsername, a.getPassword, a.getNickname, resolver.resolveActorRef(a.getReplyTo))
  }

  private def accountCreatedFromBinary(bytes: Array[Byte]): AccountCreatedEvent = {
    val a = protobuf.ClusterSandboxMessages.AccountCreatedEvent.parseFrom(bytes)
    AccountCreatedEvent(UUID.fromString(a.getId), a.getUsername, a.getPassword, a.getNickname)
  }

  private def pingedFromBinary(bytes: Array[Byte]): Pinged = {
    val a = protobuf.ClusterSandboxMessages.Pinged.parseFrom(bytes)
    Pinged(a.getTimestamp.toLong, a.getIp)
  }

  private def pongFromBinary(bytes: Array[Byte]): Pong = {
    val a = protobuf.ClusterSandboxMessages.Pong.parseFrom(bytes)
    Pong(a.getTimestamp, a.getPong, a.getEntityId)
  }
  // createAccountSuccessReplyFromBinary
  private def createAccountSuccessReplyFromBinary(bytes: Array[Byte]): CreateAccountSuccessReply = {
    val a = protobuf.ClusterSandboxMessages.CreateAccountSuccessReply.parseFrom(bytes)
    CreateAccountSuccessReply(a.getTimestamp)
  }
}
