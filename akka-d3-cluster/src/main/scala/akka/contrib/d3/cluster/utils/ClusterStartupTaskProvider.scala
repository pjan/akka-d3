package akka.contrib.d3.cluster.utils

import akka.Done
import akka.actor._
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.contrib.d3.utils._
import akka.pattern.BackoffSupervisor

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class ClusterStartupTaskProvider(
    system: ExtendedActorSystem
) extends StartupTaskProvider {

  def startupTask(
    name:                String,
    task:                () ⇒ Future[Done],
    timeout:             FiniteDuration,
    minBackoff:          FiniteDuration,
    maxBackoff:          FiniteDuration,
    randomBackoffFactor: Double
  ): StartupTask = {
    val startupTaskProps = Props(classOf[StartupTaskActor], task, timeout)

    val backoffProps = BackoffSupervisor.propsWithSupervisorStrategy(
      childProps = startupTaskProps,
      childName = name,
      minBackoff = minBackoff,
      maxBackoff = maxBackoff,
      randomFactor = randomBackoffFactor,
      strategy = SupervisorStrategy.stoppingStrategy
    )

    val singletonProps = ClusterSingletonManager.props(backoffProps, PoisonPill, ClusterSingletonManagerSettings(system))

    val singleton = system.actorOf(singletonProps, s"$name-singleton")

    val singletonProxy = system.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = singleton.path.toStringWithoutAddress,
        settings = ClusterSingletonProxySettings(system)
      ), s"$name-singletonProxy"
    )

    new StartupTask(singletonProxy)
  }

}
