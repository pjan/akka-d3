package akka.contrib.d3

import akka.dispatch.Dispatchers
import com.typesafe.config.Config

import scala.concurrent.duration._

object AggregateSettings {

  def apply(aggregateName: String, cfg: Config): AggregateSettings = {

    val d3c = cfg.getConfig("akka.contrib.d3")

    val aggregateConfig = if (d3c.hasPath(aggregateName)) d3c.getConfig(aggregateName).withFallback(d3c) else d3c

    new AggregateSettings(
      config = aggregateConfig,
      passivationTimeout = aggregateConfig.getDuration("passivation-timeout", MILLISECONDS).millis,
      commandHandlingTimeout = aggregateConfig.getDuration("command-handling-timeout", MILLISECONDS).millis,
      askTimeout = aggregateConfig.getDuration("ask-timeout", MILLISECONDS).millis,
      eventsPerSnapshot = aggregateConfig.getInt("events-per-snapshot"),
      bufferSize = aggregateConfig.getInt("buffer-size"),
      dispatcher = aggregateConfig.getString("dispatcher") match {
        case "" ⇒ Dispatchers.DefaultDispatcherId
        case id ⇒ id
      },
      journalPluginId = aggregateConfig.getString("journal.plugin"),
      snapshotPluginId = aggregateConfig.getString("snapshot-store.plugin"),
      readJournalPluginId = aggregateConfig.getString("read-journal.plugin")
    )
  }

}

final class AggregateSettings(
  val config:                 Config,
  val passivationTimeout:     FiniteDuration,
  val commandHandlingTimeout: FiniteDuration,
  val askTimeout:             FiniteDuration,
  val eventsPerSnapshot:      Int,
  val bufferSize:             Int,
  val dispatcher:             String,
  val journalPluginId:        String,
  val snapshotPluginId:       String,
  val readJournalPluginId:    String
)
