package akka.contrib.d3

import akka.dispatch.Dispatchers
import com.typesafe.config._

import scala.concurrent.duration._

object ReadSideProcessorSettings {

  def apply(readSideProcessorName: String, cfg: Config): ReadSideProcessorSettings = {

    val readSideProcessorConfig = cfg.getConfig("akka.contrib.d3.readside.processor")

    val processorRSConfig =
      if (readSideProcessorConfig.hasPath(readSideProcessorName)) readSideProcessorConfig.getConfig(readSideProcessorName).withFallback(readSideProcessorConfig)
      else readSideProcessorConfig

    new ReadSideProcessorSettings(
      config = processorRSConfig,
      globalStartupTimeout = processorRSConfig.getDuration("global-startup-timeout", MILLISECONDS).millis,
      minBackoff = processorRSConfig.getDuration("backoff.min", MILLISECONDS).millis,
      maxBackoff = processorRSConfig.getDuration("backoff.max", MILLISECONDS).millis,
      randomBackoffFactor = processorRSConfig.getDouble("backoff.random-factor"),
      dispatcher = processorRSConfig.getString("dispatcher") match {
        case "" ⇒ Dispatchers.DefaultDispatcherId
        case id ⇒ id
      }
    )

  }

}

final case class ReadSideProcessorSettings(
  config:               Config,
  globalStartupTimeout: FiniteDuration,
  minBackoff:           FiniteDuration,
  maxBackoff:           FiniteDuration,
  randomBackoffFactor:  Double,
  dispatcher:           String
)
