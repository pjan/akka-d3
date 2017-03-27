package akka.contrib.d3.writeside

import akka.actor._
import akka.contrib.d3._

private[d3] object AggregateManager {
  sealed trait AggregateQuery
  @SerialVersionUID(1L) final case class GetState(id: AggregateId) extends AggregateQuery
  @SerialVersionUID(1L) final case class Exists[A <: AggregateLike](id: AggregateId, pred: A ⇒ Boolean) extends AggregateQuery
  @SerialVersionUID(1L) final case class CommandMessage(id: AggregateId, command: AggregateCommand)
  @SerialVersionUID(1L) final case class RequestPassivation(stopMessage: Any)

  def props[E <: AggregateEntity](
    entityFactory: E#Id ⇒ E,
    settings:      AggregateSettings
  ): Props =
    Props(new AggregateManager[E](entityFactory, settings))
}

private[d3] final class AggregateManager[E <: AggregateEntity](
    entityFactory: E#Id ⇒ E,
    settings:      AggregateSettings
) extends Actor with ActorLogging {
  import AggregateManager._

  private type Aggregate = E
  private type Command = E#Command
  private type Event = E#Event
  private type Id = E#Id

  private var idByRef = Map.empty[ActorRef, Id]
  private var refById = Map.empty[Id, ActorRef]
  private var passivating = Set.empty[ActorRef]
  private var messageBuffers = Map.empty[Id, Vector[(Command, ActorRef)]]

  def totalBufferSize: Int = messageBuffers.foldLeft(0) { (sum, entity) ⇒ sum + entity._2.size }

  // Internal messaging
  case class AggregateStarted(id: Id, ref: ActorRef)
  case class AggregateStopped(id: Id, ref: ActorRef)

  // Receive
  override def receive: Receive =
    receiveCommandMessage orElse
      receiveQuery orElse
      receiveTerminated orElse
      receivePassivate

  private def receiveCommandMessage: Receive = {
    case CommandMessage(Id(id), Command(cmd)) ⇒
      deliverCommand(id, cmd, sender())
  }

  private def receiveQuery: Receive = {
    case GetState(Id(id)) ⇒
      getAggregate(id).tell(AggregateActor.GetState(sender()), sender())
    case Exists(Id(id), p: (Aggregate ⇒ Boolean) @unchecked) ⇒
      getAggregate(id).tell(AggregateActor.Exists(sender(), p), sender())
  }

  private def receiveTerminated: Receive = {
    case Terminated(ref) if idByRef.contains(ref) ⇒
      aggregateTerminated(idByRef(ref))
  }

  private def receivePassivate: Receive = {
    case RequestPassivation(stopMessage) if idByRef.contains(sender()) ⇒
      passivate(idByRef(sender()), stopMessage)
  }

  // Other

  private def aggregateTerminated(id: Id): Unit = {
    val messageBuffer = messageBuffers.getOrElse(id, Vector.empty)
    val ref = refById(id)
    if (messageBuffer.nonEmpty) {
      log.debug("Re-starting aggregate {}, re-sending {} commands", id, messageBuffer.size)
      sendMessageBuffer(AggregateStarted(id, ref))
    } else {
      passivationCompleted(AggregateStopped(id, ref))
    }

    passivating = passivating - ref
  }

  private def passivate(id: Id, stopMessage: Any): Unit = {
    if (!messageBuffers.contains(id)) {
      log.debug("Passivation started for aggregate {}", id)

      passivating = passivating + refById(id)
      messageBuffers = messageBuffers.updated(id, Vector.empty)
      refById(id) ! stopMessage
    }
  }

  private def passivationCompleted(event: AggregateStopped): Unit = {
    log.debug("Aggregate {} stopped", event.id)

    refById -= event.id
    idByRef -= event.ref

    messageBuffers = messageBuffers - event.id
  }

  private def sendMessageBuffer(event: AggregateStarted): Unit = {
    val messageBuffer = messageBuffers.getOrElse(event.id, Vector.empty)
    messageBuffers = messageBuffers - event.id

    if (messageBuffer.nonEmpty) {
      log.debug("Sending buffer of {} commands to aggregate {}", messageBuffer.size, event.id)
      getAggregate(event.id)

      messageBuffer.foreach {
        case (cmd, requester) ⇒ deliverCommand(event.id, cmd, requester)
      }
    }
  }

  private def deliverCommand(id: Id, command: Command, requester: ActorRef): Unit = {
    messageBuffers.get(id) match {
      case None ⇒
        deliverTo(id, command, requester)
      case Some(buffer) if totalBufferSize >= settings.bufferSize ⇒
        log.debug("Buffer is full, dropping command for aggregate {}", id)
        context.system.deadLetters ! CommandMessage(id, command)
      case Some(buffer) ⇒
        log.debug("Command for aggregate {} buffered", id)
        messageBuffers = messageBuffers.updated(id, buffer :+ ((command, requester)))
    }
  }

  private def deliverTo(id: Id, command: Command, requester: ActorRef): Unit = {
    getAggregate(id).tell(command, requester)
  }

  private def getAggregate(id: Id): ActorRef = {
    val name = id.value
    context.child(name).getOrElse {
      log.debug("Starting aggregate {}", id)

      val aggregate = context.watch(context.actorOf(aggregateProps(id).withDispatcher(settings.dispatcher), id.value))
      refById = refById.updated(id, aggregate)
      idByRef = idByRef.updated(aggregate, id)
      aggregate
    }
  }

  private def aggregateProps(id: Id): Props = {
    AggregateActor.props[Aggregate](id, entityFactory, settings)
  }

  // Extractors

  private object Id {
    def unapply(id: AggregateId): Option[Id] =
      Option(id.asInstanceOf[Id])
  }

  private object Command {
    def unapply(cmd: AggregateCommand): Option[Command] =
      Option(cmd.asInstanceOf[Command])
  }

}
