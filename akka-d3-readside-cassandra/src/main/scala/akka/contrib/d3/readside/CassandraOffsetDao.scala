package akka.contrib.d3.readside

import akka.Done

import akka.persistence.query._
import com.datastax.driver.core.{BoundStatement, PreparedStatement}

import scala.concurrent.Future

final class CassandraOffsetDao private[d3] (
    val session:           CassandraSession,
    statement:             PreparedStatement,
    readSideProcessorName: String,
    tag:                   String,
    val lastLoadedOffset:  Offset
) {

  def saveOffset(offset: Offset): Future[Done] = {
    session.executeWrite(bindSaveOffset(offset))
  }

  // scalastyle:off
  def bindSaveOffset(offset: Offset): BoundStatement = {
    offset match {
      case NoOffset            ⇒ statement.bind(readSideProcessorName, tag, null, null)
      case seq: Sequence       ⇒ statement.bind(readSideProcessorName, tag, null, java.lang.Long.valueOf(seq.value))
      case uuid: TimeBasedUUID ⇒ statement.bind(readSideProcessorName, tag, uuid.value, null)
    }
  }
  // scalastyle:on

}
