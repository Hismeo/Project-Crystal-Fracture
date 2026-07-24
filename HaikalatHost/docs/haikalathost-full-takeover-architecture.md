# HaikalatHost 全接管渲染与客户端性能架构    private OrderingPolicy orderingPolicy(PassKey pass) {
        if (orderIndependentTransparency && pass == PassKey.ENTITY_TRANSLUCENT) {
            return OrderingPolicy.BATCH;
        }
        return pass.orderingPolicy();
    }

## 1. 文档定位

本文描述 HaikalatHost 的激进演进路线：不再把 Haikalat 当作 Minecraft
`RenderType.draw(MeshData)` 的兼容重放器，而是让 Haikalat 成为 Minecraft 客户端唯一的
OpenGL 渲染后端。

本路线优先级如下：

1. 覆盖 Minecraft 客户端的全部主要画面；
2. 建立单一、可预测的渲染数据模型；
3. 消除 Minecraft 帧内重复的 GL 状态操作和小型上传；
4. 将可见性、排序、批处理和资源生命周期集中到 Haikalat；
5. 在渲染接管稳定后，继续优化 Minecraft 客户端的区块构建、渲染快照、动画和粒子逻辑。

本路线不以模组兼容、第三方 `RenderType`、原版 Shader 行为或旧显卡兼容为约束。开发阶段允许
未知渲染类型直接报错，禁止静默回退原版 GL 路径。画面覆盖优先于像素级复刻，性能优化不得以
随机缺失几何或未定义资源生命周期为代价。

## 2. 最终目标

完成后，Minecraft 负责：

- 游戏状态、世界逻辑、资源包解析和输入；
- 方块模型烘焙、实体动画参数、GUI 业务逻辑；
- 产生与渲染无关的客户端数据。

HaikalatHost 负责：

- 把 Minecraft 数据转换为稳定的 Render Scene；
- 管理所有长期和瞬时 GPU 资源；
- 编译并执行整个帧的 Render Graph；
- 管理 Shader、材质、纹理、Framebuffer 和 pipeline state；
- 完成视锥、遮挡、LOD、排序、实例化和间接绘制；
- 收集 CPU/GPU 诊断数据。

Haikalat 负责：

- OpenGL 资源对象和状态缓存；
- Command Buffer、Render Graph 和同步原语；
- 通用 Mesh、材质、UI、后处理和诊断能力；
- Host 所需的 MDI、persistent mapping、bindless 和 GPU-driven 扩展。

目标状态下，Minecraft 的 `RenderSystem`、`GlStateManager`、`BufferUploader`、
`VertexBuffer` 和 `ShaderInstance` 不再参与正常游戏帧。它们可以继续存在于类路径中，但不能
成为 Haikalat 帧的一部分。

## 3. 基线要求

第一版全接管后端可以设置较高的硬件基线：

- OpenGL 4.6 Core Profile；
- compute shader、SSBO、persistent mapped buffer；
- multi-draw indirect 和 shader draw parameters；
- indirect draw count；
- KHR_debug；
- 推荐支持 `ARB_bindless_texture`，正式 GPU-driven 材质系统可以将其设为硬要求。

启动时必须完成 capability 检查。缺少硬要求时直接阻止进入游戏并给出完整报告，不进入部分接管
或原版回退模式。

## 4. 总体架构

```text
Minecraft Client State
        |
        | immutable extraction / dirty events
        v
Host Extraction Layer
        |
        | RenderSceneDelta + FrameSnapshot
        v
Haikalat Render Scene
        |
        | visibility + material classification + pass building
        v
GPU Scene / Render Graph
        |
        | MDI + compute + a small number of state transitions
        v
OpenGL 4.6
```

架构分为三个平面。

### 4.1 数据提取平面

数据提取平面只能读取 Minecraft 对象，不能调用 GL。它把频繁变化的游戏对象转换为：

- `WorldRenderSnapshot`
- `ChunkMeshUpdate`
- `EntityInstanceUpdate`
- `BlockEntityRenderUpdate`
- `ParticleUpdate`
- `UiDisplayList`
- `CameraSnapshot`
- `LightingSnapshot`

转换结果必须是不可变快照或有明确所有权的 payload。Render Thread 不得在提交途中访问正在由
Game Thread 修改的 Minecraft 集合。

### 4.2 Render Scene 平面

Render Scene 是 Host 自己维护的长期数据库。对象通过稳定 ID 引用，不保存 Minecraft 实例：

```java
record RenderObjectId(int index, int generation) {}

record RenderObject(
        MeshId mesh,
        MaterialId material,
        TransformId transform,
        Bounds bounds,
        int flags
) {}
```

长期对象使用 generation handle，避免 section、实体或资源重载后旧引用重新生效。所有增删改通过
`RenderSceneDelta` 进入 Render Thread，并在帧边界一次性应用。

### 4.3 GPU 执行平面

GPU 执行平面只接收 Haikalat 数据，不理解 `RenderType`、`ShaderInstance` 或
`VertexConsumer`。它负责：

1. 更新 scene buffer；
2. 运行可见性和分类 compute pass；
3. 生成或压缩 indirect command；
4. 按 Render Graph pass 执行 MDI；
5. 完成透明、天气、粒子、UI 和后处理；
6. 插入 frame fence 并发布诊断结果。

## 5. 独占 GL 所有权

FractureLoader 完成窗口和 context 交接后，HaikalatHost 在 Render Thread 创建唯一
`GlRenderDevice`。从该时刻开始：

