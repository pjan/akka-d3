package akka.contrib.d3.utils

import akka.actor._
import akka.event.Logging

import scala.concurrent.duration._

private[d3] object LocalSingletonProxySettings {
  def apply(
    singletonName:                   String,
    bufferSize:                      Int,
    singletonIdentificationInterval: FiniteDuration
  ): LocalSingletonProxySettings =
    new LocalSingletonProxySettings(singletonName, bufferSize, singletonIdentificationInterval)
}

private[d3] final class LocalSingletonProxySettings(
    val singletonName:                   String,
    val bufferSize:                      Int,
    val singletonIdentificationInterval: FiniteDuration
) {

  def withSingletonName(name: String): LocalSingletonProxySettings = copy(singletonName = name)

  def withBufferSize(size: Int): LocalSingletonProxySettings = copy(bufferSize = size)

  def withSingletonIdentificationInterval(interval: FiniteDuration): LocalSingletonProxySettings = copy(singletonIdentificationInterval = interval)

  private def copy(
    singletonName:                   String         = singletonName,
    bufferSize:                      Int            = bufferSize,
    singletonIdentificationInterval: FiniteDuration = singletonIdentificationInterval
  ): LocalSingletonProxySettings =
    new LocalSingletonProxySettings(singletonName, bufferSize, singletonIdentificationInterval)
}

private[d3] object LocalSingletonProxy {
  def props(
    singletonManagerPath: String,
    settings:             LocalSingletonProxySettings
  ): Props =
    Props(new LocalSingletonProxy(singletonManagerPath, settings))

  private case object TryToIdentifySingleton
}

private[d3] final class LocalSingletonProxy(
    singletonManagerPath: String,
    settings:             LocalSingletonProxySettings
) extends Actor with ActorLogging {
  import settings._
  val singletonPath = (singletonManagerPath + "/" + settings.singletonName).split("/")
  var identifyCounter = 0
  var identifyId = createIdentifyId(identifyCounter)
  def createIdentifyId(i: Int): String = "identify-singleton-" + singletonPath.mkString("/") + i
  var identifyTimer: Option[Cancellable] = None
  var singleton: Option[ActorRef] = None

  var buffer = new java.util.LinkedList[(Any, ActorRef)]

  override def preStart(): Unit = {
    cancelTimer()
    identifySingleton()
  }

  override def postStop(): Unit = {
    cancelTimer()
  }

  def cancelTimer(): Unit = {
    identifyTimer.foreach(_.cancel())
    identifyTimer = None
  }

  def identifySingleton(): Unit = {
    import context.dispatcher
    log.debug("Creating singleton identification timer...")
    identifyCounter += 1
    identifyId = createIdentifyId(identifyCounter)
    singleton = None
    cancelTimer()
    identifyTimer = Some(context.system.scheduler.schedule(0.milliseconds, singletonIdentificationInterval, self, LocalSingletonProxy.TryToIdentifySingleton))
  }

  override def receive: Receive = {
    case ActorIdentity(identifyId, Some(s)) ⇒
      // if the new singleton is defined, deliver all buffered messages
      log.info("Singleton identified at [{}]", s.path)
      singleton = Some(s)
      context.watch(s)
      cancelTimer()
      sendBuffered()

    case _: ActorIdentity ⇒ // do nothing

    case LocalSingletonProxy.TryToIdentifySingleton if identifyTimer.isDefined ⇒
      val singletonAddress = RootActorPath(Address("akka", context.system.name)) / singletonPath
      log.debug("Trying to identify singleton at [{}]", singletonAddress)
      context.actorSelection(singletonAddress) ! Identify(identifyId)

    case Terminated(ref) ⇒
      if (singleton.contains(ref)) {
        // buffering mode, identification of new will start when old node is removed
        singleton = None
      }

    // forwarding/stashing logic
    case msg: Any ⇒
      singleton match {
        case Some(s) ⇒
          if (log.isDebugEnabled)
            log.debug(
              "Forwarding message of type [{}] to current singleton instance at [{}]: {}",
              Logging.simpleName(msg.getClass.getName), s.path
            )
          s forward msg
        case None ⇒
          buffer(msg)
      }
  }

  def buffer(msg: Any): Unit =
    if (settings.bufferSize == 0)
      log.debug("Singleton not available and buffering is disabled, dropping message [{}]", msg.getClass.getName)
    else if (buffer.size == settings.bufferSize) {
      val (m, _) = buffer.removeFirst()
      log.debug("Singleton not available, buffer is full, dropping first message [{}]", m.getClass.getName)
      buffer.addLast((msg, sender()))
    } else {
      log.debug("Singleton not available, buffering message type [{}]", msg.getClass.getName)
      buffer.addLast((msg, sender()))
    }

  def sendBuffered(): Unit = {
    log.debug("Sending buffered messages to the singleton instance")
    val target = singleton.get
    while (!buffer.isEmpty) {
      val (msg, snd) = buffer.removeFirst()
      target.tell(msg, snd)
    }
  }

}
