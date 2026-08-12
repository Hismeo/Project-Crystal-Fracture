package org.hismeo.actionguide.internal.network;

import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.PacketDistributor;
import org.hismeo.actionguide.ActionGuide;
import org.hismeo.actionguide.ActionNetwork;
import org.hismeo.actionguide.api.action.ActionId;
import org.hismeo.actionguide.api.action.ActionIntentId;
import org.hismeo.actionguide.api.action.ActionIntentRequest;
import org.hismeo.actionguide.api.action.IntentPhase;
import org.hismeo.actionguide.api.cue.CombatCueId;
import org.hismeo.actionguide.api.cue.CueEvent;
import org.hismeo.actionguide.api.cue.CueItemId;
import org.hismeo.actionguide.api.cue.CueTime;
import org.hismeo.actionguide.api.cue.SectionId;
import org.hismeo.actionguide.api.network.ActionSnapshot;
import org.hismeo.actionguide.api.runtime.ActionInstanceId;
import org.hismeo.actionguide.api.runtime.ActionStopReason;

import java.util.Optional;

/** Wire-only payloads. Consumers receive the neutral API messages instead of these transport records. */
public final class ActionNetworkPayloads {
    private static final Gson GSON = new Gson();

    private ActionNetworkPayloads() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(ActionIntentC2S.TYPE, ActionIntentC2S.CODEC, ActionIntentC2S::handle);
        registrar.playToClient(ActionStartedS2C.TYPE, ActionStartedS2C.CODEC, ActionStartedS2C::handle);
        registrar.playToClient(ActionTransitionedS2C.TYPE, ActionTransitionedS2C.CODEC, ActionTransitionedS2C::handle);
        registrar.playToClient(ActionStoppedS2C.TYPE, ActionStoppedS2C.CODEC, ActionStoppedS2C::handle);
        registrar.playToClient(ActionRejectedS2C.TYPE, ActionRejectedS2C.CODEC, ActionRejectedS2C::handle);
        registrar.playToClient(ActionSnapshotS2C.TYPE, ActionSnapshotS2C.CODEC, ActionSnapshotS2C::handle);
        registrar.playToClient(ActionPresentationEventS2C.TYPE, ActionPresentationEventS2C.CODEC,
                ActionPresentationEventS2C::handle);
    }

    public static void sendIntent(ActionIntentRequest request) {
        PacketDistributor.sendToServer(new ActionIntentC2S(request));
    }

    public record ActionIntentC2S(ActionIntentRequest request) implements CustomPacketPayload {
        public static final Type<ActionIntentC2S> TYPE = payloadType("intent");
        public static final StreamCodec<ByteBuf, ActionIntentC2S> CODEC = StreamCodec.ofMember(
                ActionIntentC2S::encode, ActionIntentC2S::decode);

        private void encode(ByteBuf raw) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(raw);
            buffer.writeResourceLocation(request.intent().value());
            buffer.writeEnum(request.phase());
            buffer.writeVarLong(request.sequence());
            buffer.writeVarLong(request.clientTick());
        }

        private static ActionIntentC2S decode(ByteBuf raw) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(raw);
            return new ActionIntentC2S(new ActionIntentRequest(new ActionIntentId(buffer.readResourceLocation()),
                    buffer.readEnum(IntentPhase.class), buffer.readVarLong(), buffer.readVarLong()));
        }

        private void handle(IPayloadContext context) {
            enqueue(context, () -> ActionNetwork.handleIntent(context, this));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ActionStartedS2C(ActionSnapshot snapshot) implements CustomPacketPayload {
        public static final Type<ActionStartedS2C> TYPE = payloadType("started");
        public static final StreamCodec<ByteBuf, ActionStartedS2C> CODEC = StreamCodec.ofMember(
                ActionStartedS2C::encode, ActionStartedS2C::decode);

        private void encode(ByteBuf buffer) {
            writeSnapshot(buffer, snapshot);
        }

        private static ActionStartedS2C decode(ByteBuf buffer) {
            return new ActionStartedS2C(readSnapshot(buffer));
        }

        private void handle(IPayloadContext context) {
            enqueue(context, () -> ActionNetwork.receiveStarted(snapshot));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ActionTransitionedS2C(ActionSnapshot snapshot) implements CustomPacketPayload {
        public static final Type<ActionTransitionedS2C> TYPE = payloadType("transitioned");
        public static final StreamCodec<ByteBuf, ActionTransitionedS2C> CODEC = StreamCodec.ofMember(
                ActionTransitionedS2C::encode, ActionTransitionedS2C::decode);

        private void encode(ByteBuf buffer) {
            writeSnapshot(buffer, snapshot);
        }

        private static ActionTransitionedS2C decode(ByteBuf buffer) {
            return new ActionTransitionedS2C(readSnapshot(buffer));
        }

        private void handle(IPayloadContext context) {
            enqueue(context, () -> ActionNetwork.receiveTransitioned(snapshot));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ActionStoppedS2C(ActionSnapshot snapshot) implements CustomPacketPayload {
        public static final Type<ActionStoppedS2C> TYPE = payloadType("stopped");
        public static final StreamCodec<ByteBuf, ActionStoppedS2C> CODEC = StreamCodec.ofMember(
                ActionStoppedS2C::encode, ActionStoppedS2C::decode);

        private void encode(ByteBuf buffer) {
            writeSnapshot(buffer, snapshot);
        }

        private static ActionStoppedS2C decode(ByteBuf buffer) {
            return new ActionStoppedS2C(readSnapshot(buffer));
        }

        private void handle(IPayloadContext context) {
            enqueue(context, () -> ActionNetwork.receiveStopped(snapshot));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ActionRejectedS2C(long requestSequence, ResourceLocation reason,
                                    Optional<ActionSnapshot> authority) implements CustomPacketPayload {
        public static final Type<ActionRejectedS2C> TYPE = payloadType("rejected");
        public static final StreamCodec<ByteBuf, ActionRejectedS2C> CODEC = StreamCodec.ofMember(
                ActionRejectedS2C::encode, ActionRejectedS2C::decode);

        public ActionRejectedS2C {
            authority = authority == null ? Optional.empty() : authority;
        }

        private void encode(ByteBuf raw) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(raw);
            buffer.writeVarLong(requestSequence);
            buffer.writeResourceLocation(reason);
            buffer.writeBoolean(authority.isPresent());
            authority.ifPresent(snapshot -> writeSnapshot(raw, snapshot));
        }

        private static ActionRejectedS2C decode(ByteBuf raw) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(raw);
            long requestSequence = buffer.readVarLong();
            ResourceLocation reason = buffer.readResourceLocation();
            Optional<ActionSnapshot> authority = buffer.readBoolean() ? Optional.of(readSnapshot(raw)) : Optional.empty();
            return new ActionRejectedS2C(requestSequence, reason, authority);
        }

        private void handle(IPayloadContext context) {
            enqueue(context, () -> ActionNetwork.receiveRejected(requestSequence, reason, authority));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ActionSnapshotS2C(ActionSnapshot snapshot) implements CustomPacketPayload {
        public static final Type<ActionSnapshotS2C> TYPE = payloadType("snapshot");
        public static final StreamCodec<ByteBuf, ActionSnapshotS2C> CODEC = StreamCodec.ofMember(
                ActionSnapshotS2C::encode, ActionSnapshotS2C::decode);

        private void encode(ByteBuf buffer) {
            writeSnapshot(buffer, snapshot);
        }

        private static ActionSnapshotS2C decode(ByteBuf buffer) {
            return new ActionSnapshotS2C(readSnapshot(buffer));
        }

        private void handle(IPayloadContext context) {
            enqueue(context, () -> ActionNetwork.receiveSnapshot(snapshot));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ActionPresentationEventS2C(int actorEntityId, ActionInstanceId instanceId, long loopIteration,
                                              CueEvent event) implements CustomPacketPayload {
        public static final Type<ActionPresentationEventS2C> TYPE = payloadType("presentation_event");
        public static final StreamCodec<ByteBuf, ActionPresentationEventS2C> CODEC = StreamCodec.ofMember(
                ActionPresentationEventS2C::encode, ActionPresentationEventS2C::decode);

        private void encode(ByteBuf raw) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(raw);
            buffer.writeVarInt(actorEntityId);
            buffer.writeVarLong(instanceId.value());
            buffer.writeVarLong(loopIteration);
            buffer.writeUtf(event.id().value(), 128);
            buffer.writeVarLong(event.time().micros());
            buffer.writeResourceLocation(event.type());
            buffer.writeVarInt(event.order());
            buffer.writeUtf(GSON.toJson(event.payload()), 32_767);
        }

        private static ActionPresentationEventS2C decode(ByteBuf raw) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(raw);
            int actorEntityId = buffer.readVarInt();
            ActionInstanceId instanceId = new ActionInstanceId(buffer.readVarLong());
            long loopIteration = buffer.readVarLong();
            CueItemId id = new CueItemId(buffer.readUtf(128));
            CueTime time = new CueTime(buffer.readVarLong());
            ResourceLocation type = buffer.readResourceLocation();
            int order = buffer.readVarInt();
            CueEvent event = new CueEvent(id, time, type, order,
                    GSON.fromJson(buffer.readUtf(32_767), com.google.gson.JsonObject.class));
            return new ActionPresentationEventS2C(actorEntityId, instanceId, loopIteration, event);
        }

        private void handle(IPayloadContext context) {
            enqueue(context, () -> ActionNetwork.receivePresentationEvent(actorEntityId, instanceId, loopIteration, event));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void writeSnapshot(ByteBuf raw, ActionSnapshot snapshot) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(raw);
        buffer.writeVarInt(snapshot.actorEntityId());
        buffer.writeVarLong(snapshot.instanceId().value());
        buffer.writeResourceLocation(snapshot.actionId().value());
        buffer.writeResourceLocation(snapshot.cueId().value());
        buffer.writeBoolean(snapshot.section().isPresent());
        snapshot.section().ifPresent(section -> buffer.writeUtf(section.value(), 128));
        buffer.writeVarLong(snapshot.cursor().micros());
        buffer.writeVarLong(snapshot.serverTick());
        buffer.writeVarLong(snapshot.sequence());
        buffer.writeBoolean(snapshot.stopReason().isPresent());
        snapshot.stopReason().ifPresent(reason -> buffer.writeResourceLocation(reason.value()));
    }

    private static ActionSnapshot readSnapshot(ByteBuf raw) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(raw);
        int actorEntityId = buffer.readVarInt();
        ActionInstanceId instanceId = new ActionInstanceId(buffer.readVarLong());
        ActionId actionId = new ActionId(buffer.readResourceLocation());
        CombatCueId cueId = new CombatCueId(buffer.readResourceLocation());
        Optional<SectionId> section = buffer.readBoolean() ? Optional.of(new SectionId(buffer.readUtf(128))) : Optional.empty();
        CueTime cursor = new CueTime(buffer.readVarLong());
        long serverTick = buffer.readVarLong();
        long sequence = buffer.readVarLong();
        Optional<ActionStopReason> stopReason = buffer.readBoolean()
                ? Optional.of(new ActionStopReason(buffer.readResourceLocation())) : Optional.empty();
        return new ActionSnapshot(actorEntityId, instanceId, actionId, cueId, section, cursor, serverTick, sequence, stopReason);
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ActionGuide.MODID, path));
    }

    private static void enqueue(IPayloadContext context, Runnable operation) {
        context.enqueueWork(operation).exceptionally(throwable -> {
            context.disconnect(Component.translatable("neoforge.network.invalid_flow", throwable.getMessage()));
            return null;
        });
    }
}
