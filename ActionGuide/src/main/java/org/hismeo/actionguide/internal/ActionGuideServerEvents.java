package org.hismeo.actionguide;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.hismeo.actionguide.api.action.ActionOwner;
import org.hismeo.actionguide.api.runtime.ActionStopReason;
import org.hismeo.actionguide.internal.definition.ActionGuideReloadListener;

@EventBusSubscriber(modid = ActionGuide.MODID)
public final class ActionGuideServerEvents {
    private static final ActionStopReason ENTITY_UNLOADED = ActionStopReason.parse("action_guide:entity_unloaded");
    private static final ActionStopReason ENTITY_DIED = ActionStopReason.parse("action_guide:entity_died");
    private static final ActionStopReason PLAYER_LOGGED_OUT = ActionStopReason.parse("action_guide:player_logged_out");
    private static final ActionStopReason DIMENSION_CHANGED = ActionStopReason.parse("action_guide:dimension_changed");
    private static final ActionStopReason SERVER_STOPPED = ActionStopReason.parse("action_guide:server_stopped");

    private ActionGuideServerEvents() {
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new ActionGuideReloadListener(ActionGuide.service().registry()));
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Pre event) {
        ActionNetwork.setServer(event.getServer());
        ActionGuide.service().tick();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void entityDied(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            ActionGuide.service().remove(new ActionOwner(event.getEntity().getUUID()), ENTITY_DIED);
        }
    }

    @SubscribeEvent
    public static void entityLeaves(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            ActionGuide.service().remove(new ActionOwner(event.getEntity().getUUID()), ENTITY_UNLOADED);
        }
    }

    @SubscribeEvent
    public static void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ActionGuide.service().remove(new ActionOwner(event.getEntity().getUUID()), PLAYER_LOGGED_OUT);
    }

    @SubscribeEvent
    public static void playerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        ActionGuide.service().remove(new ActionOwner(event.getEntity().getUUID()), DIMENSION_CHANGED);
    }

    @SubscribeEvent
    public static void playerStartsTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            ActionNetwork.sendSnapshot(player, event.getTarget());
        }
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event) {
        ActionGuide.service().clear(SERVER_STOPPED);
        ActionNetwork.clearServer();
    }
}
