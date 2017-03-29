package akka.contrib.d3.readside

import akka.actor._
import akka.contrib.d3.ReadSideProcessorSettings

import scala.concurrent.duration.FiniteDuration

private[d3] abstract class ReadSideProvider {

  def startReadSideCoordinator(
    coordinatorProps:    Props,
    minBackoff:          FiniteDuration,
    maxBackoff:          FiniteDuration,
    randomBackoffFactor: Double
  ): ActorRef

  def startReadSideActor(
    name:       String,
    actorProps: Props,
    settings:   ReadSideProcessorSettings
  ): ActorRef

}
