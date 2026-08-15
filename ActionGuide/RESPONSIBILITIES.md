# ActionGuide 职责与执行任务

> 文档状态：架构草案，用于约束 ActionGuide 第一版实现。  
> 上游编辑器：`D:\project\JavaScript\CombatCue`。  
> 当前编辑器导出格式：Combat Cue schema v3。  
> 目标运行格式：补充 `duration` 后的 schema v4。

## 1. 一句话定义

ActionGuide 是一个**服务端权威、内容无关、渲染无关的动作运行框架**。

它接收“角色想执行某个语义动作”的请求，解析游戏模块提供的动作定义，按 Combat Cue 时间线推进动作，并把事件、状态窗口、动作开始、跳转和结束通知给外部模块。

ActionGuide 决定：

> 角色当前正在执行哪个动作、动作推进到哪里、现在开放了哪些窗口，以及哪些时间线通知应当被触发。

ActionGuide 不决定：

> 剑有多少伤害、玩家有多少体力、动画如何蒙皮、鼠标左键代表什么，或者 UI 应该怎样显示。

---

## 2. 核心设计原则

### 2.1 服务端权威

动作是否开始、是否跳转、是否取消、何时结束，都以服务端 ActionRuntime 为准。

客户端可以进行表现预测，但不能自行确认：

- 动作已经成功发动；
- 攻击已经命中；
- 体力已经扣除；
- Combo 已经成立；
- 冷却已经完成；
- 角色已经获得无敌或霸体。

### 2.2 框架只认识语义 ID

ActionGuide 只认识可扩展的资源 ID，例如：

```text
crystal_fracture:light_attack
crystal_fracture:sword_slash
crystal_fracture:sword_main
crystal_fracture:combo
```

框架不能写死剑、斧、魂魄石、职业或者具体技能。

### 2.3 Gameplay Timeline 与动画执行分离

Combat Cue 描述的是 Gameplay Timeline：

- Section；
- Event；
- State；
- 输入窗口；
- 攻击窗口；
- 取消窗口；
- 表现通知；
- Root Motion 契约。

骨骼采样、混合、IK、Skinning 和最终渲染由 HaikalatHost/Haikalat 执行。

### 2.4 设备输入与动作意图分离

ActionGuide 不认识鼠标左键、右键或手柄按键。

FractureClient 将设备输入转换为语义意图：

```text
鼠标左键短按
    -> crystal_fracture:light_attack
    -> 发送 ActionIntent
```

CrystalFracture 再根据武器、配件和角色状态，把意图解析为具体 Action：

```text
crystal_fracture:light_attack
    -> crystal_fracture:sword_slash_1
```

### 2.5 定义不可变，运行状态可变

从资源包加载出的 `ActionDefinition` 和 `CombatCueDefinition` 必须是不可变对象。

每次动作启动时创建独立的 `ActionInstance`。目标集合、已消费窗口、当前时间、当前 Section 等可变信息只能放在 Instance 中，不能写回 Definition。

---

## 3. 模块边界

### 3.1 ActionGuide 负责

- 定义 Action、Combat Cue、Section、Event、State 等公共概念；
- 读取并验证服务端 `.combat.json`；
- 读取并验证游戏模块提供的 Action Definition；
- 保存动作定义注册表；
- 接收语义化的 Action Intent；
- 调用游戏模块提供的 Action Resolver；
- 执行动作运行时的结构性许可检查；
- 调用游戏模块提供的游戏规则检查；
- 创建、推进、中断和结束 Action Instance；
- 精确处理 Section、Event 和 State 的时间边界；
- 管理权威输入缓冲和每个窗口的一次性消费；
- 管理结构性的 Combo/Cancel 跳转；
- 向外部分发攻击窗口、表现事件和自定义通知；
- 定义中立的网络消息和动作快照；
- 将服务端动作状态同步给相关客户端；
- 提供诊断、日志、调试查询和测试支持；
- 在实体卸载、死亡、换维度等情况下清理运行时。

### 3.2 ActionGuide 不负责

- 武器、配件、技能书、魂魄石和装备内容；
- 伤害公式、暴击、元素反应、属性克制；
- 具体攻击范围、碰撞形状和目标过滤规则；
- 体力、魔力、弹药和游戏冷却规则；
- 键盘、鼠标、手柄的原始输入采集；
- HUD、Screen、准星、Hit Marker；
- 动画资源加载、骨骼采样、动画混合和渲染；
- 声音、VFX 和 Camera Shake 的实际播放；
- Blockbench 文件编辑与导出；
- 任务、剧情、地牢和世界生成；
- 由客户端提供可信的命中、消耗或动作结果。

### 3.3 相邻模块如何协作

| 模块 | 向 ActionGuide 提供 | 从 ActionGuide 获得 |
|---|---|---|
| CrystalFracture | Intent 解析、动作条件、攻击处理、资源消耗 | 动作窗口、事件、开始/结束结果 |
| FractureClient | 语义输入请求、客户端显示桥接 | 动作快照、预测确认/纠正、表现通知 |
| HaikalatHost | 不直接参与 ActionGuide 核心 | 经 FractureClient 转交的动画/VFX 请求 |
| CombatCue 插件 | `.combat.json` 数据与 GLB 中的同源 metadata | 不参与游戏运行时 |
| CrystalLib | 通用 Codec、资源与配置工具 | 不获得 ActionGuide 领域对象 |

