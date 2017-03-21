package akka.contrib.d3.utils

import akka.actor._

final case class LocalSingletonManagerSettings(
  val singletonName: String
)

object LocalSingletonManager {

  def props(
    singletonProps: Props,
    settings:       LocalSingletonManagerSettings
  ): Props =
    Props(new LocalSingletonManager(singletonProps, settings))

  case object StartSingleton

}

class LocalSingletonManager(
    singletonProps: Props,
    settings:       LocalSingletonManagerSettings
) extends Actor {
  import LocalSingletonManager._

  override def preStart(): Unit = {
    super.preStart()
    self ! StartSingleton
  }

  override def receive: Receive = start

  private def start: Receive = {
    case StartSingleton ⇒
      val singleton = startSingleton()
      context become active(singleton)
  }

  private def active(singleton: ActorRef): Receive = {
    case Terminated(ref) if ref == singleton ⇒
      val singleton = startSingleton()
      context become active(singleton)
  }

  private def startSingleton(): ActorRef =
    context.actorOf(singletonProps, settings.singletonName)
}
