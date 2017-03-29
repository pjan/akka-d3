package akka.contrib.d3

import java.net.URLEncoder

import akka.{Done, NotUsed}
import akka.persistence.query._
import akka.stream.scaladsl._

import scala.concurrent.Future

abstract class ReadSideProcessor[Event <: AggregateEvent] {
  def name: String

  def tag: Tag

  def globalPrepare(): Future[Done] =
    Future.successful(Done)

  def prepare(name: String): Future[Offset] =
    Future.successful(NoOffset)

  def rewind(name: String, offset: Offset): Future[Done] =
    Future.successful(Done)

  def eventStreamFactory(tag: Tag, fromOffset: Offset): Source[EventStreamElement[Event], NotUsed]

  def handle(): Flow[EventStreamElement[Event], Done, NotUsed]

  final def encodedName: String =
    URLEncoder.encode(name, "utf-8")
}
