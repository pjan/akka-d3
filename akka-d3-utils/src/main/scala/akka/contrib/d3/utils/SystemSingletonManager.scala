package akka.contrib.d3.utils

import akka.actor._

import scala.collection.concurrent.TrieMap

private[d3] object SystemSingletonManager extends ExtensionId[SystemSingletonManagerImpl]
    with ExtensionIdProvider {
  override def get(system: ActorSystem): SystemSingletonManagerImpl = super.get(system)

  override def lookup(): ExtensionId[_ <: Extension] = SystemSingletonManager

  override def createExtension(system: ExtendedActorSystem): SystemSingletonManagerImpl = {
    new SystemSingletonManagerImpl(system)
  }
}

private[d3] class SystemSingletonManagerImpl(
    val system: ExtendedActorSystem
) extends Extension {

  private val singletons = TrieMap.empty[String, ActorRef]

  def actorOf(props: Props, name: String): ActorRef =
    singletons.getOrElseUpdate(name, createSingleton(props, name))

  private def createSingleton(props: Props, name: String) =
    system.actorOf(props, name)

}
