package org.sniggel.cluster

import akka.actor.CoordinatedShutdown
import akka.actor.CoordinatedShutdown.ClusterDowningReason
import akka.actor.typed.{Behavior, Terminated}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.typed.{Cluster, SelfUp, Subscribe}
import akka.cluster.sharding.typed.ClusterShardingSettings
import akka.cluster.sharding.typed.scaladsl.ShardedEntity
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.Materializer
import akka.stream.typed.scaladsl.ActorMaterializer
import org.apache.logging.log4j.scala.Logging

object SystemGuardian extends Logging {

  import akka.actor.typed.scaladsl.adapter._

  def apply(): Behavior[SelfUp] =
    Behaviors.setup { context =>
      val system = context.system
      Cluster(context.system).subscriptions ! Subscribe(context.self, classOf[SelfUp])
      context.self ! SelfUp(Cluster(context.system).state)
      Behaviors.receiveMessage[SelfUp] { _ =>
        val sharding: ClusterSharding = ClusterSharding(system)
        sharding.defaultShardAllocationStrategy(ClusterShardingSettings.apply(system))
        startEntities(sharding, system.toUntyped)
        startApi(context.system.toUntyped)
        Behaviors.empty
      }
      .receiveSignal {
        case (_, Terminated(_)) =>
          CoordinatedShutdown(context.system.toUntyped)
            .run(ClusterDowningReason)
          Behaviors.same
      }
    }

  private def startEntities(sharding: ClusterSharding,
                            system: akka.actor.ActorSystem): Unit = {
    startAccountEntity(sharding, system)
  }

  private def startApi(system: akka.actor.ActorSystem): Unit = {
    implicit val untypedSystem: akka.actor.ActorSystem = system
    implicit val mat: Materializer            = ActorMaterializer()(system.toTyped)
    implicit val readJournal: CassandraReadJournal =
      PersistenceQuery(untypedSystem)
        .readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)
    Api()
  }

  private def startAccountEntity(sharding: ClusterSharding,
                                 system: akka.actor.ActorSystem): Unit = {
    logger.info(s"Starting Shard for ${AccountEntity.ShardingTypeName}")
    val shardedEntity = ShardedEntity(AccountEntity(), AccountEntity.ShardingTypeName, AccountEntity.PassivateAccount)
    sharding.start(shardedEntity)
  }
}