---

## 4. 三种“槽位”必须严格区分

Combat Cue 和游戏输入中会出现三种含义不同的槽位，不能合并为一个枚举。

### 4.1 Action Intent

表示玩家想做什么：

```text
crystal_fracture:light_attack
crystal_fracture:heavy_attack
crystal_fracture:special_1
crystal_fracture:special_2
```

`L1/L2/R1/R2` 可以是 CrystalFracture 的默认输入方案，但不是 ActionGuide 的硬编码枚举。

推荐类型：

```java
public record ActionIntentId(ResourceLocation value) {}
```

### 4.2 Attack Slot

Combat Cue 的 Attack State 使用 `payload.slot` 引用游戏模块拥有的攻击定义：

```text
primary
sword_main
shield_bash
projectile_1
```

推荐类型：

```java
public record AttackSlotId(ResourceLocation value) {}
```

### 4.3 Input Window

Combat Cue 的 Input State 使用 `payload.slot` 表示当前窗口接受哪类后续意图：

```text
combo
follow_up
finisher
dodge_cancel
```

推荐类型：

```java
public record InputWindowId(ResourceLocation value) {}
```

即使三者底层都使用 ResourceLocation，也必须使用不同的 Java 类型，防止误传。

---

## 5. ActionGuide 的核心数据模型

## 5.1 CombatCueDefinition

`CombatCueDefinition` 是从 Combat Cue 导出数据加载出的不可变时间线。

建议包含：

```java
public record CombatCueDefinition(
        CombatCueId id,
        int schemaVersion,
        CueTime duration,
        SkeletonBinding skeleton,
        List<CueSection> sections,
        List<CueEvent> events,
        List<CueState> states,
        RootMotionContract rootMotion,
        boolean loop
) {}
```

它负责描述“什么时候发生什么”，不描述动作为什么可以发动。

### 5.1.1 CueSection

```java
public record CueSection(
        SectionId id,
        CueTime start,
        CueTime end
) {}
```

Section 只是动画片段结构，不保存：

- `next`；
- 输入按键；
- 伤害；
- Hitbox；
- Combo 规则；
- 游戏资源消耗。

### 5.1.2 CueEvent

```java
public record CueEvent(
        CueItemId id,
        CueTime time,
        ResourceLocation type,
        int order,
        JsonObject payload
) {}
```

内置事件语义包括：

- `sound`；
- `vfx`；
- `projectile`；
- `camera_shake`；
- `custom`。

ActionGuide 负责按顺序分发事件，不负责播放声音、创建粒子或摇动相机。

### 5.1.3 CueState

```java
public record CueState(
        CueItemId id,
        ResourceLocation type,
        CueTime start,
        CueTime end,
        JsonObject payload
) {}
```

内置 State 语义包括：

- `attack`；
- `input`；
- `invulnerable`；
- `super_armor`；
- `cancel`；
- namespaced custom state。

State ID 必须稳定且在一个 Cue 内唯一。运行时用 ID 区分窗口实例，不能只用 State 类型或 `payload.slot`。

## 5.2 ActionDefinition

Combat Cue 只描述时间线。Action Definition 描述如何把时间线变成一个可执行动作。

建议包含：

```java
public record ActionDefinition(
        ActionId id,
        CombatCueId cue,
        SectionId entrySection,
        ActionChannel channel,
        Set<ActionTag> tags,
        List<ActionTransition> transitions,
        ActionEndPolicy endPolicy
) {}
```

Action Definition 的结构由 ActionGuide 定义，具体内容由 CrystalFracture 或其他游戏模块提供。

它可以表达：

- 使用哪条 Combat Cue；
- 从哪个 Section 开始；
- 动作占用哪个逻辑通道；
- 动作具有哪些通用标签；
- 某个 Section 的某个输入窗口将跳到哪里；
- 到达 Section 或 Cue 末尾后如何结束。

它不能表达武器伤害、魂魄石词条等游戏内容。

## 5.3 ActionInstance

每次启动动作都创建一个新的实例：

```java
public final class ActionInstance {
    ActionInstanceId instanceId;
    ActionDefinition definition;
    CombatCueDefinition cue;
    ActionOwner owner;
    CueTime cursor;
    SectionId currentSection;
    Set<CueItemId> activeStateIds;
    Set<CueItemId> consumedInputStateIds;
    ActionInstanceStatus status;
    long definitionGeneration;
}
```

Instance 至少需要记录：

- 全局或会话内唯一的实例 ID；
- 执行动作的实体；
- 当前 Cue 时间；
- 当前 Section；
- 当前活跃 State；
- 已经消费过的 Input State；
- 当前状态和结束原因；
- 启动时使用的资源定义版本。

攻击窗口的已命中目标集合可以由 CrystalFracture 保存；ActionGuide 只提供稳定的 `instanceId + stateId` 作为窗口实例键。

## 5.4 ActionRuntime

`ActionRuntime` 是每个可执行角色的动作控制器。

第一版建议每个角色只允许一个 Full Body 动作：

