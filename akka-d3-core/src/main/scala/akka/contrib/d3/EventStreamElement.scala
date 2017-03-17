package akka.contrib.d3

import akka.persistence.query.Offset

@SerialVersionUID(1L) final case class EventStreamElement[+Event](
  entityId: String,
  event:    Event,
  offset:   Offset
)
