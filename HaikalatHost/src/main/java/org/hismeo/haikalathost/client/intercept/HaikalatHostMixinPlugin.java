package org.hismeo.haikalathost.client.intercept;

import org.hismeo.haikalathost.client.backend.FullTakeoverConfiguration;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Removes disabled experimental interceptors before their target classes are transformed. */
public final class HaikalatHostMixinPlugin implements IMixinConfigPlugin {
    private final FullTakeoverConfiguration configuration;

    public HaikalatHostMixinPlugin() {
        this(FullTakeoverConfiguration.current());
    }

    HaikalatHostMixinPlugin(FullTakeoverConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        return switch (simpleName) {
            case "MinecraftMixin" -> true;
            case "RenderTypeMixin" -> false;
            case "RenderSectionMixin", "SectionRenderDispatcherMixin",
                    "LevelRendererMixin", "ModelPartMixin", "ItemRendererMixin" ->
                    configuration.haikalatBackend();
            case "FullTakeoverRenderTypeAuditMixin", "FullTakeoverBufferUploaderAuditMixin",
                    "FullTakeoverVertexBufferAuditMixin", "FullTakeoverBufferSourceMixin",
                    "FullTakeoverRenderSystemStateMixin",
                    "CompositeRenderTypeAccessor", "RenderTypeCompositeStateAccessor",
                    "RenderStateBooleanAccessor", "RenderStateTextureInvoker" ->
                    configuration.haikalatBackend();
            default -> true;
        };
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass,
                                   String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass,
                                    String mixinClassName, IMixinInfo mixinInfo) { }
}
