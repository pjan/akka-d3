package akka.contrib.d3.readside

import akka.actor._
import akka.persistence.query.Offset

import scala.concurrent.duration._

private[d3] object ReadSideCoordinator {
  @SerialVersionUID(1L) final case class Register(name: String, actorRef: ActorRef)
  @SerialVersionUID(1L) final case class Start(name: String)
  @SerialVersionUID(1L) final case class Stop(name: String)
  @SerialVersionUID(1L) final case class Rewind(name: String, offset: Offset)
  @SerialVersionUID(1L) final case class IsActive(name: String)
  @SerialVersionUID(1L) final case class IsStopped(name: String)
  @SerialVersionUID(1L) final case class GetStatus(name: String)

  case class ReadSideProcessorState(status: ReadSideProcessorState.Status, delta: List[ReadSideProcessorState.Status] = List.empty) {
    def registerDelta(status: ReadSideProcessorState.Status): ReadSideProcessorState = {
      if (this.status == status) this.copy(delta = List.empty)
      else if (this.status != status && delta.isEmpty) this.copy(delta = List(status))
      else ReadSideProcessorState(status)
    }

    def isStarted: Boolean = status == ReadSideProcessorState.Status.Started
    def isStopped: Boolean = status == ReadSideProcessorState.Status.Stopped
    def isUnknown: Boolean = status == ReadSideProcessorState.Status.Unknown
  }
  object ReadSideProcessorState {
    sealed trait Status
    object Status {
      case object Unknown extends Status
      case object Started extends Status
      case object Stopped extends Status
    }
  }

  def props(
    heartbeatInterval: FiniteDuration
  ): Props =
    Props(new ReadSideCoordinator(heartbeatInterval))
}

private[d3] final class ReadSideCoordinator(
    heartbeatInterval: FiniteDuration
) extends Actor with ActorLogging {
  import ReadSideCoordinator._
  import context.dispatcher

  private var refByName = Map.empty[String, ActorRef]
  private var nameByRef = Map.empty[ActorRef, String]
  private var stateByName = Map.empty[String, ReadSideProcessorState]

  private val tick = context.system.scheduler.schedule(0.seconds, heartbeatInterval, self, Tick)

  // Internal messaging
  case object Tick extends DeadLetterSuppression

  // Lifecycle
  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    log.error("Restarting due to exception", reason)
    super.preRestart(reason, message)
  }

  override def postStop(): Unit = {
    tick.cancel()
    ()
  }

  // Receive
  override def receive: Receive = {
    case Register(name, actorRef) ⇒
      log.info("Registering {}", name)
      context.watch(actorRef)
      refByName = refByName.updated(name, actorRef)
      nameByRef = nameByRef.updated(actorRef, name)
      if (stateByName.get(name).isEmpty) stateByName = stateByName.updated(name, ReadSideProcessorState(ReadSideProcessorState.Status.Unknown))

    case Terminated(actorRef) if nameByRef.get(actorRef).isDefined ⇒
      val name = nameByRef.getOrElse(actorRef, "")
      log.info("{} terminated", name)
      refByName -= name
      nameByRef -= actorRef

    case Tick ⇒
      stateByName.foreach {
        case (name, state) if state.isStarted ⇒
          refByName.get(name).foreach { _ ! ReadSideActor.EnsureActive(name) }
        case (name, state) if state.isStopped ⇒
          refByName.get(name).foreach { _ ! ReadSideActor.EnsureStopped(name) }
        case _ ⇒ // do nothing
      }

    case Start(name) ⇒
      log.debug(s"Start {}", name)
      stateByName = stateByName.updated(name, ReadSideProcessorState(ReadSideProcessorState.Status.Started))
      refByName.get(name).foreach { _ ! ReadSideActor.EnsureActive(name) }

    case Stop(name) ⇒
      log.debug(s"Stop {}", name)
      stateByName = stateByName.updated(name, ReadSideProcessorState(ReadSideProcessorState.Status.Stopped))
      refByName.get(name).foreach { _ ! ReadSideActor.EnsureStopped(name) }

    case Rewind(name, offset) ⇒
      log.debug(s"Rewind {} to offset {}", name, offset)
      refByName.get(name).foreach { _ forward ReadSideActor.AttemptRewind(name, offset) }

    case IsActive(name) ⇒
      registerActorStatus(name, sender, ReadSideProcessorState.Status.Started)

    case IsStopped(name) ⇒
      registerActorStatus(name, sender, ReadSideProcessorState.Status.Stopped)

    case GetStatus(name) ⇒
      log.debug(s"Get status for {}", name)
      refByName.get(name).foreach { _ forward ReadSideActor.GetStatus(name) }

  }

  // Other

  private def registerActorStatus(name: String, actorRef: ActorRef, status: ReadSideProcessorState.Status): Unit = {
    val updatedState = stateByName.get(name).fold {
      ReadSideProcessorState(ReadSideProcessorState.Status.Unknown, List(status))
    } {
      currentState ⇒ currentState.registerDelta(status)
    }
    refByName = refByName.updated(name, actorRef)
    nameByRef = nameByRef.updated(actorRef, name)
    stateByName = stateByName.updated(name, updatedState)
  }

}
