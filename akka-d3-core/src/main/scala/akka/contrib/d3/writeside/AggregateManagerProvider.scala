package akka.contrib.d3.writeside

import akka.actor.ActorRef
import akka.contrib.d3._

import scala.reflect.ClassTag

private[d3] abstract class AggregateManagerProvider {
  def aggregateManagerRef[E <: AggregateEntity](
    entityFactory: E#Id ⇒ E,
    name:          Option[String],
    settings:      AggregateSettings
  )(
    implicit
    ect: ClassTag[E]
  ): ActorRef
}
