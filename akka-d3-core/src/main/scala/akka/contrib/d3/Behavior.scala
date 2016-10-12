package akka.contrib.d3

import scala.concurrent.{ExecutionContext, Future}

trait Behavior[A <: AggregateLike] extends AggregateAliases {
  type Aggregate = A

  def onCommand(
    state: AggregateState[A],
    cmd:   Command
  )(
    implicit
    ec: ExecutionContext
  ): Future[Either[Throwable, Events]]

  def onEvent(
    state: AggregateState[A],
    evt:   Event
  ): Either[Throwable, AggregateState[A]]

}