```text
IDLE
  -> STARTING
  -> RUNNING
  -> COMPLETED / INTERRUPTED / FAILED
  -> IDLE
```

多通道动作，例如上半身施法与下半身移动并行，不属于第一版；类型中可以预留 `ActionChannel`，但不能提前实现复杂混合。

---

## 6. 时间表示与推进规则

### 6.1 不使用墙上时钟作为权威时间

服务端按逻辑 Tick 推进动作。第一版在每个服务端 Tick 前进 50 ms，不使用 `System.currentTimeMillis()` 或客户端帧时间。

### 6.2 内部建议使用整数微秒

Combat Cue 将时间规范化到小数点后六位。Java 运行时可以在加载时转换为整数微秒：

```java
public record CueTime(long micros) implements Comparable<CueTime> {}
```

这样可以避免不同平台上反复进行浮点比较。

```text
0.200000 秒 -> 200000 微秒
Minecraft 1 Tick -> 50000 微秒
```

插件中的 `EPSILON = 1e-5` 仍用于导入时兼容编辑器浮点结果；进入内部模型后使用规范化整数时间。

### 6.3 区间约定

Section 和 State 必须使用半开区间：

```text
[start, end)
```

含义：

- `time == start` 时已经进入；
- `time == end` 时已经退出；
- 两个窗口可以在同一个时间点无缝衔接；
- Section 边界上的 Event 属于从该边界开始的新 Section。

### 6.4 大步长不能漏通知

一次 Tick 可能跨过一个比 50 ms 更短的 State：

```text
previous = 0.19
current  = 0.25
State    = [0.20, 0.24)
```

运行时必须在同一次推进中依次产生 State Enter 和 State Exit，不能因为 Tick 结束时 State 已不活跃而完全漏掉它。

### 6.5 同一时刻的确定性顺序

必须为同一时间点冻结统一处理顺序。推荐：

1. 退出在该时刻结束的旧 State；
2. 结束旧 Section；
3. 进入从该时刻开始的新 Section；
4. 进入从该时刻开始的新 State；
5. 按 `time -> order -> id` 分发 Event；
6. 执行由该时间点触发的结束或跳转决议。

这样 Event 处理器查询当前状态时，可以看到半开区间语义下真正活跃的新 State。

该顺序一旦由测试冻结，后续不得在没有 schema/runtime 版本升级的情况下改变。

---

## 7. 动作请求执行流程

一次完整请求应按以下流程执行。

### 7.1 接收语义意图

客户端发送：

```java
public record ActionIntentRequest(
        ActionIntentId intent,
        IntentPhase phase,
        long sequence,
        long clientTick
) {}
```

服务端从网络上下文确定玩家身份，不能信任客户端传入的任意实体 ID。

### 7.2 解析具体动作

ActionGuide 调用由游戏模块实现的 Resolver：

```java
public interface ActionResolver {
    Optional<ActionId> resolve(ActionContext context, ActionIntentRequest request);
}
```

CrystalFracture 可以根据以下内容进行解析：

- 当前武器类型；
- 安装的配件；
- 当前 Section；
- 当前 Combo 层数；
- 技能槽；
- 角色姿态。

ActionGuide 不读取武器或背包。

### 7.3 执行结构性检查

ActionGuide 检查：

- Action 和 Cue 是否存在；
- 当前是否已有动作；
- 是否存在开放的 Input/Cancel 窗口；
- 当前输入窗口是否已经消费；
- 目标 Action 是否满足结构性的跳转规则；
- 是否允许打断当前 Action Channel；
- 请求序号是否重复或过期。  

### 7.4 执行游戏规则检查

ActionGuide 调用外部规则接口：

```java
public interface ActionPolicy {
    ActionDecision evaluate(ActionContext context, ActionDefinition action);
}
```

CrystalFracture 在这里检查：

- 体力；
- 冷却；
- 装备；
- 异常状态；
- 弹药；
- 技能解锁；
- 游戏模式限制。

`ActionDecision` 应提供稳定的拒绝原因 ID，供客户端本地化显示：

```text
action_guide:busy
crystal_fracture:not_enough_stamina
crystal_fracture:skill_on_cooldown
```

### 7.5 提交副作用并启动

检查通过后才允许提交游戏副作用，例如扣体力或进入冷却。

推荐将检查与提交拆开，避免“规则检查失败但资源已经扣除”：

```java
public interface ActionAdmission {
    ActionDecision evaluate(ActionContext context, ActionDefinition action);
    void commit(ActionContext context, ActionDefinition action, ActionInstanceId instanceId);
}
```

随后 ActionGuide：

1. 生成 Action Instance ID；
2. 创建不可变定义的运行快照；
3. 进入入口 Section；
4. 激活入口时刻的 State；
5. 触发入口时刻的 Event；
6. 发送 Action Started 通知；
7. 同步客户端。

### 7.6 拒绝请求

拒绝必须返回：

- 请求 sequence；
- 稳定的原因 ID；
- 当前权威动作快照；
- 必要时的服务端 Tick。

客户端使用它撤销错误预测，而不是猜测服务端状态。

---

## 8. ActionRuntime 每 Tick 的任务

每个服务端 Tick，ActionRuntime 按以下顺序工作：

