package akka.contrib.d3

import java.time.OffsetDateTime

trait AggregateMetadata {
  def versionNr: Int
  def createdAt: OffsetDateTime
  def updatedAt: OffsetDateTime
}

trait AggregateMetadataCarrier[M <: AggregateMetadata] {
  def metadata: M

  final def versionNr: Int = metadata.versionNr
  final def createdAt: OffsetDateTime = metadata.createdAt
  final def updatedAt: OffsetDateTime = metadata.updatedAt
}
