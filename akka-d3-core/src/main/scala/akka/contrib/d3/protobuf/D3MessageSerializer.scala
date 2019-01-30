package akka.contrib.d3.protobuf

import akka.actor._
import akka.contrib.d3._
import writeside._
import akka.protobuf.ByteString
import akka.contrib.d3.protobuf.msg.{D3Messages ⇒ pm}
import akka.contrib.d3.readside.{ReadSideActor, ReadSideCoordinator}
import akka.persistence.query.Offset
import akka.serialization._
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success}

private[d3] object D3MessageSerializer {
  final val AAGetStateManifest = "D3AAG"
  final val AAPassivateManifest = "D3AAP"
  final val AMCommandMessageManifest = "D3AMC"
  final val AMGetStateManifest = "D3AMG"
  final val AMRequestPassivationManifest = "D3AMR"
  final val ASInitializedManifest = "D3ASI"
  final val ASUninitializedManifest = "D3ASU"
  final val RAWakeUpManifest = "DRRAWU"
  final val RAEnsureActiveManifest = "D3RAEA"
  final val RAEnsureStoppedManifest = "D3RAES"
  final val RAAttemptRewindManifest = "D3RAAR"
  final val RCIsActiveManifest = "D3RCIA"
  final val RCIsStoppedManifest = "D3RCIS"
  final val RCRegisterManifest = "D3RCR"
  final val RCRewindManifest = "D3RCR"
  final val RCStartManifest = "D3RCSTART"
  final val RCStopManifest = "D3RCSTOP"
  final val RCGetStatusManifest = "D3RCGS"
  final val RSStoppedManifest = "D3RSS"
  final val RSActiveManifest = "D3RSA"
}

