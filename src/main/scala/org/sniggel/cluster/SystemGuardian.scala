package org.sniggel.cluster

import akka.actor.CoordinatedShutdown
import akka.actor.CoordinatedShutdown.ClusterDowningReason
import akka.actor.typed.{Behavior, Terminated}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.typed.{Cluster, SelfUp, Subscribe}
import akka.actor.typed.scaladsl.adapter._

object SystemGuardian {

  def apply(): Behavior[SelfUp] =
    Behaviors.setup { context =>
      Cluster(context.system).subscriptions ! Subscribe(context.self, classOf[SelfUp])

      Behaviors.receiveMessage[SelfUp] { _ =>
        Behaviors.empty
      }
      .receiveSignal {
        case (_, Terminated(_)) =>
          CoordinatedShutdown(context.system.toUntyped)
            .run(ClusterDowningReason)
          Behaviors.same
      }
    }

}