- 只有 Haikalat 可以创建、绑定或删除游戏帧使用的 GL 对象；
- 禁止在帧中调用 Minecraft 的 `RenderType.setupRenderState()`；
- 禁止调用 `ShaderInstance.apply()`/`clear()`；
- 禁止调用 `BufferUploader.draw*()`；
- 禁止 Minecraft `VertexBuffer.bind()`/`draw()`；
- 禁止在 Host 外直接调用 LWJGL GL API，调试工具和启动交接代码除外；
- 所有 framebuffer、viewport、scissor、blend、depth、cull 和 texture 状态由 Render Graph
  pass descriptor 决定。

当前的双状态缓存问题由此消失：正常帧中只有 Haikalat `StateCache` 是状态事实来源，不再需要在
每个 draw 前后同时 invalid Haikalat 和 Minecraft 缓存。

开发构建应安装 GL 调用审计器。进入游戏帧后，若调用栈来自以下类则立即记录或抛错：

- `com.mojang.blaze3d.platform.GlStateManager`
- `com.mojang.blaze3d.systems.RenderSystem`
- `com.mojang.blaze3d.vertex.BufferUploader`
- `com.mojang.blaze3d.vertex.VertexBuffer`

白名单只能包含窗口 resize、截图读回和明确登记的启动/关闭操作。

## 6. 覆盖优先的迁移策略

全接管不能一开始就要求所有 Minecraft renderer 都重写为 GPU-driven。先建立一个不使用 Minecraft
GL 的“通用捕获层”，再逐类替换上游 CPU 逻辑。

### 6.1 第一层：通用 CPU 几何捕获

保留 Minecraft 的 `BufferBuilder`、模型烘焙和 `VertexConsumer` 生产逻辑，但在
`MultiBufferSource.BufferSource.endBatch(RenderType)` 处：

1. 取得 `MeshData`；
2. 将原版 vertex format 转换为 Host canonical layout；
3. 将 RenderType 转换为 `MaterialKey` 和 `PassKey`；
4. 拷贝到 Host 的 persistent mapped frame arena；
5. 生成 `DrawPacket`；
6. 关闭 `MeshData`；
7. 不调用任何 Minecraft GL API。

这条路径用于尽快覆盖实体、方块实体、手持物品、文本、调试线和暂未专门优化的 renderer。它是
“通用 Haikalat 提交路径”，不是兼容回退路径。

未知 `RenderType` 在开发期输出完整结构并中止该帧；在覆盖矩阵登记之后才能进入正式构建。
禁止使用 `RenderType.toString()` 猜测状态，应通过内置实例、Mixin 暴露的 composite state 和
显式注册表生成稳定 `MaterialKey`。

### 6.2 第二层：专用数据提取器

通用捕获达到画面覆盖后，依次把高成本 renderer 替换成不生成临时 `MeshData` 的专用提取器：

- 区块：长期 Mesh + GPU scene；
- 实体模型：静态 model-part Mesh + instance transform；
- 物品：按 baked model 缓存 Mesh；
- 粒子：GPU instance buffer；
- 文本：Glyph instance；
- GUI：UI display list；
- 云和天气：程序化 Mesh 或 GPU instance。

迁移完成的类型不再经过 `BufferBuilder`。

## 7. 统一 Shader 与材质系统

Minecraft 的每 draw Shader/Uniform 模型必须被替换。建议使用少量稳定 shader family：

- `world_opaque`
- `world_cutout`
- `world_translucent`
- `entity_opaque`
- `entity_cutout`
- `entity_translucent`
- `particle`
- `line`
- `text`
- `ui`
- `sky`
- `cloud`
- `weather`
- `outline`
- `postprocess`

每个 family 通过 feature bits 产生有限 permutation：

```text
TEXTURED
VERTEX_COLOR
LIGHTMAP
OVERLAY
ALPHA_CUTOUT
EMISSIVE
FOG
SKINNED
INSTANCED
DOUBLE_SIDED
```

材质信息进入 SSBO：

```java
record GpuMaterial(
        long baseColorHandle,
        long normalHandle,
        int samplerId,
        int flags,
        float alphaCutoff,
        int tintMode
) {}
```

使用 bindless texture 时，draw 不再逐次绑定 atlas、lightmap、overlay 或实体纹理。暂未启用 bindless
时，也必须按 texture array/atlas page 分组批量提交，不能回到每 draw `glBindTexture`。

全局数据按更新频率拆分：

- Frame UBO：camera、projection、fog、time、screen size；
- World UBO：dimension、sky、weather、global light；
- Material SSBO：长期材质表；
- Draw SSBO：transform、section origin、material index、object flags；
- Light/overlay texture：按脏区域或 frame slot 更新。

## 8. GPU 几何与内存布局

### 8.1 长期区块 Arena

区块不能继续“一 section 一个 VBO”。建立分页式长期 arena：

- 一个或少量大型 vertex buffer；
- 一个或少量大型 index buffer；
- page/buddy allocator；
- allocation generation；
- relocation table；
- fence 保护的延迟回收；
- 后台压缩和碎片整理；
- section 只保存 offset、count、bounds、material range。

第一版可保留 Minecraft `BLOCK` 的等价数据精度，但必须转换为 Host 统一 layout。覆盖稳定后再采用
紧凑格式，例如：

```text
position       3 x signed/unsigned 16-bit, section local
uv             2 x 16-bit or packed atlas coordinate
color          RGBA8
light          2 x unsigned 16-bit
normal         10_10_10_2
material       16/32-bit index
```

压缩格式必须由离线/单元测试验证量化误差，不能在 draw path 临时转换。

### 8.2 瞬时 Frame Arena

实体临时几何、线框、调试绘制和尚未专用化的捕获使用 persistent mapped frame arena：

