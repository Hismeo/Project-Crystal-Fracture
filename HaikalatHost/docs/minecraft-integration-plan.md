# Haikalat 与 Minecraft 1.21.1 对接规划

## 1. 目标

HaikalatHost 的第一目标不是立即优化 Minecraft 的完整渲染器，而是建立一条可验证、可回退的兼容链路：

> 让 Minecraft 生成的一份 `MeshData` 在不改变画面的前提下，由 Haikalat 完成几何上传、VAO 配置和 draw call。

链路正确后，再逐步接管区块长期网格、瞬时几何分配、状态排序和批量间接提交。第一阶段不追求性能提升，也不替换 Minecraft 的 Shader、纹理系统、Camera、Framebuffer 或窗口交换流程。

## 2. 模块职责

### FractureLoader

- 创建早期窗口和 OpenGL 4.6 context；
- 使用 Haikalat 绘制启动阶段画面；
- 将窗口和 context 交给 Minecraft；
- 交接后不再驱动游戏帧，不向 Minecraft 侧暴露早期 `GlRenderDevice` 单例。

### HaikalatHost

- 作为常规 NeoForge 游戏层模组运行；
- 在 Minecraft Render Thread 上创建独立的 `MinecraftHaikalatRuntime`；
- 截获受支持的 Minecraft 绘制调用；
- 管理 Minecraft/Haikalat 状态边界、几何上传、回退策略和诊断；
- 隔离所有 client-only 类型，避免专用服务器类加载失败。

### Haikalat

- 不依赖 Minecraft 或 NeoForge 类型；
- 提供通用 buffer、vertex layout、command buffer、资源生命周期和后续 arena 能力；
- Minecraft 专用翻译和策略全部留在 HaikalatHost。

## 3. 第一阶段架构：兼容重放通道

第一阶段让 Minecraft 继续充当渲染状态和 Shader 的权威，Haikalat 只接管几何相关工作。

```text
MultiBufferSource.BufferSource.endBatch(RenderType)
        -> RenderType.draw(MeshData)
        -> HaikalatHost 路由
            |-> 不支持：原版 BufferUploader
            `-> 支持：
                RenderType.setupRenderState()
                Minecraft ShaderInstance 设置默认 uniform 和 sampler
                Haikalat 上传 VBO/IBO、配置 VAO、发出 draw
                ShaderInstance.clear()
                RenderType.clearRenderState()
                清理边界状态并关闭 MeshData
```

兼容通道中 Haikalat 可以修改：

- 自己拥有的 VBO、IBO 和 VAO；
- 与当前 `MeshData` 对应的 vertex attribute；
- draw call。

兼容通道中 Haikalat 暂不修改：

- Shader program；
- texture/sampler；
- read/draw framebuffer；
- viewport/scissor；
- blend、depth、cull、color mask；
- Minecraft 的 projection、model-view 和 texture matrix。

Minecraft 的 `RenderSystem`/`GlStateManager` 也维护状态缓存。只用原生 GL 查询和恢复状态，无法同步 Minecraft 的 Java 缓存，可能导致后续原版绘制跳过必要的状态提交。

## 4. 接入点

### 4.1 即时 MeshData

首个接入点选择 `RenderType.draw(MeshData)`，通过 cancellable Mixin 在方法入口路由。该入口同时持有 `RenderType`、`MeshData` 以及当前 RenderSystem 中的矩阵与全局 Shader 参数。

路由接口应把“判定”和“消费”分开，避免部分上传失败后错误回退：

```java
public enum DrawRoute {
    HAIKALAT_COMPAT,
    VANILLA
}

public final class MinecraftHaikalatRuntime implements AutoCloseable {
    public DrawRoute route(RenderType renderType, MeshData.DrawState drawState);

    /** 调用后由 runtime 消费并关闭 meshData。 */
    public void drawOwned(RenderType renderType, MeshData meshData);

    public void invalidateResources();

