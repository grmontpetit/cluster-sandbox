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

import akka.actor.CoordinatedShutdown
import akka.actor.CoordinatedShutdown.ClusterDowningReason
import akka.actor.typed.{ActorRef, Behavior, Props, Terminated}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.typed._
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef, ShardedEntity}
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.Materializer
import akka.stream.typed.scaladsl.ActorMaterializer
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.ExecutionContext

object SystemGuardian extends Logging {

  import akka.actor.typed.scaladsl.adapter._

  def apply(): Behavior[SelfUp] =
    Behaviors.setup { context =>
      val system = context.system

      Cluster(context.system).subscriptions ! Subscribe(context.self, classOf[SelfUp])

      // Initiate SelfUp by sending a message to self
      context.self ! SelfUp(Cluster(context.system).state)
      Behaviors.receiveMessage[SelfUp] { _ =>
        val sharding: ClusterSharding = ClusterSharding(system)
        sharding.defaultShardAllocationStrategy(ClusterShardingSettings.apply(system))

        // Create account entity shard
        accountEntityShard(sharding, context.system.toUntyped)

        val accounts: EntityRef[AccountEntity.Command] =
          ClusterSharding(system).entityRefFor(AccountEntity.ShardingTypeName, AccountEntity.EntityId)

        startApi(context.system.toUntyped, accounts)
        Behaviors.empty
      }
      .receiveSignal {
        case (_, Terminated(_)) =>
          CoordinatedShutdown(context.system.toUntyped)
            .run(ClusterDowningReason)
          Behaviors.same
      }
    }

  private def startApi(system: akka.actor.ActorSystem,
                       accounts: EntityRef[AccountEntity.Command]): Unit = {
    implicit val untypedSystem: akka.actor.ActorSystem = system
    implicit val mat: Materializer = ActorMaterializer()(system.toTyped)
    implicit val readJournal: CassandraReadJournal =
      PersistenceQuery(untypedSystem)
        .readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)
    implicit val context: ExecutionContext = system.dispatcher
    val authenticator = ClusterSingleton(system.toTyped)
      .spawn(Authenticator(),
        "authenticator",
        Props.empty,
        ClusterSingletonSettings(system.toTyped),
        Authenticator.StopAuthenticator)
    Api(accounts, authenticator)
  }

  private def accountEntityShard(sharding: ClusterSharding,
                                 system: akka.actor.ActorSystem): ActorRef[ShardingEnvelope[AccountEntity.Command]] = {
    logger.info(s"Starting Shard for ${AccountEntity.ShardingTypeName}")
    val shardedEntity = ShardedEntity(
      AccountEntity(),
      AccountEntity.ShardingTypeName,
      AccountEntity.PassivateAccount)
    sharding.start(shardedEntity)
  }
}
