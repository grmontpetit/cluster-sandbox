package org.sniggel.cluster

import java.io.NotSerializableException

import akka.actor.ExtendedActorSystem
import akka.actor.typed.ActorRefResolver
import akka.serialization.{BaseSerializer, SerializerWithStringManifest}
import org.sniggel.cluster.AccountEntity._
import org.sniggel.cluster.protobuf

class ClusterSerializer(system: ExtendedActorSystem)
  extends SerializerWithStringManifest with BaseSerializer {

  import akka.actor.typed.scaladsl.adapter._

  private final val resolver = ActorRefResolver(system.toTyped)

  private final val GetStateManifest = "aa"
  private final val PingManifest = "ab"
  private final val CreateAccountManifest = "ba"
  private final val AccountCreatedManifest = "ac"
  private final val PingedManifest = "cb"
  private final val PongManifest = "cc"

  override def manifest(msg: AnyRef): String = msg match {
    case _: GetStateCommand => GetStateManifest
    case _: Ping => PingManifest
    case _: CreateAccountCommand => CreateAccountManifest
    case _: AccountCreatedEvent=> AccountCreatedManifest
    case _: Pinged => PingedManifest
    case _: Pong => PongManifest
    case _ =>
      throw new IllegalArgumentException(s"Can't serialize object of type ${msg.getClass} in [${getClass.getName}]")
  }

  override def toBinary(msg: AnyRef): Array[Byte] = msg match {
    case a: GetStateCommand =>
      accountStateToBinary(a)
    case a: Ping =>
      pingToBinary(a)
    case a: CreateAccountCommand =>
      createAccountToBinary(a)
    case a : AccountCreatedEvent=>
      accountCreatedToBinary(a)
    case a : Pinged =>
      pingedToBinary(a)
    case a: Pong =>
      pongToBinary(a)
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {
    case GetStateManifest => accountStateFromBinary(bytes)
    case PingManifest => pingFromBinary(bytes)
    case CreateAccountManifest => createAccountFromBinary(bytes)
    case AccountCreatedManifest => accountCreatedFromBinary(bytes)
    case PingedManifest => pingedFromBinary(bytes)
    case PongManifest => pongFromBinary(bytes)
    case _ =>
      throw new NotSerializableException(
        s"Unimplemented deserialization of message with manifest [$manifest] in [${getClass.getName}]")
  }

  // to binary
  private def accountStateToBinary(command: GetStateCommand): Array[Byte] = {
    val a = protobuf.ClusterSandboxMessages.GetStateCommand
    ???
  }

  private def pingToBinary(ping: Ping): Array[Byte] = {
    ???
  }

  private def createAccountToBinary(command: CreateAccountCommand): Array[Byte] = {
    ???
  }

  private def accountCreatedToBinary(event: AccountCreatedEvent): Array[Byte] = {
    ???
  }

  private def pingedToBinary(pinged: Pinged): Array[Byte] = {
    ???
  }

  private def pongToBinary(pong: Pong): Array[Byte] = {
    ???
  }

  // from binary
  private def accountStateFromBinary(bytes: Array[Byte]): State = {
    ???
  }

  private def pingFromBinary(bytes: Array[Byte]): Ping = {
    ???
  }

  private def createAccountFromBinary(bytes: Array[Byte]): CreateAccountCommand = {
    ???
  }

  private def accountCreatedFromBinary(bytes: Array[Byte]): AccountCreatedEvent = {
    ???
  }

  private def pingedFromBinary(bytes: Array[Byte]): Pinged = {
    ???
  }

  private def pongFromBinary(bytes: Array[Byte]): Pong = {
    ???
  }
}