- 3 至 4 个 frame slot；
- vertex、index、instance、draw-data、indirect-command 分区；
- 帧开始一次检查 fence；
- 禁止帧中 map/unmap；
- 禁止每 draw 创建/删除 buffer；
- overflow 进入独立 overflow page，而不是回退 Minecraft；
- overflow page 在统计中可见，并在 fence 后回收。

### 8.3 静态 Mesh 缓存

以下数据应内容寻址或按资源 generation 缓存：

- baked item model；
- entity `ModelPart`；
- 方块实体静态部分；
- glyph quad/index；
- sky dome、sun、moon；
- cloud tile；
- debug primitive。

缓存 key 必须包含 resource generation。F3+T 后旧 generation 整体失效，不能逐对象扫描
Minecraft 引用。

## 9. 区块接管

### 9.1 CPU 构建

Mixin `SectionRenderDispatcher.uploadSectionLayer` 只是过渡入口。最终流程应变为：

```text
Section dirty
    -> capture immutable neighborhood/light snapshot
    -> Host chunk build scheduler
    -> canonical mesh + material ranges + bounds
    -> ChunkMeshUpdate
    -> Render Thread arena upload
    -> GPU scene handle swap
    -> old handle deferred free
```

不再创建 Minecraft `VertexBuffer`，也不镜像 GPU 数据。

### 9.2 构建调度优化

Host 区块调度器可以优化 Minecraft 客户端逻辑：

- 合并同一 section 的重复 dirty 请求；
- generation cancellation，旧任务不进入上传队列；
- 优先当前视锥内、屏幕占比大、距相机近的 section；
- 限制每帧 snapshot、build 和 upload 时间预算；
- 固定 CPU worker 数，避免 CPU-bound 工作使用无限虚拟线程；
- 邻区快照只捕获构建需要的数据；
- 相邻 section 未准备好时生成明确的 incomplete mask，并在邻区到达时精准重建；
- solid/cutout/translucent 分开生成 material range，不生成多个独立 GL buffer；
- 只在几何或光照实际变化时更新对应 payload。

### 9.3 GPU 可见性

每个 section/object 在 SSBO 中保存：

- AABB；
- vertex/index range；
- material/pass；
- section origin；
- generation/visibility flags。

帧流程：

1. CPU 粗视锥和距离裁剪；
2. depth prepass；
3. 构建 Hi-Z pyramid；
4. compute occlusion culling；
5. 压缩 visible draw commands；
6. 按 pipeline/material page 生成 MDI range；
7. `glMultiDrawElementsIndirectCount` 提交。

保留迟滞和上一帧可见性，防止快速转动相机时一帧错误剔除。相机瞬移、FOV 大变或 resize 时重置
遮挡历史。

## 10. 实体、方块实体和物品

### 10.1 实体模型

不要每帧让每个实体重新输出所有顶点。把实体渲染拆成：

- 静态 `ModelPart` Mesh；
- skeleton/model-part transform；
- entity instance data；
- texture/material ID；
- overlay、hurt、glow 等 flags。

相同模型和材质进入 instanced batch。动画只更新 transform/skin buffer，不更新静态 vertex/index。

### 10.2 客户端实体更新优化

渲染层可以按可见性降低纯视觉更新频率：

- 视锥外且长时间遮挡的实体降低动画采样频率；
- 远距离实体使用更低动画频率和 LOD Mesh；
- nameplate、shadow、附加层独立裁剪；
- skinning 优先放到 compute/vertex shader；
- 只上传发生变化的 transform；
- 不改变游戏 tick、碰撞、声音和网络插值语义。

优化目标是减少客户端视觉工作，不允许因不可见而跳过影响游戏逻辑的实体 tick。

### 10.3 方块实体

按以下顺序迁移：

1. 静态或少量状态模型：缓存 Mesh + instance；
2. 箱子、床、牌子等有限动画模型：共享 Mesh + per-instance data；
3. 文本类：进入统一 glyph batch；
4. 高度动态、特殊 framebuffer 类型：单独 pass；
5. 未分类类型暂走通用 CPU 捕获层，但仍禁止 Minecraft GL。

### 10.4 物品

缓存 baked item model 到 GPU Mesh。GUI、地面实体、第一人称和第三人称只改变 transform、lighting 和
material flags。foil/glint 作为独立 shader feature/pass，不重复上传物品几何。

## 11. 粒子、天气、天空和云

### 11.1 粒子

第一阶段保留 Minecraft 粒子模拟，只把粒子状态写入 persistent instance buffer；一个粒子材质族
使用一次或少量 MDI。

第二阶段将纯视觉粒子迁入 GPU：

- compute 更新 position/velocity/lifetime；
- GPU compact 存活粒子；
- 按 material 分类；
- billboard 在 vertex shader 展开；
- CPU 只提交 spawn command。

需要与游戏交互、碰撞复杂或自定义 renderer 的粒子继续由 CPU 更新，但仍走统一 instance 提交。

### 11.2 天气

雨雪不再按 column 产生大量小批次。CPU 生成相机附近有效 column mask，GPU 根据高度图、天气强度
和时间生成实例。

### 11.3 天空和云

天空、太阳、月亮、星空和云进入独立 Render Graph pass。云数据使用长期 tile/volume 表示，只在
天气或资源改变时重建，不在每帧生成临时 Mesh。

## 12. GUI、文本和加载画面

保留 Minecraft Screen、Widget 和输入逻辑，但替换绘制执行：

