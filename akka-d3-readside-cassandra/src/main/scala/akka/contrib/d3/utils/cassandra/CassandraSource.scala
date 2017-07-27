package akka.contrib.d3.utils.cassandra

import akka.NotUsed
import akka.stream.scaladsl._
import com.datastax.driver.core._

import scala.concurrent.Future

object CassandraSource {

  def fromStatement(statement: Statement)(implicit session: Session): Source[Row, NotUsed] =
    Source.fromGraph(new CassandraSourceStage(Future.successful(statement), session))

  def fromFutureStatement(statement: Future[Statement])(implicit session: Session): Source[Row, NotUsed] =
    Source.fromGraph(new CassandraSourceStage(statement, session))

}
