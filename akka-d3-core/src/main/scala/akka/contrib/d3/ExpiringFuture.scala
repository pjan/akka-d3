package akka.contrib.d3

import akka.actor.ActorSystem

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.FiniteDuration

private[d3] class ExpiringFuture[A] private (val future: Future[A]) extends AnyVal {

  def withTimeout(timeout: FiniteDuration)(implicit ec: ExecutionContext, system: ActorSystem): Future[A] = {
    val timeoutPromise = Promise[A]

    val scheduledTimeout = system.scheduler.scheduleOnce(timeout) {
      val _ = timeoutPromise.tryFailure(new scala.concurrent.TimeoutException)
    }

    val result = Future.firstCompletedOf(List(future, timeoutPromise.future))

    result onSuccess { case _ ⇒ scheduledTimeout.cancel() }

    result
  }

}

private[d3] object ExpiringFuture {
  def apply[A](timeout: FiniteDuration)(body: ⇒ A)(implicit ec: ExecutionContext, system: ActorSystem): Future[A] =
    new ExpiringFuture[A](Future(body)).withTimeout(timeout)

  object syntax {
    implicit def futureToExpiringFuture[A](future: Future[A]): ExpiringFuture[A] =
      new ExpiringFuture[A](future)
  }
}
