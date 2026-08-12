package org.hismeo.actionguide;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.hismeo.actionguide.api.action.ActionContext;
import org.hismeo.actionguide.api.action.ActionOwner;
import org.hismeo.actionguide.api.cue.CueEvent;
import org.hismeo.actionguide.api.network.ActionSnapshot;
import org.hismeo.actionguide.api.network.ActionSyncMessage;
import org.hismeo.actionguide.api.runtime.ActionInstanceId;
import org.hismeo.actionguide.api.runtime.ActionInstanceView;
import org.hismeo.actionguide.api.runtime.ActionRequestResult;
import org.hismeo.actionguide.api.runtime.ActionStopReason;
import org.hismeo.actionguide.internal.ActionGuideService;
import org.hismeo.actionguide.internal.network.ActionClientSyncState;
import org.hismeo.actionguide.internal.network.ActionNetworkPayloads;
import org.hismeo.actionguide.api.event.ActionLifecycleListener;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Server-authoritative transport bridge. It intentionally has no dependency on a client implementation module. */
public final class ActionNetwork {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicLong SEQUENCES = new AtomicLong();
    private static final ActionClientSyncState CLIENT_SYNC = new ActionClientSyncState();
    private static volatile MinecraftServer server;

    private ActionNetwork() {
    }

    public static void install(ActionGuideService service) {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        service.registerLifecycleListener(new ActionLifecycleListener() {
            @Override
            public void onStarted(ActionContext context, ActionInstanceView action) {
                broadcast(context, action, Optional.empty(), snapshot ->
                        new ActionNetworkPayloads.ActionStartedS2C(snapshot));
            }

            @Override
            public void onTransitioned(ActionContext context, ActionInstanceView action) {
                broadcast(context, action, Optional.empty(), snapshot ->
                        new ActionNetworkPayloads.ActionTransitionedS2C(snapshot));
            }

            @Override
            public void onStopped(ActionContext context, ActionInstanceView action, ActionStopReason reason) {
                broadcast(context, action, Optional.of(reason), snapshot ->
                        new ActionNetworkPayloads.ActionStoppedS2C(snapshot));
            }
        });
        service.registerRuntimeObserver(ActionNetwork::broadcastPresentationEvent);
    }

    public static void setServer(MinecraftServer value) {
        server = value;
    }

    public static void clearServer() {
        server = null;
    }

    public static void handleIntent(IPayloadContext context, ActionNetworkPayloads.ActionIntentC2S payload) {
        if (!(context.player() instanceof ServerPlayer player)) {
            throw new IllegalStateException("Action intent sender is not a server player");
        }
        MinecraftServer currentServer = player.server;
        setServer(currentServer);
        ActionRequestResult result = ActionGuide.service().submitNetworkIntent(new ActionOwner(player.getUUID()),
                payload.request(), currentServer.getTickCount());
        if (!result.accepted()) {
            Optional<ActionSnapshot> authority = result.action().map(action -> snapshot(player.getId(), action,
                    currentServer.getTickCount(), Optional.empty()));
            PacketDistributor.sendToPlayer(player, new ActionNetworkPayloads.ActionRejectedS2C(
                    payload.request().sequence(), result.reason(), authority));
        }
    }

    public static void sendSnapshot(ServerPlayer observer, Entity target) {
        ActionGuide.service().runtime(new ActionOwner(target.getUUID())).flatMap(runtime -> runtime.currentAction())
                .map(action -> snapshot(target.getId(), action, observer.server.getTickCount(), Optional.empty()))
                .ifPresent(snapshot -> PacketDistributor.sendToPlayer(observer,
                        new ActionNetworkPayloads.ActionSnapshotS2C(snapshot)));
    }

    public static void receiveStarted(ActionSnapshot snapshot) {
        if (CLIENT_SYNC.acceptSnapshot(snapshot)) {
            ActionGuide.dispatchSync(new ActionSyncMessage.Started(snapshot));
        }
    }

    public static void receiveTransitioned(ActionSnapshot snapshot) {
        if (CLIENT_SYNC.acceptSnapshot(snapshot)) {
            ActionGuide.dispatchSync(new ActionSyncMessage.Transitioned(snapshot));
        }
    }

    public static void receiveStopped(ActionSnapshot snapshot) {
        if (CLIENT_SYNC.acceptSnapshot(snapshot)) {
            ActionGuide.dispatchSync(new ActionSyncMessage.Stopped(snapshot));
        }
    }

    public static void receiveRejected(long sequence, net.minecraft.resources.ResourceLocation reason,
                                       Optional<ActionSnapshot> authority) {
        authority.ifPresent(CLIENT_SYNC::acceptSnapshot);
        ActionGuide.dispatchSync(new ActionSyncMessage.Rejected(sequence, reason, authority));
    }

    public static void receiveSnapshot(ActionSnapshot snapshot) {
        if (CLIENT_SYNC.acceptSnapshot(snapshot)) {
            ActionGuide.dispatchSync(new ActionSyncMessage.Snapshot(snapshot));
        }
    }

    public static void receivePresentationEvent(int actorEntityId, ActionInstanceId instanceId, long loopIteration,
                                                CueEvent event) {
        if (CLIENT_SYNC.acceptEvent(actorEntityId, instanceId, loopIteration, event.id())) {
            ActionGuide.dispatchSync(new ActionSyncMessage.PresentationEvent(actorEntityId, instanceId,
                    loopIteration, event));
        }
    }

    private static void broadcastPresentationEvent(ActionContext context, ActionInstanceView action,
                                                   CueEvent event, long loopIteration) {
        Entity entity = findEntity(context.owner());
        if (entity != null) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,
                    new ActionNetworkPayloads.ActionPresentationEventS2C(entity.getId(), action.instanceId(),
                            loopIteration, event));
        }
    }

    private static void broadcast(ActionContext context, ActionInstanceView action, Optional<ActionStopReason> reason,
                                  java.util.function.Function<ActionSnapshot, CustomPacketPayload> packetFactory) {
        Entity entity = findEntity(context.owner());
        if (entity != null) {
            ActionSnapshot snapshot = snapshot(entity.getId(), action, serverTick(context), reason);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, packetFactory.apply(snapshot));
        }
    }

    private static ActionSnapshot snapshot(int actorEntityId, ActionInstanceView action, long serverTick,
                                           Optional<ActionStopReason> stopReason) {
        return new ActionSnapshot(actorEntityId, action.instanceId(), action.actionId(), action.cueId(),
                action.currentSection(), action.cursor(), serverTick, SEQUENCES.incrementAndGet(), stopReason);
    }

    private static long serverTick(ActionContext context) {
        MinecraftServer current = server;
        return current == null ? context.serverTick() : current.getTickCount();
    }

    private static Entity findEntity(ActionOwner owner) {
        MinecraftServer current = server;
        if (current == null) {
            return null;
        }
        for (ServerLevel level : current.getAllLevels()) {
            Entity entity = level.getEntity(owner.id());
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }
}