1. 读取当前 Action Instance；
2. 计算本次逻辑时间区间；
3. 找出区间内所有 Section 边界；
4. 找出区间内所有 State 起点和终点；
5. 找出区间内所有 Event；
6. 按冻结的确定性顺序组成 Timeline Operation；
7. 逐个执行 Operation；
8. 尝试消费当前窗口可接受的缓冲输入；
9. 根据 transition 决定继续、跳转或切换 Action；
10. 到达结束点时结束 Instance；
11. 发送必要的网络增量或状态纠正；
12. 更新只读诊断快照。

运行时不能在遍历通知列表时直接修改定义集合。外部处理器提出的中断、跳转或新动作请求，应先形成命令，在当前时间点的通知分发结束后统一提交。

---

## 9. Event 的执行职责

### 9.1 Event 分发

ActionGuide 为每种 Event 类型维护处理器注册表：

```java
public interface CueEventHandler {
    void handle(ActionContext context, ActionInstanceView action, CueEvent event);
}
```

事件处理器由其他模块注册。

### 9.2 内置 Event 的预期归属

| Event | ActionGuide 的任务 | 实际执行模块 |
|---|---|---|
| `sound` | 分发并按需同步 | FractureClient/Minecraft 声音系统 |
| `vfx` | 分发并按需同步 | FractureClient/HaikalatHost |
| `camera_shake` | 只发给应该看到的客户端 | FractureClient |
| `projectile` | 在服务端分发 gameplay event | CrystalFracture |
| namespaced custom | 查找注册处理器 | 注册该类型的内容模块 |

### 9.3 Event 去重

网络重发、客户端预测和状态纠正不能导致同一表现事件播放两次。

客户端可以使用下面的组合键去重：

```text
ActionInstanceId + CueEvent.id
```

循环动作还要加入 loop iteration：

```text
ActionInstanceId + loopIteration + CueEvent.id
```

---

## 10. State 的执行职责

### 10.1 通用生命周期

每个 State 必须支持：

```text
enter
active
exit
```

外部模块通过以下接口监听：

```java
public interface CueStateHandler {
    void onEnter(ActionContext context, ActionInstanceView action, CueState state);
    void onExit(ActionContext context, ActionInstanceView action, CueState state);
}
```

### 10.2 Attack State

ActionGuide：

- 验证 `payload.slot` 存在；
- 以 `ActionInstanceId + StateId` 标识攻击窗口；
- 在窗口起止时通知攻击处理器；
- 保证短窗口不会因 Tick 步长而丢失；
- 在动作中断时补发 Exit/Cleanup。

CrystalFracture：

- 将 Attack Slot 解析为攻击定义；
- 创建碰撞查询；
- 保存该窗口已经命中的目标；
- 计算并应用伤害；
- 处理魂魄石、暴击、击退和元素效果。

### 10.3 Input State

ActionGuide：

- 验证 `payload.slot` 存在；
- 表示一个输入接受窗口；
- 从权威输入缓冲中匹配语义意图；
- 每个 State ID 最多消费一次输入；
- 成功消费后查询 Action Transition；
- 不因玩家持续按住按键而重复触发同一窗口。

### 10.4 Cancel State

ActionGuide 解释结构性的允许目标和窗口范围，但仍需调用 CrystalFracture 的 Action Policy。

Cancel 窗口开放不代表目标动作一定可以执行。体力不足、冷却未完成等游戏规则仍可拒绝取消请求。

### 10.5 Invulnerable 与 Super Armor

ActionGuide 只维护当前窗口是否活跃并分发状态变化。

CrystalFracture 决定：

- 哪类伤害可被无敌阻止；
- 霸体能抵抗哪种硬直；
- 是否仍然受到伤害；
- 多个来源叠加时如何计算。

### 10.6 Custom State

自定义 State 类型必须是 namespaced ID：

```text
crystal_fracture:parry_window
crystal_fracture:execution_lock
```

ActionGuide 保存和分发其 payload，但不理解其游戏含义。

---

## 11. 输入缓冲

### 11.1 缓冲内容

服务端缓冲保存的是语义意图，不是原始设备输入：

```java
public record BufferedIntent(
        ActionIntentId intent,
        IntentPhase phase,
        long sequence,
        long receivedServerTick,
        long expiresAtServerTick
) {}
```

### 11.2 缓冲规则

- 每个玩家的容量必须有限；
- 相同 sequence 不得重复入队；
- 过期输入必须清理；
- 每个 Input State 实例最多消费一个匹配输入；
- 一个输入默认只能被一个窗口消费；
- 消费顺序必须确定；
- 客户端时间只能辅助诊断，不能覆盖服务端接收顺序；
- 服务端应限制请求频率，避免恶意刷包。

### 11.3 Press/Hold/Release

为了支持蓄力、持续防御和松开释放，Intent 应保留输入阶段：

```text
PRESS
HOLD
RELEASE
```

第一版可以只实现 `PRESS`，但协议和类型不应把未来扩展堵死。

---

## 12. Section 与动作跳转

### 12.1 Section 本身不携带跳转规则

Combat Cue 插件只导出：

```text
Section = id + start + end
```

跳转规则属于 Action Definition，例如：

