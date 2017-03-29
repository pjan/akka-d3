package akka.contrib.d3

import akka.dispatch.Dispatchers
import com.typesafe.config._

import scala.concurrent.duration._

object AggregateSettings {

  def apply(aggregateName: String, cfg: Config): AggregateSettings = {

    val writeSideConfig = cfg.getConfig("akka.contrib.d3.writeside")

    val aggregateWSConfig = if (writeSideConfig.hasPath(aggregateName)) writeSideConfig.getConfig(aggregateName).withFallback(writeSideConfig) else writeSideConfig

    new AggregateSettings(
      config = aggregateWSConfig,
      passivationTimeout = aggregateWSConfig.getDuration("passivation-timeout", MILLISECONDS).millis,
      commandHandlingTimeout = aggregateWSConfig.getDuration("command-handling-timeout", MILLISECONDS).millis,
      askTimeout = aggregateWSConfig.getDuration("ask-timeout", MILLISECONDS).millis,
      eventsPerSnapshot = aggregateWSConfig.getInt("events-per-snapshot"),
      bufferSize = aggregateWSConfig.getInt("buffer-size"),
      dispatcher = aggregateWSConfig.getString("dispatcher") match {
        case "" ⇒ Dispatchers.DefaultDispatcherId
        case id ⇒ id
      },
      journalPluginId = if (aggregateWSConfig.getIsNull("journal.plugin")) "" else aggregateWSConfig.getString("journal.plugin"),
      snapshotPluginId = if (aggregateWSConfig.getIsNull("snapshot-store.plugin")) "" else aggregateWSConfig.getString("snapshot-store.plugin")
    )
  }

}

final case class AggregateSettings(
  config:                 Config,
  passivationTimeout:     FiniteDuration,
  commandHandlingTimeout: FiniteDuration,
  askTimeout:             FiniteDuration,
  eventsPerSnapshot:      Int,
  bufferSize:             Int,
  dispatcher:             String,
  journalPluginId:        String,
  snapshotPluginId:       String
)
