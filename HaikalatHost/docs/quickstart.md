# HaikalatHost 第三方模组快速接入

本文面向 Minecraft 1.21.1、NeoForge 21.1 与 Haikalat 0.20.1。HaikalatHost 是薄宿主：它提供 Minecraft framebuffer、相机、资源与生命周期；你的客户端扩展直接使用 Haikalat 创建 Scene、Pipeline、VFX 或 UI。

完整契约见 [haikalathost-0.20.md](haikalathost-0.20.md)，可运行模板见 [HaikalatHostExample](../../HaikalatHostExample/README.md)。

## 1. 添加编译依赖

发布到本地 Maven：

```powershell
.\gradlew.bat :HaikalatHost:publishHaikalatHostApiPublicationToMavenLocal
```

第三方工程只在编译期依赖 Host API 与 Haikalat 类型：

```groovy
repositories {
    mavenLocal()
}

dependencies {
    compileOnly("org.hismeo:haikalathost-api:1.0")
    compileOnly("com.github.KLjiana:haikalat:0.20.1") {
        transitive = false
    }
}
```

如果在本仓库内开发：

```groovy
dependencies {
    compileOnly(project(
            path: ":HaikalatHost",
            configuration: "haikalatHostApiElements"))
    compileOnly("com.github.KLjiana:haikalat:0.20.1") {
        transitive = false
    }
}
```

不要把 API JAR 或 Haikalat 用 `implementation`、jarJar、shadow 等方式装入你的模组。玩家运行时安装完整 `haikalat_host`，由它提供唯一一份 Haikalat。

## 2. 声明客户端依赖

在 `neoforge.mods.toml` 中把 `example_mod` 替换为你的真实 mod id：

```toml
[[dependencies.example_mod]]
modId="haikalat_host"
type="required"
versionRange="[1.0,2.0)"
ordering="AFTER"
side="CLIENT"
```

如果 Host 只是可选增强项，必须把全部 Host/Haikalat 类型放入只在确认 Host 已加载后才反射加载的客户端 compat 类。不要让公共入口的字段、方法签名或静态初始化器引用这些类型，否则 dedicated server 或未安装 Host 的客户端仍可能在类解析时失败。

## 3. 注册扩展

建立一个只在客户端加载的模组总线订阅类：

```java
package com.example.examplemod.client;

import com.example.examplemod.ExampleMod;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.hismeo.haikalathost.api.client.advanced.event
        .RegisterHaikalatExtensionsEvent;

@EventBusSubscriber(modid = ExampleMod.MOD_ID, value = Dist.CLIENT)
public final class ExampleHaikalatRegistration {
    private ExampleHaikalatRegistration() {
    }

    @SubscribeEvent
    public static void registerExtensions(
            RegisterHaikalatExtensionsEvent event
    ) {
        event.register(
                ResourceLocation.fromNamespaceAndPath(
                        ExampleMod.MOD_ID,
                        "main"),
                new ExampleHaikalatExtension());
    }
}
```

扩展 ID 必须使用注册模组自己的 namespace，且全局不能重复。注册阶段只创建普通 Java 实例；不要读取资源、访问世界或创建 GPU 对象。

注册扩展会自动把扩展 ID 的 namespace 挂载进 Host 提供的 `ResourceCatalog`。

## 4. 实现扩展

最小骨架：

```java
package com.example.examplemod.client;

import com.kaleblangley.haikalat.subsystems.render3d.RenderPipeline;
import org.hismeo.haikalathost.api.client.advanced.HaikalatEngineContext;
import org.hismeo.haikalathost.api.client.advanced.HaikalatFrameContext;
import org.hismeo.haikalathost.api.client.advanced.HaikalatReloadContext;
import org.hismeo.haikalathost.api.client.advanced.HaikalatRenderExtension;

public final class ExampleHaikalatExtension
        implements HaikalatRenderExtension {
    private RenderPipeline pipeline;

    @Override
    public void initialize(HaikalatEngineContext context) {
        // 可做不依赖世界的 Render Thread 初始化。
    }

    @Override
    public void render(HaikalatFrameContext frame) {
        if (pipeline == null) {
            pipeline = createPipeline(
                    frame.resources(),
                    frame.target());
            pipeline.build();
        }

        pipeline.render(
                frame.device(),
                frame.camera(),
                frame.target(),
                frame.deltaSeconds());
    }

    @Override
    public void resourcesReloaded(HaikalatReloadContext context) {
        closeOwnedObjects();
        // 下一帧可通过新的 ResourceCatalog generation 懒重建。
    }

    @Override
    public void worldClosed(HaikalatEngineContext context) {
        closeOwnedObjects();
    }

    @Override
    public void close(HaikalatEngineContext context) {
        closeOwnedObjects();
    }

    private void closeOwnedObjects() {
        if (pipeline != null) {
            pipeline.close();
            pipeline = null;
        }
        // 同时关闭你拥有的 Material、Shader、Mesh、Texture 等对象。
    }
}
```

