package akka.contrib.d3

import akka.Done
import akka.actor._
import akka.pattern.ask
import akka.contrib.d3.readside._
import akka.contrib.d3.utils.StartupTasks
import akka.persistence.query.Offset
import akka.stream.ActorMaterializer
import akka.util.{Reflect, Timeout}
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

object ReadSide extends ExtensionId[ReadSide]
    with ExtensionIdProvider {
  override def get(system: ActorSystem): ReadSide = super.get(system)

  override def lookup(): ExtensionId[_ <: Extension] = ReadSide

  override def createExtension(system: ExtendedActorSystem): ReadSide = {
    val cl = findClassLoader()
    val appConfig = system.settings.config
    new ReadSideImpl(system, appConfig, cl)
  }

  final class Settings(classLoader: ClassLoader, cfg: Config) {
    val config: Config = {
      val config = cfg.withFallback(ConfigFactory.defaultReference(classLoader))
      config.checkValid(ConfigFactory.defaultReference(classLoader), "akka.contrib.d3.readside")
      config
    }

    import config._

    val topology: String =
      getString("akka.contrib.d3.topology") match {
        case "local"   ⇒ "local"
        case "cluster" ⇒ "cluster"
        case other     ⇒ throw new IllegalArgumentException(s"Unknown value $other for setting akka.contrib.d3.topology")
      }

    val rsProviderClass: String =
      Try(getString("akka.contrib.d3.readside.provider")).toOption.getOrElse(topology) match {
        case "local"   ⇒ classOf[LocalReadSideProvider].getName
        case "cluster" ⇒ "akka.contrib.d3.readside.ClusterAggregateManagerProvider"
        case fqcn      ⇒ fqcn
      }

    val rewindTimeout: FiniteDuration =
      getDuration("akka.contrib.d3.readside.rewind-timeout", MILLISECONDS).millis

    val coordinatorHeartBeatInterval: FiniteDuration =
      getDuration("akka.contrib.d3.readside.coordinator.heartbeat-interval", MILLISECONDS).millis

    val coordinatorMinBackoff: FiniteDuration =
      getDuration("akka.contrib.d3.readside.coordinator.backoff.min", MILLISECONDS).millis

    val coordinatorMaxBackoff: FiniteDuration =
      getDuration("akka.contrib.d3.readside.coordinator.backoff.max", MILLISECONDS).millis

    val coordinatorRandomBackoffFactor: Double =
      getDouble("akka.contrib.d3.readside.coordinator.backoff.random-factor")

  }

  private[d3] def findClassLoader(): ClassLoader = Reflect.findClassLoader()
}

abstract class ReadSide
    extends Extension {

  private[d3] def system: ActorSystem

  final def register[Event <: AggregateEvent](
    processor: ReadSideProcessor[Event]
  ): Unit =
    register[Event](processor, None)

  final def register[Event <: AggregateEvent](
    processor: ReadSideProcessor[Event],
    settings:  ReadSideProcessorSettings
  ): Unit =
    register[Event](processor, Some(settings))

  def register[Event <: AggregateEvent](
    processor: ReadSideProcessor[Event],
    settings:  Option[ReadSideProcessorSettings]
  ): Unit

  def start(
    name: String
  ): Unit

  def stop(
    name: String
  ): Unit

  def rewind(
    name:   String,
    offset: Offset
  ): Future[Done]

}

class ReadSideImpl(
    val system:        ExtendedActorSystem,
    applicationConfig: Config,
    classLoader:       ClassLoader
) extends ReadSide {
  import ReadSide._

  final val settings: Settings = new Settings(classLoader, applicationConfig)

  private implicit val materializer = ActorMaterializer()(system) // TODO: make this configurable

  private lazy val coordinator =
    readSideProvider.startReadSideCoordinator(
      coordinatorProps = ReadSideCoordinator.props(settings.coordinatorHeartBeatInterval),
      minBackoff = settings.coordinatorMinBackoff,
      maxBackoff = settings.coordinatorMaxBackoff,
      randomBackoffFactor = settings.coordinatorRandomBackoffFactor
    )

  override def register[Event <: AggregateEvent](
    processor:         ReadSideProcessor[Event],
    processorSettings: Option[ReadSideProcessorSettings]
  ): Unit = {
    val readSideProcessorName = processor.name
    val readSideProcessorSettings = processorSettings.getOrElse(ReadSideProcessorSettings(readSideProcessorName, system.settings.config))

    val startupTask = StartupTasks(system).create(
      name = s"GlobalStartup-${processor.encodedName}",
      task = () ⇒ processor.buildHandler().globalPrepare(),
      timeout = readSideProcessorSettings.globalStartupTimeout,
      minBackoff = 1.second,
      maxBackoff = 5.seconds,
      randomBackoffFactor = 0.2
    )

    readSideProvider.startReadSideActor(
      name = processor.name,
      actorProps = Props(new ReadSideActor(processor, readSideProcessorSettings, 4 / 5 * settings.coordinatorHeartBeatInterval, startupTask, coordinator)),
      settings = readSideProcessorSettings
    )
    ()
  }

  override def start(
    name: String
  ): Unit = {
    coordinator ! ReadSideCoordinator.Start(name)
  }

  override def stop(
    name: String
  ): Unit = {
    coordinator ! ReadSideCoordinator.Stop(name)
  }

  override def rewind(
    name:   String,
    offset: Offset
  ): Future[Done] = {
    implicit val timeout = Timeout(settings.rewindTimeout)
    (coordinator ? ReadSideCoordinator.Rewind(name, offset)).mapTo[Done]
  }

  import settings._

  private val dynamicAccess: DynamicAccess = system.dynamicAccess

  private val readSideProvider: ReadSideProvider = try {
    val arguments = Vector(
      classOf[ExtendedActorSystem] → system
    )

    dynamicAccess.createInstanceFor[ReadSideProvider](rsProviderClass, arguments).get
  } catch {
    case NonFatal(e) ⇒
      throw e
  }

}
