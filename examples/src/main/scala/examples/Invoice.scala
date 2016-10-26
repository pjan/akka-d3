package examples

import java.time.OffsetDateTime

import akka.contrib.d3._

import scala.concurrent.{ExecutionContext, Future}

object InvoiceProtocol
    extends ProtocolLike {

  sealed trait InvoiceCommand extends ProtocolCommand with CommandIdCarrier
  object InvoiceCommand {

    case class Create(
      amount: Invoice.Amount
    ) extends InvoiceCommand

    case class Close(
      reason: String
    ) extends InvoiceCommand

  }

  sealed trait InvoiceEvent extends ProtocolEvent with EventIdCarrier with EventMetadataCarrier[InvoiceEvent.Metadata]
  object InvoiceEvent {

    case class Metadata(
        commandId:   CommandId,
        aggregateId: Invoice.Id,
        timestamp:   OffsetDateTime,
        tags:        Set[Tag]
    ) extends EventMetadata {
      type Id = Invoice.Id
    }

    case class Created(
      amount:   Invoice.Amount,
      metadata: InvoiceEvent.Metadata
    ) extends InvoiceEvent

    case class Closed(
      reason:   String,
      metadata: InvoiceEvent.Metadata
    ) extends InvoiceEvent

  }

}

case class Invoice(
    id:       Invoice.Id,
    amount:   Invoice.Amount,
    status:   Invoice.Status,
    metadata: Invoice.Metadata
) extends AggregateLike with AggregateMetadataCarrier[Invoice.Metadata] {
  type Id = Invoice.Id
  type Protocol = InvoiceProtocol.type
}

object Invoice {
  case class Id(value: String) extends AggregateId
  case class Amount(value: BigDecimal) extends AnyVal

  case class Metadata(
    versionNr: Int,
    updatedAt: OffsetDateTime,
    createdAt: OffsetDateTime
  ) extends AggregateMetadata

  sealed trait Status
  case object Status {
    case object Open extends Status
    case object Closed extends Status
  }
}

object InvoiceBehavior extends Behavior[Invoice] {
  import AggregateState._

  override def onCommand(state: AggregateState[Invoice], cmd: InvoiceBehavior.Command)(implicit ec: ExecutionContext): Future[Either[Throwable, InvoiceBehavior.Events]] = {
    import InvoiceProtocol._
    import InvoiceCommand._
    import InvoiceEvent._
    (state, cmd) match {
      case (s: Uninitialized[Invoice], c: Create) ⇒
        Future.successful(Right(List(Created(c.amount, InvoiceEvent.Metadata(c.id, state.aggregateId, OffsetDateTime.now, Set.empty)))))
      case (s: Uninitialized[Invoice], c: Close) ⇒
        Future.successful(Left(new Exception("illegal")))
      case (s: Initialized[Invoice], c: Create) ⇒
        Future.successful(Left(new Exception("illegal")))
      case (s: Initialized[Invoice], c: Close) ⇒
        Future.successful(Right(List(Closed(c.reason, InvoiceEvent.Metadata(c.id, state.aggregateId, OffsetDateTime.now, Set.empty)))))
      case (_, _) ⇒
        Future.successful(Left(new Exception("illegal")))
    }
  }

  override def onEvent(state: AggregateState[Invoice], evt: InvoiceBehavior.Event): Either[Throwable, AggregateState[Invoice]] = {
    import InvoiceProtocol.InvoiceEvent._
    (state, evt) match {
      case (s: Uninitialized[Invoice], e: Created) ⇒
        Right(Initialized(Invoice(state.aggregateId, e.amount, Invoice.Status.Open, Invoice.Metadata(1, e.timestamp, e.timestamp))))
      case (s: Uninitialized[Invoice], e: Closed) ⇒
        Left(new Exception("illegal"))
      case (s: Initialized[Invoice], e: Created) ⇒
        Left(new Exception("illegal"))
      case (s @ Initialized(invoice), e: Closed) ⇒
        Right(s.copy(invoice.copy(status = Invoice.Status.Closed, metadata = invoice.metadata.copy(versionNr = invoice.metadata.versionNr + 1, updatedAt = e.timestamp))))
      case (_, _) ⇒
        Left(new Exception("illegal"))
    }
  }
}
