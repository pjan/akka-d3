package akka.contrib.d3

import akka.actor._

import scala.reflect.ClassTag
import scala.util.Try

private[d3] object AggregateManager {
  sealed trait AggregateQuery
  @SerialVersionUID(1L) final case class GetState(id: AggregateId) extends AggregateQuery
  @SerialVersionUID(1L) final case class Exists(id: AggregateId) extends AggregateQuery
  @SerialVersionUID(1L) final case class CommandMessage(id: AggregateId, command: DomainCommand)
  @SerialVersionUID(1L) final case class RequestPassivation(stopMessage: Any)

  def props[A <: AggregateLike](
    behavior: Behavior[A],
    settings: AggregateSettings
  )(
    implicit
    idct:  ClassTag[A#Id],
    cmdct: ClassTag[A#Command]
  ): Props =
    Props(new AggregateManager[A](behavior, settings))
}

private[d3] class AggregateManager[A <: AggregateLike](
    behavior: Behavior[A],
    settings: AggregateSettings
)(
    implicit
    idct:  ClassTag[A#Id],
    cmdct: ClassTag[A#Command]
) extends Actor with ActorLogging with AggregateAliases {
  import AggregateManager._

  type Aggregate = A

  var idByRef = Map.empty[ActorRef, Id]
  var refById = Map.empty[Id, ActorRef]
  var passivating = Set.empty[ActorRef]
  var messageBuffers = Map.empty[Id, Vector[(Command, ActorRef)]]

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

  def receiveCommandMessage: Receive = {
    case ValidCommandMessage(id, cmd) ⇒
      deliverCommand(id, cmd, sender())
    case cmdMsg @ InvalidCommandMessage(id, cmd) ⇒
      log.debug("Invalid CommandMessage: {}", cmdMsg)
      sender ! Left(new IllegalArgumentException(s"Invalid CommandMessage: $cmdMsg"))
    case cmd: Command ⇒
      log.debug("Command without AggregateId: {}", cmd)
      sender ! Left(new IllegalArgumentException(s"Command without AggregateId: $cmd"))
  }

  def receiveQuery: Receive = {
    case GetState(ValidId(id)) ⇒
      getAggregate(id).tell(AggregateActor.GetState(sender()), sender())
    case Exists(ValidId(id)) ⇒
      getAggregate(id).tell(AggregateActor.Exists(sender()), sender())
    case GetState(InvalidId(id)) ⇒
      log.debug("Invalid id: {}", id)
      sender ! Left(new IllegalArgumentException(s"Invalid id type: ${id.getClass.getSimpleName}"))
    case Exists(InvalidId(id)) ⇒
      log.debug("Invalid id: {}", id)
      sender ! Left(new IllegalArgumentException(s"Invalid id type: ${id.getClass.getSimpleName}"))
  }

  def receiveTerminated: Receive = {
    case Terminated(ref) if idByRef.contains(ref) ⇒
      aggregateTerminated(idByRef(ref))
  }

  def receivePassivate: Receive = {
    case RequestPassivation(stopMessage) if idByRef.contains(sender()) ⇒
      passivate(idByRef(sender()), stopMessage)
  }

  // Other

  def aggregateTerminated(id: Id): Unit = {
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

  def passivate(id: Id, stopMessage: Any): Unit = {
    if (!messageBuffers.contains(id)) {
      log.debug("Passivation started for aggregate {}", id)

      passivating = passivating + refById(id)
      messageBuffers = messageBuffers.updated(id, Vector.empty)
      refById(id) ! stopMessage
    }
  }

  def passivationCompleted(event: AggregateStopped): Unit = {
    log.debug("Aggregate {} stopped", event.id)

    refById -= event.id
    idByRef -= event.ref

    messageBuffers = messageBuffers - event.id
  }

  def sendMessageBuffer(event: AggregateStarted): Unit = {
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

  def deliverCommand(id: Id, command: Command, requester: ActorRef): Unit = {
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

  def deliverTo(id: Id, command: Command, requester: ActorRef): Unit = {
    getAggregate(id).tell(command, requester)
  }

  def getAggregate(id: Id): ActorRef = {
    val name = id.value
    context.child(name).getOrElse {
      log.debug("Starting aggregate {}", id)

      val aggregate = context.watch(context.actorOf(aggregateProps(id).withDispatcher(settings.dispatcher), id.value))
      refById = refById.updated(id, aggregate)
      idByRef = idByRef.updated(aggregate, id)
      aggregate
    }
  }

  def aggregateProps(id: Id): Props = {
    AggregateActor.props[Aggregate](id, behavior, settings)
  }

  // Extractors

  object ValidCommandMessage {
    def unapply(cmdMsg: CommandMessage): Option[(Id, Command)] = {
      // TODO: the check for the type of the command still doesn't work
      (for {
        id ← Try(cmdMsg.id.asInstanceOf[Id]) if id.getClass == idct.runtimeClass
        cmd ← Try(cmdMsg.command.asInstanceOf[Command]) if cmdct.runtimeClass.isInstance(cmd)
      } yield (id, cmd)).toOption
    }
  }

  object InvalidCommandMessage {
    def unapply(cmdMsg: CommandMessage): Option[(AggregateId, DomainCommand)] = {
      // TODO: the check for the type of the command still doesn't work
      if (cmdMsg.id.getClass != idct.runtimeClass || cmdct.runtimeClass.isInstance(cmdMsg.command)) Some((cmdMsg.id, cmdMsg.command)) else None
    }
  }

  object ValidId {
    def unapply(aggregateId: Aggregate#Id): Option[Aggregate#Id] = {
      if (aggregateId.getClass == idct.runtimeClass) Some(aggregateId) else None
    }
  }

  object InvalidId {
    def unapply(aggregateId: AggregateId): Option[AggregateId] = {
      if (aggregateId.getClass != idct.runtimeClass) Some(aggregateId) else None
    }
  }

}
