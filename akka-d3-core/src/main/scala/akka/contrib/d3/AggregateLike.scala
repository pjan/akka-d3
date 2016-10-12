package akka.contrib.d3

trait AggregateId extends Any {
  def value: String
}

trait AggregateLike extends ProtocolAliases {
  type Id <: AggregateId
  def id: Id
}

trait AggregateAliases extends ProtocolAliases {
  type Aggregate <: AggregateLike
  type Id = Aggregate#Id
  type Protocol = Aggregate#Protocol
}
