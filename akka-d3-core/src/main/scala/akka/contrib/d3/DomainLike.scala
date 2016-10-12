package akka.contrib.d3

import akka.actor._
import akka.util.Reflect
import com.typesafe.config.{Config, ConfigFactory}

import scala.util.control.NonFatal

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

object Domain extends ExtensionId[DomainImpl]
    with ExtensionIdProvider {
  override def get(system: ActorSystem): DomainImpl = super.get(system)

  override def lookup(): ExtensionId[_ <: Extension] = Domain

  override def createExtension(system: ExtendedActorSystem): DomainImpl = {
    val cl = findClassLoader()
    val appConfig = system.settings.config
    new DomainImpl(system, appConfig, cl)
  }

  class Settings(classLoader: ClassLoader, cfg: Config) {
    final val config: Config = {
      val config = cfg.withFallback(ConfigFactory.defaultReference(classLoader))
      config.checkValid(ConfigFactory.defaultReference(classLoader), "akka.contrib.d3")
      config
    }

    import config._

    final val amProviderClass =
      getString("akka.contrib.d3.provider") match {
        case "local"   ⇒ classOf[LocalAggregateManagerProvider].getName
        case "cluster" ⇒ "akka.contrib.d3.cluster.ClusterAggregateManagerProvider"
        case fqcn      ⇒ fqcn
      }
  }

  private[d3] def findClassLoader(): ClassLoader = Reflect.findClassLoader()
}

abstract class Domain {
  def register[A <: AggregateLike](
    behavior: Behavior[A],
    name:     Option[String]            = None,
    settings: Option[AggregateSettings] = None
  )(
    implicit
    act:  ClassTag[A],
    idct: ClassTag[A#Id]
  ): Domain
  def aggregateRef[A <: AggregateLike](
    id: A#Id
  )(
    implicit
    ec:  ExecutionContext,
    act: ClassTag[A]
  ): AggregateRef[A]
}

class DomainImpl(
  val system:        ExtendedActorSystem,
  applicationConfig: Config,
  classLoader:       ClassLoader
) extends Domain
    with Extension {
  import Domain._

  final val settings: Settings = new Settings(classLoader, applicationConfig)

  protected val dynamicAccess: DynamicAccess = system.dynamicAccess

  private val aggregateManagers: collection.concurrent.Map[ClassTag[_], ActorRef] = collection.concurrent.TrieMap()
  private val aggregateSettings: collection.concurrent.Map[ClassTag[_], AggregateSettings] = collection.concurrent.TrieMap()

  override def register[A <: AggregateLike](
    behavior: Behavior[A],
    name:     Option[String],
    settings: Option[AggregateSettings]
  )(
    implicit
    act:  ClassTag[A],
    idct: ClassTag[A#Id]
  ): Domain = {
    val aggregateName = name.getOrElse(act.runtimeClass.getName.toLowerCase)
    val aggregateSetting = settings.getOrElse(AggregateSettings(aggregateName, system.settings.config))

    aggregateManagers.putIfAbsent(act, provider.getAggregateManagerRef[A](behavior, name, aggregateSetting))
    aggregateSettings.putIfAbsent(act, aggregateSetting)

    this
  }

  override def aggregateRef[A <: AggregateLike](
    id: A#Id
  )(
    implicit
    ec:  ExecutionContext,
    act: ClassTag[A]
  ): AggregateRef[A] = {
    val aggregateManager = aggregateManagers(act)
    val settings = aggregateSettings(act)

    AggregateRef[A](id, aggregateManager, settings.askTimeout)
  }

  import settings._

  val provider: AggregateManagerProvider = try {
    val arguments = Vector(
      classOf[ExtendedActorSystem] → system
    )

    dynamicAccess.createInstanceFor[AggregateManagerProvider](amProviderClass, arguments).get
  } catch {
    case NonFatal(e) ⇒
      throw e
  }

}