```java
public record ActionTransition(
        SectionId fromSection,
        InputWindowId window,
        ActionIntentId acceptedIntent,
        ActionTarget target
) {}
```

`ActionTarget` 可以是：

- 当前 Cue 的另一个 Section；
- 另一个 Action；
- 正常结束；
- 回到 Idle。

### 12.2 连续进入与直接进入

必须区分：

```text
CONTINUOUS
DIRECT
```

`CONTINUOUS`：目标 Section 与当前 Section 边界连续，时间线自然向前推进。

`DIRECT`：运行时把 cursor 跳到目标 Section 的 start：

- 退出当前所有不再活跃的 State；
- 进入目标 Section；
- 激活目标起点 State；
- 显式触发目标 `section.start` 上的 Event；
- 重建 Root Motion 基线；
- 防止同一个入口事件在同一跳转中重复触发。

### 12.3 Action 切换

切换到另一个 Action 时必须：

1. 以明确原因结束旧 Instance；
2. 清理旧 Instance 所有活跃 State；
3. 创建新的 Instance ID；
4. 重新执行目标 Action 的许可检查；
5. 只在检查成功后提交新动作成本；
6. 同步旧动作结束和新动作开始。

---

## 13. Combat Cue 资源加载

### 13.1 服务端只读取 Sidecar

权威 Gameplay Timeline 必须来自数据资源中的 `.combat.json`。

专用服务端不能为了获得攻击窗口而解析客户端 `.anim.glb`，也不能依赖 Haikalat。

推荐资源位置：

```text
data/<namespace>/action_guide/combat_cues/<path>.combat.json
data/<namespace>/action_guide/actions/<path>.json
```

对应资源 ID：

```text
<namespace>:<path>
```

客户端动画可以位于：

```text
assets/<namespace>/animations/<path>.anim.glb
```

GLB 中 `animations[i].extras.combatcue` 与 Sidecar 应由同一次导出生成，但 Gameplay 只以服务端 Sidecar 为权威。

### 13.2 schema v3 的缺口

当前 Combat Cue schema v3 没有显式导出动画总时长。

不能使用“最后一个 Event/State/Section 的时间”推断动作结束，因为动画可能包含没有通知的 Recovery 尾段。

因此进入正式 ActionRuntime 前，应将插件格式升级为 schema v4，并加入：

```json
{
  "schema_version": 4,
  "duration": 0.8
}
```

ActionGuide 内部模型从第一版就把 `duration` 设计为必填字段。

对 v3 的兼容仅用于迁移或诊断；缺少权威 duration 的资源不能直接进入生产运行时。

### 13.3 加载验证

资源重载时至少验证：

- schema version 是否支持；
- duration 是否大于零；
- Section ID 是否非空且唯一；
- Section 是否为合法 `[start, end)`；
- Section 是否重叠；
- Event/State ID 是否唯一；
- Event 顺序是否可确定；
- State 是否为合法 `[start, end)`；
- 所有时间是否位于 duration 内；
- Attack/Input State 是否包含 slot；
- Custom type 是否 namespaced；
- Root Motion mode 是否有效；
- Action 引用的 Cue 和入口 Section 是否存在；
- Transition 引用的 Section、窗口和目标是否存在。

Section gap 以及跨 Section 的 Attack/Input State 可以作为警告，不必直接阻止加载。

### 13.4 原子发布

资源重载应当采用：

```text
prepare -> validate -> build immutable registry -> atomically publish
```

加载失败不能把运行中的注册表留在半更新状态。

正在执行的 Action Instance 保留启动时的定义快照；新动作使用新一代注册表，避免热重载把进行中的动作改到一半。

---

## 14. Root Motion 职责

schema v3 只导出 Root Motion 配置：

- enabled；
- bone；
- mode。

真实位移曲线仍在 GLB 骨骼动画内。专用服务端无法只靠当前 Sidecar 计算权威位移。

因此第一版：

- ActionGuide 读取并暴露 `RootMotionContract`；
- HaikalatHost 可以据此执行客户端视觉 Root Motion；
- ActionGuide 不把客户端骨骼采样结果当作权威实体位移；
- CrystalFracture 不根据客户端回传结果移动实体。

未来如果需要服务端权威 Root Motion，Combat Cue 导出器必须额外输出可由服务端读取的位移曲线或确定性运动轨迹。届时由 ActionGuide 定义 `RootMotionProvider` 接口，具体采样数据仍不能依赖 Haikalat 类。

---

## 15. 网络同步

### 15.1 C2S

第一版需要：

```text
ActionIntentC2S
```

只传语义 Intent、阶段和序号。客户端不能直接声明：

- “我已经命中”；
- “我应该造成 30 点伤害”；
- “我的动作已经进入 Active”；
- “我现在无敌”；
- “请直接启动这个未解析的服务端 Action”。

### 15.2 S2C

建议至少具有：

```text
ActionStartedS2C
ActionTransitionedS2C
ActionStoppedS2C
ActionRejectedS2C
ActionSnapshotS2C
ActionPresentationEventS2C
```

快照至少包含：

- actor entity ID；
- Action Instance ID；
- Action ID；
- Cue ID；
- 当前 Section；
- 权威 cursor；
- 服务端 Tick；
-状态版本或 sequence；
- 停止原因（如适用）。

