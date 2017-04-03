package akka.contrib.d3.readside

import akka.Done
import akka.actor._
import akka.contrib.d3.utils.StartupTasks
import akka.persistence.query._
import akka.util.Timeout
import com.datastax.driver.core.{PreparedStatement, Row}

import scala.concurrent.Future
import scala.concurrent.duration._

object CassandraOffsetStore {
  def apply(
    system:  ActorSystem,
    session: CassandraSession,
    timeout: FiniteDuration
  ): CassandraOffsetStore =
    new CassandraOffsetStore(system, session, timeout)
}

final class CassandraOffsetStore private[d3] (
    system:  ActorSystem,
    session: CassandraSession,
    timeout: FiniteDuration
) {
  import system.dispatcher

  private val startupTask = StartupTasks(system).create(
    name = "cassandraOffsetStorePrepare",
    task = () ⇒ createTable(),
    timeout = timeout,
    minBackoff = timeout / 20,
    maxBackoff = timeout / 5,
    randomBackoffFactor = 0.2
  )

  def prepare(readSideProcessorName: String, tag: String): Future[CassandraOffsetDao] = {
    implicit val timeout = Timeout(10.seconds)
    for {
      _ ← startupTask.execute()
      offset ← readOffset(readSideProcessorName, tag)
      statement ← prepareWriteOffset
    } yield {
      new CassandraOffsetDao(session, statement, readSideProcessorName, tag, offset)
    }
  }

  private def createTable(): Future[Done] = {
    session.executeCreateTable(
      s"""|CREATE TABLE IF NOT EXISTS offsets (
          |  processor_id text, tag text, time_uuid_offset timeuuid, sequence_offset bigint,
          |  PRIMARY KEY (processor_id, tag)
          |)""".stripMargin
    )
  }

  private def readOffset(eventProcessorId: String, tag: String): Future[Offset] = {
    session.selectOne(
      s"SELECT time_uuid_offset, sequence_offset FROM offsets WHERE processor_id = ? AND tag = ?",
      eventProcessorId, tag
    ).map(extractOffset)
  }

  private def extractOffset(maybeRow: Option[Row]): Offset = {
    (for {
      row ← maybeRow
      offset ← Option(row.getUUID("time_uuid_offset")).map(TimeBasedUUID).orElse { if (row.isNull("sequence_offset")) None else Some(row.getLong("sequence_offset")).map(Sequence) }
    } yield offset).getOrElse(NoOffset)
  }

  private def prepareWriteOffset: Future[PreparedStatement] = {
    session.prepare("INSERT INTO offsets (processor_id, tag, time_uuid_offset, sequence_offset) VALUES (?, ?, ?, ?)")
  }

}
