package akka.contrib.d3.query

import akka.NotUsed
import akka.actor.ExtendedActorSystem
import akka.persistence.query.{EventEnvelope, Offset}
import akka.persistence.query.scaladsl.EventsByTagQuery
import akka.stream.scaladsl.Source

final class EmptyReadJournalProvider(
    system: ExtendedActorSystem
) extends ReadJournalProvider {
  override val defaultReadJournalPluginId: String = ""
  override def readJournal(readJournalPluginId: String): EventsByTagQuery =
    new EventsByTagQuery {
      override def eventsByTag(
        tag:    String,
        offset: Offset
      ): Source[EventEnvelope, NotUsed] = {
        Source.empty
      }
    }
}
