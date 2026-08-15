package org.hismeo.fractureclient.client.avatar;

import com.kaleblangley.haikalat.core.material.MaterialInstance;
import com.kaleblangley.haikalat.subsystems.render3d.MeshRenderer;
import com.kaleblangley.haikalat.subsystems.render3d.SceneObject;
import com.kaleblangley.haikalat.subsystems.render3d.gltf.GltfSceneInstance;
import net.minecraft.client.player.AbstractClientPlayer;
import org.hismeo.fractureclient.FractureClient;
import org.hismeo.fractureclient.client.avatar.skin.PlayerSkinCache;
import org.hismeo.fractureclient.client.avatar.skin.PlayerSkinHandle;
import org.hismeo.fractureclient.client.avatar.skin.PlayerSkinProvider;
import org.hismeo.fractureclient.client.avatar.skin.PlayerSkinSource;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** One Minecraft player bound to one Haikalat character instance. */
public final class PlayerAvatarBinding implements AutoCloseable {
    private static final String BASE_COLOR_SAMPLER = "uBaseColorMap";
    private static final long SKIN_RETRY_DELAY_NANOS = 1_000_000_000L;

    private final UUID playerId;
    private final PlayerAvatarModelCatalog models;
    private final PlayerSkinProvider skinProvider;
    private final PlayerSkinCache skinCache;
    private final PlayerAvatarPoseSampler poseSampler;
    private final Matrix4f rootTransform = new Matrix4f();
    private final Vector3f rootMotionCompensation = new Vector3f();

    private AvatarKind avatarKind;
    private GltfSceneInstance character;
    private PlayerAvatarAnimator animator;
    private PlayerSkinHandle skin;
    private List<MeshRenderer> renderers = List.of();
    private PlayerAvatarState state = PlayerAvatarState.UNINITIALIZED;
    private PlayerSkinSource activeSkin;
    private long retrySkinAtNanos = Long.MAX_VALUE;
    private AvatarKind failedReplacementKind;
    private long retryAvatarAtNanos = Long.MIN_VALUE;

