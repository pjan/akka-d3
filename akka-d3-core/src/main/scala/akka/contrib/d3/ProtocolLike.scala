package akka.contrib.d3

import scala.collection.immutable

trait ProtocolLike {
  trait ProtocolCommand extends DomainCommand
  trait ProtocolEvent extends DomainEvent
}

trait ProtocolAliases {
  type Protocol <: ProtocolLike
  type Command = Protocol#ProtocolCommand
  type Event = Protocol#ProtocolEvent
  type Events = immutable.Seq[Event]
}
