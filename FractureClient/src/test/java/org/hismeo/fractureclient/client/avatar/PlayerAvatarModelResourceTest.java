package org.hismeo.fractureclient.client.avatar;

import com.kaleblangley.haikalat.core.assets.AssetByteResolver;
import com.kaleblangley.haikalat.core.assets.AssetRef;
import com.kaleblangley.haikalat.core.assets.gltf.GltfAssetLoader;
import com.kaleblangley.haikalat.core.assets.gltf.LoadedGltfScene;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlayerAvatarModelResourceTest {
    private static final Set<String> REQUIRED_ANIMATIONS = Set.of(
            "stand", "move", "run", "jump", "dash");
    private static final Map<String, Integer> REQUIRED_ANIMATION_CHANNELS = Map.of(
            "stand", 14,
            "move", 14,
            "run", 16,
            "jump", 15,
            "dash", 14);
    private static final Set<String> REQUIRED_BONES = Set.of(
            "bone",
            "torso",
            "chest",
            "head",
            "right_arm",
            "right_hand",
            "left_arm",
            "left_hand",
            "right_leg",
            "right_shin",
            "left_leg",
            "left_shin");

    @ParameterizedTest
    @ValueSource(strings = {
            PlayerAvatarModelCatalog.WILD_PATH,
            PlayerAvatarModelCatalog.SILE_PATH
    })
    void modelMeetsPlayerAvatarContract(String path) {
        LoadedGltfScene scene = new GltfAssetLoader(new ClasspathResolver())
                .load(AssetRef.of(path));

        Set<String> nodes = scene.nodes().stream()
                .map(LoadedGltfScene.Node::name)
                .collect(Collectors.toSet());
        assertTrue(nodes.containsAll(REQUIRED_BONES), "missing required player bones");
        assertEquals(1, scene.skins().size(), "player model must contain one skin");
        assertTrue(scene.images().stream().allMatch(image -> image.encoded().length > 0),
                "all model images must be embedded");
        assertTrue(scene.primitives().stream().allMatch(primitive -> {
            var bounds = primitive.mesh().localBounds();
            return bounds.isFinite()
                    && bounds.minY() >= -0.05F
                    && bounds.minY() <= 0.05F
                    && bounds.maxY() >= 1.7F
                    && bounds.maxY() <= 2.2F;
        }), "player mesh must remain close to the root floor and Minecraft player scale");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            PlayerAvatarModelCatalog.WILD_LIBRARY_PATH,
            PlayerAvatarModelCatalog.SILE_LIBRARY_PATH
    })
    void animationLibraryBindsEveryAuthoredPlayerClip(String path) {
        LoadedGltfScene scene = new GltfAssetLoader(new ClasspathResolver())
                .loadAnimationLibrary(AssetRef.of(path));

        Set<String> animations = scene.animations().stream()
                .map(LoadedGltfScene.AnimationDef::name)
                .collect(Collectors.toSet());
        assertEquals(REQUIRED_ANIMATIONS, animations);
        scene.animations().forEach(animation -> assertEquals(
                REQUIRED_ANIMATION_CHANNELS.get(animation.name()),
                animation.channels().size(),
                animation.name() + " must keep every authored channel"));
    }

    @org.junit.jupiter.api.Test
    void wildAndSileDifferOnlyOutsideTheSharedRigContract() {
        GltfAssetLoader loader = new GltfAssetLoader(new ClasspathResolver());
        LoadedGltfScene wild = loader.load(AssetRef.of(PlayerAvatarModelCatalog.WILD_PATH));
        LoadedGltfScene sile = loader.load(AssetRef.of(PlayerAvatarModelCatalog.SILE_PATH));

        PlayerAvatarModelCatalog.validateSharedRig(
                wild, PlayerAvatarModelCatalog.WILD_PATH,
                sile, PlayerAvatarModelCatalog.SILE_PATH);
    }

    private static final class ClasspathResolver implements AssetByteResolver {
        @Override
        public byte[] readBytes(AssetRef asset, long maxBytes) {
            try (InputStream stream = resource(asset.path())) {
                byte[] bytes = stream.readAllBytes();
                if (bytes.length > maxBytes) {
                    throw new IllegalArgumentException("asset exceeds byte limit: " + asset.path());
                }
                return bytes;
            } catch (IOException failure) {
                throw new IllegalArgumentException("could not read " + asset.path(), failure);
            }
        }

        @Override
        public boolean exists(AssetRef asset) {
            return PlayerAvatarModelResourceTest.class.getClassLoader()
                    .getResource(classpathPath(asset.path())) != null;
        }

        @Override
        public AssetRef resolveRelative(AssetRef base, String relative) {
            int separator = base.path().lastIndexOf('/');
            String directory = separator < 0 ? "" : base.path().substring(0, separator + 1);
            return AssetRef.of(directory + relative);
        }

        private static InputStream resource(String path) {
            InputStream stream = PlayerAvatarModelResourceTest.class.getClassLoader()
                    .getResourceAsStream(classpathPath(path));
            if (stream == null) {
                throw new IllegalArgumentException("missing classpath resource " + path);
            }
            return stream;
        }

        private static String classpathPath(String path) {
            return "assets/fracture_client/" + (path.startsWith("/") ? path.substring(1) : path);
        }
    }
}
