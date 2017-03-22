package akka.contrib.d3.writeside

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.sharding._
import akka.contrib.d3._

import scala.reflect.ClassTag

private[d3] object ClusterAggregateManagerProvider {

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

private[d3] final class ClusterAggregateManagerProvider(
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
    val maxNumberOfShards = settings.config.getInt("max-number-of-shards")
    val role: Option[String] = settings.config.getString("run-on-role") match {
      case "" ⇒ None
      case r  ⇒ Some(r)
    }
    val messageExtractor = ClusterAggregateManagerProvider.MessageExtractor(maxNumberOfShards)

    if (role.forall(Cluster(system).selfRoles.contains)) {
      ClusterSharding(system).start(
        typeName = s"$aggregateName-shard",
        entityProps = AggregateManager.props(entityFactory, settings),
        settings = ClusterShardingSettings(system),
        messageExtractor = messageExtractor
      )
    } else {
      // start in proxy mode
      ClusterSharding(system).startProxy(
        typeName = s"$aggregateName-shard",
        role = role,
        extractShardId = { msg ⇒ ClusterAggregateManagerProvider.MessageExtractor(maxNumberOfShards).shardId(msg) },
        extractEntityId = { case msg ⇒ (messageExtractor.entityId(msg), messageExtractor.entityMessage(msg)) }
      )
    }

  }
}
