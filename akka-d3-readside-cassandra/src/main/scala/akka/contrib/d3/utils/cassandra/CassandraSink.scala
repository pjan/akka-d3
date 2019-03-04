package akka.contrib.d3.utils.cassandra

import akka.Done
import akka.stream.scaladsl._
import com.datastax.driver.core._

import scala.concurrent._

import GuavaFutures._

object CassandraSink {
  def apply[T](
    parallelism:     Int,
    statement:       PreparedStatement,
    statementBinder: (T, PreparedStatement) ⇒ BoundStatement
  )(implicit session: Session, ec: ExecutionContext): Sink[T, Future[Done]] =
    Flow[T]
      .mapAsyncUnordered(parallelism)(t ⇒ session.executeAsync(statementBinder(t, statement)).asScala(ec))
      .toMat(Sink.ignore)(Keep.right)
}
