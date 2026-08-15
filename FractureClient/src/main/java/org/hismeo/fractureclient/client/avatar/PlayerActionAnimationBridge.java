package org.hismeo.fractureclient.client.avatar;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.hismeo.actionguide.api.ActionGuideApi;
import org.hismeo.actionguide.api.network.ActionSnapshot;
import org.hismeo.actionguide.api.network.ActionSyncMessage;
import org.hismeo.crystalfracture.dash.DashIds;

/** Maps authoritative ActionGuide starts/snapshots onto player Avatar one-shots. */
public final class PlayerActionAnimationBridge {
    private static final PlayerActionAnimationState STATE = new PlayerActionAnimationState();
    private static boolean installed;

    private PlayerActionAnimationBridge() {
    }

    public static synchronized void install(ActionGuideApi actions) {
        if (installed) {
            return;
        }
        installed = true;
        actions.registerSyncListener(message -> {
            if (message instanceof ActionSyncMessage.Started started) {
                play(started.snapshot());
            } else if (message instanceof ActionSyncMessage.Snapshot snapshot) {
                play(snapshot.snapshot());
            } else if (message instanceof ActionSyncMessage.Stopped stopped) {
                stop(stopped.snapshot());
            }
        });
    }

    private static void play(ActionSnapshot snapshot) {
        if (!snapshot.actionId().equals(DashIds.ACTION)) {
            return;
        }
        STATE.started(snapshot.actorEntityId(), snapshot.instanceId());
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(snapshot.actorEntityId());
        if (entity instanceof Player player) {
            PlayerAvatarExtension.playDash(
                    player, snapshot.cursor().seconds().floatValue());
        }
    }

    private static void stop(ActionSnapshot snapshot) {
        if (!snapshot.actionId().equals(DashIds.ACTION)
                || !STATE.stopped(snapshot.actorEntityId(), snapshot.instanceId())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(snapshot.actorEntityId());
        if (entity instanceof Player player) {
            PlayerAvatarExtension.stopDash(player);
        }
    }

    static void worldClosed() {
        STATE.clear();
    }
}
