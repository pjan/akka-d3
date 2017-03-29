package akka.contrib.d3.readside

import akka.actor._
import akka.contrib.d3.ReadSideProcessorSettings
import akka.contrib.d3.utils._
import akka.pattern.BackoffSupervisor

import scala.concurrent.duration.FiniteDuration

private[d3] object LocalReadSideProvider {
  final val SingletonName = "supervisor"
}

private[d3] final class LocalReadSideProvider(
    system: ExtendedActorSystem
) extends ReadSideProvider {
  import LocalReadSideProvider._

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

    val singletonProps = LocalSingletonManager.props(
      singletonProps = backoffProps,
      settings = LocalSingletonManagerSettings(
        singletonName = SingletonName
      )
    )

    val singleton = system.actorOf(singletonProps, "readside-coordinator")

    val singletonProxy = system.actorOf(
      LocalSingletonProxy.props(
        singletonManagerPath = singleton.path.toStringWithoutAddress,
        settings = LocalSingletonProxySettings(
          singletonName = SingletonName
        )
      ), s"readside-coordinator-proxy"
    )

    singletonProxy
  }

  override def startReadSideActor(
    name:       String,
    actorProps: Props,
    settings:   ReadSideProcessorSettings
  ): ActorRef = {
    val backoffProps = BackoffSupervisor.propsWithSupervisorStrategy(
      childProps = actorProps,
      childName = s"$name",
      minBackoff = settings.minBackoff,
      maxBackoff = settings.maxBackoff,
      randomFactor = settings.randomBackoffFactor,
      strategy = SupervisorStrategy.stoppingStrategy
    )

    system.actorOf(backoffProps, s"readside-$name-supervisor")
  }
}
