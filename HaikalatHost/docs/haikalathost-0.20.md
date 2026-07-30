# HaikalatHost 0.20.1 薄宿主契约

> 状态：核心实现完成，进入兼容性与发布验证
>
> 基线：Minecraft 1.21.1、NeoForge 21.1.x、Haikalat 0.20.1、OpenGL 4.6 Core
>
> 旧文档：[haikalathost.md](haikalathost.md) 仅作为历史计划保留，不再作为实现依据。

## 1. 最终定位

HaikalatHost 是 Minecraft/NeoForge 与 Haikalat 之间的客户端薄宿主层。它只解决多个模组无法各自正确解决的一组宿主问题：

- 复用 Minecraft 已存在的窗口、OpenGL Context 和 Render Thread；
- 维护唯一的 embedded `HaikalatRuntime` 与 `GlRenderDevice`；
- 把 Minecraft framebuffer、颜色和深度附件转换为只借用的 `PresentationTarget`；
- 把当前世界相机转换为 `ExternalCamera`；
- 在 `AFTER_LEVEL` 调度扩展，并为每次回调单独隔离 GL 状态；
- 把 Minecraft `ResourceManager` 挂接成 `ResourceCatalog`；
- 通知资源 generation、世界关闭和游戏关闭；
- 隔离单个扩展的失败，并公开诊断信息。

HaikalatHost 不再计划内建通用 `SceneHandle`、`VFXHandle`、`VisualHandle`、动画状态机或 GPU Scene 管理器。第三方模组通过高级扩展 API 直接创建普通 Haikalat `Scene`、`RenderPipeline`、VFX 和 UI 对象，并拥有这些对象。

只有多个真实模组反复实现同一段适配代码时，才考虑把那一小段提升为可选帮助类。

## 2. 当前完成情况

| 范围 | 状态 | 契约 |
|---|---|---|
| OpenGL 与运行时 | 已完成 | OpenGL 4.6 Core 能力检测；复用 Minecraft Context；唯一 embedded runtime/device；不创建窗口、不 swap |
| 外部目标 | 已完成 | 导入 Minecraft 非零 FBO、颜色与兼容深度；所有权为 `BORROWED`；resize 后产生新 target generation |
| 外部相机 | 已完成 | 在 `AFTER_SKY` 捕获同帧稳定相机，在 `AFTER_LEVEL` 提供 `ExternalCamera` |
| GL 边界 | 已完成 | 每个扩展回调单独 capture/restore，调用前后 invalidate Haikalat device state |
| 资源桥 | 已完成 | `ResourceManager` → `ResourceCatalog`；资源包覆盖、generation 与 `F3 + T` 通知 |
| 扩展 API | 已完成 | 注册、初始化、逐帧渲染、重载、世界关闭、反向关闭 |
| 故障隔离 | 已完成 | 单个扩展进入 `FAILED` 后停止调度，不中断 Minecraft 或后续扩展 |
| 诊断 | 已完成 | `/haikalathost status`、`assets`、`reload`、`extensions`、`embedded_probe` |
| API 打包 | 已完成 | 同一 Gradle 工程分别导出轻量 API JAR 和合并 Host JAR |
| 示例模组 | 已完成 | 仅使用公开 API 和 Haikalat 类型创建真实 Scene/Pipeline，并绘制到 Minecraft target |

真实客户端已经验证公开扩展绘制成功，并在窗口变化时经历 target generation
1→2→3 后继续运行；退出世界与正常关游也依次触发了 `worldClosed()`、扩展 `close()`
和 Host runtime 关闭。其余发布前人工矩阵见第 11 节。

## 3. 公共 API 分层

API 源码仍位于同一个工程：

```text
HaikalatHost/src/api/java/
└─ org/hismeo/haikalathost/api/
   ├─ runtime/              # GL-free、Haikalat-free 的基础状态 API
   ├─ content/              # 可选资源声明与 CPU 预检
   └─ client/advanced/      # 客户端高级 API，直接引用 Haikalat 类型
```

基础 API 可以由公共代码引用。`api.client.advanced` 只能由 `Dist.CLIENT` 类引用；它不承诺跨 Haikalat 大版本保持二进制兼容。

产物为：

```text
haikalathost-api-<version>.jar
haikalathost-<version>.jar
```

完整 Host JAR 已合入同一套 API。使用方不应把 API 或 Haikalat shadow、jarJar 或 `implementation` 进自己的模组。

