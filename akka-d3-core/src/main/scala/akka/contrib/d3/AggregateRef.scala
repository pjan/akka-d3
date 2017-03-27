package akka.contrib.d3

import akka.actor._
import akka.contrib.d3.writeside.AggregateManager
import akka.pattern.AskableActorRef
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

object AggregateRef {
  def apply[E <: AggregateEntity](
    identifier:       E#Id,
    aggregateManager: ActorRef,
    timeoutDuration:  FiniteDuration
  )(
    implicit
    ec: ExecutionContext
  ): AggregateRef[E] =
    new AggregateRef[E](identifier, aggregateManager, timeoutDuration)
}

protected[d3] class AggregateRef[E <: AggregateEntity](
    identifier:       E#Id,
    aggregateManager: ActorRef,
    timeoutDuration:  FiniteDuration
)(
    implicit
    ec: ExecutionContext
) {

  private type Aggregate = E#Aggregate
  private type Command = E#Command
  private type Event = E#Event
  private type Events = collection.immutable.Seq[Event]

  private val askTimeout: Timeout =
    Timeout(timeoutDuration)

  private val askableAggregateManager =
    new AskableActorRef(aggregateManager)

  def withAskTimeout(timeout: FiniteDuration): AggregateRef[E] =
    new AggregateRef[E](identifier, aggregateManager, timeout)

  def tell(cmd: Command): Unit =
    aggregateManager ! AggregateManager.CommandMessage(identifier, cmd)
  def !(cmd: Command): Unit = tell(cmd)

  def ask(cmd: Command): Future[Either[Throwable, Events]] =
    askableAggregateManager.ask(AggregateManager.CommandMessage(identifier, cmd))(askTimeout).mapTo[Either[Throwable, Events]]
  def ?(cmd: Command): Future[Either[Throwable, Events]] = ask(cmd)

  def state: Future[Either[Throwable, Aggregate]] =
    askableAggregateManager.ask(AggregateManager.GetState(identifier))(askTimeout).mapTo[Either[Throwable, Aggregate]]

  def exists(p: Aggregate ⇒ Boolean): Future[Boolean] =
    askableAggregateManager.ask(AggregateManager.Exists(identifier, p))(askTimeout).mapTo[Boolean]

  def isInitialized: Future[Boolean] =
    exists(_ ⇒ true)

}
