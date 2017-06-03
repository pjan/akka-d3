package akka.contrib.d3.readside

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.cluster.singleton._
import akka.contrib.d3.ReadSideProcessorSettings
import akka.pattern.BackoffSupervisor

import scala.concurrent.duration._

private[d3] final class ClusterReadSideProvider(
    system: ExtendedActorSystem
) extends ReadSideProvider {
  override def startReadSideCoordinator(
    coordinatorProps:    Props,
    minBackoff:          FiniteDuration,
    maxBackoff:          FiniteDuration,
    randomBackoffFactor: Double
  ): ActorRef = {
    val backoffProps = BackoffSupervisor.propsWithSupervisorStrategy(
      childProps = coordinatorProps,
      childName = "coordinator",
      minBackoff = minBackoff,
      maxBackoff = maxBackoff,
      randomFactor = randomBackoffFactor,
      strategy = SupervisorStrategy.stoppingStrategy
    )

    val singletonProps = ClusterSingletonManager.props(
      singletonProps = backoffProps,
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system)
    )

    val singleton = system.actorOf(singletonProps, "readside-coordinator")

    val singletonProxy = system.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = singleton.path.toStringWithoutAddress,
        settings = ClusterSingletonProxySettings(system)
      ), s"readside-coordinator-proxy"
    )

    singletonProxy
  }

  override def startReadSideActor(
    name:       String,
    actorProps: Props,
    settings:   ReadSideProcessorSettings
  ): ActorRef = {
    val role: Option[String] = settings.config.getString("run-on-role") match {
      case "" ⇒ None
      case r  ⇒ Some(r)
    }

    val extractEntityId: ShardRegion.ExtractEntityId = {
      case msg: ReadSideActor.Message ⇒ (msg.name, msg)
    }
    val extractShardId: ShardRegion.ExtractShardId = {
      case msg: ReadSideActor.Message ⇒ msg.name
    }

    val clusterShardingSettings = ClusterShardingSettings(system).withRole(role)

    val sharding = ClusterSharding(system)

    if (role.exists(Cluster(system).getSelfRoles.contains)) {
      val shardRegion = sharding.start(
        name,
        actorProps,
        clusterShardingSettings,
        extractEntityId,
        extractShardId
      )

      shardRegion ! ReadSideActor.WakeUp(name)

      shardRegion
    } else {
      sharding.startProxy(
        name,
        role,
        extractEntityId,
        extractShardId
      )
    }
  }
}
