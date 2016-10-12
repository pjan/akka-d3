package akka.contrib.d3

import java.util.concurrent.TimeoutException

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import scala.util.control.NoStackTrace

class ExpiringFutureSpec(_actorSystem: ActorSystem)
    extends TestKit(_actorSystem)
    with AsyncWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures {
  import ExpiringFuture.syntax._

  class TheException extends RuntimeException("Expected exception") with NoStackTrace

  implicit val dispatcher = system.dispatcher

  def this() = this(ActorSystem("expiring-future-spec"))

  override def afterAll(): Unit = {
    shutdown(_actorSystem)
  }

  "ExpiringFuture" should {
    "implement withTimeout" which {
      "returns successful" when {
        "the future returns successful before the timeout" in {
          testSuccessFuture(0.millis, 100.millis) map { result ⇒
            result shouldBe 1
          }
        }
      }
      "returns the original failure" when {
        "the future fails before the timeout" in {
          recoverToSucceededIf[TheException] {
            testFailedFuture(0.millis, 100.millis)
          }
        }
      }
      "returns a TimeoutException" when {
        "the future returns successful after the timeout" in {
          recoverToSucceededIf[TimeoutException] {
            testSuccessFuture(200.millis, 100.millis)
          }
        }
      }
      "returns a TimeoutException" when {
        "the future fails after the timeout" in {
          recoverToSucceededIf[TimeoutException] {
            testFailedFuture(200.millis, 100.millis)
          }
        }
      }
    }
  }

  def testSuccessFuture(wait: FiniteDuration, timeout: FiniteDuration): Future[Int] =
    Future { Thread.sleep(wait.toMillis); 1 }.withTimeout(timeout)

  def testFailedFuture(wait: FiniteDuration, timeout: FiniteDuration): Future[Int] =
    Future { Thread.sleep(wait.toMillis); throw new TheException }.withTimeout(timeout)

}
