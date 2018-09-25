package org.sniggel.cluster

import akka.actor.typed.{ActorSystem, Props}
import akka.cluster.sharding.typed.ClusterShardingSettings
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.typed.SelfUp
import akka.management.AkkaManagement
import akka.management.cluster.bootstrap.ClusterBootstrap
import org.sniggel.cluster.AccountEntity.Command

object Main {

  def main(args: Array[String]): Unit = {
    startNode
  }

  private def startEntities(sharding: ClusterSharding,
                            system: ActorSystem[SelfUp]): Unit = {
    startAccountEntity(sharding, system)
  }

  private def startAccountEntity(sharding: ClusterSharding,
                                 system: ActorSystem[SelfUp]): Unit = {
    sharding.spawn[Command](
      AccountEntity(),
      Props.empty,
      AccountEntity.ShardingTypeName,
      ClusterShardingSettings(system),
      100,
      AccountEntity.PassivateAccount)
  }

  def startNode: Unit = {
    import akka.actor.typed.scaladsl.adapter._
    val clusterName = "cluster"

    val system: ActorSystem[SelfUp] = ActorSystem(SystemGuardian(), clusterName)

    AkkaManagement(system.toUntyped).start()
    ClusterBootstrap(system.toUntyped).start()

    val sharding: ClusterSharding = ClusterSharding(system)
    sharding.defaultShardAllocationStrategy(ClusterShardingSettings.apply(system))
    startEntities(sharding, system)

  }

}