`createPipeline(...)` 的完整真实实现涉及 shader、mesh 和 material。直接参考 [ExampleHaikalatExtension.java](../../HaikalatHostExample/src/main/java/org/hismeo/haikalathostexample/client/ExampleHaikalatExtension.java)；该模板只导入公开 Host API，没有使用 `org.hismeo.haikalathost.internal.*`。

## 5. 理解 frame context

`HaikalatFrameContext` 在每个可渲染世界帧提供：

- `device()`：Host 拥有的 shared `RenderDevice`；
- `camera()`：当前帧的 Minecraft `ExternalCamera`；
- `target()`：当前帧只借用的 Minecraft `PresentationTarget`；
- `resources()`：Minecraft `ResourceManager` 背后的 `ResourceCatalog`；
- `deltaSeconds()`：清洗后的帧间隔；
- `frameIndex()`：Host 单调帧序号；
- `resourceGeneration()`：当前已接受的资源代际。

最重要的限制是：`target()` 只在本次 `render()` 返回前有效。窗口 resize、渲染目标重建或其他渲染集成都可能在下一帧提供新 generation。每帧都把当前 `frame.target()` 传给 Pipeline，不要缓存其中的 FBO 或纹理 ID。

## 6. 资源与跨 namespace

推荐目录：

```text
src/main/resources/
└─ assets/example_mod/
   └─ haikalat/
      ├─ scenes/
      ├─ models/
      ├─ animations/
      ├─ textures/
      ├─ effects/
      └─ ui/
```

扩展自己的 namespace 会自动挂载。如果 Scene 需要读取 `shared_visuals:*` 或 `minecraft:*`，可在同一个客户端订阅类中声明额外 namespace：

```java
@SubscribeEvent
public static void registerContent(RegisterHaikalatContentEvent event) {
    event.namespaces().register("shared_visuals");
}
```

`RegisterHaikalatContentEvent` 中的 Scene、Model、Animation、Effect 和 UI 声明是可选的 CPU 预检与诊断功能，不是 GPU 激活的前置条件。GPU 对象仍由扩展自己创建。

## 7. 资源重载

`F3 + T` 或：

```text
/haikalathost reload
```

会产生新的资源 generation，并在 Render Thread 调用 `resourcesReloaded()`。Host 不会替你重建扩展拥有的 Pipeline 或 Scene。

第一版最简单且安全的策略是：

1. 在 `resourcesReloaded()` 中关闭自己的 GPU 对象；
2. 清空字段；
3. 在下一次 `render()` 中通过新 catalog 懒重建。

如果你需要无闪烁替换，可以自行保留 last-known-good，等新资源完整构建成功后再交换。

## 8. 所有权规则

可以做：

- 在 Host 回调里创建、渲染和关闭自己的 Haikalat 对象；
- 使用每帧 camera、target、device；
- 从 Host catalog 读取 Minecraft 资源；
- 在 reload/worldClosed/close 中释放对象。

禁止：

- 关闭 Host 的 device、runtime 或 catalog；
- 缓存并跨帧使用 `PresentationTarget` 或附件 ID；
- 删除、resize 或改变 Minecraft framebuffer 附件存储；
- 创建第二个窗口、OpenGL Context 或抢占 Context 的渲染线程；
- 调用 swap/present；
- 从工作线程执行 Haikalat GPU 操作；
- 把 Host internal 类当作 API。

扩展抛出异常后会被单独禁用，但这只是保护 Minecraft；它不能替代正确释放已创建资源。

## 9. 诊断

```text
/haikalathost status
/haikalathost extensions
/haikalathost assets
```

`extensions` 会显示每个扩展的状态、成功帧数、最后 target/resource generation 与最后失败。状态为 `FAILED` 时先查看失败阶段和异常摘要。

开发 probe：

```text
/haikalathost embedded_probe true
/haikalathost embedded_probe false
```

probe 只检查 Host 自身的 target/camera/Pipeline 契约；正式内容应始终通过扩展 API 运行。

## 10. 发布前检查

- 订阅类只在 `Dist.CLIENT` 加载；
- JAR 中没有 Host API、Host internal 或 Haikalat class；
- 运行时只存在 Host 提供的一份 Haikalat；
- 每帧使用最新 `frame.target()`；
- reload、worldClosed 与 close 都能重复安全地清理字段；
- `close()` 后不再使用任何 GPU 对象；
- `/haikalathost extensions` 显示 ACTIVE 且 frames 持续增长；
- resize、最小化恢复、`F3 + T`、重进世界、切维度和正常关游均已实测。