- `GuiGraphics` 操作记录为 Host UI display list；
- rect、sprite、nine-slice、item、tooltip、scissor 变成结构化 UI command；
- UI Batcher 按 texture、clip、blend 和 shader variant 合批；
- Minecraft Font 调用转换为 glyph instance；
- 长期目标是直接使用 Haikalat `UiSystem`、`UiDisplayList`、`UiRenderer` 和文本栈；
- 加载画面继续复用 `HaikalatLoadingFrameRenderer`，但正式 runtime 创建后应共享统一 UI pipeline。

UI 使用独立正交 camera 和 framebuffer pass，不允许每个 widget 单独切 blend、scissor 或 texture。
clip rectangle 进入 draw data；可合并的相邻元素不得因 Java 对象边界拆成 draw call。

## 13. Render Graph

建议帧图：

```text
ApplySceneDeltas
        |
DepthPrepass
        |
BuildHiZ
        |
CullAndBuildIndirect
        |
+-------+-------------------+
|                           |
WorldOpaque             Shadow/auxiliary data
|
WorldCutout
|
EntitiesAndBlockEntities
|
SkyCloudWeather
|
WorldTranslucent
|
Particles
|
OutlineAndFirstPerson
|
PostProcess
  |- exposure
  |- bloom
  |- tone mapping
  |- TAA/FXAA
|
UIAndText
|
Present
```

可以直接复用 Haikalat 0.17.2 的：

- `RenderGraph`
- `RenderGraphCompiler`
- `CompiledRenderGraph`
- `Framebuffer`/`RenderTargetManager`
- `BloomPass`
- `ToneMappingPass`
- `TemporalAccumulationPass`
- `FxaaPostProcessor`
- `GpuTimer`
- `FrameProfile`

Minecraft 的主 RenderTarget 在全接管模式下不再是事实所有者。过渡期可以作为 imported external
resource；最终由 Haikalat 创建和 resize，Minecraft 截图、GUI scale 和读取接口通过 Host bridge
访问。

## 14. 透明与顺序语义

不考虑模组兼容不等于可以破坏透明顺序。按域处理：

- opaque/cutout：允许任意 pipeline/material 排序；
- 普通透明：按 view-space depth 进行 CPU 或 GPU 排序；
- 粒子：按 material 后在组内排序，或采用 weighted blended OIT；
- glint/outline/first-person/UI：稳定 pass，不跨域重排；
- debug overlay：固定在 world 或 UI 的明确阶段。

第一版使用稳定 CPU radix sort，排序 key 包含 pass、pipeline、material、depth 和 original sequence。
覆盖完成后再评估 GPU radix sort 或 OIT。

## 15. Render Thread 与快照

线程规则：

- Game Thread：产生 immutable `FrameSnapshot` 和 scene delta；
- Chunk Workers：只处理不可变 chunk snapshot；
- Render Thread：唯一允许调用 Haikalat/GL 的线程；
- GPU：可见性、间接命令、粒子和后处理。

使用双缓冲或三缓冲 mailbox：

```text
Game Thread writes Snapshot N+1
Render Thread consumes Snapshot N
GPU executes Frame N-1
```

快照发布必须是 O(1) 指针交换。Render Thread 不等待 Game Thread；没有新 snapshot 时允许重绘上一份
快照，并单独更新 camera/time。

禁止：

- Render Thread 等待 chunk future；
- chunk worker 持有 Level/Chunk 可变对象；
- 在 command buffer 中保存 `MeshData`、Minecraft collection 或临时 `ByteBuffer`；
- 在 fence 完成前复用 frame slot。

## 16. Haikalat 0.17.2 需要补充的能力

HaikalatHost 当前已经补了部分 indexed/indirect command 编码，但全接管后这些能力应下沉到 Haikalat
通用层：

1. `drawElementsBaseVertex` 原生 command opcode；
2. `drawElementsIndirect` 和 `multiDrawElementsIndirect` 原生 opcode；
3. `multiDrawElementsIndirectCount`；
4. indexed draw 的 `baseInstance`/draw ID 数据约定；
5. 通用 persistent mapped ring/page arena；
6. bindless texture handle、resident 生命周期和材质表；
7. Render Graph imported resource 和 resize generation；
8. async GPU readback；
9. compute culling/scan/compact helper；
10. pipeline permutation cache；
11. 每 pass 的 GL call、primitive、vertex、GPU timer 统计；
12. KHR_debug group 和资源 label 的 Host 命名空间；
13. buffer relocation/copy 与碎片整理；
14. Shader 热重载失败时保留上一 generation。

`CommandBuffer.custom(Runnable)` 只用于原型验证。正式热路径必须使用原生 opcode，否则无法可靠统计、
验证和优化命令流。

## 17. Minecraft 客户端逻辑优化

渲染覆盖完成后，可以继续优化不影响服务端和游戏语义的客户端工作。

### 17.1 可见性驱动的工作预算

- 将上一帧 GPU visibility 回读为低频 hint；
- 延迟不可见 section 的非关键 rebuild；
- 降低不可见实体的纯视觉动画和附加层更新频率；
- 不生成确定不可见的 nameplate、shadow 和 block-entity display data；
- camera teleport 时立即取消延迟策略。

GPU 可见性只能作为调度优先级，不能成为永久不更新资源的依据。

### 17.2 资源重载

- 资源解析、图片解码和 shader preprocessing 放到后台；
- GPU 对象在 Render Thread 分批创建；
- 新 generation 完整后原子切换；
- 未变化资源按内容 hash 复用；
- atlas/material/model 依赖图决定局部失效范围；
- 避免 F3+T 后全部缓存同时重建造成长帧。

### 17.3 分配和集合