    @Override
    public void close();
}
```

所有权约束：

- `VANILLA`：不得修改或关闭 `MeshData`，由原方法继续处理；
- `HAIKALAT_COMPAT`：Mixin 取消原方法，runtime 必须在 `finally` 中关闭 `MeshData`；
- 一旦进入 `drawOwned`，发生异常时不得再调用原版绘制，否则可能重复绘制或访问已释放内存；
- 异常应记录诊断、关闭后续 Haikalat 路由并向上传播。

### 4.2 区块网格是独立链路

世界区块不透明层不会在每一帧经过 `RenderType.draw(MeshData)`。其主要流程是：

```text
SectionCompiler 生成 MeshData
        -> SectionRenderDispatcher.uploadSectionLayer
        -> Minecraft 长期 VertexBuffer
        -> LevelRenderer.renderSectionLayer 直接绘制 VertexBuffer
```

因此第一里程碑只能证明即时 `MeshData` 的兼容重放，不能把“全部世界 solid 方块由 Haikalat 绘制”列为验收条件。

区块接管应作为后续单独里程碑：在上传边界把 `MeshData` 转换为 `ChunkMeshHandle`，并在 `renderSectionLayer` 使用 Haikalat 的长期 GPU 存储绘制。

## 5. MeshData 几何模型

`MeshData.indexBuffer()` 允许为 `null`。没有显式索引时，Minecraft 会使用共享顺序索引，因此 HaikalatHost 需要显式表达两种情况：

```java
public sealed interface MinecraftIndexPayload {
    record Explicit(ByteBuffer data, int indexCount, int glType)
            implements MinecraftIndexPayload {}

    record Sequential(
            VertexFormat.Mode mode,
            int vertexCount,
            int indexCount,
            int glType
    ) implements MinecraftIndexPayload {}
}
```

顺序索引生成规则必须与 Minecraft 一致：

- `QUADS`：`0, 1, 2, 2, 3, 0`；
- `LINES`：`0, 1, 2, 3, 2, 1`；
- 其他模式：连续顺序索引。

第一阶段必须在截获方法内同步上传和绘制。`MeshData` 的 ByteBuffer 由 `ByteBufferBuilder.Result` 管理，关闭后不可继续使用。以后如需延迟队列，必须深拷贝 payload 或引入明确的 buffer lease/ownership，不能直接保存原始 ByteBuffer。

## 6. 顶点格式翻译

第一批支持 Minecraft 1.21.1 的常见元素：

- `POSITION`；
- `COLOR`；
- `UV0`；
- `UV1`/Overlay；
- `UV2`/Lightmap；
- `NORMAL`；
- padding、任意合法 stride 和 offset。

必须保留 Minecraft 原始交错内存布局，不在第一版重排或重新打包顶点。

### Haikalat 前置修改

Haikalat 0.17.2 的 `VertexLayout.apply()` 当前统一调用 `glVertexAttribPointer`，不能正确表达 Minecraft 的整数 UV 输入。需要在 Haikalat 通用层增加：

```java
public enum VertexInputClass {
    FLOATING,
    INTEGER,
    DOUBLE
}
```

对应调用：

```text
FLOATING -> glVertexAttribPointer
INTEGER  -> glVertexAttribIPointer
DOUBLE   -> glVertexAttribLPointer
```

Minecraft 映射规则：

- `POSITION`：floating，非 normalized；
- `COLOR`：floating，normalized；
- 浮点 `UV`：floating，非 normalized；
- short/int `UV1`、`UV2`：integer；
- `NORMAL`：floating，normalized。

未知或 NeoForge 扩展的 vertex element 在开发模式中记录完整格式，在兼容模式中回退原版。

## 7. Shader 与 RenderType 策略

第一阶段不实现完整 `MinecraftRenderTypeTranslator`。兼容通道调用原版：

1. `renderType.setupRenderState()`；
2. 取得 `RenderSystem.getShader()`；
3. 使用当前 model-view、projection、窗口尺寸和 RenderSystem 全局参数设置默认 uniform；
4. 调用 `ShaderInstance.apply()`；
5. Haikalat 绘制；
6. 调用 `ShaderInstance.clear()`；
7. 调用 `renderType.clearRenderState()`。

后续原生通道再为已知内置 RenderType 建立显式注册表：

```text
RenderType 实例/工厂
        -> MinecraftPipelineDescriptor
        -> Haikalat pipeline/resource bindings
