package akka.contrib.d3

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.contrib.d3.readside._
import akka.dispatch.Dispatchers
import akka.persistence.query.Offset
import akka.stream.ActorAttributes
import akka.stream.scaladsl.Flow
import com.datastax.driver.core.{BatchStatement, BoundStatement}
import org.slf4j.LoggerFactory

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

class CassandraReadSideProvider private[d3] (readSide: ReadSide) {

  def cassandra(
    session:     CassandraSession,
    offsetStore: CassandraOffsetStore
  ): CassandraReadSide =
    new CassandraReadSideImpl(readSide, readSide.system, session, offsetStore)

}

object CassandraReadSide {
  type EventHandler[Event] = (EventStreamElement[_ <: Event]) ⇒ Future[immutable.Seq[BoundStatement]]

  final class Handler[Event <: AggregateEvent](
      name:                  String,
      tag:                   Tag,
      session:               CassandraSession,
      offsetStore:           CassandraOffsetStore,
      handlers:              Map[Class[_ <: Event], EventHandler[Event]],
      globalPrepareCallback: () ⇒ Future[Done],
      prepareCallback:       String ⇒ Future[Done],
      dispatcher:            String
  )(
      implicit
      ec: ExecutionContext
  ) extends ReadSideProcessor.Handler[Event] {

    @volatile
    private var maybeOffsetDao: Option[CassandraOffsetDao] = _
    def offsetDao: CassandraOffsetDao = maybeOffsetDao.get

    private val log = LoggerFactory.getLogger(this.getClass)

    protected def invoke(handler: Handler[Event], element: EventStreamElement[Event]): Future[immutable.Seq[BoundStatement]] = {
      for {
        statements ← handler
          .asInstanceOf[EventStreamElement[Event] ⇒ Future[immutable.Seq[BoundStatement]]]
          .apply(element)
      } yield statements :+ offsetDao.bindSaveOffset(element.offset)
    }

    override def globalPrepare(): Future[Done] =
      globalPrepareCallback()

    override def prepare(name: String): Future[Offset] = {
      for {
        _ ← prepareCallback(name)
        dao ← offsetStore.prepare(name, tag.value)
      } yield {
        maybeOffsetDao = Some(dao)
        dao.lastLoadedOffset
      }
    }

    override def rewind(name: String, offset: Offset): Future[Done] = {
      for {
        dao ← if (maybeOffsetDao.isEmpty) offsetStore.prepare(name, tag.value) else Future.successful(offsetDao)
        result ← dao.saveOffset(offset)
      } yield result
    }

    override def flow(): Flow[EventStreamElement[Event], Done, NotUsed] = {

      def invoke(handler: EventHandler[Event], element: EventStreamElement[Event]): Future[immutable.Seq[BoundStatement]] = {
        for {
          statements ← handler
            .asInstanceOf[EventStreamElement[Event] ⇒ Future[immutable.Seq[BoundStatement]]]
            .apply(element)
        } yield statements :+ offsetDao.bindSaveOffset(element.offset)
      }

      def invokeHandler(handler: EventHandler[Event], elem: EventStreamElement[Event]): Future[Done] = {
        for {
          statements ← invoke(handler, elem)
          done ← statements.size match {
            case 0 ⇒ Future.successful(Done)
            case 1 ⇒ session.executeWrite(statements.head)
            case _ ⇒
              val batch = new BatchStatement
              val iter = statements.iterator
              while (iter.hasNext)
                batch.add(iter.next)
              session.executeWriteBatch(batch)
          }
        } yield done
        //        invoke(handler, elem).map { _ ⇒ Done }
      }

      Flow[EventStreamElement[Event]].mapAsync(parallelism = 1) { elem ⇒
        handlers.get(elem.event.getClass.asInstanceOf[Class[Event]]) match {
          case Some(handler) ⇒ invokeHandler(handler, elem)
          case None ⇒
            if (log.isDebugEnabled)
              log.debug("Unhandled event [{}]", elem.event.getClass.getName)
            Future.successful(Done)
        }
      }.withAttributes(ActorAttributes.dispatcher(dispatcher))

    }
  }

  trait ReadSideHandlerBuilder[Event <: AggregateEvent] {
    def setGlobalPrepareCallback(cb: () ⇒ Future[Done]): ReadSideHandlerBuilder[Event]

    def setPrepareCallback(cb: String ⇒ Future[Done]): ReadSideHandlerBuilder[Event]

    def setEventHandler[E <: Event: ClassTag](handler: EventHandler[E]): ReadSideHandlerBuilder[Event]

    def build(): ReadSideProcessor.Handler[Event]
  }

}

abstract class CassandraReadSide extends ReadSide {
  import CassandraReadSide._

  val readSide: ReadSide

  override private[d3] def system =
    readSide.system

  override def register[Event <: AggregateEvent](processor: ReadSideProcessor[Event], settings: Option[ReadSideProcessorSettings]): Unit =
    readSide.register[Event](processor, settings)

  override def start(name: String): Unit =
    readSide.start(name)

  override def stop(name: String): Unit =
    readSide.stop(name)

  override def rewind(name: String, offset: Offset): Future[Done] =
    readSide.rewind(name, offset)

  override def status(name: String): Future[ReadSideStatus] =
    readSide.status(name)

  def builder[Event <: AggregateEvent](name: String, tag: Tag): ReadSideHandlerBuilder[Event]
}

private[d3] class CassandraReadSideImpl(
    val readSide: ReadSide,
    system:       ActorSystem,
    session:      CassandraSession,
    offsetStore:  CassandraOffsetStore
) extends CassandraReadSide {
  import CassandraReadSide._

  def builder[Event <: AggregateEvent](name: String, tag: Tag): ReadSideHandlerBuilder[Event] = {
    val config = system.settings.config.getConfig(s"akka.contrib.d3.readside.processor")

    val dispatcher = (if (config.hasPath(name)) config.getConfig(name).withFallback(config) else config).getString("dispatcher") match {
      case "" ⇒ Dispatchers.DefaultDispatcherId
      case id ⇒ id
    }

    implicit val ec = system.dispatchers.lookup(dispatcher)

    new ReadSideHandlerBuilder[Event] {
      private var globalPrepareCallback: () ⇒ Future[Done] = () ⇒ Future.successful(Done)
      private var prepareCallback: String ⇒ Future[Done] = name ⇒ Future.successful(Done)
      private var handlers = Map.empty[Class[_ <: Event], EventHandler[Event]]

      override def setGlobalPrepareCallback(cb: () ⇒ Future[Done]): ReadSideHandlerBuilder[Event] = {
        globalPrepareCallback = cb
        this
      }

      override def setPrepareCallback(cb: (String) ⇒ Future[Done]): ReadSideHandlerBuilder[Event] = {
        prepareCallback = cb
        this
      }

      override def setEventHandler[E <: Event: ClassTag](handler: EventHandler[E]): ReadSideHandlerBuilder[Event] = {
        val eventClass = implicitly[ClassTag[E]].runtimeClass.asInstanceOf[Class[Event]]
        handlers += (eventClass → handler.asInstanceOf[EventHandler[Event]])
        this
      }

      override def build(): ReadSideProcessor.Handler[Event] =
        new Handler[Event](name, tag, session, offsetStore, handlers, globalPrepareCallback, prepareCallback, dispatcher)

    }
  }

}
