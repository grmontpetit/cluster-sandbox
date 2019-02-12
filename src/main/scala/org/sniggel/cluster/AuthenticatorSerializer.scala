package org.sniggel.cluster

import java.io.NotSerializableException

import akka.actor.ExtendedActorSystem
import akka.actor.typed.ActorRefResolver
import akka.serialization.{BaseSerializer, SerializerWithStringManifest}
import org.sniggel.cluster.Authenticator.{AddCredentials, Authenticate, Authenticated, InvalidCredentials}

class AuthenticatorSerializer(val system: ExtendedActorSystem)
  extends SerializerWithStringManifest with BaseSerializer {

  import akka.actor.typed.scaladsl.adapter._

  private final val resolver = ActorRefResolver(system.toTyped)

  // Commands
  private final val AddCredentialsManifest = "za"
  private final val AuthenticateManifest = "zb"

  // Events

  // Replies
  private final val InvalidCredentialsManifest = "zc"
  private final val AuthenticatedManifest = "zd"

  // State

  override def manifest(msg: AnyRef): String = msg match {
    // Commands
    case _: AddCredentials => AddCredentialsManifest
    case _: Authenticate => AuthenticateManifest

    // Events

    // Replies
    case _: InvalidCredentials => InvalidCredentialsManifest
    case _: Authenticated => AuthenticatedManifest

    // State

    case _ =>
      throw new IllegalArgumentException(s"Can't serialize object of type ${msg.getClass} in [${getClass.getName}]")
  }

  override def toBinary(msg: AnyRef): Array[Byte] = msg match {
    // Commands
    case a: AddCredentials => ???
    case a: Authenticate => ???

    // Events

    // Replies
    case a: InvalidCredentials => ???
    case a: Authenticated => ???

    // State

  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {
    // Commands
    case AddCredentialsManifest => ???
    case AuthenticateManifest => ???

    // Events

    // Replies
    case InvalidCredentialsManifest => ???
    case AuthenticatedManifest => ???

    // State

    case _ =>
      throw new NotSerializableException(
        s"Unimplemented deserialization of message with manifest [$manifest] in [${getClass.getName}]")
  }
}
