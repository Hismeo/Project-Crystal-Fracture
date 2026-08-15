package org.hismeo.fractureclient.client.avatar;

import com.kaleblangley.haikalat.subsystems.render3d.MeshRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import org.hismeo.fractureclient.FractureClient;
import org.hismeo.fractureclient.client.avatar.skin.PlayerSkinCache;
import org.hismeo.haikalathost.api.HaikalatHostApi;
import org.hismeo.haikalathost.api.client.advanced.HaikalatEngineContext;
import org.hismeo.haikalathost.api.client.advanced.HaikalatFrameContext;
import org.hismeo.haikalathost.api.client.advanced.HaikalatReloadContext;
import org.hismeo.haikalathost.api.client.advanced.HaikalatRenderExtension;
import java.util.List;
import java.util.UUID;
import org.joml.Matrix4f;

/** Translates client-world players into Haikalat characters and draws them before post-processing. */
public final class PlayerAvatarExtension implements HaikalatRenderExtension {
    private static volatile PlayerAvatarExtension active;

    private final PlayerAvatarRenderGate renderGate = new PlayerAvatarRenderGate();
    private PlayerAvatarModelCatalog models;
    private PlayerSkinCache skins;
    private PlayerAvatarManager manager;
    private final PlayerAvatarOverlayRenderer overlayRenderer =
            new PlayerAvatarOverlayRenderer();
    private ClientLevel activeLevel;

    @Override
    public void initialize(HaikalatEngineContext context) {
        active = this;
        tryLoadInitialModels(context);
    }

    @Override
    public void render(HaikalatFrameContext frame) {
        // PlayerRenderer ran earlier in this world frame using the previous heartbeat. Clearing it
        // here guarantees that an exception in this callback restores vanilla rendering next frame.
        renderGate.reset();
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || models == null) {
            return;
        }
        ensureWorld(level);
        manager.update(level, frame.camera().partialTick(), frame.deltaSeconds());
        List<MeshRenderer> readyRenderers = manager.readyRenderers();
        if (readyRenderers.isEmpty()) {
            return;
        }

        overlayRenderer.render(frame, readyRenderers);
        renderGate.heartbeat(level);
    }

    @Override
    public void resourcesReloaded(HaikalatReloadContext context) {
        PlayerAvatarModelCatalog replacement;
        try {
            replacement = PlayerAvatarModelCatalog.load(
                    context.resources(),
                    context.resourceGeneration());
        } catch (RuntimeException failure) {
            FractureClient.LOGGER.error(
                    "Keeping the last-known-good player Avatar models after reload failure",
                    failure);
            return;
        }

        closeWorldObjects();
        if (skins != null) {
            skins.close();
            skins = null;
        }
        PlayerAvatarModelCatalog previous = models;
        models = replacement;
        if (previous != null) {
            previous.close();
        }
    }

    @Override
    public void worldClosed(HaikalatEngineContext context) {
        closeWorldObjects();
    }

    @Override
    public void close(HaikalatEngineContext context) {
        closeWorldObjects();
        if (skins != null) {
            skins.close();
            skins = null;
        }
        if (models != null) {
            models.close();
            models = null;
        }
        renderGate.close();
        if (active == this) {
            active = null;
        }
    }

    public static boolean canReplace(Player player) {
        PlayerAvatarExtension extension = active;
        return extension != null
                && HaikalatHostApi.get().available()
                && extension.manager != null
                && extension.manager.canReplace(player, extension.renderGate);
    }

    public static void beginMinecraftFrame() {
        PlayerAvatarExtension extension = active;
        if (extension != null) {
            extension.renderGate.beginFrame();
        }
    }

    public static void beginMinecraftWorldRender() {
        PlayerAvatarExtension extension = active;
        if (extension != null) {
            extension.renderGate.beginWorldRender();
        }
    }

    public static void endMinecraftWorldRender() {
        PlayerAvatarExtension extension = active;
        if (extension != null) {
            extension.renderGate.endWorldRender();
        }
    }

    /** Entry point for the gameplay action layer to trigger the authored dash one-shot. */
    public static void playDash(Player player) {
        playDash(player, 0.0F);
    }

    /** Starts or catches up the authored dash one-shot from an ActionGuide cursor. */
    public static void playDash(Player player, float timeSeconds) {
        PlayerAvatarExtension extension = active;
        if (extension != null && extension.manager != null) {
            extension.manager.playDash(player.getUUID(), timeSeconds);
        }
    }

    /** Terminates the matching authoritative dash one-shot immediately. */
    public static void stopDash(Player player) {
        PlayerAvatarExtension extension = active;
        if (extension != null && extension.manager != null) {
            extension.manager.stopDash(player.getUUID());
        }
    }

    /** Client render-thread query for the animated hand_r/weapon_socket matrix. */
    public static boolean weaponSocket(UUID playerId, Matrix4f destination) {
        PlayerAvatarExtension extension = active;
        return extension != null
                && extension.manager != null
                && extension.manager.weaponSocket(playerId, destination);
    }

    private void tryLoadInitialModels(HaikalatEngineContext context) {
        try {
            models = PlayerAvatarModelCatalog.load(
                    context.resources(),
                    context.resourceGeneration());
            skins = new PlayerSkinCache();
        } catch (RuntimeException failure) {
            FractureClient.LOGGER.error(
                    "Player Avatar models are unavailable; vanilla player rendering remains active",
                    failure);
            models = null;
            if (skins != null) {
                skins.close();
                skins = null;
            }
        }
    }

    private void ensureWorld(ClientLevel level) {
        if (activeLevel == level && manager != null) {
            return;
        }
        closeWorldObjects();
        if (skins == null) {
            skins = new PlayerSkinCache();
        }
        activeLevel = level;
        manager = new PlayerAvatarManager(models, skins);
    }

    private void closeWorldObjects() {
        PlayerActionAnimationBridge.worldClosed();
        renderGate.reset();
        overlayRenderer.reset();
        if (manager != null) {
            manager.close();
            manager = null;
        }
        activeLevel = null;
    }
}
