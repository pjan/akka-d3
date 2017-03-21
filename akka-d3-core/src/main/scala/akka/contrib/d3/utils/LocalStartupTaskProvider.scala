package akka.contrib.d3.utils

import akka.Done
import akka.actor._
import akka.pattern.BackoffSupervisor

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class LocalStartupTaskProvider(
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
      startupTaskProps, name, minBackoff, maxBackoff, randomBackoffFactor, SupervisorStrategy.stoppingStrategy
    )

    val singletonProps = LocalSingletonManager.props(backoffProps, LocalSingletonManagerSettings("singleton"))

    val singleton = system.actorOf(singletonProps, s"$name-singleton")

    new StartupTask(singleton)
  }
}
