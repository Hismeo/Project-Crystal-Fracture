package org.hismeo.fractureclient.client.avatar;

import com.kaleblangley.haikalat.subsystems.render3d.MeshRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import org.hismeo.fractureclient.client.avatar.skin.PlayerSkinCache;
import org.hismeo.fractureclient.client.avatar.skin.PlayerSkinProvider;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Owns all client-world player Avatar bindings. */
public final class PlayerAvatarManager implements AutoCloseable {
    private final Map<UUID, PlayerAvatarBinding> bindings = new HashMap<>();
    private final Map<UUID, Float> pendingDashTimes = new HashMap<>();
    private final PlayerAvatarModelCatalog models;
    private final PlayerSkinCache skinCache;
    private final PlayerSkinProvider skinProvider = new PlayerSkinProvider();
    private final PlayerAvatarPoseSampler poseSampler = new PlayerAvatarPoseSampler();
    private final PlayerAvatarModelSelector modelSelector = new PlayerAvatarModelSelector();
    private boolean closed;

    public PlayerAvatarManager(
            PlayerAvatarModelCatalog models,
            PlayerSkinCache skinCache
    ) {
        this.models = models;
        this.skinCache = skinCache;
    }

    public void update(ClientLevel level, float partialTick, float deltaSeconds) {
        if (closed) {
            return;
        }
        Set<UUID> present = new HashSet<>();
        for (AbstractClientPlayer player : level.players()) {
            if (!isSupported(player)) {
                continue;
            }
            UUID playerId = player.getUUID();
            present.add(playerId);
            PlayerAvatarBinding binding = bindings.computeIfAbsent(
                    playerId,
                    ignored -> new PlayerAvatarBinding(
                            playerId,
                            modelSelector.select(player),
                            models,
                            skinProvider,
                            skinCache,
                            poseSampler));
            binding.update(
                    player,
                    modelSelector.select(player),
                    partialTick,
                    deltaSeconds);
            Float pendingDashTime = pendingDashTimes.remove(playerId);
            if (pendingDashTime != null) {
                binding.playDash(pendingDashTime);
            }
        }
        bindings.entrySet().removeIf(entry -> {
            if (present.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().close();
            return true;
        });
    }

    public boolean canReplace(Player player, PlayerAvatarRenderGate gate) {
        if (closed
                || !isSupported(player)
                || player.level() != Minecraft.getInstance().level) {
            return false;
        }
        if (player == Minecraft.getInstance().player
                && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            return false;
        }
        PlayerAvatarBinding binding = bindings.get(player.getUUID());
        return binding != null
                && binding.ready()
                && gate.permits(Minecraft.getInstance().level);
    }

    public List<MeshRenderer> readyRenderers() {
        Minecraft minecraft = Minecraft.getInstance();
        UUID hiddenLocalPlayer = minecraft.player != null
                && minecraft.options.getCameraType().isFirstPerson()
                ? minecraft.player.getUUID()
                : null;
        List<MeshRenderer> result = new ArrayList<>();
        bindings.values().stream()
                .filter(PlayerAvatarBinding::ready)
                .filter(binding -> !binding.playerId().equals(hiddenLocalPlayer))
                .forEach(binding -> result.addAll(binding.renderers()));
        return result;
    }

    public int size() {
        return bindings.size();
    }

    public boolean weaponSocket(UUID playerId, Matrix4f destination) {
        PlayerAvatarBinding binding = bindings.get(playerId);
        return binding != null && binding.weaponSocket(destination);
    }

    public void playDash(UUID playerId) {
        playDash(playerId, 0.0F);
    }

    public void playDash(UUID playerId, float timeSeconds) {
        PlayerAvatarBinding binding = bindings.get(playerId);
        if (binding != null) {
            binding.playDash(timeSeconds);
        } else if (!closed) {
            pendingDashTimes.put(playerId, Math.max(0.0F, timeSeconds));
        }
    }

    public void stopDash(UUID playerId) {
        pendingDashTimes.remove(playerId);
        PlayerAvatarBinding binding = bindings.get(playerId);
        if (binding != null) {
            binding.stopDash();
        }
    }

    private static boolean isSupported(Player player) {
        return !player.isRemoved()
                && !player.isInvisible();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        bindings.values().forEach(PlayerAvatarBinding::close);
        bindings.clear();
        pendingDashTimes.clear();
    }
}
