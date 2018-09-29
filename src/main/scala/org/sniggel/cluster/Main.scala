package org.sniggel.cluster

import akka.actor.typed.ActorSystem
import akka.cluster.typed.SelfUp
import akka.management.AkkaManagement
import akka.management.cluster.bootstrap.ClusterBootstrap
import org.apache.logging.log4j.core.async.AsyncLoggerContextSelector
import org.apache.logging.log4j.scala.Logging

object Main extends Logging {

  def main(args: Array[String]): Unit = {
    init
  }

  def init: Unit = {
    import akka.actor.typed.scaladsl.adapter._
    sys.props += "log4j2.contextSelector" -> classOf[AsyncLoggerContextSelector].getName
    val clusterName = "cluster"
    logger.info("Starting Actor System.")
    val system: ActorSystem[SelfUp] = ActorSystem(SystemGuardian(), clusterName)

    AkkaManagement(system.toUntyped).start()
    ClusterBootstrap(system.toUntyped).start()

    logger.info(s"${system.name} started and ready to join cluster.")
  }

}
