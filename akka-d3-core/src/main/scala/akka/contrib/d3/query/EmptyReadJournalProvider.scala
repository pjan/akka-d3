package akka.contrib.d3.query

import akka.NotUsed
import akka.actor.ExtendedActorSystem
import akka.persistence.query.{EventEnvelope2, Offset}
import akka.persistence.query.scaladsl.EventsByTagQuery2
import akka.stream.scaladsl.Source

class EmptyReadJournalProvider(
    system: ExtendedActorSystem
) extends ReadJournalProvider {
  override val defaultReadJournalPluginId: String = ""
  override def readJournal(readJournalPluginId: String): EventsByTagQuery2 =
    new EventsByTagQuery2 {
      override def eventsByTag(
        tag:    String,
        offset: Offset
      ): Source[EventEnvelope2, NotUsed] = {
        Source.empty
      }
    }
}