- 帧内 command 使用 primitive arena；
- object ID、material ID 和 mesh ID 使用 packed integer；
- 可见列表使用复用的 primitive arrays；
- 排序 key 使用 `long`；
- 诊断热路径使用计数器和 ring event buffer；
- 禁止每 draw record、lambda、字符串和临时集合；
- profile 后再处理低频对象，避免为“零分配”引入复杂所有权错误。

### 17.4 帧率和延迟

- 游戏 tick、snapshot 发布、render submit 和 present 分别计时；
- 支持无新游戏 snapshot 时以最新 camera 重绘；
- frame cap 在提交前后使用独立指标；
- 避免在 Render Thread 执行同步 readback；
- screenshot、debug capture 和 profiling readback 使用 PBO/fence。

## 18. 当前 HaikalatHost 代码的取舍

全接管模式建立后，应删除或停用：

- `MinecraftRenderScope`
- `MinecraftShaderBridge`
- `MinecraftDrawRouter` 的 vanilla fallback
- 每 MeshData 的 `RenderType.setupRenderState()`
- Minecraft/Haikalat 双状态 cache invalidation
- `ChunkMeshManager` 的原版 VBO 镜像
- 每 section 独立 `ChunkMeshHandle`/VAO

可以保留并演进：

- `MinecraftRuntimeLifecycle`
- `MinecraftInteropDiagnostics`
- `MinecraftVertexFormatTranslator`，仅作为迁移输入格式转换器
- `GpuRingAllocator`/`TransientGeometryArena`
- `DrawPacket`、`OrderDomain`、`MinecraftPass`
- indirect command 编码器
- Mixin 启动选择逻辑
- Haikalat 加载画面

全接管模式不再需要 `compatDraws` 和 `chunkMeshes` 两个独立开关。建议使用：

```text
-Dhaikalathost.backend=haikalat
-Dhaikalathost.validation=strict
-Dhaikalathost.gpuDriven=true
-Dhaikalathost.bindless=true
```

开发构建只允许 `haikalat` 或显式 `vanilla` 启动模式，不能在同一帧混用。

## 19. 性能预算

以下是目标预算，不是未经测量的性能承诺：

| 指标 | 第一阶段覆盖目标 | 最终目标 |
| --- | ---: | ---: |
| 世界区块 draw call | 每 layer 每 section | 每主要 pass 1～8 个 MDI |
| 全帧 `glDraw*`/MDI 调用 | 小于 1000 | 小于 64 |
| 帧内 buffer map/unmap | 0 | 0 |
| 帧内 buffer 创建/删除 | 0 | 0 |
| 每 draw texture bind | 允许按 batch | 0（bindless） |
| 每 draw shader apply/clear | 0 | 0 |
| Render Thread submit CPU | 小于 4 ms | 小于 2 ms |
| 热路径 Java 分配 | 可测且受控 | 接近 0 |
| 同步 GPU readback | 0 | 0 |

应固定世界、位置、视距、分辨率、帧率上限和相机轨迹进行 A/B。至少记录：

- Game Thread CPU；
- Render Thread CPU；
- chunk build/upload 时间；
- draw/MDI 数；
- state change 数；
- 上传字节和 overflow；
- visible/culled object 数；
- GPU pass 时间；
- VRAM；
- 1% low 和 0.1% low；
- input-to-present 延迟。

## 20. 覆盖矩阵

每一项必须有截图基线、动态场景和资源重载测试：

- solid/cutout/translucent 区块；
- 流体及排序更新；
- 天空、太阳、月亮、星星、云；
- 雨雪、世界边界和雾；
- 普通、发光、受伤、隐身实体；
- armor、elytra、held item、leash、nameplate；
- block entity；
- dropped item、item frame、first-person hand；
- particles；
- block breaking、outline、selection；
- signs、books、文本和 Unicode；
- HUD、screen、tooltip、inventory、recipe book；
- portal、vignette、boss overlay；
- screenshot、resize、GUI scale；
- F3+T、世界切换、维度切换；
- pause/resume、窗口失焦和关闭。

覆盖矩阵未完成前不开始高风险的顶点压缩和 GPU 粒子模拟，以免同时引入多个画面变量。

## 21. 实施里程碑

### H0：独占后端边界

- 完成 capability 检查；
- 建立唯一 `GlRenderDevice`；
- 加入 Minecraft GL 调用审计；
- Haikalat 创建主 framebuffer；
- 支持 vanilla/haikalat 启动级二选一；
- 禁止帧内混用。

验收：Haikalat 模式进入帧后，除白名单外没有 Minecraft GL 调用。

### H1：通用捕获与基础画面覆盖

- 捕获所有 `MeshData`，不再调用 Minecraft GL；
- 建立 RenderType → material/pass 注册表；
- 完成 world/entity/text/UI 基础 shader family；
- persistent frame arena；
- 统一 frame/world/material/draw 数据。

验收：主要游戏画面可用，未知 RenderType 数量为零。

### H2：长期区块所有权

- 替换 `VertexBuffer` 上传；
- 长期 shared arena；
- section generation 和 deferred free；
- CPU 视锥；
- 按 pass/material MDI；
- 不再保存原版区块 GPU 副本。

验收：区块 draw call 与可见 section 数解耦。

### H3：实体、方块实体和物品缓存

- ModelPart/BakedModel 静态 Mesh；
- entity/block entity instance；
- item 和 glint；
- transform/animation buffer；
- 通用捕获层仅保留少数动态 renderer。

验收：常见实体不再每帧重新上传静态顶点。

### H4：完整画面覆盖

- 粒子、天气、天空、云；
- UI、文本、tooltip、HUD；
- outline、first-person、debug；
- resize、F3+T、世界/维度切换；
- 覆盖矩阵全部通过。

