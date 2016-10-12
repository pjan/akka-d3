package akka.contrib.d3

import java.util.{Base64, UUID}

case class CommandId(value: String) extends AnyVal

object CommandId {
  private val b64encoder = Base64.getUrlEncoder

  def generate(): CommandId =
    CommandId(generateValue())

  private[CommandId] def generateValue(): String =
    "cmd_" + b64encoder.encodeToString(UUID.randomUUID().toString.replaceAllLiterally("-", "").getBytes)
}

trait CommandIdCarrier {
  val id: CommandId = CommandId.generate()
}
