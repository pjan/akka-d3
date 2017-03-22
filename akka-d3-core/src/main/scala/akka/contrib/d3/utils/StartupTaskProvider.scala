package akka.contrib.d3.utils

import akka.Done

import scala.concurrent.Future
import scala.concurrent.duration._

private[d3] abstract class StartupTaskProvider {
  def startupTask(
    name:                String,
    task:                () ⇒ Future[Done],
    timeout:             FiniteDuration,
    minBackoff:          FiniteDuration,
    maxBackoff:          FiniteDuration,
    randomBackoffFactor: Double
  ): StartupTask
}
