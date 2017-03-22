package akka.contrib.d3.utils

import akka.Done
import akka.actor._
import akka.pattern.BackoffSupervisor

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

private[d3] final class LocalStartupTaskProvider(
    system: ExtendedActorSystem
) extends StartupTaskProvider {

  private val singletons = TrieMap.empty[String, ActorRef]

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

    val singleton = getSingleton(backoffProps, s"$name-singleton")

    new StartupTask(singleton)
  }

  private def getSingleton(props: Props, name: String): ActorRef =
    singletons.getOrElseUpdate(name, system.actorOf(props, name))

}
