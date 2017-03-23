package akka.contrib.d3

import com.typesafe.config.Config

object EventStreamSettings {

  def apply(eventStreamName: String, cfg: Config): EventStreamSettings = {

    val queryConfig = cfg.getConfig("akka.contrib.d3.query")

    val aggregateQConfig = if (queryConfig.hasPath(eventStreamName)) queryConfig.getConfig(eventStreamName).withFallback(queryConfig) else queryConfig

    new EventStreamSettings(
      config = aggregateQConfig,
      readJournalPluginId = if (aggregateQConfig.getIsNull("read-journal.plugin")) None else Some(aggregateQConfig.getString("read-journal.plugin"))
    )

  }

}

final class EventStreamSettings(
  val config:              Config,
  val readJournalPluginId: Option[String]
)
