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

import java.io.NotSerializableException
import java.util.UUID

import akka.actor.ExtendedActorSystem
import akka.actor.typed.ActorRefResolver
import akka.serialization.{BaseSerializer, SerializerWithStringManifest}
import org.sniggel.cluster.Authenticator._

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
    case a: AddCredentials => addCredentialsToBinary(a)
    case a: Authenticate => authenticateToBinary(a)

    // Events

    // Replies
    case a: InvalidCredentials => invalidCredentialsToBinary(a)
    case a: Authenticated => authenticatedToBinary(a)

    // State

  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {
    // Commands
    case AddCredentialsManifest => addCredentialsFromBinary(bytes)
    case AuthenticateManifest => authenticateFromBinary(bytes)

    // Events

    // Replies
    case InvalidCredentialsManifest => invalidCredentialsFromBinary(bytes)
    case AuthenticatedManifest => authenticatedFromBinary(bytes)

    // State

    case _ =>
      throw new NotSerializableException(
        s"Unimplemented deserialization of message with manifest [$manifest] in [${getClass.getName}]")
  }

  /** to binary **/
  // Commands
  private def addCredentialsToBinary(addCredentials: AddCredentials): Array[Byte] = {
    val builder = protobuf.AuthenticatorMessages.AddCredentials.newBuilder()
    builder
      .setId(addCredentials.id.toString)
      .setPassword(addCredentials.passwordHash)
      .setReplyTo(resolver.toSerializationFormat(addCredentials.replyTo))
      .setSeqNo(addCredentials.seqNo)
      .setUsername(addCredentials.username)
      .build
      .toByteArray
  }

  private def authenticateToBinary(authenticate: Authenticate): Array[Byte] = {
    val builder = protobuf.AuthenticatorMessages.Authenticate.newBuilder()
    builder
      .setUsername(authenticate.username)
      .setPassword(authenticate.password)
      .setReplyTo(resolver.toSerializationFormat(authenticate.replyTo))
      .build
      .toByteArray
  }

  // Events
  private def invalidCredentialsToBinary(credentials: InvalidCredentials): Array[Byte] = {
    val builder = protobuf.AuthenticatorMessages.InvalidCredentials.newBuilder()
    builder
      .setTimestamp(credentials.timestamp)
      .build
      .toByteArray
  }

  private def authenticatedToBinary(auth: Authenticated): Array[Byte] = {
    val builder = protobuf.AuthenticatorMessages.Authenticated.newBuilder()
    builder
      .setUsername(auth.username)
      .build
      .toByteArray
  }

  // Replies

  // State

  /** from binary **/
  // Commands
  private def addCredentialsFromBinary(bytes: Array[Byte]): AddCredentials = {
    val a = protobuf.AuthenticatorMessages.AddCredentials.parseFrom(bytes)
    AddCredentials(a.getSeqNo, UUID.fromString(a.getId), a.getUsername, a.getPassword, resolver.resolveActorRef(a.getReplyTo))
  }

  private def authenticateFromBinary(bytes: Array[Byte]): Authenticate = {
    val a = protobuf.AuthenticatorMessages.Authenticate.parseFrom(bytes)
    Authenticate(a.getUsername, a.getPassword, resolver.resolveActorRef(a.getReplyTo))
  }

  // Events

  // Replies
  private def invalidCredentialsFromBinary(bytes: Array[Byte]): InvalidCredentials = {
    val a = protobuf.AuthenticatorMessages.InvalidCredentials.parseFrom(bytes)
    InvalidCredentials(a.getTimestamp)
  }

  private def authenticatedFromBinary(bytes: Array[Byte]): Authenticated = {
    val a = protobuf.AuthenticatorMessages.Authenticate.parseFrom(bytes)
    Authenticated(a.getUsername)
  }

  // State

}