验收：通用捕获层覆盖所有剩余类型，且没有 Minecraft GL 调用。

### H5：Render Graph 与后处理

- depth/Hi-Z；
- opaque/cutout/translucent；
- outline；
- exposure/bloom/tone mapping/TAA 或 FXAA；
- UI/present；
- GPU timer。

验收：所有 pass 资源和状态由 Render Graph 声明。

### H6：GPU-driven

H6 的目标不是把 CPU 已经整理好的 draw list 原样交给 compute shader，而是让 CPU 只发布场景增量、
相机和少量动态数据，由 GPU 完成可见性、分类、命令生成和计数。正常帧中，Render Thread 不再遍历
所有可绘制对象，也不读取可见数量。

进入 H6 前必须满足：

- H4 覆盖矩阵全部通过，不能用 GPU-driven 掩盖缺失的渲染类型；
- H5 的 depth、Hi-Z、主要 world pass 和 GPU timer 已由 Render Graph 管理；
- `RenderObjectId`、Mesh、Material、Transform 和 allocation generation 已稳定；
- CPU MDI 路径仍可作为启动级 A/B 基线，但不能成为帧内或 per-draw fallback；
- capability 检查确认 compute、SSBO、shader draw parameters、indirect count 和所选 bindless 模式可用。

#### H6.1 GPU Scene 数据契约

现有 `GpuDrawData`、`GpuCullingMetadata` 和 `DrawElementsIndirectCommand` 作为第一版 ABI 的起点，
但正式 GPU Scene 应把长期对象、每帧数据和生成结果分开：

| Buffer | 主要内容 | 写入方 | 生命周期 |
| --- | --- | --- | --- |
| `ObjectTable` | bounds、mesh range、material、transform、pass、flags、generation | Scene delta | 长期 |
| `TransformTable` | 当前/上一帧 transform、normal matrix、motion flags | CPU/动画 compute | frame slot |
| `MaterialTable` | bindless handles、sampler、feature bits、alpha cutoff | 资源系统 | resource generation |
| `SourceCommandTable` | index count、first index、base vertex、base instance | Mesh/scene 更新 | 长期或增量 |
| `VisibilityHistory` | 上帧可见、遮挡年龄、强制可见原因 | GPU | 长期，按 object ID |
| `VisibleObjectList` | 本帧通过剔除的 object/draw ID | GPU | frame slot |
| `CompactCommandTable` | 可直接用于 MDI 的紧凑命令 | GPU | frame slot |
| `BatchTable` | pipeline、index type、arena page、输出范围和上限 | CPU 增量/GPU 分类 | frame slot |
| `DrawCountTable` | 每个 batch 的最终 draw count | GPU | frame slot |
| `DiagnosticsTable` | overflow、剔除原因和各阶段计数 | GPU，异步回读 | ring |

`baseInstance` 统一索引 `GpuDrawData`，shader 通过 draw ID/base instance 读取 transform、material 和
object flags。对象 ID 与 arena 物理 offset 解耦；chunk arena 搬迁只更新 Mesh/relocation 表，不能改变
游戏侧对象 ID。

所有表都必须携带明确的 capacity 和 generation。compute shader 在写入前验证逻辑范围；严格模式下
发现越界、失效 generation 或未知 flags 时标记该帧失败，禁止静默丢 draw。

#### H6.2 两阶段可见性管线

当前单个 `GpuCullCompact` 只能在 depth prepass 前完成视锥剔除，无法使用本帧 Hi-Z。正式 H6 将其
拆成两个阶段，避免 `cull -> depth -> Hi-Z -> cull` 的依赖环：

```text
ApplySceneDeltas / ResetCounters
                |
CullFrustumAndHistory
                |
BuildDepthIndirect
                |
DepthPrepass
                |
BuildHiZ
                |
CullOcclusionAndClassify
                |
Count -> PrefixSum -> ScatterIndirect
                |
World/Entity/Particle MDI passes
```

第一阶段只生成保守的 depth candidate：

- 对 section、实体和方块实体执行 sphere/AABB 视锥测试；
- 可选使用上一帧 Hi-Z 剔除稳定遮挡对象，但新建、移动、上一帧可见的对象必须强制进入；
- depth candidate 只覆盖能够可靠写深度的 opaque/cutout 几何；
- near-plane 相交、投影范围异常或 bounds 无效的对象按可见处理。

depth prepass 完成后构建完整 mip chain。Hi-Z reduction 必须与深度约定匹配：普通 Z 使用保守的
最大深度，reverse-Z 使用最小深度。第二阶段把 bounds 投影为屏幕矩形，选择覆盖该矩形的 mip，
使用带 epsilon 的保守深度比较；不确定时保留对象。允许 false positive，不允许稳定复现的 false
negative。

可见性历史至少保存 `visibleLastFrame`、`occludedFrames` 和 `forceVisibleFrames`。以下事件清空遮挡
历史并强制一至数帧可见：

- 相机瞬移、维度切换或世界切换；
- FOV、projection convention、窗口尺寸或 render scale 大幅变化；
- section rebuild、arena relocation、模型 bounds 或 transform generation 改变；
- F3+T 导致 Mesh/Material generation 切换。

GPU 可见性只决定渲染提交。H7 可以异步读取低频摘要作为 CPU 调度 hint，但 H6 不允许同步读回，
也不能据此跳过游戏逻辑更新。

#### H6.3 分类、压缩和 MDI count

CPU 只为 pipeline family、index type、primitive mode 和必须绑定的 arena page 建立稳定 batch。启用
bindless 后，texture/material 不再拆 batch；非 bindless 模式只能按 texture page 批量分组。