### 15.3 同步对象

- 本地玩家需要确认、拒绝和纠正；
- 追踪该实体的其他玩家需要动作开始、跳转和结束；
- 新开始追踪的客户端需要完整快照；
- 离开追踪范围后应清理客户端镜像。

### 15.4 预测边界

FractureClient 可以预测开始动画和按键反馈。

ActionGuide 必须提供足够信息让客户端：

- 确认预测；
- 拒绝并回滚；
- 将时间游标平滑校正到服务端；
- 根据 Instance ID 忽略过期消息；
- 避免重复播放 Event。

第一版可以暂不实现复杂预测，但协议必须携带 Instance ID、sequence 和服务端时间。

---

## 16. 动画与表现桥接

推荐链路：

```text
服务端 ActionRuntime
    -> ActionStarted/ActionSnapshot
    -> FractureClient PresentationBridge
    -> HaikalatHost API
    -> Haikalat Animator
```

ActionGuide 可以发布中立的表现通知：

```java
public sealed interface ActionPresentationCommand {
    record StartAnimation(...) implements ActionPresentationCommand {}
    record StopAnimation(...) implements ActionPresentationCommand {}
    record CueEventCommand(...) implements ActionPresentationCommand {}
}
```

但 ActionGuide 源码中不得出现：

```text
HaikalatAnimationPlayer
HaikalatFrameContext
AnimationClip
SkinMatrix
Minecraft Screen
Camera 实现类
```

真正的适配器放在 FractureClient，并通过 HaikalatHost 的公开 API 执行。

---

## 17. 生命周期和清理

ActionGuide 需要为可执行实体维护 Runtime Store。

必须处理：

- 实体加入世界；
- 实体卸载；
- 实体死亡；
- 玩家退出；
- 玩家换维度；
- 世界关闭；
- 数据包重载；
- 服务端停止。

动作被异常终止时必须：

1. 退出所有活跃 State；
2. 通知外部模块清理窗口资源；
3. 清除输入消费状态；
4. 发送停止原因；
5. 从 Runtime Store 移除或回到 Idle。

第一版不持久化进行中的动作。服务器重启、实体重新加载或玩家重新登录后统一回到 Idle。

---

## 18. 扩展 API

ActionGuide 第一版至少应提供以下扩展点。

### 18.1 ActionResolver

把语义 Intent 解析为 Action ID。

### 18.2 ActionAdmission / ActionPolicy

进行游戏规则检查，并在动作真正启动时提交成本。

### 18.3 CueEventHandler

处理指定类型的点事件。

### 18.4 CueStateHandler

处理指定类型 State 的 Enter/Exit。

### 18.5 ActionLifecycleListener

监听动作开始、跳转、中断、完成和失败。

### 18.6 ActionRuntimeView

向其他模块提供只读查询：

```java
public interface ActionRuntimeView {
    Optional<ActionInstanceView> currentAction();
    boolean isStateActive(ResourceLocation stateType);
    boolean isInSection(SectionId section);
    CueTime cursor();
}
```

外部模块不能通过 View 直接修改运行状态。

### 18.7 ActionCommand API

服务器逻辑需要中断或强制动作时，应提交明确命令：

```text
RequestStart
RequestInterrupt
RequestTransition
RequestStop
```

命令在安全的运行时边界统一执行，避免监听器重入修改集合。

---

## 19. 错误处理与诊断

### 19.1 内容错误

无效 JSON、重复 ID、非法时间区间和缺失引用应在资源重载阶段报告完整资源路径。

错误信息至少包含：

- ResourceLocation；
- schema version；
- JSON 字段路径；
- 错误类型；
- 是否阻止加载。

### 19.2 运行错误

外部处理器抛出异常时：

- 记录 Action Instance、Actor、Cue Item 和处理器类型；
- 保证 Runtime Store 不处于半更新状态；
- 对关键 gameplay handler 失败可安全中止动作；
- 对纯表现 handler 失败不应破坏服务端动作推进。

### 19.3 调试查询

开发环境应能够查看：

```text
actor
instance_id
action_id
cue_id
cursor
section
active_states
buffered_intents
consumed_input_states
definition_generation
```

可以后续提供只读调试命令，但调试命令不能成为正常游戏逻辑依赖。

---

## 20. 推荐包结构

```text
org.hismeo.actionguide
├─ api
│  ├─ action
│  │  ├─ ActionId
│  │  ├─ ActionDefinition
│  │  ├─ ActionIntentId
│  │  ├─ ActionResolver
│  │  ├─ ActionAdmission
│  │  └─ ActionDecision
│  ├─ cue
│  │  ├─ CombatCueId
│  │  ├─ CombatCueDefinition
│  │  ├─ CueSection
│  │  ├─ CueEvent
│  │  ├─ CueState
│  │  ├─ CueTime
│  │  └─ RootMotionContract
│  ├─ runtime
│  │  ├─ ActionInstanceId
│  │  ├─ ActionInstanceView
│  │  ├─ ActionRuntimeView
│  │  └─ ActionStopReason
│  └─ event
│     ├─ CueEventHandler
│     ├─ CueStateHandler
│     └─ ActionLifecycleListener
├─ internal
│  ├─ definition
│  │  ├─ CombatCueLoader
│  │  ├─ ActionDefinitionLoader
│  │  ├─ DefinitionValidator
│  │  └─ ActionRegistry
│  ├─ runtime
│  │  ├─ ActionRuntime
│  │  ├─ ActionInstance
│  │  ├─ TimelineStepper
│  │  ├─ TimelineOperation
│  │  ├─ InputBuffer
│  │  └─ ActionRuntimeStore
│  ├─ network
│  │  ├─ payload
│  │  ├─ ServerIntentHandler
│  │  └─ ActionSynchronizer
│  └─ diagnostics
│     ├─ ActionDiagnostics
│     └─ ActionDebugCommand
└─ ActionGuide
```