private[d3] class D3MessageSerializer(val system: ExtendedActorSystem)
    extends SerializerWithStringManifest with BaseSerializer {
  import D3MessageSerializer._

  private val logger = LoggerFactory.getLogger(getClass.getName)

  private lazy val serialization: Serialization = SerializationExtension(system)

  private def deserialize(bytes: Array[Byte], serializerId: Int, manifest: String): Option[AnyRef] = {
    serialization.deserialize(bytes, serializerId, manifest) match {
      case Failure(exception) ⇒
        logger.error(s"Could not deserialize bytes: $bytes, serializerId: $serializerId, manifest: $manifest.", exception)
        None
      case Success(value) ⇒
        Some(value)
    }
  }

  private val fromBinaryMap = collection.immutable.HashMap[String, Array[Byte] ⇒ AnyRef](
    AAGetStateManifest → aaGetStateFromBinary,
    AAPassivateManifest → aaPassivateFromBinary,
    AMCommandMessageManifest → amCommandMessageFromBinary,
    AMGetStateManifest → amGetStateFromBinary,
    AMRequestPassivationManifest → amRequestPassivationFromBinary,
    ASInitializedManifest → asInitializedFromBinary,
    ASUninitializedManifest → asUninitializedFromBinary,
    RAWakeUpManifest → raWakeUpFromBinary,
    RAEnsureActiveManifest → raEnsureActiveFromBinary,
    RAEnsureStoppedManifest → raEnsureStoppedFromBinary,
    RAAttemptRewindManifest → raAttemptRewindFromBinary,
    RCIsActiveManifest → rcIsActiveFromBinary,
    RCIsStoppedManifest → rcIsStoppedFromBinary,
    RCRegisterManifest → rcRegisterFromBinary,
    RCRewindManifest → rcRewindFromBinary,
    RCStartManifest → rcStartFromBinary,
    RCStopManifest → rcStopFromBinary,
    RCGetStatusManifest → rcGetStatusFromBinary,
    RSActiveManifest → rsActiveFromBinary,
    RSStoppedManifest → rsStoppedFromBinary
  )

  override def manifest(obj: AnyRef): String = obj match {
    case _: AggregateActor.GetState             ⇒ AAGetStateManifest
    case AggregateActor.Passivate               ⇒ AAPassivateManifest
    case _: AggregateManager.CommandMessage     ⇒ AMCommandMessageManifest
    case _: AggregateManager.GetState           ⇒ AMGetStateManifest
    case _: AggregateManager.RequestPassivation ⇒ AMRequestPassivationManifest
    case _: AggregateState.Initialized[_]       ⇒ ASInitializedManifest
    case _: AggregateState.Uninitialized[_]     ⇒ ASUninitializedManifest
    case _: ReadSideActor.WakeUp                ⇒ RAWakeUpManifest
    case _: ReadSideActor.EnsureActive          ⇒ RAEnsureActiveManifest
    case _: ReadSideActor.EnsureStopped         ⇒ RAEnsureStoppedManifest
    case _: ReadSideActor.AttemptRewind         ⇒ RAAttemptRewindManifest
    case _: ReadSideCoordinator.IsActive        ⇒ RCIsActiveManifest
    case _: ReadSideCoordinator.IsStopped       ⇒ RCIsStoppedManifest
    case _: ReadSideCoordinator.Register        ⇒ RCRegisterManifest
    case _: ReadSideCoordinator.Rewind          ⇒ RCRewindManifest
    case _: ReadSideCoordinator.Start           ⇒ RCStartManifest
    case _: ReadSideCoordinator.Stop            ⇒ RCStopManifest
    case _: ReadSideCoordinator.GetStatus       ⇒ RCGetStatusManifest
    case _: ReadSideStatus.Stopped              ⇒ RSStoppedManifest
    case _: ReadSideStatus.Active               ⇒ RSActiveManifest
    case _ ⇒
      throw new IllegalArgumentException(s"Can't serialize object of type ${obj.getClass} in [${getClass.getName}]")
  }

  override def toBinary(obj: AnyRef): Array[Byte] = obj match {
    case m: AggregateActor.GetState             ⇒ aaGetStateToProto(m).toByteArray
    case AggregateActor.Passivate               ⇒ aaPassivateToProto.toByteArray
    case m: AggregateManager.CommandMessage     ⇒ amCommandMessageToProto(m).toByteArray
    case m: AggregateManager.GetState           ⇒ amGetStateToProto(m).toByteArray
    case m: AggregateManager.RequestPassivation ⇒ amRequestPassivationToProto(m).toByteArray
    case m: AggregateState.Initialized[_]       ⇒ asInitializedToProto(m).toByteArray
    case m: AggregateState.Uninitialized[_]     ⇒ asUninitializedToProto(m).toByteArray
    case m: ReadSideActor.WakeUp                ⇒ raWakeUpToProto(m).toByteArray
    case m: ReadSideActor.EnsureActive          ⇒ raEnsureActiveToProto(m).toByteArray
    case m: ReadSideActor.EnsureStopped         ⇒ raEnsureStoppedToProto(m).toByteArray
    case m: ReadSideActor.AttemptRewind         ⇒ raAttemptRewindToProto(m).toByteArray
    case m: ReadSideCoordinator.IsActive        ⇒ rcIsActiveToProto(m).toByteArray
    case m: ReadSideCoordinator.IsStopped       ⇒ rcIsStoppedToProto(m).toByteArray
    case m: ReadSideCoordinator.Register        ⇒ rcRegisterToProto(m).toByteArray
    case m: ReadSideCoordinator.Rewind          ⇒ rcRewindToProto(m).toByteArray
    case m: ReadSideCoordinator.Start           ⇒ rcStartToProto(m).toByteArray
    case m: ReadSideCoordinator.Stop            ⇒ rcStopToProto(m).toByteArray
    case m: ReadSideCoordinator.GetStatus       ⇒ rcGetStatusToProto(m).toByteArray
    case m: ReadSideStatus.Stopped              ⇒ rsStoppedToProto(m).toByteArray
    case m: ReadSideStatus.Active               ⇒ rsActiveToProto(m).toByteArray
    case _ ⇒
      throw new IllegalArgumentException(s"Can't serialize object of type ${obj.getClass} in [${getClass.getName}]")
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef =
    fromBinaryMap.get(manifest) match {
      case Some(f) ⇒ f(bytes)
      case None ⇒ throw new IllegalArgumentException(
        s"Unimplemented deserialization of message with manifest [$manifest] in [${getClass.getName}]"
      )
    }

  // AggregateActor$GetState

  private def aaGetStateToProto(getState: AggregateActor.GetState): pm.AAGetState = {
    val actorRefMessage = pm.ActorRef.newBuilder()
      .setPath(Serialization.serializedActorPath(getState.requester)).build()

    val builder = pm.AAGetState.newBuilder()
      .setRequester(actorRefMessage)

    builder.build()
  }

  private def aaGetStateFromBinary(bytes: Array[Byte]): AggregateActor.GetState =
    aaGetStateFromProto(pm.AAGetState.parseFrom(bytes))

  private def aaGetStateFromProto(getState: pm.AAGetState): AggregateActor.GetState = {
    AggregateActor.GetState(system.provider.resolveActorRef(getState.getRequester.getPath))
  }

  // AggregateActor$Passivate

  private def aaPassivateToProto: pm.AAPassivate = {
    pm.AAPassivate.newBuilder().build()
  }

  private def aaPassivateFromBinary(bytes: Array[Byte]): AggregateActor.Passivate.type =
    AggregateActor.Passivate

  // AggregateManager$CommandMessage

  private def amCommandMessageToProto(commandMessage: AggregateManager.CommandMessage): pm.AMCommandMessage = {
    val id = commandMessage.id.asInstanceOf[AnyRef]
    val idSerializer = serialization.findSerializerFor(id)
    val command = commandMessage.command.asInstanceOf[AnyRef]
    val commandSerializer = serialization.findSerializerFor(command)
    val builder = pm.AMCommandMessage.newBuilder()
      .setId(ByteString.copyFrom(idSerializer.toBinary(id)))
      .setIdSerializerId(idSerializer.identifier)
      .setCommand(ByteString.copyFrom(commandSerializer.toBinary(command)))
      .setCommandSerializerId(commandSerializer.identifier)

    idSerializer match {
      case ser2: SerializerWithStringManifest ⇒
        val manifest = ser2.manifest(id)
        if (manifest != "")
          builder.setIdManifest(ByteString.copyFromUtf8(manifest))
      case _ ⇒
        if (idSerializer.includeManifest)
          builder.setIdManifest(ByteString.copyFromUtf8(id.getClass.getName))
    }

    commandSerializer match {
      case ser2: SerializerWithStringManifest ⇒
        val manifest = ser2.manifest(command)
        if (manifest != "")
          builder.setCommandManifest(ByteString.copyFromUtf8(manifest))
      case _ ⇒
        if (commandSerializer.includeManifest)
          builder.setCommandManifest(ByteString.copyFromUtf8(command.getClass.getName))
    }

    builder.build()
  }

  private def amCommandMessageFromBinary(bytes: Array[Byte]): AggregateManager.CommandMessage =
    amCommandMessageFromProto(pm.AMCommandMessage.parseFrom(bytes))

  private def amCommandMessageFromProto(commandMessage: pm.AMCommandMessage): AggregateManager.CommandMessage = {
    val idManifest = if (commandMessage.hasIdManifest) commandMessage.getIdManifest.toStringUtf8 else ""
    val commandManifest = if (commandMessage.hasCommandManifest) commandMessage.getCommandManifest.toStringUtf8 else ""
    val id = deserialize(
      commandMessage.getId.toByteArray,
      commandMessage.getIdSerializerId,
      idManifest
    ).get
    val command = deserialize(
      commandMessage.getCommand.toByteArray,
      commandMessage.getCommandSerializerId,
      commandManifest
    ).get
    AggregateManager.CommandMessage(id.asInstanceOf[AggregateId], command.asInstanceOf[AggregateCommand])
  }

  // AggregateManager$GetState

  private def amGetStateToProto(commandMessage: AggregateManager.GetState): pm.AMGetState = {
    val id = commandMessage.id.asInstanceOf[AnyRef]
    val idSerializer = serialization.findSerializerFor(id)
    val builder = pm.AMGetState.newBuilder()
      .setId(ByteString.copyFrom(idSerializer.toBinary(id)))
      .setIdSerializerId(idSerializer.identifier)

    idSerializer match {
      case ser2: SerializerWithStringManifest ⇒
        val manifest = ser2.manifest(id)
        if (manifest != "")
          builder.setIdManifest(ByteString.copyFromUtf8(manifest))
      case _ ⇒
        if (idSerializer.includeManifest)
          builder.setIdManifest(ByteString.copyFromUtf8(id.getClass.getName))
    }

    builder.build()
  }

  private def amGetStateFromBinary(bytes: Array[Byte]): AggregateManager.GetState =
    amGetStateFromProto(pm.AMGetState.parseFrom(bytes))

  private def amGetStateFromProto(getState: pm.AMGetState): AggregateManager.GetState = {
    val idManifest = if (getState.hasIdManifest) getState.getIdManifest.toStringUtf8 else ""
    val id = deserialize(
      getState.getId.toByteArray,
      getState.getIdSerializerId,
      idManifest
    ).get
    AggregateManager.GetState(id.asInstanceOf[AggregateId])
  }

  // AggregateState$RequestPassivation

  private def amRequestPassivationToProto(requestPassivation: AggregateManager.RequestPassivation): pm.AMRequestPassivation = {
    val msg = requestPassivation.stopMessage.asInstanceOf[AnyRef]
    val msgSerializer = serialization.findSerializerFor(msg)
    val builder = pm.AMRequestPassivation.newBuilder()
      .setMsg(ByteString.copyFrom(msgSerializer.toBinary(msg)))
      .setMsgSerializerId(msgSerializer.identifier)

    msgSerializer match {
      case ser2: SerializerWithStringManifest ⇒
        val manifest = ser2.manifest(msg)
        if (manifest != "")
          builder.setMsgManifest(ByteString.copyFromUtf8(manifest))
      case _ ⇒
        if (msgSerializer.includeManifest)
          builder.setMsgManifest(ByteString.copyFromUtf8(msg.getClass.getName))
    }

    builder.build()
  }

  private def amRequestPassivationFromBinary(bytes: Array[Byte]): AggregateManager.RequestPassivation =
    amRequestPassivationFromProto(pm.AMRequestPassivation.parseFrom(bytes))

  private def amRequestPassivationFromProto(getState: pm.AMRequestPassivation): AggregateManager.RequestPassivation = {
    val msgManifest = if (getState.hasMsgManifest) getState.getMsgManifest.toStringUtf8 else ""
    val msg = deserialize(
      getState.getMsg.toByteArray,
      getState.getMsgSerializerId,
      msgManifest
    ).get
    AggregateManager.RequestPassivation(msg)
  }

  // AggregateState$Initialized

  private def asInitializedToProto(initialized: AggregateState.Initialized[_]): pm.ASInitialized = {
    val state = initialized.aggregate.asInstanceOf[AnyRef]
    val stateSerializer = serialization.findSerializerFor(state)
    val builder = pm.ASInitialized.newBuilder()
      .setState(ByteString.copyFrom(stateSerializer.toBinary(state)))
      .setStateSerializerId(stateSerializer.identifier)

    stateSerializer match {
      case ser2: SerializerWithStringManifest ⇒
        val manifest = ser2.manifest(state)
        if (manifest != "")
          builder.setStateManifest(ByteString.copyFromUtf8(manifest))
      case _ ⇒
        if (stateSerializer.includeManifest)
          builder.setStateManifest(ByteString.copyFromUtf8(state.getClass.getName))
    }

    builder.build()
  }

  private def asInitializedFromBinary(bytes: Array[Byte]): AggregateState.Initialized[_] =
    asInitializedFromProto(pm.ASInitialized.parseFrom(bytes))

  private def asInitializedFromProto(initialized: pm.ASInitialized): AggregateState.Initialized[_] = {
    val stateManifest = if (initialized.hasStateManifest) initialized.getStateManifest.toStringUtf8 else ""
    val state = deserialize(
      initialized.getState.toByteArray,
      initialized.getStateSerializerId,
      stateManifest
    ).get
    AggregateState.Initialized(state.asInstanceOf[AggregateLike])
  }

  // AggregateState#Uninitialized

  private def asUninitializedToProto(uninitialized: AggregateState.Uninitialized[_]): pm.ASUninitialized = {
    val id = uninitialized.aggregateId.asInstanceOf[AnyRef]
    val idSerializer = serialization.findSerializerFor(id)
    val builder = pm.ASUninitialized.newBuilder()
      .setId(ByteString.copyFrom(idSerializer.toBinary(id)))
      .setIdSerializerId(idSerializer.identifier)

    idSerializer match {
      case ser2: SerializerWithStringManifest ⇒
        val manifest = ser2.manifest(id)
        if (manifest != "")
          builder.setIdManifest(ByteString.copyFromUtf8(manifest))
      case _ ⇒
        if (idSerializer.includeManifest)
          builder.setIdManifest(ByteString.copyFromUtf8(id.getClass.getName))
    }

    builder.build()
  }

  private def asUninitializedFromBinary(bytes: Array[Byte]): AggregateState.Uninitialized[_] =
    asUninitializedFromProto(pm.ASUninitialized.parseFrom(bytes))

  private def asUninitializedFromProto(uninitialized: pm.ASUninitialized): AggregateState.Uninitialized[_] = {
    val idManifest = if (uninitialized.hasIdManifest) uninitialized.getIdManifest.toStringUtf8 else ""
    val id = deserialize(
      uninitialized.getId.toByteArray,
      uninitialized.getIdSerializerId,
      idManifest
    ).get
    AggregateState.Uninitialized(id.asInstanceOf[AggregateLike#Id])
  }

  // ReadSideActor#WakeUp
  private def raWakeUpToProto(wakeUp: ReadSideActor.WakeUp): pm.RAWakeUp = {
    pm.RAWakeUp.newBuilder()
      .setName(wakeUp.name)
      .build()
  }

  private def raWakeUpFromBinary(bytes: Array[Byte]): ReadSideActor.WakeUp =
    raWakeUpFromProto(pm.RAWakeUp.parseFrom(bytes))

  private def raWakeUpFromProto(wakeUp: pm.RAWakeUp): ReadSideActor.WakeUp = {
    ReadSideActor.WakeUp(wakeUp.getName)
  }

  // ReadSideActor#EnsureActive

  private def raEnsureActiveToProto(ensureActive: ReadSideActor.EnsureActive): pm.RAEnsureActive = {
    pm.RAEnsureActive.newBuilder()
      .setName(ensureActive.name)
      .build()
  }

  private def raEnsureActiveFromBinary(bytes: Array[Byte]): ReadSideActor.EnsureActive =
    raEnsureActiveFromProto(pm.RAEnsureActive.parseFrom(bytes))

  private def raEnsureActiveFromProto(ensureActive: pm.RAEnsureActive): ReadSideActor.EnsureActive = {
    ReadSideActor.EnsureActive(ensureActive.getName)
  }

  // ReadSideActor#EnsureStopped

  private def raEnsureStoppedToProto(ensureStopped: ReadSideActor.EnsureStopped): pm.RAEnsureStopped = {
    pm.RAEnsureStopped.newBuilder()
      .setName(ensureStopped.name)
      .build()
  }

  private def raEnsureStoppedFromBinary(bytes: Array[Byte]): ReadSideActor.EnsureStopped =
    raEnsureStoppedFromProto(pm.RAEnsureStopped.parseFrom(bytes))

  private def raEnsureStoppedFromProto(ensureStopped: pm.RAEnsureStopped): ReadSideActor.EnsureStopped = {
    ReadSideActor.EnsureStopped(ensureStopped.getName)
  }

  // ReadSideActor#AttemptRewind

  private def raAttemptRewindToProto(attemptRewind: ReadSideActor.AttemptRewind): pm.RAAttemptRewind = {
    val offset = attemptRewind.offset.asInstanceOf[AnyRef]
    val offsetSerializer = serialization.findSerializerFor(offset)
    val builder = pm.RAAttemptRewind.newBuilder()
      .setName(attemptRewind.name)
      .setOffset(ByteString.copyFrom(offsetSerializer.toBinary(offset)))
      .setOffsetSerializerId(offsetSerializer.identifier)

    offsetSerializer match {
      case ser2: SerializerWithStringManifest ⇒
        val manifest = ser2.manifest(offset)
        if (manifest != "")
          builder.setOffsetManifest(ByteString.copyFromUtf8(manifest))
      case _ ⇒
        if (offsetSerializer.includeManifest)
          builder.setOffsetManifest(ByteString.copyFromUtf8(offset.getClass.getName))
    }

    builder.build()
  }

  private def raAttemptRewindFromBinary(bytes: Array[Byte]): ReadSideActor.AttemptRewind =
    raAttemptRewindFromProto(pm.RAAttemptRewind.parseFrom(bytes))

  private def raAttemptRewindFromProto(attemptRewind: pm.RAAttemptRewind): ReadSideActor.AttemptRewind = {
    val offsetManifest = if (attemptRewind.hasOffsetManifest) attemptRewind.getOffsetManifest.toStringUtf8 else ""
    val offset = deserialize(
      attemptRewind.getOffset.toByteArray,
      attemptRewind.getOffsetSerializerId,
      offsetManifest
    ).get
    ReadSideActor.AttemptRewind(attemptRewind.getName, offset.asInstanceOf[Offset])
  }

  // ReadSideCoordinator#IsActive

  private def rcIsActiveToProto(isActive: ReadSideCoordinator.IsActive): pm.RCIsActive = {
    pm.RCIsActive.newBuilder()
      .setName(isActive.name)
      .build()
  }

  private def rcIsActiveFromBinary(bytes: Array[Byte]): ReadSideCoordinator.IsActive =
    rcIsActiveFromProto(pm.RCIsActive.parseFrom(bytes))

  private def rcIsActiveFromProto(isActive: pm.RCIsActive): ReadSideCoordinator.IsActive = {
    ReadSideCoordinator.IsActive(isActive.getName)
  }

  // ReadSideCoordinator#IsStopped

  private def rcIsStoppedToProto(isStopped: ReadSideCoordinator.IsStopped): pm.RCIsStopped = {
    pm.RCIsStopped.newBuilder()
      .setName(isStopped.name)
      .build()
  }

  private def rcIsStoppedFromBinary(bytes: Array[Byte]): ReadSideCoordinator.IsStopped =
    rcIsStoppedFromProto(pm.RCIsStopped.parseFrom(bytes))

  private def rcIsStoppedFromProto(isStopped: pm.RCIsStopped): ReadSideCoordinator.IsStopped = {
    ReadSideCoordinator.IsStopped(isStopped.getName)
  }

  // ReadSideCoordinator#Register

  private def rcRegisterToProto(register: ReadSideCoordinator.Register): pm.RCRegister = {
    val actorRefMessage = pm.ActorRef.newBuilder()
      .setPath(Serialization.serializedActorPath(register.actorRef)).build()

    val builder = pm.RCRegister.newBuilder()
      .setName(register.name)
      .setActorRef(actorRefMessage)

    builder.build()
  }

  private def rcRegisterFromBinary(bytes: Array[Byte]): ReadSideCoordinator.Register =
    rcRegisterFromProto(pm.RCRegister.parseFrom(bytes))

  private def rcRegisterFromProto(register: pm.RCRegister): ReadSideCoordinator.Register = {
    ReadSideCoordinator.Register(register.getName, system.provider.resolveActorRef(register.getActorRef.getPath))
  }

  // ReadSideCoordinator#Rewind

  private def rcRewindToProto(rewind: ReadSideCoordinator.Rewind): pm.RCRewind = {
    val offset = rewind.offset.asInstanceOf[AnyRef]
    val offsetSerializer = serialization.findSerializerFor(offset)
    val builder = pm.RCRewind.newBuilder()
      .setName(rewind.name)
      .setOffset(ByteString.copyFrom(offsetSerializer.toBinary(offset)))
      .setOffsetSerializerId(offsetSerializer.identifier)

    offsetSerializer match {
      case ser2: SerializerWithStringManifest ⇒
        val manifest = ser2.manifest(offset)
        if (manifest != "")
          builder.setOffsetManifest(ByteString.copyFromUtf8(manifest))
      case _ ⇒
        if (offsetSerializer.includeManifest)
          builder.setOffsetManifest(ByteString.copyFromUtf8(offset.getClass.getName))
    }

    builder.build()
  }

  private def rcRewindFromBinary(bytes: Array[Byte]): ReadSideCoordinator.Rewind =
    rcRewindFromProto(pm.RCRewind.parseFrom(bytes))

  private def rcRewindFromProto(rewind: pm.RCRewind): ReadSideCoordinator.Rewind = {
    val offsetManifest = if (rewind.hasOffsetManifest) rewind.getOffsetManifest.toStringUtf8 else ""
    val offset = deserialize(
      rewind.getOffset.toByteArray,
      rewind.getOffsetSerializerId,
      offsetManifest
    ).get
    ReadSideCoordinator.Rewind(rewind.getName, offset.asInstanceOf[Offset])
  }

  // ReadSideCoordinator#Start

  private def rcStartToProto(start: ReadSideCoordinator.Start): pm.RCStart = {
    pm.RCStart.newBuilder()
      .setName(start.name)
      .build()
  }

  private def rcStartFromBinary(bytes: Array[Byte]): ReadSideCoordinator.Start =
    rcStartFromProto(pm.RCStart.parseFrom(bytes))

  private def rcStartFromProto(start: pm.RCStart): ReadSideCoordinator.Start = {
    ReadSideCoordinator.Start(start.getName)
  }

  // ReadSideCoordinator#Stop

  private def rcStopToProto(stop: ReadSideCoordinator.Stop): pm.RCStop = {
    pm.RCStop.newBuilder()
      .setName(stop.name)
      .build()
  }

  private def rcStopFromBinary(bytes: Array[Byte]): ReadSideCoordinator.Stop =
    rcStopFromProto(pm.RCStop.parseFrom(bytes))

  private def rcStopFromProto(stop: pm.RCStop): ReadSideCoordinator.Stop = {
    ReadSideCoordinator.Stop(stop.getName)
  }

  // ReadSideCoordinator#GetStatus

  private def rcGetStatusToProto(getStatus: ReadSideCoordinator.GetStatus): pm.RCGetStatus = {
    pm.RCGetStatus.newBuilder()
      .setName(getStatus.name)
      .build()
  }

  private def rcGetStatusFromBinary(bytes: Array[Byte]): ReadSideCoordinator.GetStatus =
    rcGetStatusFromProto(pm.RCGetStatus.parseFrom(bytes))

  private def rcGetStatusFromProto(getStatus: pm.RCGetStatus): ReadSideCoordinator.GetStatus = {
    ReadSideCoordinator.GetStatus(getStatus.getName)
  }

  // ReadSideStatus#Stopped

  private def rsStoppedToProto(stopped: ReadSideStatus.Stopped): pm.RSStopped = {
    pm.RSStopped.newBuilder()
      .setName(stopped.name)
      .build()
  }

  private def rsStoppedFromBinary(bytes: Array[Byte]): ReadSideStatus.Stopped =
    rsStoppedFromProto(pm.RSStopped.parseFrom(bytes))

  private def rsStoppedFromProto(stopped: pm.RSStopped): ReadSideStatus.Stopped = {
    ReadSideStatus.Stopped(stopped.getName)
  }

  // ReadSideStatus#Active

  private def rsActiveToProto(active: ReadSideStatus.Active): pm.RSActive = {
    val offset = active.offset.asInstanceOf[AnyRef]
    val offsetSerializer = serialization.findSerializerFor(offset)
    val builder = pm.RSActive.newBuilder()
      .setName(active.name)
      .setOffset(ByteString.copyFrom(offsetSerializer.toBinary(offset)))
      .setOffsetSerializerId(offsetSerializer.identifier)

    offsetSerializer match {
      case ser2: SerializerWithStringManifest ⇒
        val manifest = ser2.manifest(offset)
        if (manifest != "")
          builder.setOffsetManifest(ByteString.copyFromUtf8(manifest))
      case _ ⇒
        if (offsetSerializer.includeManifest)
          builder.setOffsetManifest(ByteString.copyFromUtf8(offset.getClass.getName))
    }

    builder.build()
  }

  private def rsActiveFromBinary(bytes: Array[Byte]): ReadSideStatus.Active =
    rsActiveFromProto(pm.RSActive.parseFrom(bytes))

  private def rsActiveFromProto(active: pm.RSActive): ReadSideStatus.Active = {
    val offsetManifest = if (active.hasOffsetManifest) active.getOffsetManifest.toStringUtf8 else ""
    val offset = deserialize(
      active.getOffset.toByteArray,
      active.getOffsetSerializerId,
      offsetManifest
    ).get
    ReadSideStatus.Active(active.getName, offset.asInstanceOf[Offset])
  }

}
