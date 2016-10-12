package akka.contrib.d3

sealed trait AggregateState[+A <: AggregateLike] {
  def aggregateId: A#Id
  def isInitialized: Boolean
}

object AggregateState {

  final case class Uninitialized[A <: AggregateLike](aggregateId: A#Id) extends AggregateState[A] {
    val isInitialized: Boolean = false
  }

  final case class Initialized[A <: AggregateLike](aggregate: A) extends AggregateState[A] {
    val aggregateId = aggregate.id
    val isInitialized: Boolean = true
  }

}
