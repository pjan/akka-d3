package akka.contrib.d3.readside

import akka.actor.{ActorSystem, ExtendedActorSystem}
import akka.contrib.d3.utils.cassandra.{CassandraSink, CassandraSource}
import akka.dispatch.Dispatchers
import akka.event.Logging
import akka.persistence.cassandra.{CassandraPluginConfig, SessionProvider}
import akka.persistence.cassandra.session.CassandraSessionSettings
import akka.stream.scaladsl.{Sink, Source}
import akka.{Done, NotUsed}
import com.datastax.driver.core._

import scala.annotation.varargs
import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

object CassandraSession {
  def apply(
    system:           ActorSystem,
    settings:         CassandraSessionSettings,
    executionContext: ExecutionContext
  ): CassandraSession =
    new CassandraSession(create(system, settings, executionContext))

  def apply(system: ActorSystem): CassandraSession =
    apply(system, None)

  def apply(system: ActorSystem, name: String): CassandraSession =
    apply(system, Some(name))

  private def apply(system: ActorSystem, name: Option[String]): CassandraSession = {
    val cassandraConfig = system.settings.config.getConfig("akka.contrib.d3.readside.cassandra")

    val sessionCConfig =
      if (name.isDefined && cassandraConfig.hasPath(name.get)) cassandraConfig.getConfig(name.get).withFallback(cassandraConfig)
      else cassandraConfig

    val settings = CassandraSessionSettings(
      system.settings.config.getConfig(
        sessionCConfig.getString("plugin")
      )
    )
    val executionContext = system.dispatchers.lookup(
      sessionCConfig.getString("dispatcher") match {
        case "" ⇒ Dispatchers.DefaultDispatcherId
        case id ⇒ id
      }
    )

    apply(system, settings, executionContext)
  }

  private def create(
    system:           ActorSystem,
    settings:         CassandraSessionSettings,
    executionContext: ExecutionContext
  ): akka.persistence.cassandra.session.scaladsl.CassandraSession = {
    import akka.persistence.cassandra.ListenableFutureConverter
    import akka.util.Helpers.Requiring

    import scala.collection.JavaConverters._

    val cfg = settings.config
    val replicationStrategy: String = CassandraPluginConfig.getReplicationStrategy(
      cfg.getString("replication-strategy"),
      cfg.getInt("replication-factor"),
      cfg.getStringList("data-center-replication-factors").asScala
    )

    val keyspaceAutoCreate: Boolean = cfg.getBoolean("keyspace-autocreate")
    val keyspace: String = cfg.getString("keyspace").requiring(
      !keyspaceAutoCreate || _ > "",
      "'keyspace' configuration must be defined, or use keyspace-autocreate=off"
    )

    def init(session: Session): Future[Done] = {
      implicit val ec = executionContext
      if (keyspaceAutoCreate) {
        val result1 =
          session.executeAsync(s"""
            CREATE KEYSPACE IF NOT EXISTS $keyspace
            WITH REPLICATION = { 'class' : $replicationStrategy }
            """).asScala(ec)
        result1.flatMap { _ ⇒
          session.executeAsync(s"USE $keyspace;").asScala(ec)
        }.map(_ ⇒ Done)
      } else if (keyspace != "")
        session.executeAsync(s"USE $keyspace;").asScala(ec).map(_ ⇒ Done)
      else
        Future.successful(Done)
    }

    val metricsCategory = "akka-contrib-d3-" + system.name

    new akka.persistence.cassandra.session.scaladsl.CassandraSession(
      system,
      SessionProvider(system.asInstanceOf[ExtendedActorSystem], settings.config),
      settings,
      executionContext,
      Logging.getLogger(system, this.getClass),
      metricsCategory,
      init
    )
  }
}

final class CassandraSession private[d3] (
    val delegate: akka.persistence.cassandra.session.scaladsl.CassandraSession
) {

  def underlying(): Future[Session] =
    delegate.underlying()

  def close(): Unit =
    delegate.close()

  def executeCreateTable(stmt: String): Future[Done] =
    delegate.executeCreateTable(stmt)

  def prepare(stmt: String): Future[PreparedStatement] =
    delegate.prepare(stmt)

  def executeWriteBatch(batch: BatchStatement): Future[Done] =
    delegate.executeWriteBatch(batch)

  def executeWrite(stmt: Statement): Future[Done] =
    delegate.executeWrite(stmt)

  def source(stmt: Statement)(implicit ec: ExecutionContext): Future[Source[Row, NotUsed]] = for {
    session ← delegate.underlying()
    source = CassandraSource.fromStatement(stmt)(session)
  } yield source

  def sink[T](
    parallelism:     Int,
    statement:       PreparedStatement,
    statementBinder: (T, PreparedStatement) ⇒ BoundStatement
  )(
    implicit
    ec: ExecutionContext
  ): Future[Sink[T, Future[Done]]] = for {
    session ← delegate.underlying()
    sink = CassandraSink(parallelism, statement, statementBinder)(session, ec)
  } yield sink

  @varargs
  def executeWrite(stmt: String, bindValues: AnyRef*): Future[Done] =
    delegate.executeWrite(stmt, bindValues: _*)

  def select(stmt: Statement): Source[Row, NotUsed] =
    delegate.select(stmt)

  @varargs
  def select(stmt: String, bindValues: AnyRef*): Source[Row, NotUsed] =
    delegate.select(stmt, bindValues: _*)

  def selectAll(stmt: Statement): Future[immutable.Seq[Row]] =
    delegate.selectAll(stmt)

  @varargs
  def selectAll(stmt: String, bindValues: AnyRef*): Future[immutable.Seq[Row]] =
    delegate.selectAll(stmt, bindValues: _*)

  def selectOne(stmt: Statement): Future[Option[Row]] =
    delegate.selectOne(stmt)

  def selectOne(stmt: String, bindValues: AnyRef*): Future[Option[Row]] =
    delegate.selectOne(stmt, bindValues: _*)

}
