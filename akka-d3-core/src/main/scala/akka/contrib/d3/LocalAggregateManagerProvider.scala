package akka.contrib.d3

import akka.actor._

import scala.reflect.ClassTag

class LocalAggregateManagerProvider(
    system: ExtendedActorSystem
) extends AggregateManagerProvider {
  override def getAggregateManagerRef[A <: AggregateLike](
    behavior: Behavior[A],
    name:     Option[String],
    settings: AggregateSettings
  )(implicit
    act: ClassTag[A],
    idct: ClassTag[A#Id]): ActorRef = {
    val aggregateName = name.getOrElse(act.runtimeClass.getSimpleName.toLowerCase)
    system.actorOf(AggregateManager.props(behavior, settings), aggregateName)
  }
}
