package akka.contrib.d3.utils.cassandra

import com.google.common.util.concurrent._

import scala.concurrent._

private[cassandra] object GuavaFutures {
  import java.util.concurrent.Executor
  import scala.util.control.NonFatal

  class ExecutorWrapper(ec: ExecutionContext) extends Executor {

    def execute(command: Runnable): Unit = {
      ec.execute(new Runnable {
        def run(): Unit = {
          try command.run() catch { case NonFatal(ex) ⇒ ec.reportFailure(ex) }
        }
      })
    }
  }

  implicit final class GuavaFutureOpts[A](val guavaFut: ListenableFuture[A]) extends AnyVal {
    def asScala(ec: ExecutionContext): Future[A] = {
      val p = Promise[A]()
      val callback = new FutureCallback[A] {
        override def onSuccess(a: A): Unit = {
          p.success(a)
          ()
        }
        override def onFailure(err: Throwable): Unit = {
          p.failure(err)
          ()
        }
      }
      val executor = new ExecutorWrapper(ec)
      Futures.addCallback(guavaFut, callback, executor)
      p.future
    }
  }
}