```

不要依赖 `RenderType.toString()` 或名称猜测状态。未知和第三方 RenderType 永远允许回退兼容通道或原版路径。

## 8. GL 状态边界

每次进入 Haikalat 命令执行前调用现有的：

```java
glRenderDevice.invalidateState();
```

`GlContextStateEpoch` 不能感知 Minecraft 任意修改的 program、VAO、texture 或 pipeline state，因此必须手动失效。

每次兼容绘制结束至少执行：

- `ShaderInstance.clear()`；
- `RenderType.clearRenderState()`；
- `BufferUploader.invalidate()`；
- 将 VAO 解绑或绑定到明确的宿主安全状态；
- 再次标记 Haikalat state cache 失效。

第一版不得在 Haikalat 命令中绑定默认 FBO。实际目标可能是主 RenderTarget、translucent target、item entity target 或 outline target，目标由 `RenderType.setupRenderState()` 决定。

## 9. 生命周期与线程

- `MinecraftHaikalatRuntime` 只能在 Render Thread 初始化、使用和关闭；
- 不使用 Haikalat `GlRenderThread`；
- 不创建 `GlfwWindow`、独立 Camera 或 `FrameDriver.present()`；
- 不调用 `glfwSwapBuffers()`；
- 资源重载开始时停止接收新绘制，重建依赖 Minecraft GL ID 的缓存；
- F3+T 后不能继续引用旧 Shader、纹理或 framebuffer；
- 客户端关闭或 context 释放前关闭所有 HaikalatHost 拥有的 GL 资源；
- 世界切换只释放世界级资源，runtime 级顺序索引缓存可在 context 存活期间保留。

## 10. 建议包结构

```text
org.hismeo.haikalathost/
|- client/
|  |- runtime/
|  |  |- MinecraftHaikalatRuntime
|  |  `- MinecraftRuntimeLifecycle
|  |- intercept/
|  |  `- RenderTypeMixin
|  |- compat/
|  |  |- MinecraftRenderScope
|  |  `- MinecraftShaderBridge
|  |- geometry/
|  |  |- MinecraftGeometryUploader
|  |  |- MinecraftVertexFormatTranslator
|  |  |- MinecraftIndexPayload
|  |  `- SequentialIndexCache
|  |- routing/
|  |  `- MinecraftDrawRouter
|  `- diagnostics/
|     `- MinecraftInteropDiagnostics
`- HaikalatHost
```

只有 `client` 包可以引用 `com.mojang.blaze3d` 和 `net.minecraft.client` 类型。

## 11. 诊断与回退

至少记录以下计数：

- 捕获的 `MeshData` 数量；
- Haikalat 成功绘制数量；
- 原版回退数量和按原因分类的回退数量；
- 上传的 vertex/index 字节数；
- 生成的顺序索引数量；
- 按 RenderType 和 VertexFormat 聚合的首次未知项；
- GL、Shader 和资源生命周期异常。

回退原因至少包括：

```text
DISABLED
UNSUPPORTED_RENDER_TYPE
UNSUPPORTED_VERTEX_ELEMENT
UNSUPPORTED_VERTEX_MODE
MISSING_SHADER
RUNTIME_NOT_READY
RESOURCE_RELOAD_IN_PROGRESS
```

开发模式对未知格式输出完整结构；正常模式对同一结构只记录一次，避免刷屏。

## 12. 里程碑与验收

### M0：工程与运行时边界

- 将 `HaikalatHost` 加入根 `settings.gradle`；
- 明确发布包中 Haikalat 的唯一运行时来源；
- 明确 HaikalatHost 对 FractureLoader 的运行时依赖和加载顺序；
- 加入 client-only Mixin 配置；
- 在 Render Thread 创建和关闭独立 runtime；
- 专用服务器启动时不加载任何 Minecraft client 类。

当前 `compileOnly` 指向 FractureLoader 内的 Haikalat jar，只解决编译问题。正式发布前必须验证 FractureLoader 内嵌的 Haikalat 类在 GAME 层对 HaikalatHost 可见，并避免同一层出现重复 Haikalat 包。若类加载边界不稳定，应选择固定方案：共享运行时库，或对早期窗口使用的 Haikalat 副本做 relocation。

### M1：第一份即时 MeshData

- 只允许一个受控 RenderType/VertexFormat；
- Haikalat 上传 vertex/index buffer 并执行 draw；
- Minecraft Shader、矩阵、纹理和 FBO 保持原版；
- 不支持的调用无副作用地回退；
- 连续多帧不出现错误 Shader、VAO 或纹理。

建议测试顺序：

1. `POSITION_COLOR`；
2. `POSITION_TEX`；
3. `NEW_ENTITY`；
4. `BLOCK`。

### M2：常见即时 RenderType 覆盖

- 覆盖显式索引和顺序索引；
- 覆盖实体 solid/cutout、文本、粒子和基础 translucent；
- Lightmap、Overlay、normal、混合和输出目标正确；
- resize 与 F3+T 正常；
- 形成按 RenderType/格式统计的兼容矩阵。

### M3：区块长期网格

- 截获 `SectionRenderDispatcher.uploadSectionLayer`；
- 引入 `ChunkMeshHandle`、generation 和 deferred free；
- 在 `LevelRenderer.renderSectionLayer` 使用 Haikalat 绘制可见 section；
- 首先仅接管 `solid`，随后是 `cutoutMipped` 和 `cutout`；
- translucent 保留原版，直到排序和索引更新链路验证完成。

### M4：瞬时几何 Arena

- 抽象通用 `GpuRingAllocator`；
- persistent-mapped vertex/index ring；
- frame slots 与 fence protection；
- 明确 overflow 行为；
- 用 Arena 替代每个 MeshData 独立创建 buffer。

### M5：安全排序与批量提交

- 引入 pass、epoch、order domain 和 original sequence；
- 只优化 opaque/cutout 等明确可重排域；
- translucent、glint、第一人称和 UI 保持稳定顺序；
- 扩展 indexed base vertex、indirect 和 MDI 命令。

## 13. 第一阶段暂不做

在 M1/M2 完成前不开始：

- GPU culling 或 Hi-Z occlusion；
- OIT 或 GPU 透明排序；
- Bindless Texture；
- 全面替换 Minecraft Shader；
- PBR 方块；
- 完整 RenderGraph 接管；
- 区块压缩顶点格式；
- 实体 GPU skinning；
- DrawPacket 跨调用排序或批处理。

这些能力会同时引入新的画面变量和生命周期问题，使基础桥接错误难以定位。

## 14. 推荐提交顺序

1. Haikalat 整数顶点输入支持与测试；
2. HaikalatHost client runtime、生命周期和 Mixin 壳；
3. `POSITION_COLOR` 即时 MeshData 兼容重放；
4. 显式/顺序索引与常见顶点格式；
5. Shader/RenderType scope、状态边界和错误处理；
6. F3+T、resize、关闭流程与诊断；
7. 常见即时 RenderType 覆盖；
8. 区块长期网格接管；
9. TransientGeometryArena；
10. 安全排序与 MDI。

每一个提交都必须保持全局开关可关闭，并保证关闭后完整走原版绘制路径。