## 4. 高级扩展入口

扩展实现：

```java
public interface HaikalatRenderExtension {
    default void initialize(HaikalatEngineContext context) {
    }

    void render(HaikalatFrameContext frame);

    default void resourcesReloaded(HaikalatReloadContext context) {
    }

    default void worldClosed(HaikalatEngineContext context) {
    }

    default void close(HaikalatEngineContext context) {
    }
}
```

逐帧上下文提供：

```java
RenderDevice device();
ResourceCatalog resources();
long resourceGeneration();
ExternalCamera camera();
PresentationTarget target();
float deltaSeconds();
long frameIndex();
```

客户端模组总线注册：

```java
@SubscribeEvent
public static void registerExtensions(RegisterHaikalatExtensionsEvent event) {
    event.register(
            ResourceLocation.fromNamespaceAndPath("example_mod", "main"),
            new ExampleHaikalatExtension());
}
```

注册只声明 ID 和实例，不允许读取资源或创建 GPU 对象。ID 必须使用当前注册模组的 namespace，重复 ID 会立即失败，事件完成后注册表被冻结。

扩展 ID 还会自动让 Host 在共享 `ResourceCatalog` 中挂载该模组的 namespace。跨 namespace 资源仍应通过 `RegisterHaikalatContentEvent.namespaces()` 显式声明。

完整可运行写法见 [HaikalatHostExample](../../HaikalatHostExample/README.md)。

## 5. 调度与生命周期

扩展状态机：

```text
REGISTERED → INITIALIZING → ACTIVE
                         ↘ FAILED
ACTIVE ─────────────────→ FAILED
REGISTERED / ACTIVE / FAILED → CLOSED
```

生命周期顺序：

```text
Host runtime ready
→ initialize()

每个可渲染世界帧
→ render(frame)

Minecraft 接受新的资源 generation
→ resourcesReloaded()

退出世界或替换 ClientLevel
→ worldClosed()

游戏关闭，Context 仍有效
→ 按注册顺序的反方向 close()
```

`initialize()` 每个扩展只尝试一次。`worldClosed()` 不代表扩展永久结束；之后进入新世界时，同一个 ACTIVE 扩展可以继续收到 `render()`。`close()` 对所有尝试过初始化的扩展恰好调用一次，包括已经失败的扩展。

每个回调都在 Minecraft Render Thread、当前 OpenGL Context 和 shared embedded runtime 内串行执行。

## 6. 每帧执行边界

Host 对每个 ACTIVE 扩展分别执行：

```text
捕获约定内的 Minecraft GL 状态
→ invalidate Haikalat device cache
→ embeddedRuntime.execute(extension callback)
→ invalidate Haikalat device cache
→ 恢复 Minecraft GL 状态
```

扩展之间也存在独立边界。一个扩展抛出 `RuntimeException`、`LinkageError` 或 `AssertionError` 时：

- 只把该扩展标记为 `FAILED`；
- 保存失败阶段与摘要；
- 后续帧不再调用它；
- 继续调度其他扩展；
- 不把异常传播进 NeoForge 世界渲染事件。

`VirtualMachineError` 等 JVM 致命错误不会被吞掉。

## 7. 所有权与禁止事项

Host 拥有：

- embedded `HaikalatRuntime`；
- shared `RenderDevice`；
- Minecraft framebuffer、纹理和深度附件；
- 当前帧 `ExternalCamera` 与 `PresentationTarget`；
- Minecraft-backed `ResourceCatalog`；
- GL 状态隔离和生命周期调度。

扩展拥有：

- 自己创建的 `RenderPipeline` 与 `Scene`；
- Mesh、Material、Shader、Texture；
- VFX、UI 及其他 Haikalat 对象；
- 这些对象的重建和关闭责任。

硬规则：

- `PresentationTarget` 及其附件只在当前 `render()` 回调内有效；
- 不缓存、删除、resize 或改变 Minecraft 附件存储；
- 不关闭 Host 的 device、runtime 或 catalog；
- 不创建第二个窗口、Context 或抢占 Context 的渲染线程；
- 不调用 swap/present；
- Haikalat GPU 对象只在 Host 回调中创建、使用和释放；
- 不从其他线程保存 context 后延迟执行 GPU 操作。

## 8. 资源重载

Host 接受新 Minecraft 资源 generation 后，在 Render Thread 调用：

