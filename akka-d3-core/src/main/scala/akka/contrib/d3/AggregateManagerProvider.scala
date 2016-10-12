package akka.contrib.d3

import akka.actor.ActorRef

import scala.reflect.ClassTag

private[d3] trait AggregateManagerProvider {
  def getAggregateManagerRef[A <: AggregateLike](
    behavior: Behavior[A],
    name:     Option[String],
    settings: AggregateSettings
  )(
    implicit
    act:  ClassTag[A],
    idct: ClassTag[A#Id]
  ): ActorRef
}
