package org.sniggel.cluster

import java.io.File

import akka.actor.typed.ActorSystem
import akka.cluster.typed.SelfUp
import akka.management.AkkaManagement
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.persistence.cassandra.testkit.CassandraLauncher
import com.datastax.driver.core.policies.DCAwareRoundRobinPolicy
import com.typesafe.config.ConfigFactory
import org.apache.logging.log4j.core.async.AsyncLoggerContextSelector
import org.apache.logging.log4j.scala.Logging

object Main extends Logging {

  val config = ConfigFactory.load()

  def main(args: Array[String]): Unit = {
    init
  }

  def init: Unit = {
    import akka.actor.typed.scaladsl.adapter._
    sys.props += "log4j2.contextSelector" -> classOf[AsyncLoggerContextSelector].getName
    val clusterName = "cluster"
    logger.info("Starting Actor System.")
    val system: ActorSystem[SelfUp] = ActorSystem(SystemGuardian(), clusterName)

    //startCassandraDatabase()
    AkkaManagement(system.toUntyped).start()
    ClusterBootstrap(system.toUntyped).start()

    logger.info(s"${system.name} started and ready to join cluster.")
  }

  private def startCassandraDatabase(): Unit = {
    val databaseDirectory = new File("target/cassandra-db")
    val roundRobin: DCAwareRoundRobinPolicy = DCAwareRoundRobinPolicy.builder()
      .withLocalDc(s"dc-${config.getString("akka.cluster.multi-data-center.self-data-center")}")
      .build()

    CassandraLauncher.start(
      databaseDirectory,
      CassandraLauncher.DefaultTestConfigResource,
      clean = false,
      port = 9042)

    // shut the cassandra instance down when the JVM stops
    sys.addShutdownHook {
      CassandraLauncher.stop()
    }
  }
}
