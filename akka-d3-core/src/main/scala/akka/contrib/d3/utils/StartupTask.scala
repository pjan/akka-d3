package akka.contrib.d3.utils

import akka.Done
import akka.actor._
import akka.util.Timeout
import akka.pattern.{ask, pipe}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure

class StartupTask(actorRef: ActorRef) {

  import StartupTaskActor._

  def execute()(implicit timeout: Timeout): Future[Done] = {
    (actorRef ? Execute).mapTo[Done]
  }

}

private[d3] object StartupTaskActor {
  case object Execute
}

private[d3] class StartupTaskActor(
  task:    () ⇒ Future[Done],
  timeout: FiniteDuration
) extends Actor
    with ActorLogging {

  import StartupTaskActor._

  import context.dispatcher

  override def preStart(): Unit = {
    implicit val askTimeout = Timeout(timeout)
    self ? Execute pipeTo self
    ()
  }

  def receive: Receive = started

  def started: Receive = {
    case Execute ⇒
      log.info(s"Executing start task ${self.path.name}.")
      task() pipeTo self
      context become executing(List(sender()))
  }

  def executing(outstandingRequests: List[ActorRef]): Receive = {
    case Execute ⇒
      context become executing(sender() :: outstandingRequests)

    case Done ⇒
      log.info(s"Start task ${self.path.name} finished successfully.")
      outstandingRequests foreach { requester ⇒
        requester ! Done
      }
      context become executed

    case failure @ Failure(e) ⇒
      outstandingRequests foreach { requester ⇒
        requester ! failure
      }
      // If we failed to prepare, crash and let our parent handle this
      throw e
  }

  def executed: Receive = {
    case Execute ⇒
      sender() ! Done

    case Done ⇒
    // We do expect to receive Done once executed since we initially asked ourselves to execute
  }

}
