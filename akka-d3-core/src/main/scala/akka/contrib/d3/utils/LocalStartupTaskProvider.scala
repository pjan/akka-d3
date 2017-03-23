package akka.contrib.d3.utils

import akka.Done
import akka.actor._
import akka.pattern.BackoffSupervisor

import scala.concurrent.Future
import scala.concurrent.duration._

private[d3] final class LocalStartupTaskProvider(
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

    val singletonProps = LocalSingletonManager.props(backoffProps, LocalSingletonManagerSettings("singleton"))

    val singleton = system.actorOf(singletonProps, s"$name-singleton")

    val singletonProxy = system.actorOf(
      LocalSingletonProxy.props(
        singletonManagerPath = singleton.path.toStringWithoutAddress,
        settings = LocalSingletonProxySettings("singleton", 10000, 1.second)
      ), s"$name-singletonProxy"
    )

    new StartupTask(singletonProxy)
  }

}
