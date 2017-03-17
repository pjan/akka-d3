package akka.contrib.d3.query

import akka.persistence.query.scaladsl.EventsByTagQuery2

private[d3] abstract class ReadJournalProvider {
  def defaultReadJournalPluginId: String
  def readJournal(readJournalPluginId: String): EventsByTagQuery2
  def readJournal: EventsByTagQuery2 =
    readJournal(defaultReadJournalPluginId)
}
