package akka.contrib.d3

import akka.actor._

import scala.reflect.ClassTag

class LocalAggregateManagerProvider(
    system: ExtendedActorSystem
) extends AggregateManagerProvider {
  override def getAggregateManagerRef[E <: AggregateEntity](
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
