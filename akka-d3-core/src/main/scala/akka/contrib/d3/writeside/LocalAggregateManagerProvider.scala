package akka.contrib.d3.writeside

import akka.actor._
import akka.contrib.d3._

import scala.reflect.ClassTag

private[d3] class LocalAggregateManagerProvider(
    system: ExtendedActorSystem
) extends AggregateManagerProvider {
  override def aggregateManagerRef[E <: AggregateEntity](
    entityFactory: E#Id ⇒ E,
    name:          Option[String],
    settings:      AggregateSettings
  )(
    implicit
    ect: ClassTag[E]
  ): ActorRef = {
    val aggregateName = name.getOrElse(ect.runtimeClass.getSimpleName.toLowerCase)
    system.actorOf(AggregateManager.props(entityFactory, settings), aggregateName)
  }
}
