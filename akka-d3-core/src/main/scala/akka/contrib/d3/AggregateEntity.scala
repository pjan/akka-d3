package akka.contrib.d3

import scala.concurrent.{ExecutionContext, Future}

trait AggregateEntity {

  type Aggregate <: AggregateLike
  type Command <: AggregateCommand
  type Event <: AggregateEvent

  type Id = Aggregate#Id
  type State = AggregateState[Aggregate]

  def identifier: Id

  def initialState: State

  def onCommand(
    state: State,
    cmd:   Command
  )(
    implicit
    ec: ExecutionContext
  ): Future[Either[Throwable, collection.immutable.Seq[Event]]]

  def onEvent(
    state: State,
    evt:   Event
  ): Either[Throwable, State]

}
