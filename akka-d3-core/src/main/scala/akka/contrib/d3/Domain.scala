package akka.contrib.d3

import akka.NotUsed
import akka.actor._
import akka.contrib.d3.query.ReadJournalProvider
import akka.contrib.d3.writeside.{AggregateManagerProvider, LocalAggregateManagerProvider}
import akka.persistence.query.Offset
import akka.stream.scaladsl.Source
import akka.util.Reflect
import com.typesafe.config.{Config, ConfigFactory}

import scala.util.control.{NoStackTrace, NonFatal}
import scala.concurrent.ExecutionContext
import scala.collection.concurrent.{Map ⇒ ConcurrentMap}
import scala.reflect.ClassTag
import scala.util.Try

object Domain extends ExtensionId[Domain]
    with ExtensionIdProvider {
  override def get(system: ActorSystem): Domain = super.get(system)

  override def lookup(): ExtensionId[_ <: Extension] = Domain

  override def createExtension(system: ExtendedActorSystem): Domain = {
    val cl = findClassLoader()
    val appConfig = system.settings.config
    new DomainImpl(system, appConfig, cl)
  }

  final class Settings(classLoader: ClassLoader, cfg: Config) {
    val config: Config = {
      val config = cfg.withFallback(ConfigFactory.defaultReference(classLoader))
      config.checkValid(ConfigFactory.defaultReference(classLoader), "akka.contrib.d3.writeside")
      config
    }

    import config._

    val topology: String =
      getString("akka.contrib.d3.topology") match {
        case "local"   ⇒ "local"
        case "cluster" ⇒ "cluster"
        case other     ⇒ throw new IllegalArgumentException(s"Unknown value $other for setting akka.contrib.d3.topology")
      }

    val amProviderClass: String =
      Try(getString("akka.contrib.d3.writeside.provider")).toOption.getOrElse(topology) match {
        case "local"   ⇒ classOf[LocalAggregateManagerProvider].getName
        case "cluster" ⇒ "akka.contrib.d3.writeside.ClusterAggregateManagerProvider"
        case fqcn      ⇒ fqcn
      }

    val readJournalProviderClass: String =
      getString("akka.contrib.d3.query.provider") match {
        case "empty"     ⇒ classOf[query.EmptyReadJournalProvider].getName
        case "in-memory" ⇒ "akka.contrib.d3.query.InMemoryReadJournalProvider"
        case "cassandra" ⇒ "akka.contrib.d3.query.CassandraReadJournalProvider"
        case fqcn        ⇒ fqcn
      }
  }

  private[d3] def findClassLoader(): ClassLoader = Reflect.findClassLoader()
}

abstract class Domain
    extends Extension {
  final def register[E <: AggregateEntity](
    entityFactory: E#Id ⇒ E
  )(
    implicit
    ect: ClassTag[E]
  ): Domain =
    register[E](entityFactory, None, None)

  final def register[E <: AggregateEntity](
    entityFactory: E#Id ⇒ E,
    name:          String
  )(
    implicit
    ect: ClassTag[E]
  ): Domain =
    register[E](entityFactory, Some(name), None)

  final def register[E <: AggregateEntity](
    entityFactory: E#Id ⇒ E,
    settings:      AggregateSettings
  )(
    implicit
    ect: ClassTag[E]
  ): Domain =
    register[E](entityFactory, None, Some(settings))

  final def register[E <: AggregateEntity](
    entityFactory: E#Id ⇒ E,
    name:          String,
    settings:      AggregateSettings
  )(
    implicit
    ect: ClassTag[E]
  ): Domain =
    register[E](entityFactory, Some(name), Some(settings))

  def register[E <: AggregateEntity](
    entityFactory: E#Id ⇒ E,
    name:          Option[String],
    settings:      Option[AggregateSettings]
  )(
    implicit
    ect: ClassTag[E]
  ): Domain

  def aggregateRef[E <: AggregateEntity](
    id: E#Id
  )(
    implicit
    ec:  ExecutionContext,
    ect: ClassTag[E]
  ): AggregateRef[E]

  final def eventStream[E <: AggregateEvent](
    tag:        Tag,
    fromOffset: Offset
  ): Source[EventStreamElement[E], NotUsed] =
    eventStream[E](tag, fromOffset, None, None)

  final def eventStream[E <: AggregateEvent](
    tag:        Tag,
    fromOffset: Offset,
    name:       String
  ): Source[EventStreamElement[E], NotUsed] =
    eventStream[E](tag, fromOffset, Some(name), None)

  final def eventStream[E <: AggregateEvent](
    tag:        Tag,
    fromOffset: Offset,
    settings:   EventStreamSettings
  ): Source[EventStreamElement[E], NotUsed] =
    eventStream[E](tag, fromOffset, None, Some(settings))

  final def eventStream[E <: AggregateEvent](
    tag:        Tag,
    fromOffset: Offset,
    name:       String,
    settings:   EventStreamSettings
  ): Source[EventStreamElement[E], NotUsed] =
    eventStream[E](tag, fromOffset, Some(name), Some(settings))

  def eventStream[E <: AggregateEvent](
    tag:        Tag,
    fromOffset: Offset,
    name:       Option[String],
    settings:   Option[EventStreamSettings]
  ): Source[EventStreamElement[E], NotUsed]

}

