package akka.contrib.d3.query

import akka.persistence.query.scaladsl.EventsByTagQuery

private[d3] abstract class ReadJournalProvider {
  def defaultReadJournalPluginId: String
  def readJournal(readJournalPluginId: String): EventsByTagQuery
  def readJournal: EventsByTagQuery =
    readJournal(defaultReadJournalPluginId)
}
