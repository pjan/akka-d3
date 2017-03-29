package akka.contrib.d3.writeside

import akka.actor._
import akka.contrib.d3._
import akka.pattern._
import akka.persistence._

import scala.concurrent.{Future, TimeoutException}
import scala.util.control.NonFatal

private[d3] object AggregateActor {
  @SerialVersionUID(1L) final case class GetState(requester: ActorRef)
  @SerialVersionUID(1L) case object Passivate

  def props[E <: AggregateEntity](
    identifier:    E#Id,
    entityFactory: E#Id ⇒ E,
    settings:      AggregateSettings
  ): Props =
    Props(new AggregateActor(identifier, entityFactory(identifier), settings))
}

private[d3] class AggregateActor[E <: AggregateEntity](
    identifier: E#Id,
    val entity: AggregateEntity,
    settings:   AggregateSettings
) extends PersistentActor with ActorLogging {
  import AggregateActor._
  import AggregateState._

  private type Aggregate = entity.Aggregate
  private type Command = entity.Command
  private type Event = entity.Event
  private type State = entity.State

  private val system = context.system
  implicit private val dispatcher = system.dispatchers.lookup(settings.dispatcher)

  // Actor State
  private sealed trait ActorState
  private case object Available extends ActorState
  private case object Busy extends ActorState

  // Internal messaging
  private case class Succeeded(events: collection.immutable.Seq[Event], requester: ActorRef)
  private case class Failed(command: Command, error: Throwable, requester: ActorRef)

  // Configuration
  override val persistenceId: String = entity.identifier.value
  override val journalPluginId: String = settings.journalPluginId
  override val snapshotPluginId: String = settings.snapshotPluginId

  // State
  private var aggregateState = entity.initialState
  private var eventsSinceLastSnapshot: Int = 0
  private var lastSnapshotSequenceNr: Option[Long] = None

  // Lifecycle
  override def preStart(): Unit = {
    context.setReceiveTimeout(settings.passivationTimeout)
  }

  // Recovery handler
  override def receiveRecover: Receive = {
    case SnapshotOffer(snapshotMetadata, aggregate: Aggregate @unchecked) ⇒
      eventsSinceLastSnapshot = 0
      log.debug("{} | recovering snapshot: {}", identifier, aggregate)
      restoreState(snapshotMetadata, aggregate)
    case RecoveryCompleted ⇒
      log.debug("{} | recovery completed", identifier)
      changeState(Available)
    case e: AggregateEvent ⇒
      val event = e.asInstanceOf[Event]
      log.debug("{} | replaying event: {}", identifier, event)
      eventsSinceLastSnapshot += 1
      entity.onEvent(aggregateState, event) match {
        case Right(newAggregateState) ⇒
          aggregateState = newAggregateState
          log.debug("{} | state after event: {}", identifier, aggregateState)
        case Left(e) ⇒
          log.error("{} | application of event during recovery failed: {}", identifier, event)
      }
    case other ⇒
      log.error("{} | unknown message during recovery: {}", identifier, other)
  }

  // Receive
  override def receiveCommand: Receive = available

  private def available: Receive = {
    val availableReceive: Receive = {
      case c: AggregateCommand ⇒
        val cmd = c.asInstanceOf[Command]
        log.debug("{} | received command: {}", identifier, cmd)
        val timeout = after(duration = settings.commandHandlingTimeout, using = context.system.scheduler) { Future.failed(new TimeoutException(s"Timed out for command: $cmd")) }
        val result = Future.firstCompletedOf(Seq(entity.onCommand(aggregateState, cmd), timeout))
        val requester = sender()

        result map {
          case Right(events) ⇒ Succeeded(events, requester)
          case Left(e)       ⇒ Failed(cmd, e, requester)
        } recover {
          case NonFatal(e) ⇒ Failed(cmd, e, requester)
        } pipeTo self

        changeState(Busy)
    }

    availableReceive orElse defaultReceive
  }

  private def busy: Receive = {
    val busyReceive: Receive = {
      case Succeeded(events, requester) ⇒
        onSucceeded(events, requester)
      case Failed(command, error, requester) ⇒
        onFailed(command, error, requester)
      case _ ⇒
        stash()
    }

    busyReceive orElse defaultReceive
  }

  private def defaultReceive: Receive = {
    case GetState(requester) ⇒ sendState(requester)
    case Passivate           ⇒ context.stop(self)
    case x: SaveSnapshotSuccess ⇒
      lastSnapshotSequenceNr.foreach { sequenceNr ⇒
        deleteSnapshots(SnapshotSelectionCriteria(maxSequenceNr = sequenceNr))
      }
      lastSnapshotSequenceNr = Option(x.metadata.sequenceNr)
  }

  override def unhandled(message: Any): Unit = {
    message match {
      case ReceiveTimeout ⇒
        log.debug("{} | requesting passivation", identifier)
        requestPassivation()
      case AggregateActor.Passivate ⇒
        log.debug("{} | passivation completed", identifier)
        context.stop(self)
      case _ ⇒ super.unhandled(message)
    }
  }

  // Private

  private def requestPassivation(): Unit = {
    context.parent ! AggregateManager.RequestPassivation(Passivate)
  }

  private def onFailed(command: Command, error: Throwable, requester: ActorRef): Unit = {
    requester ! Left(error)
    changeState(Available)
  }

  private def onSucceeded(events: collection.immutable.Seq[Event], requester: ActorRef): Unit = {
    if (events.nonEmpty) {
      persistAll(events) { evt ⇒
        onEventPersisted(evt)
        requester ! Right(events)
      }
    }
    changeState(Available)
  }

  private def onEventPersisted(event: Event): Unit = {
    entity.onEvent(aggregateState, event) match {
      case Right(newAggregateState) ⇒
        aggregateState = newAggregateState
        eventsSinceLastSnapshot += 1
        saveSnapshotIfRequired()
      case Left(e) ⇒
        log.error("{} | application of event failed: {}", identifier, event)
    }
  }

  private def saveSnapshotIfRequired(): Unit = {
    if (eventsSinceLastSnapshot >= settings.eventsPerSnapshot) {
      aggregateState match {
        case Initialized(aggregate) ⇒
          log.debug("{} | saving snapshot after {} events", identifier, settings.eventsPerSnapshot)
          saveSnapshot(aggregate)
        case _ ⇒
      }
      eventsSinceLastSnapshot = 0
    }
  }

  private def sendState(requester: ActorRef): Unit = {
    aggregateState match {
      case Initialized(aggregate) ⇒
        log.debug("{} | sending state to {}", aggregate.id, requester)
        requester ! Right(aggregate)
      case Uninitialized(id) ⇒
        log.debug("{} | failed to send uninitialized state to {}", id, requester)
        requester ! Left(new NoSuchElementException(s"Aggregate $id doesn't exist"))
    }
  }

  private def changeState(state: ActorState): Unit = {
    state match {
      case Available ⇒
        log.debug("{} | <<Available>>", identifier)
        context.become(available)
        unstashAll()
      case Busy ⇒
        log.debug("{} | <<Busy>>", identifier)
        context.become(busy)
    }
  }

  private def restoreState(metadata: SnapshotMetadata, aggregate: Aggregate): Unit = {
    lastSnapshotSequenceNr = Option(metadata.sequenceNr)
    aggregateState = Initialized(aggregate)
    changeState(Available)
  }

}
