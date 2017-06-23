package akka.contrib.d3

import akka.persistence.query.Offset

@SerialVersionUID(1L) sealed abstract class ReadSideStatus
object ReadSideStatus {
  @SerialVersionUID(1L) final case class Stopped(name: String) extends ReadSideStatus
  @SerialVersionUID(1L) final case class Active(name: String, offset: Offset) extends ReadSideStatus
}
