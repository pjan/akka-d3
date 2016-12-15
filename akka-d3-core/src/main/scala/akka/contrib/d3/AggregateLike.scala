package akka.contrib.d3

trait AggregateId extends Any {
  def value: String
}

trait AggregateLike {
  type Id <: AggregateId
  def id: Id
}
