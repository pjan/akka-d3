package akka.contrib.d3

import java.time.OffsetDateTime

trait EventMetadata {
  type Id <: AggregateId

  def aggregateId: Id
  def timestamp: OffsetDateTime
  def tags: Set[Tag]
}

trait EventMetadataCarrier[M <: EventMetadata] {
  def metadata: M

  final def aggregateId: M#Id = metadata.aggregateId
  final def timestamp: OffsetDateTime = metadata.timestamp
  final def tags: Set[Tag] = metadata.tags
}