class DomainImpl(
    val system:        ExtendedActorSystem,
    applicationConfig: Config,
    classLoader:       ClassLoader
) extends Domain {
  import Domain._

  final val settings: Settings = new Settings(classLoader, applicationConfig)

  private val registeredTypeNames: ConcurrentMap[String, ClassTag[_]] = collection.concurrent.TrieMap()
  private val aggregateManagers: ConcurrentMap[ClassTag[_], ActorRef] = collection.concurrent.TrieMap()
  private val aggregateSettings: ConcurrentMap[ClassTag[_], AggregateSettings] = collection.concurrent.TrieMap()

  override def register[E <: AggregateEntity](
    entityFactory: E#Id ⇒ E,
    name:          Option[String],
    settings:      Option[AggregateSettings]
  )(
    implicit
    ect: ClassTag[E]
  ): Domain = {
    val aggregateName = name.getOrElse(ect.runtimeClass.getName.toLowerCase)
    val aggregateSetting = settings.getOrElse(AggregateSettings(aggregateName, system.settings.config))

    val alreadyRegistered = registeredTypeNames.putIfAbsent(aggregateName, ect)
    alreadyRegistered match {
      case Some(rct) if !rct.equals(ect) ⇒
        throw new IllegalArgumentException(
          s"The AggregateName [$aggregateName] for aggregate ${ect.runtimeClass.getSimpleName} is not unique. " +
            s"It is already for ${rct.runtimeClass.getSimpleName}. Use the name argument to define a unique name."
        ) with NoStackTrace
      case _ ⇒
        aggregateManagers.putIfAbsent(ect, aggregateManagerProvider.aggregateManagerRef[E](entityFactory, name, aggregateSetting))
        aggregateSettings.putIfAbsent(ect, aggregateSetting)
    }

    this
  }

  override def aggregateRef[E <: AggregateEntity](
    id: E#Id
  )(
    implicit
    ec:  ExecutionContext,
    ect: ClassTag[E]
  ): AggregateRef[E] = {
    val aggregateManager = aggregateManagers(ect)
    val settings = aggregateSettings(ect)

    AggregateRef[E](id, aggregateManager, settings.askTimeout)
  }

  override def eventStream[E <: AggregateEvent](
    tag:        Tag,
    fromOffset: Offset,
    name:       Option[String],
    settings:   Option[EventStreamSettings]
  ): Source[EventStreamElement[E], NotUsed] = {
    val eventStreamName = name.getOrElse(tag.value)
    val eventStreamSettings = settings.getOrElse(EventStreamSettings(eventStreamName, system.settings.config))

    readJournalProvider.readJournal(eventStreamSettings.readJournalPluginId.getOrElse(readJournalProvider.defaultReadJournalPluginId)).eventsByTag(tag.value, fromOffset)
      .map { envelope ⇒
        EventStreamElement[E](
          envelope.persistenceId,
          envelope.event.asInstanceOf[E],
          envelope.offset
        )
      }
  }

  import settings._

  private val dynamicAccess: DynamicAccess = system.dynamicAccess

  private val aggregateManagerProvider: AggregateManagerProvider = try {
    val arguments = Vector(
      classOf[ExtendedActorSystem] → system
    )

    dynamicAccess.createInstanceFor[AggregateManagerProvider](amProviderClass, arguments).get
  } catch {
    case NonFatal(e) ⇒
      throw e
  }

  private val readJournalProvider: ReadJournalProvider = try {
    val arguments = Vector(
      classOf[ExtendedActorSystem] → system
    )

    dynamicAccess.createInstanceFor[ReadJournalProvider](readJournalProviderClass, arguments).get
  } catch {
    case NonFatal(e) ⇒
      throw e
  }

}