```java
extension.resourcesReloaded(reloadContext);
```

Host 不猜测扩展的资源策略。扩展可以：

- 关闭并在下一帧懒重建；
- 先构建新资源，成功后替换旧版本；
- 保留 last-known-good；
- 忽略与自身无关的重载。

现有 `RegisteredSceneRepository` 只保留为声明式 Scene 的 CPU 预检和诊断功能，不继续发展成 GPU 激活系统。

## 9. 构建与运行依赖

第三方模组编译依赖：

```groovy
dependencies {
    compileOnly("org.hismeo:haikalathost-api:1.0")
    compileOnly("com.github.KLjiana:haikalat:0.20.1") {
        transitive = false
    }
}
```

运行时由 `haikalat_host` 提供唯一一份 Haikalat。使用高级 API 的模组元数据应声明 Host 为客户端强依赖；可选兼容模式则必须把全部 Host/Haikalat 引用隔离在条件加载的客户端 compat 类中。

构建门禁检查：

- API JAR 不携带 Host internal 或 Haikalat 实现类；
- 基础 API 常量池不引用 Haikalat 或 `net.minecraft.client`；
- 完整 Host JAR 包含高级 API 与运行时；
- 示例 JAR 不携带 Host API、Host internal 或 Haikalat 类；
- 示例元数据声明 `haikalat_host` 客户端强依赖。

## 10. 诊断

```text
/haikalathost status
/haikalathost assets
/haikalathost reload
/haikalathost extensions
/haikalathost embedded_probe true|false
```

`extensions` 为每个扩展输出：

```text
id
owner
state
frames
targetGeneration
resourceGeneration
lastFailure
```

embedded probe 继续存在，但只是 Host 的无内容自检，不是第三方渲染入口。

## 11. 验证标准

自动测试覆盖：

- 重复 ID 与越权 namespace 被拒绝；
- freeze 后新旧 registrar 都不能修改；
- 固定注册与回调顺序；
- 单扩展失败不影响后续扩展；
- `close()` 只执行一次且反向执行；
- frame target/camera 不能为 null；
- 生命周期和诊断快照；
- API、Host 与第三方示例的打包边界。

已完成的真实客户端验证：

- 只通过公开高级 API 创建真实 Haikalat Scene/Pipeline；
- 绘制到 Minecraft borrowed framebuffer；
- target generation 经历 1→2→3 后继续渲染；
- 严格 GL 状态校验运行；
- 未启用内部 embedded probe，证明正式入口与 probe 无关。
- 退出世界调用 `worldClosed()`；
- 正常关游在 Render Thread 依次关闭扩展与 Host runtime，日志无关闭错误。

发布前仍应人工回归：

- 最小化后跳过，恢复后继续；
- `F3 + T` 后扩展重建；
- 退出并重新进入世界；
- 切换维度；
- 故意失败的扩展不使 Minecraft 崩溃；
- 多个真实扩展连续执行；
- 使用外部 GL 调试器做最终泄漏审计。

## 12. 非核心的可选方向

以下功能只有出现真实重复需求后才考虑：

- `ManagedRenderPipelineExtension` 帮助类；
- 多模组共享 Pipeline；
- Entity transform 与玩家骨骼同步工具；
- 动画参数工具；
- VFX 方块/实体查询桥；
- HUD、Screen、输入、IME 与剪贴板阶段；
- Fabulous 与 shader mod 兼容；
- 声明式 `SceneHandle`。

这些都不是薄宿主核心的完成条件。

## 13. Haikalat 本体的边界

暂时不预设新的 Haikalat 开发任务。只有发现宿主无关的通用缺陷时才修改 Haikalat，例如：

- 公共 API 无法创建或关闭某个引擎对象；
- 多个 Pipeline 共享一个 `RenderDevice` 有通用问题；
- external target resize/generation 有引擎缺陷；
- 必要能力仍是包私有；
- 外部相机、资源导入或异常恢复存在宿主无关错误。

Haikalat 本体不得出现 Minecraft 类型或 NeoForge 事件。

## 14. 核心完成标准

> 一个第三方模组只通过公开 API 注册扩展，就能使用 Haikalat 0.20.1 创建、渲染和释放自己的内容，并安全运行在 Minecraft 的 framebuffer、相机、资源和生命周期中。

达到这个标准后，HaikalatHost 核心即告完成；剩余工作是兼容性验证和按真实需求增加的小型便利层。
