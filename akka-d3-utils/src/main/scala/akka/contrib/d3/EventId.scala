package akka.contrib.d3

import java.util.{Base64, UUID}

case class EventId(value: String) extends AnyVal

object EventId {
  private val b64encoder = Base64.getUrlEncoder

  def generate(): EventId =
    EventId(generateValue())

  private[EventId] def generateValue(): String =
    "evt_" + b64encoder.encodeToString(UUID.randomUUID().toString.replaceAllLiterally("-", "").getBytes)
}

trait EventIdCarrier {
  val id: EventId = EventId.generate()
}
