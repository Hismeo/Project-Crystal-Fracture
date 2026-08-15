package org.hismeo.crystalfracture.dash;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.hismeo.actionguide.api.ActionGuideApi;
import org.hismeo.actionguide.api.action.ActionAdmission;
import org.hismeo.actionguide.api.action.ActionContext;
import org.hismeo.actionguide.api.action.ActionDecision;
import org.hismeo.actionguide.api.action.ActionDefinition;
import org.hismeo.actionguide.api.action.IntentPhase;
import org.hismeo.actionguide.api.cue.RootMotionContract;
import org.hismeo.actionguide.api.cue.RootMotionMode;
import org.hismeo.actionguide.api.cue.RootMotionSample;
import org.hismeo.actionguide.api.event.ActionLifecycleListener;
import org.hismeo.actionguide.api.runtime.ActionInstanceId;
import org.hismeo.actionguide.api.runtime.ActionInstanceView;
import org.hismeo.actionguide.api.runtime.ActionStopReason;
import org.hismeo.crystalfracture.CrystalFracture;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative dash admission, cooldown and CombatCue root-motion application. */
public final class DashActionController {
    public static final int COOLDOWN_TICKS = 24;

    private static final ResourceLocation COOLDOWN = CrystalFracture.packRL("dash_cooldown");
    private static final ResourceLocation INVALID_STATE = CrystalFracture.packRL("dash_invalid_state");
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();
    private static final Map<UUID, ActiveDash> ACTIVE = new HashMap<>();

    private DashActionController() {
    }

    public static void install(ActionGuideApi actions) {
        actions.registerResolver((context, request) -> request.phase() == IntentPhase.PRESS
                && request.intent().equals(DashIds.INTENT)
                ? java.util.Optional.of(DashIds.ACTION)
                : java.util.Optional.empty());
        actions.registerAdmission(new ActionAdmission() {
            @Override
            public ActionDecision evaluate(ActionContext context, ActionDefinition action) {
                if (!action.id().equals(DashIds.ACTION)) {
                    return ActionDecision.allow();
                }
                ServerPlayer player = findPlayer(context.owner().id());
                if (player == null || !player.isAlive() || player.isPassenger()
                        || player.isSpectator() || player.isFallFlying()) {
                    return ActionDecision.reject(INVALID_STATE);
                }
                return context.serverTick() < COOLDOWNS.getOrDefault(player.getUUID(), Long.MIN_VALUE)
                        ? ActionDecision.reject(COOLDOWN)
                        : ActionDecision.allow();
            }

            @Override
            public void commit(ActionContext context, ActionDefinition action,
                               ActionInstanceId instanceId) {
                if (action.id().equals(DashIds.ACTION)) {
                    COOLDOWNS.put(context.owner().id(), context.serverTick() + COOLDOWN_TICKS);
                }
            }
        });
        actions.registerLifecycleListener(new ActionLifecycleListener() {
            @Override
            public void onStarted(ActionContext context, ActionInstanceView action) {
                if (!action.actionId().equals(DashIds.ACTION)) {
                    return;
                }
                ServerPlayer player = findPlayer(context.owner().id());
                if (player == null) {
                    return;
                }
                RootMotionContract rootMotion = actions.runtime(context.owner())
                        .flatMap(runtime -> runtime.currentRootMotion())
                        .filter(DashActionController::usableRootMotion)
                        .orElseThrow(() -> new IllegalStateException(
                                "Dash CombatCue has no server-readable root motion keyframes"));
                ACTIVE.put(player.getUUID(), new ActiveDash(
                        action.instanceId(), rootMotion,
                        rootMotion.sample(org.hismeo.actionguide.api.cue.CueTime.ZERO),
                        player.getYRot()));
            }

            @Override
            public void onStopped(ActionContext context, ActionInstanceView action,
                                  ActionStopReason reason) {
                ActiveDash dash = ACTIVE.get(context.owner().id());
                if (dash != null && dash.instanceId.equals(action.instanceId())) {
                    ServerPlayer player = findPlayer(context.owner().id());
                    if (player != null && reason.equals(ActionStopReason.COMPLETED)) {
                        RootMotionSample end = dash.rootMotion.sample(
                                dash.rootMotion.keyframes().getLast().time());
                        move(player, dash.rootMotion, dash.lastSample, end, dash.startYaw);
                    }
                    ACTIVE.remove(context.owner().id());
                }
            }
        });
    }

    public static void tick(ActionGuideApi actions) {
        Iterator<Map.Entry<UUID, ActiveDash>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveDash> entry = iterator.next();
            ServerPlayer player = findPlayer(entry.getKey());
            var current = actions.runtime(new org.hismeo.actionguide.api.action.ActionOwner(entry.getKey()))
                    .flatMap(runtime -> runtime.currentAction());
            if (player == null || current.isEmpty()
                    || !current.orElseThrow().instanceId().equals(entry.getValue().instanceId)) {
                iterator.remove();
                continue;
            }
            ActionInstanceView action = current.orElseThrow();
            ActiveDash dash = entry.getValue();
            RootMotionSample sample = dash.rootMotion.sample(action.cursor());
            move(player, dash.rootMotion, dash.lastSample, sample, dash.startYaw);
            entry.setValue(new ActiveDash(
                    dash.instanceId, dash.rootMotion, sample, dash.startYaw));
        }
    }

    public static void clear() {
        ACTIVE.clear();
        COOLDOWNS.clear();
    }

    private static boolean usableRootMotion(RootMotionContract rootMotion) {
        return rootMotion.enabled() && !rootMotion.keyframes().isEmpty();
    }

    private static void move(ServerPlayer player, RootMotionContract rootMotion,
                             RootMotionSample previous, RootMotionSample current,
                             float startYaw) {
        double localX = current.x() - previous.x();
        double localY = rootMotion.mode() == RootMotionMode.XZ_YAW
                ? 0.0
                : current.y() - previous.y();
        double localZ = current.z() - previous.z();
        double radians = Math.toRadians(180.0F - startYaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        Vec3 worldDelta = new Vec3(
                localX * cos + localZ * sin,
                localY,
                -localX * sin + localZ * cos);
        double beforeX = player.getX();
        double beforeY = player.getY();
        double beforeZ = player.getZ();
        player.move(MoverType.SELF, worldDelta);
        player.setYRot(startYaw - (float) current.yawDegrees());
        DashNetwork.sendStep(player, new Vec3(
                player.getX() - beforeX,
                player.getY() - beforeY,
                player.getZ() - beforeZ));
    }

    private static ServerPlayer findPlayer(UUID id) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getPlayerList().getPlayer(id);
    }

    private record ActiveDash(
            ActionInstanceId instanceId,
            RootMotionContract rootMotion,
            RootMotionSample lastSample,
            float startYaw
    ) {
    }
}
