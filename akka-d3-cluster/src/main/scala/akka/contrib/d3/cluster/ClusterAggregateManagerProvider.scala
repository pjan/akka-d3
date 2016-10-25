package akka.contrib.d3.cluster

import akka.actor._
import akka.cluster.sharding._
import akka.contrib.d3._

import scala.reflect.ClassTag

object ClusterAggregateManagerProvider {

  private[ClusterAggregateManagerProvider] case class MessageExtractor(maxNumberOfShards: Int) extends ShardRegion.MessageExtractor {
    override def entityMessage(message: Any): Any = message

    override def shardId(message: Any): String =
      entityId(message)

    override def entityId(message: Any): String = message match {
      case AggregateManager.CommandMessage(id, cmd) ⇒ (math.abs(id.value.hashCode) % maxNumberOfShards).toString
      case AggregateManager.GetState(id)            ⇒ (math.abs(id.value.hashCode) % maxNumberOfShards).toString
      case AggregateManager.Exists(id, _)           ⇒ (math.abs(id.value.hashCode) % maxNumberOfShards).toString
    }
  }

}

class ClusterAggregateManagerProvider(
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
    val maxNumberOfShards = settings.config.getInt("max-number-of-shards")

    ClusterSharding(system).start(
      typeName = s"$aggregateName-shard",
      entityProps = AggregateManager.props(behavior, settings),
      settings = ClusterShardingSettings(system),
      messageExtractor = ClusterAggregateManagerProvider.MessageExtractor(maxNumberOfShards)
    )
  }
}
