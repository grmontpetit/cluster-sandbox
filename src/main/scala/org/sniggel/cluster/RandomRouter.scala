package org.sniggel.cluster

import java.util.concurrent.ThreadLocalRandom

import akka.actor.Address
import akka.actor.typed.receptionist.Receptionist.Listing
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.ClusterEvent.ReachabilityEvent
import akka.cluster.typed.{Cluster, Subscribe}

object RandomRouter {

  def clusterRouter[T](serviceKey: ServiceKey[T]): Behavior[T] =
    Behaviors.setup[Any] { ctx =>
      ctx.system.receptionist ! Receptionist.Subscribe(serviceKey, ctx.self)

      val cluster = Cluster(ctx.system)
      val reachabilityAdapter: ActorRef[ReachabilityEvent] = ctx.messageAdapter(WrappedReachabilityEvent.apply)
      cluster.subscriptions ! Subscribe(reachabilityAdapter, classOf[ReachabilityEvent])

      def routingBehavior(routees: Vector[ActorRef[T]], unreachable: Set[Address]): Behavior[Any] =
        Behaviors.receiveMessage {
            case l: Listing =>
              routingBehavior(l.serviceInstances(serviceKey).toVector, unreachable)
            case WrappedReachabilityEvent(event) => event match {
              case akka.cluster.ClusterEvent.UnreachableMember(member) =>
                routingBehavior(routees, unreachable + member.address)
              case akka.cluster.ClusterEvent.ReachableMember(member) =>
                routingBehavior(routees, unreachable - member.address)
            }
            case other: T @unchecked =>
              if (routees.isEmpty) Behavior.unhandled
              else {
                val reachableRoutes =
                  if (unreachable.isEmpty) routees
                  else routees.filterNot(r => unreachable(r.path.address))
                val i = ThreadLocalRandom.current.nextInt(reachableRoutes.size)
                reachableRoutes(i) ! other
                Behavior.same
              }
        }
          routingBehavior(Vector.empty, Set.empty)
    }.narrow[T]

  private final case class WrappedReachabilityEvent(event: ReachabilityEvent)

}