    public PlayerAvatarBinding(
            UUID playerId,
            AvatarKind avatarKind,
            PlayerAvatarModelCatalog models,
            PlayerSkinProvider skinProvider,
            PlayerSkinCache skinCache,
            PlayerAvatarPoseSampler poseSampler
    ) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.avatarKind = Objects.requireNonNull(avatarKind, "avatarKind");
        this.models = Objects.requireNonNull(models, "models");
        this.skinProvider = Objects.requireNonNull(skinProvider, "skinProvider");
        this.skinCache = Objects.requireNonNull(skinCache, "skinCache");
        this.poseSampler = Objects.requireNonNull(poseSampler, "poseSampler");
    }

    public void update(
            AbstractClientPlayer player,
            AvatarKind selectedAvatarKind,
            float partialTick,
            float deltaSeconds
    ) {
        if (state == PlayerAvatarState.CLOSED || state == PlayerAvatarState.FAILED) {
            return;
        }
        try {
            Objects.requireNonNull(selectedAvatarKind, "selectedAvatarKind");
            PlayerAvatarPoseSampler.PlayerAvatarPose pose = poseSampler.sample(player, partialTick);
            pose.modelMatrix(rootTransform);
            if (character == null) {
                avatarKind = selectedAvatarKind;
                state = PlayerAvatarState.LOADING_MODEL;
                character = models.instantiate(avatarKind, new Matrix4f());
                animator = new PlayerAvatarAnimator(character);
            } else if (avatarKind != selectedAvatarKind
                    && (failedReplacementKind != selectedAvatarKind
                    || System.nanoTime() >= retryAvatarAtNanos)) {
                try {
                    replaceAvatar(player, selectedAvatarKind);
                    failedReplacementKind = null;
                    retryAvatarAtNanos = Long.MIN_VALUE;
                } catch (RuntimeException failure) {
                    failedReplacementKind = selectedAvatarKind;
                    retryAvatarAtNanos = System.nanoTime() + SKIN_RETRY_DELAY_NANOS;
                    FractureClient.LOGGER.error(
                            "Could not switch player Avatar {} from {} to {}; keeping the previous model",
                            playerId,
                            avatarKind,
                            selectedAvatarKind,
                            failure);
                }
            }
            animator.update(player, deltaSeconds);
            animator.rootMotionCompensation(rootMotionCompensation);
            rootTransform.translate(
                    -rootMotionCompensation.x,
                    -rootMotionCompensation.y,
                    -rootMotionCompensation.z);

            PlayerSkinSource skinSource = skinProvider.current(player);
            if (skin == null
                    || !skinSource.equals(activeSkin)
                    || (skin.fallback() && System.nanoTime() >= retrySkinAtNanos)) {
                state = PlayerAvatarState.WAITING_FOR_SKIN;
                replaceSkin(skinSource);
            }
            buildRenderers();
            state = PlayerAvatarState.READY;
        } catch (RuntimeException failure) {
            FractureClient.LOGGER.error(
                    "Player Avatar binding {} failed; keeping vanilla rendering active",
                    playerId,
                    failure);
            closeOwnedObjects();
            state = PlayerAvatarState.FAILED;
        }
    }

    public UUID playerId() {
        return playerId;
    }

    public AvatarKind avatarKind() {
        return avatarKind;
    }

    public PlayerAvatarState state() {
        return state;
    }

    public boolean ready() {
        return state == PlayerAvatarState.READY;
    }

    public List<MeshRenderer> renderers() {
        return ready() ? renderers : List.of();
    }

    /** Resolves the logical hand_r/weapon_socket after the current animation pose. */
    public boolean weaponSocket(Matrix4f destination) {
        Objects.requireNonNull(destination, "destination");
        if (!ready() || character == null) {
            return false;
        }
        destination.set(character.nodeModelMatrix(models.weaponSocketNodeIndex()))
                .mulLocal(rootTransform);
        return true;
    }

    public void playDash() {
        playDash(0.0F);
    }

    public void playDash(float timeSeconds) {
        if (animator != null && state != PlayerAvatarState.CLOSED
                && state != PlayerAvatarState.FAILED) {
            animator.playDash(timeSeconds);
        }
    }

    public void stopDash() {
        if (animator != null && state != PlayerAvatarState.CLOSED
                && state != PlayerAvatarState.FAILED) {
            animator.stopDash();
        }
    }

    @Override
    public void close() {
        if (state == PlayerAvatarState.CLOSED) {
            return;
        }
        closeOwnedObjects();
        state = PlayerAvatarState.CLOSED;
    }

    private void replaceSkin(PlayerSkinSource source) {
        PlayerSkinHandle replacement = skinCache.acquire(source, avatarKind.armLayout());
        PlayerSkinHandle previous = skin;
        skin = replacement;
        activeSkin = source;
        retrySkinAtNanos = replacement.fallback() && source.textureUrl() != null
                ? System.nanoTime() + SKIN_RETRY_DELAY_NANOS
                : Long.MAX_VALUE;
        renderers = List.of();
        if (previous != null) {
            previous.close();
        }
    }

    /** Builds the complete replacement before publishing it, so render code never sees half a model. */
    private void replaceAvatar(AbstractClientPlayer player, AvatarKind replacementKind) {
        GltfSceneInstance replacementCharacter = null;
        PlayerSkinHandle replacementSkin = null;
        try {
            replacementCharacter = models.instantiate(replacementKind, new Matrix4f());
            PlayerAvatarAnimator replacementAnimator =
                    new PlayerAvatarAnimator(replacementCharacter);
            PlayerSkinSource replacementSkinSource = skinProvider.current(player);
            replacementSkin = skinCache.acquire(
                    replacementSkinSource,
                    replacementKind.armLayout());
            List<MeshRenderer> replacementRenderers = buildRenderers(
                    replacementCharacter,
                    replacementSkin);

            GltfSceneInstance previousCharacter = character;
            PlayerSkinHandle previousSkin = skin;
            avatarKind = replacementKind;
            character = replacementCharacter;
            animator = replacementAnimator;
            skin = replacementSkin;
            activeSkin = replacementSkinSource;
            retrySkinAtNanos = replacementSkin.fallback()
                    && replacementSkinSource.textureUrl() != null
                    ? System.nanoTime() + SKIN_RETRY_DELAY_NANOS
                    : Long.MAX_VALUE;
            renderers = replacementRenderers;

            replacementCharacter = null;
            replacementSkin = null;
            if (previousCharacter != null) {
                previousCharacter.close();
            }
            if (previousSkin != null) {
                previousSkin.close();
            }
        } finally {
            if (replacementCharacter != null) {
                replacementCharacter.close();
            }
            if (replacementSkin != null) {
                replacementSkin.close();
            }
        }
    }

    private void buildRenderers() {
        if (!renderers.isEmpty()) {
            return;
        }
        if (character == null || skin == null) {
            throw new IllegalStateException("Cannot build avatar renderers before model and skin");
        }
        renderers = buildRenderers(character, skin);
    }

    private List<MeshRenderer> buildRenderers(
            GltfSceneInstance sourceCharacter,
            PlayerSkinHandle sourceSkin
    ) {
        return sourceCharacter.objects().stream()
                .map(object -> withSkin(object, sourceSkin))
                .toList();
    }

    private MeshRenderer withSkin(SceneObject object, PlayerSkinHandle sourceSkin) {
        MaterialInstance material = models.embeddedMaterial(object.material()).createInstance()
                .texture(0, BASE_COLOR_SAMPLER, sourceSkin.texture(), skinCache.sampler());
        return new MeshRenderer(
                object.mesh(),
                material,
                com.kaleblangley.haikalat.subsystems.render3d.Transform.identity(),
                (destination, frameIndex) -> {
                    object.computeModel(destination, frameIndex);
                    destination.mulLocal(rootTransform);
                },
                false,
                object.drawBinding());
    }

    private void closeOwnedObjects() {
        renderers = List.of();
        if (character != null) {
            character.close();
            character = null;
        }
        animator = null;
        if (skin != null) {
            skin.close();
            skin = null;
        }
        activeSkin = null;
        retrySkinAtNanos = Long.MAX_VALUE;
        failedReplacementKind = null;
        retryAvatarAtNanos = Long.MIN_VALUE;
    }
}
