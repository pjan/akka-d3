package akka.contrib.d3.query

import akka.actor.ExtendedActorSystem
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.scaladsl.EventsByTagQuery2

class CassandraReadJournalProvider(
    system: ExtendedActorSystem
) extends ReadJournalProvider {
  override val defaultReadJournalPluginId: String = CassandraReadJournal.Identifier
  override def readJournal(readJournalPluginId: String): EventsByTagQuery2 =
    PersistenceQuery(system).readJournalFor(readJournalPluginId)
}
