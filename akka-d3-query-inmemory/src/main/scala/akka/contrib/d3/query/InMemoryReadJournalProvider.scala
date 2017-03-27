package akka.contrib.d3.query

import akka.actor.ExtendedActorSystem
import akka.persistence.inmemory.query.scaladsl.InMemoryReadJournal
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.scaladsl.EventsByTagQuery2

private[d3] final class InMemoryReadJournalProvider(
    system: ExtendedActorSystem
) extends ReadJournalProvider {
  override val defaultReadJournalPluginId: String = InMemoryReadJournal.Identifier
  override def readJournal(readJournalPluginId: String): EventsByTagQuery2 =
    PersistenceQuery(system).readJournalFor(readJournalPluginId)
}
