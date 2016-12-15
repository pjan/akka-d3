package akka.contrib.d3

import akka.actor.ActorRef

import scala.reflect.ClassTag

private[d3] trait AggregateManagerProvider {
  def getAggregateManagerRef[E <: AggregateEntity](
    entityFactory: E#Id ⇒ E,
    name:          Option[String],
    settings:      AggregateSettings
  )(
    implicit
    ect: ClassTag[E]
  ): ActorRef
}