`internal` 包不作为其他模块的编译依赖。CrystalFracture 和 FractureClient 只能使用 `api` 包。

---

## 21. 测试契约

ActionGuide 必须先通过纯 Java 单元测试，再接入 NeoForge。

### 21.1 从 Combat Cue 插件移植的测试

- 同时刻 Event 保持 `time -> order -> id` 顺序；
- 时间规范化到六位小数；
- State 使用 `[start, end)`；
- 大步长能够发现短 State；
- 循环跨尾部时 Event 顺序稳定；
- Section 边界归入后一个 Section；
- 直接进入 Section 时触发入口 Event；
- 每个 Input State ID 只能消费一次；
- 每个 Attack State ID 是独立窗口；
- 非法区间与重复 ID 被拒绝；
- Attack/Input 缺少 slot 被拒绝；
- Section 重叠被拒绝，共享边界合法；
- Section gap 只警告；
- 自定义类型必须 namespaced。

### 21.2 Runtime 测试

- 从 Idle 正确启动 Action；
- 首帧激活起点 State 和 Event；
- Tick 推进顺序确定；
- 正常到达 duration 后结束；
- 动作中断会退出全部 State；
- 拒绝动作不会提交体力等成本；
- 接受动作只提交一次成本；
- 过期和重复 sequence 被忽略；
- 缓冲输入只被一个窗口消费；
- Section 跳转不会重复触发边界事件；
- Action 切换生成新的 Instance ID；
- 热重载不改变正在执行的定义快照；
- Handler 重入请求延迟到安全点执行。

### 21.3 资源兼容测试

Combat Cue 仓库应提供固定的 golden fixture：

```text
test/fixtures/sword_slash.combat.json
```

同一份 fixture 同时被：

- JavaScript 插件测试验证；
- ActionGuide Java Loader 测试读取；
- schema 迁移测试读取。

这样可以及时发现编辑器导出和 Java 运行时的契约漂移。

### 21.4 专用服务端测试

需要验证 ActionGuide 的 common/server 代码不会加载：

- `net.minecraft.client.*`；
- Haikalat 类型；
- OpenGL/LWJGL 渲染类型；
- FractureClient 类型。

---

## 22. 分阶段实施任务

## 阶段 0：冻结 Combat Cue 运行契约

- [ ] 在 Combat Cue metadata 中加入 `duration`；
- [ ] schema version 从 3 升级为 4；
- [ ] 更新插件迁移器和校验器；
- [ ] GLB metadata 与 Sidecar 同时输出 duration；
- [ ] 建立至少一个 golden `.combat.json` fixture；
- [ ] 冻结同一时刻的 State/Section/Event 执行顺序；
- [ ] 记录 schema v3 到 v4 的迁移规则。

完成标准：ActionGuide 不需要解析 GLB，也能准确知道动作总长度和全部 Gameplay Timeline。

## 阶段 1：纯 Java 数据模型与 Loader

- [ ] 实现所有强类型 ID；
- [ ] 实现 `CueTime`；
- [ ] 实现不可变 Combat Cue 数据模型；
- [ ] 实现 Action Definition 数据模型；
- [ ] 实现 JSON Codec；
- [ ] 实现 schema version 检查；
- [ ] 实现完整验证和诊断；
- [ ] 读取 golden fixture；
- [ ] 移植插件中的数据契约测试。

完成标准：给定 `.combat.json`，能够得到经过验证、排序和规范化的不可变 Definition。

## 阶段 2：纯 Java Timeline Runtime

- [ ] 实现 `ActionInstance`；
- [ ] 实现 `TimelineStepper`；
- [ ] 实现 Section Enter/Exit；
- [ ] 实现 Event Crossing；
- [ ] 实现 State Enter/Exit；
- [ ] 实现短窗口穿越；
- [ ] 实现动作完成与中断清理；
- [ ] 实现 Timeline Operation 延迟提交；
- [ ] 完成确定性单元测试。

完成标准：不启动 Minecraft，也能模拟 `sword_slash` 从开始推进到结束，并得到完全确定的通知序列。

## 阶段 3：Resolver、规则检查和输入缓冲

- [ ] 实现 `ActionResolver` 注册；
- [ ] 实现 `ActionAdmission` 的 evaluate/commit 两阶段；
- [ ] 实现服务端语义输入缓冲；
- [ ] 实现 sequence 去重；
- [ ] 实现 Input State 一次性消费；
- [ ] 实现结构性 Cancel；
- [ ] 实现 Section/Action Transition；
- [ ] 实现稳定拒绝原因。