命令生成分为三个 compute 步骤：

1. `Count`：对可见对象计算 batch ID，并累计每 batch 数量；
2. `PrefixSum`：计算每个 batch 在 compact command/draw-data buffer 中的输出范围；
3. `Scatter`：写入 `DrawElementsIndirectCommand` 和与之对应的 draw data。

原型阶段可以继续使用当前 `outputBase + atomicAdd(counts[batch])`，但 batch 很大或竞争明显后必须换成
分组计数加 prefix scan，不能依赖全局原子计数扩展到高视距场景。每个输出范围的最大容量必须由
source object count 推导，任何 overflow 都进入 `DiagnosticsTable` 并使严格模式失败。

每个 frame slot 开始时由 GPU 清零 count/scan scratch，之后通过
`glMultiDrawElementsIndirectCount` 直接消费 `CompactCommandTable` 和 `DrawCountTable`。CPU 不映射
count buffer，不根据可见数量重录 command buffer。每个 batch 仍提供 `maxDrawCount` 上限，驱动读取的
count 不能越过已分配范围。

Opaque、cutout、depth 和采用 OIT 的 pass 可以在 batch 内自由 compact。需要稳定顺序的普通透明、
glint、outline、first-person、UI 和 debug pass 不允许由原子 scatter 随机重排：

- 普通透明在 GPU radix sort 完成前继续使用 H5 的稳定 CPU 排序结果，只通过稳定 prefix-scan 压缩
  visibility mask，不能使用原子 scatter 改变相对顺序；
- GPU sort 的 key 必须包含 pass、pipeline、material、view depth 和 original sequence；
- OIT 只能替代明确登记为 order-independent 的粒子/透明材质，不能全局改变透明语义；
- UI 和 first-person 不进入 world GPU culling，继续由各自稳定 pass 批量提交。

#### H6.4 Bindless 材质

H6 最终性能目标以 bindless material 为正式路径。`MaterialTable` 保存 64-bit texture handle、sampler、
feature bits 和参数；draw 只携带 `materialIndex`。生命周期必须遵循：

```text
create texture/sampler
    -> obtain handle
    -> make resident
    -> publish new MaterialTable generation
    -> draw frames
    -> retire generation after fence
    -> make handle non-resident
    -> delete resource
```

资源重载必须先完整创建新 generation，再在帧边界原子切换。旧 handle 在所有引用它的 frame fence
完成前保持 resident。失败的 shader/texture generation 保留上一份可用资源，禁止留下引用已删除纹理
的材质项。

`haikalathost.bindless=false` 只表示在启动时选择 texture-page MDI 路径，用于硬件覆盖和 A/B；运行中
不能因为单个材质失败而切换绑定模型。若发布目标把 bindless 设为硬要求，则 capability 报告必须在
进入世界前终止启动。

#### H6.5 GPU 粒子与透明扩展

GPU 粒子只接管纯视觉、行为可表达的粒子。CPU 通过 persistent spawn ring 写入类型、初始状态、
随机种子和 material ID；GPU 使用固定步长或有上限的 substep 完成：

1. consume spawn commands；
2. 更新 position、velocity、age 和简单碰撞/环境参数；
3. compact alive list；
4. 按 material/pass 分类；
5. 可选计算透明排序 key；
6. 写 instance data 和 indirect draw count。

影响游戏语义、需要复杂世界查询或自定义 CPU renderer 的粒子继续由 CPU 模拟，只把最终 instance
提交到同一个粒子 pass。世界卸载、维度切换和资源重载通过 generation 一次性失效旧 spawn/alive
数据。粒子容量达到上限时使用确定的淘汰策略并记录 dropped spawn，不能写越界或阻塞 Render Thread。

Weighted blended OIT 是独立可选扩展：必须使用单独 accumulation/revealage target，并与普通排序
透明、glint、portal 和 first-person 保持明确的 pass 边界。H6 基线验收不依赖 OIT。

#### H6.6 Chunk arena 碎片整理

碎片整理只解决长期 arena 的外部碎片，不能在每帧无条件搬迁。每个 page 记录 committed、live、free、
largest-free-range 和 fragmentation ratio，仅在分配失败或超过阈值时进入候选队列。

搬迁流程：

1. 在每帧 copy byte/time budget 内为 live allocation 选择新位置；
2. 通过 GPU buffer copy 迁移 vertex/index range；
3. 写入下一 generation 的 relocation/Mesh 表；
4. copy barrier 后在帧边界发布新表；
5. 保留旧 allocation，直到所有引用旧 offset 的 frame fence 完成；
6. 延迟回收旧 range，空 page 才允许销毁。

正在上传、重建或已经排队退休的 allocation 不参与搬迁。同一对象同时发生 rebuild 和 relocation 时，
以更高 generation 的 rebuild 为准，旧 copy 结果直接退休。碎片整理不得修改 vertex/index 内容，也不
允许 CPU readback 后重新上传。

#### H6.7 同步、回退与诊断

GPU-driven 的 buffer 全部按 frame slot 或 generation 管理。CPU persistent write、compute SSBO write、
indirect/count consumption、depth-to-Hi-Z sampling 和 arena copy 的依赖必须作为 Render Graph resource
access 声明，由 Haikalat 统一插入最小 barrier。正式路径禁止散落的 `glMemoryBarrier`、`glFinish` 和
同步 query/readback。

`-Dhaikalathost.gpuDriven=false` 保留 CPU MDI 基线，用于启动级回归定位。GPU-driven 已启用后，
shader 编译失败、buffer overflow、generation 不匹配或 GL error 必须产生结构化故障报告；不能在同一
帧切回 CPU draw，也不能只跳过出错对象继续显示不完整画面。

