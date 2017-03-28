package akka.contrib.d3.protobuf

import akka.actor._
import akka.contrib.d3._
import writeside._
import akka.protobuf.ByteString
import akka.contrib.d3.protobuf.msg.{D3Messages ⇒ pm}
import akka.serialization._

private[d3] object D3MessageSerializer {
  final val AAGetStateManifest = "D3AAG"
  final val AAPassivateManifest = "D3AAP"
  final val AMCommandMessageManifest = "D3AMC"
  final val AMGetStateManifest = "D3AMG"
  final val AMRequestPassivationManifest = "D3AMR"
  final val ASInitializedManifest = "D3ASI"
  final val ASUninitializedManifest = "D3ASU"
}

private[d3] class D3MessageSerializer(val system: ExtendedActorSystem)
    extends SerializerWithStringManifest with BaseSerializer {
  import D3MessageSerializer._

  @volatile
  private var ser: Serialization = _
  def serialization: Serialization = {
    //scalastyle:off
    if (ser == null) ser = SerializationExtension(system)
    //scalastyle:on
    ser
  }

  private val emptyByteArray = Array.empty[Byte]

  private val fromBinaryMap = collection.immutable.HashMap[String, Array[Byte] ⇒ AnyRef](
    AAGetStateManifest → aaGetStateFromBinary,
    AAPassivateManifest → aaPassivateFromBinary,
    AMCommandMessageManifest → amCommandMessageFromBinary,
    AMGetStateManifest → amGetStateFromBinary,
    AMRequestPassivationManifest → amRequestPassivationFromBinary,
    ASInitializedManifest → asInitializedFromBinary,
    ASUninitializedManifest → asUninitializedFromBinary
  )

  override def manifest(obj: AnyRef): String = obj match {
    case _: AggregateActor.GetState             ⇒ AAGetStateManifest
    case AggregateActor.Passivate               ⇒ AAPassivateManifest
    case _: AggregateManager.CommandMessage     ⇒ AMCommandMessageManifest
    case _: AggregateManager.GetState           ⇒ AMGetStateManifest
    case _: AggregateManager.RequestPassivation ⇒ AMRequestPassivationManifest
    case _: AggregateState.Initialized[_]       ⇒ ASInitializedManifest
    case _: AggregateState.Uninitialized[_]     ⇒ ASUninitializedManifest
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
    val id = ser.deserialize(
      commandMessage.getId.toByteArray,
      commandMessage.getIdSerializerId,
      idManifest
    ).get
    val command = serialization.deserialize(
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
    val id = ser.deserialize(
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
    val msg = ser.deserialize(
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
    val state = ser.deserialize(
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
    val id = ser.deserialize(
      uninitialized.getId.toByteArray,
      uninitialized.getIdSerializerId,
      idManifest
    ).get
    AggregateState.Uninitialized(id.asInstanceOf[AggregateLike#Id])
  }

}
