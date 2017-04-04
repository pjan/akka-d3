package akka.contrib.d3.readside

import akka.Done
import akka.actor._
import akka.contrib.d3._
import akka.contrib.d3.utils.StartupTask
import akka.persistence.query.Offset
import akka.stream._
import akka.stream.scaladsl._
import akka.util.Timeout

import scala.concurrent.duration._

object ReadSideActor {
  sealed trait Message extends Serializable { def name: String }
  @SerialVersionUID(1L) final case class EnsureActive(name: String) extends Message
  @SerialVersionUID(1L) final case class EnsureStopped(name: String) extends Message
  @SerialVersionUID(1L) final case class AttemptRewind(name: String, offset: Offset) extends Message
}

class ReadSideActor[Event <: AggregateEvent](
    processor:         ReadSideProcessor[Event],
    settings:          ReadSideProcessorSettings,
    heartbeatInterval: FiniteDuration,
    globalStartupTask: StartupTask,
    coordinator:       ActorRef
)(
    implicit
    materializer: Materializer
) extends Actor with Stash with ActorLogging {
  import ReadSideActor._
  import akka.pattern.pipe
  private val system = context.system
  implicit private val dispatcher = system.dispatchers.lookup(settings.dispatcher)

  private var shutdown: Option[KillSwitch] = None

  private val tick = context.system.scheduler.schedule(0.seconds, heartbeatInterval, self, Tick)

  // Internal messaging
  case object Tick extends DeadLetterSuppression
  case class Start(offset: Offset)
  case class Rewind(offset: Offset, requester: ActorRef)

  override def preStart(): Unit = {
    coordinator ! ReadSideCoordinator.Register(processor.name, self)
    if (settings.autoStart) coordinator ! ReadSideCoordinator.Start(processor.name)
  }

  override def postStop: Unit = {
    tick.cancel()
    shutdown.foreach(_.shutdown())
  }

  override def receive: Receive = stopped

  private def stopped: Receive = {
    case EnsureActive(name) if name == processor.name ⇒
      log.info("[{}] preparing for start.", name)
      implicit val timeout = Timeout(settings.globalStartupTimeout)
      val tag = processor.tag

      globalStartupTask.execute() pipeTo self
      context.become(preparingForStart(name, tag))

    case EnsureStopped(name) ⇒
      log.debug("[{}] not running.", name)

    case AttemptRewind(name, offset) if name == processor.name ⇒
      log.info("[{}] preparing for rewind to offset {}.", name, offset)
      implicit val timeout = Timeout(settings.rewindTimeout)

      globalStartupTask.execute() pipeTo self
      context.become(preparingForRewind(name, offset, sender))

    case Tick ⇒
      coordinator ! ReadSideCoordinator.IsStopped(processor.name)
  }

  private def preparingForRewind(name: String, rewindOffset: Offset, requester: ActorRef): Receive = {
    case Done ⇒
      log.info("[{}] prepared for rewind to offset {}", name, rewindOffset)
      val handler = processor.buildHandler()
      handler.prepare(processor.name).map { _ ⇒ Rewind(rewindOffset, requester) } pipeTo self
      unstashAll()
      context.become(rewinding(name, handler))

    case Status.Failure(e) ⇒
      unstashAll()
      throw e

    case Tick ⇒ // Do nothing while preparing

    case _ ⇒
      stash()
  }

  private def rewinding(name: String, handler: ReadSideProcessor.Handler[Event]): Receive = {
    case Rewind(offset, requester) ⇒
      log.info("[{}] rewinding to offset {}", name, offset)
      handler.rewind(name, offset) pipeTo requester
      unstashAll()
      context.become(stopped)

    case Tick ⇒ // Do nothing while rewinding

    case _ ⇒
      stash()
  }

  private def preparingForStart(name: String, tag: Tag): Receive = {
    case Done ⇒
      log.info("[{}] prepared for start.", name)
      val handler = processor.buildHandler()
      handler.prepare(processor.name).map(Start) pipeTo self
      unstashAll()
      context.become(active(name, tag, handler))

    case Status.Failure(e) ⇒
      unstashAll()
      throw e

    case Tick ⇒ // Do nothing while preparing

    case _ ⇒
      stash()
  }

  private def active(name: String, tag: Tag, handler: ReadSideProcessor.Handler[Event]): Receive = {
    case Start(offset) ⇒
      log.info("[{}] starting.", name)
      val (killSwitch, streamDone) =
        processor.eventStreamFactory(tag, offset)
          .viaMat(KillSwitches.single)(Keep.right)
          .via(handler.flow())
          .toMat(Sink.ignore)(Keep.both)
          .run()

      streamDone pipeTo self
      shutdown = Some(killSwitch)

    case EnsureActive(_) ⇒
      log.debug("[{}] is active.", name)

    case EnsureStopped(_) ⇒
      log.info("[{}] stopping.", name)
      shutdown.foreach(_.shutdown())
      context.become(stopping(name))

    case AttemptRewind(n, _) if n == processor.name ⇒
      sender ! Status.Failure(new IllegalStateException(s"Can't rewind when active."))

    case Tick ⇒
      coordinator ! ReadSideCoordinator.IsActive(processor.name)

    case Done ⇒
      log.info("[{}] terminated when it shouldn't.", name)
      throw new IllegalStateException(s"Stream $name terminated when it shouldn't")
  }

  private def stopping(name: String): Receive = {
    case Done ⇒
      log.info(s"[{}] stopped.", name)
      unstashAll()
      context.become(stopped)

    case _ ⇒
      stash()
  }

}