完成标准：输入一个语义 Intent，能够经过 Resolver 和 Policy 启动、缓冲、跳转或拒绝动作。

## 阶段 4：NeoForge 服务端接入

- [ ] 注册数据资源 Reload Listener；
- [ ] 构建原子 Action/Cue Registry；
- [ ] 为实体建立 Runtime Store；
- [ ] 接入 Server Tick；
- [ ] 处理实体卸载、死亡、换维度和玩家退出；
- [ ] 提供只读查询 API；
- [ ] 提供开发诊断日志；
- [ ] 验证专用服务端可加载。

完成标准：服务端可以通过命令或测试入口启动动作，并在日志中看到正确的 Section/Event/State/End 序列。

## 阶段 5：网络同步

- [ ] 定义 C2S Action Intent；
- [ ] 定义 S2C Start/Stop/Reject/Snapshot；
- [ ] 由服务端验证发送者和请求频率；
- [ ] 同步给本地玩家和实体追踪者；
- [ ] 新追踪者接收完整快照；
- [ ] 客户端使用 Instance ID 丢弃过期消息；
- [ ] 为表现 Event 提供去重键；
- [ ] 完成高延迟和乱序测试。

完成标准：两个客户端看到同一服务端角色处于同一动作和近似一致的时间位置。

## 阶段 6：CrystalFracture 最小纵切

- [ ] 定义一个测试 Intent；
- [ ] 定义一个测试 Weapon Moveset；
- [ ] 将 Intent 解析为 `sword_slash`；
- [ ] 实现一个简单体力 Policy；
- [ ] 注册 Attack State Handler；
- [ ] 用 Attack Slot 找到简单攻击定义；
- [ ] 服务端完成一次目标查询和伤害；
- [ ] 验证同一 Attack State 不重复命中同一目标。

完成标准：玩家输入一次轻攻击，服务端正确启动动作，并只在 Combat Cue Attack State 内造成一次权威伤害。

## 阶段 7：FractureClient 与 HaikalatHost 表现接入

- [ ] 将鼠标/键盘映射为语义 Intent；
- [ ] 接收 Action Snapshot；
- [ ] 建立 Presentation Bridge；
- [ ] 将 Action/Cue ID 映射为动画资源；
- [ ] 通过 HaikalatHost API 播放和停止动画；
- [ ] 分发表现型 Sound/VFX/Camera Shake Event；
- [ ] 实现事件去重；
- [ ] 实现基本时间校正；
- [ ] 验证专用服务端仍不依赖任何客户端类。

完成标准：服务端权威动作、客户端动画和攻击窗口使用同一条时间线，且拒绝请求时客户端能够停止错误表现。

## 阶段 8：后续能力

以下内容不属于第一版验收范围：

- 蓄力与 Hold/Release；
- 多 Action Channel；
- 复杂 Combo 图；
- Guard/Parry；
- Hit Stop；
- 客户端高级预测和回滚；
- 服务端权威 Root Motion；
- 动画 Layer、Mask 和 Montage；
- 动态播放速率；
- 时间缩放；
- 动作持久化；
- 跨模组 Action Definition 覆盖规则。

这些能力应当在第一条纵切稳定后逐项加入，每项都必须明确服务端和客户端的权威边界。

---

## 23. 第一版明确不做什么

为了避免 ActionGuide 在第一阶段膨胀，第一版不实现：

- 完整武器系统；
- 完整伤害系统；
- 完整技能和冷却系统；
- 完整 Combo 编辑器；
- Root Motion 权威移动；
- 通用动画引擎抽象；
- UI 框架；
- 任意数量的并行动作层；
- 高级客户端回滚；
- 从 GLB 读取服务端 Gameplay Timeline。

第一版只需要证明一条完整链路：

```text
加载 Combat Cue Sidecar
    -> 接收语义 Intent
    -> Resolve Action
    -> 服务端启动 Action Instance
    -> 按 Tick 推进 Section/Event/State
    -> Attack State 通知 CrystalFracture
    -> 同步客户端
    -> FractureClient 请求 HaikalatHost 播放动画
    -> 动作正确结束
```

---

## 24. ActionGuide 第一版完成定义

同时满足以下条件，才能认为 ActionGuide v1 完成：

- Combat Cue schema 具有权威 duration；
- Java Loader 与插件导出格式有共享 fixture 测试；
- 时间线处理符合 `[start, end)`；
- 大 Tick 不会漏掉短 State；
- 同时刻通知顺序完全确定；
- Input State 每实例只消费一次；
- 服务端是动作状态唯一权威；
- ActionGuide 不依赖 CrystalFracture；
- ActionGuide 不依赖 FractureClient；
- ActionGuide 不依赖 HaikalatHost 或 Haikalat；
- 专用服务端可以安全加载；
- CrystalFracture 可以通过公开 API 注册 Resolver、Policy 和 Handler；
- FractureClient 可以通过公开快照驱动表现；
- 一个测试剑击可以从输入完整运行到伤害和动画结束；
- 所有异常结束路径都会清理活跃 State；
- 资源热重载不会破坏正在运行的 Instance。

满足这些要求之后，ActionGuide 才真正是一个可复用的动作框架，而不是 CrystalFracture 的战斗代码仓库。