每帧至少记录：

- source、frustum candidate、occlusion visible 和 rejected object 数；
- 每 pass/batch 的 source、compact 和 submitted draw count；
- Hi-Z build、cull、count、scan、scatter、particle 和 defrag GPU 时间；
- indirect/count/material/particle buffer 高水位和 overflow；
- arena live/free/fragmentation、每帧搬迁字节和退休队列长度；
- history reset、forced-visible 和 invalid-generation 次数；
- 全帧 GL draw/MDI、state change、Render Thread submit CPU 和异步 readback 延迟。

开发验证模式可以抽样把 GPU visibility/command 结果异步回读，与 CPU reference culler 和 source
command 做帧号对齐比较。比较任务不得阻塞当前帧，日志必须能够定位 object ID、batch、bounds、
generation 和剔除阶段。

#### H6.8 交付顺序与验收

按以下顺序交付，上一阶段没有通过画面和性能基线时不进入下一阶段：

1. 固化 GPU Scene/indirect ABI，完成 capacity、generation、overflow 和 buffer 编码测试；
2. GPU 视锥剔除、compact 和 indirect count，画面与 CPU MDI 基线一致；
3. 拆分两阶段可见性，接入本帧 Hi-Z、迟滞和所有 history reset 条件；
4. 接入 bindless material generation 和 resident/deferred-retire 生命周期；
5. 将符合约束的粒子迁入 GPU，普通透明仍保留稳定顺序；
6. 加入异步诊断、GPU/CPU reference 对比和 chunk arena 增量碎片整理；
7. GPU radix sort/OIT 仅在 profile 证明透明排序是瓶颈后单独启用。

H6 完成必须同时满足：

- 覆盖矩阵及固定相机轨迹截图与 H5 基线一致，不存在稳定复现的错误遮挡或顺序回归；
- 相机瞬移、resize、FOV 改变、F3+T、世界/维度切换和 arena relocation 后无缺失几何；
- 正常世界每个主要 pass 为 1～8 个 MDI，全帧 `glDraw*`/MDI 调用小于 64；
- Render Thread submit CPU 小于 2 ms，帧内 buffer map/unmap、创建/删除和同步 GPU readback 均为 0；
- indirect、count、material、particle 和 relocation buffer 在压力场景下无越界、无静默 overflow；
- 固定场景 A/B 中，GPU-driven 相比 CPU MDI 的 Render Thread 时间和 1% low 有可重复改善，且 GPU
  culling/compaction 成本没有抵消减少 draw submission 带来的收益；
- debug/validation 关闭后仍保留低成本计数器，出现故障时可以由 capture 复原该帧的 scene generation、
  batch table 和 indirect buffer。

### H7：客户端逻辑优化

- chunk build 优先级、合并和取消；
- 增量资源重载；
- 可见性驱动的纯视觉更新频率；
- render snapshot/mailbox；
- 分配和集合优化；
- 完整 CPU/GPU/延迟基准。

验收：相同画面设置下，平均帧、1% low、Render Thread 时间和交互延迟均有可重复改善。

## 22. 推荐包结构

```text
org.hismeo.haikalathost.client/
|- backend/
|  |- HaikalatMinecraftBackend
|  |- BackendCapabilities
|  `- MinecraftGlAudit
|- extraction/
|  |- MinecraftFrameExtractor
|  |- RenderSceneDelta
|  |- WorldRenderSnapshot
|  `- RenderTypeMaterialRegistry
|- scene/
|  |- RenderScene
|  |- RenderObjectId
|  |- MeshRegistry
|  |- MaterialRegistry
|  `- TransformRegistry
|- gpu/
|  |- GpuScene
|  |- PersistentFrameArena
|  |- StaticMeshArena
|  |- ChunkMeshArena
|  |- IndirectCommandArena
|  `- DeferredFreeQueue
|- graph/
|  |- MinecraftFrameGraph
|  |- WorldPasses
|  |- TranslucencyPasses
|  |- PostProcessPasses
|  `- UiPasses
|- chunk/
|  |- HostChunkBuildScheduler
|  |- ChunkBuildSnapshot
|  |- ChunkMeshUpdate
|  `- ChunkVisibilitySystem
|- entity/
|  |- EntityModelCache
|  |- EntityInstanceExtractor
|  `- AnimationBuffer
|- particle/
|  |- ParticleInstanceExtractor
|  `- GpuParticleSystem
|- ui/
|  |- MinecraftUiRecorder
|  |- MinecraftGlyphBridge
|  `- MinecraftUiRenderer
|- runtime/
|  |- MinecraftRuntimeLifecycle
|  |- FrameSnapshotMailbox
|  `- ResourceGeneration
`- diagnostics/
   |- MinecraftFrameDiagnostics
   |- GlCallAudit
   `- PerformanceCapture
```

## 23. 关键决策

1. 覆盖阶段允许继续使用 Minecraft CPU tessellation，但绝不允许继续使用 Minecraft GL。
2. 全接管模式没有 per-draw vanilla fallback；失败必须可见且可复现。
3. 先完成覆盖矩阵，再进行大规模顶点压缩、GPU 粒子和 OIT。
4. 区块必须从镜像 VBO 演进为 Host 独占的 shared arena。
5. 真实性能收益来自长期数据、批量状态、MDI 和 GPU 可见性，不来自把同一个 draw call 换成
   Haikalat API 调用。
6. 客户端逻辑优化只能降低纯视觉工作，不能改变服务端同步和游戏逻辑语义。
7. 所有优化必须由固定场景 A/B、GPU capture 和 1% low 数据证明。
