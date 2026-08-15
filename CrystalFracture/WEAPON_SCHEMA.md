# WeaponSchema 职责与数据契约

> 文档状态：架构草案，用于冻结 WeaponSchema v1 的实现边界。  
> 上游资产规范：`D:\project\JavaScript\WeaponPart\v2.md`（Crystal Fracture Weapon Part — Blockbench 使用规范 V2）。  
> 所属模块：`CrystalFracture`。 
> 上游工具：`D:\project\JavaScript\WeaponPart` 中的 Blockbench Weapon Part 编辑器。

## 1. 一句话定义

WeaponSchema 是一个**服务端可验证、与渲染实现无关的武器装配拓扑定义**。

它决定：

> 一类武器包含哪些 Part Slot、每个 Slot 接受哪种 WeaponPart Type、哪个 Slot 是根，以及各 Slot 的哪两个 Connect 按什么父子方向连接。

它不决定：

> Blockbench 模型如何制作、具体武器实例选择了哪一件 Part、攻击动作怎样播放、伤害是多少，或者 glTF 最终怎样渲染。

---

## 2. 必须区分的四个对象

### 2.1 WeaponPartDefinition

Blockbench 插件构建出的单个配件资产描述。

```json
{
  "id": "crystal_fracture:steel_blade",
  "type": "crystal_fracture:sword_blade",
  "visual": {
    "model": "crystal_fracture:weapon/parts/steel_blade.glb"
  },
  "connects": {
    "guard": "guard_connect"
  },
  "markers": {
    "tip": "blade_tip",
    "base": "blade_base",
    "trail_start": "trail_start",
    "trail_end": "trail_end"
  }
}
```

其中：

- `id` 是具体配件资产 ID；
- `type` 是配件满足的类型契约；
- `connects` 将语义 Connect Name 绑定到 glTF Locator/Node Name；
- `markers` 将语义 Marker Name 绑定到 glTF Locator/Node Name；
- `visual.model` 仅描述客户端视觉资源。

WeaponPartDefinition 不保存它最后会接到哪个 Part，也不保存 parent/child。

### 2.2 WeaponPartTypeDefinition

WeaponPart Type 是一个稳定的接口契约，不是 Java 枚举。

例如：

```text
crystal_fracture:sword_grip
crystal_fracture:sword_guard
crystal_fracture:sword_blade
```

类型定义至少描述该类型保证提供的语义端口：

```json
{
  "required_connects": ["guard"],
  "required_markers": ["main_hand_grip"]
}
```

类型 ID 必须使用 `ResourceLocation`。第一版只做**精确类型匹配**，不实现继承、子类型、标签表达式或模糊兼容。

类型契约有两个用途：

1. Blockbench 插件可以验证一个 Part 是否满足所选 Part Type；
2. 游戏侧可以在还没有选择具体 Part 前验证 WeaponSchema 的 Slot 和连接端口。

具体 Part 可以声明类型契约之外的额外 Connect 或 Marker；Schema 只有显式引用后才会使用它们。

### 2.3 WeaponSchemaDefinition

WeaponSchema 是武器类别的装配蓝图。

它保存：

- Slot；
- 每个 Slot 的 Part Type 约束；
- Root Slot；
- 有方向的 Connection；
- 对外暴露的 Marker 别名。

它不保存具体 Part ID。

例如 `crystal_fracture:standard_sword` 可以要求：

```text
grip       -> crystal_fracture:sword_grip
crossguard -> crystal_fracture:sword_guard
blade      -> crystal_fracture:sword_blade
```

### 2.4 WeaponAssembly

WeaponAssembly 表示一把具体组装结果：

```json
{
  "schema": "crystal_fracture:standard_sword",
  "parts": {
    "grip": "crystal_fracture:oak_grip",
    "crossguard": "crystal_fracture:iron_crossguard",
    "blade": "crystal_fracture:steel_blade"
  }
}
```

同一个 WeaponSchema 可以产生许多不同 WeaponAssembly。随机掉落、锻造、存档和网络同步保存的是 Assembly，而不是复制一份 Schema。

---

## 3. 模块边界

### 3.1 WeaponSchema 子系统负责

- 定义 WeaponPart Type、WeaponPart、WeaponSchema 和 WeaponAssembly 的公共数据模型；
- 从数据资源加载并验证 Type、Part 和 Schema；
- 检查 Assembly 中的具体 Part 是否满足 Slot Type；
- 检查 Connection 引用的语义 Connect 是否存在；
- 检查拓扑是否构成一棵以 Root Slot 为根的确定性树；
- 冻结 parent/child 方向；
- 将 Schema 和 Assembly 编译成不可变的 `CompiledWeaponAssembly`；
- 提供 Slot、Part、Connection 和导出 Marker 的只读查询；
- 定义稳定的存档和网络表示；
- 提供完整诊断信息和原子资源热重载。

### 3.2 WeaponSchema 子系统不负责

- 修改或导出 `.bbmodel`；
- 从 Locator 距离、名称或位置自动猜测连接关系；
- 骨骼动画、蒙皮、IK 和 glTF 渲染；
- 玩家手部挂点的最终矩阵；
- 武器属性加总、伤害、暴击和元素反应；
- ActionGuide Action、Combat Cue、连招和输入窗口；
- 攻击 Hitbox 的权威判定；
- VFX、Trail、Sound 和 Camera Shake 的实际执行；
- 背包、锻造台、掉落和稀有度规则；
- 客户端自行决定权威 Assembly。

### 3.3 相邻模块协作

| 模块 | 向 WeaponSchema 提供 | 从 WeaponSchema 获得 |
|---|---|---|
| Blockbench Weapon Part 插件 | Part JSON、GLB、Connect/Marker 绑定 | Part Type 契约或可导入类型清单 |
| CrystalFracture 内容逻辑 | Schema 数据、具体 Assembly、属性/攻击规则 | 经验证的 Slot、Part 和拓扑查询 |
| FractureClient | 客户端资源与持有者表现上下文 | Compiled Assembly、模型 ID、导出 Marker |
| HaikalatHost | 通用 glTF Scene/Node/Transform API | 由 FractureClient 提交的模型实例和矩阵 |
| ActionGuide | Attack Slot、动作状态和表现通知 | 不直接依赖 WeaponSchema；由 CrystalFracture 进行映射 |

---

## 4. WeaponSchema v1 JSON

推荐资源位置：

```text
data/<namespace>/crystal_fracture/weapon_schemas/<path>.json
```

文件路径决定 Schema ID：

```text
data/crystal_fracture/crystal_fracture/weapon_schemas/standard_sword.json
-> crystal_fracture:standard_sword
```

规范示例：

```json
{
  "schema_version": 1,
  "root": "grip",
  "slots": {
    "grip": {
      "part_type": "crystal_fracture:sword_grip"
    },
    "crossguard": {
      "part_type": "crystal_fracture:sword_guard"
    },
    "blade": {
      "part_type": "crystal_fracture:sword_blade"
    }
  },
  "connections": [
    {
      "parent": {
        "slot": "grip",
        "connect": "guard"
      },
      "child": {
        "slot": "crossguard",
        "connect": "grip"
      }
    },
    {
      "parent": {
        "slot": "crossguard",
        "connect": "blade"
      },
      "child": {
        "slot": "blade",
        "connect": "guard"
      }
    }
  ],
  "markers": {
    "main_hand_grip": {
      "slot": "grip",
      "marker": "main_hand_grip"
    },
    "blade_tip": {
      "slot": "blade",
      "marker": "tip"
    },
    "blade_base": {
      "slot": "blade",
      "marker": "base"
    },
    "trail_start": {
      "slot": "blade",
      "marker": "trail_start"
    },
    "trail_end": {
      "slot": "blade",
      "marker": "trail_end"
    }
  }
}
```

顶层不重复保存 `id`。Schema ID 始终从资源路径得到，避免文件路径和 JSON 内部 ID 漂移。

v1 对未知 JSON 字段采用严格拒绝策略。新增字段必须通过显式 schema 版本升级引入，Loader 不静默忽略或猜测字段含义。

---

## 5. 字段语义

### 5.1 `schema_version`

- 必填整数；
- v1 只接受 `1`；
- 不认识的高版本必须拒绝加载；
- 兼容迁移必须显式进行，不能静默猜测字段含义。

### 5.2 `root`

- 必填；
- 必须引用 `slots` 中存在的 Slot；
- Root Slot 没有入边；
- Root Slot 的局部坐标就是整个武器 Assembly 的根坐标。

Root Part 的 Blockbench Group Origin 定义其 Part Local Origin。WeaponSchema 不再创建额外 Canonical Mount。

### 5.3 `slots`

Slot 是 Schema 内部稳定的**实例键**，不是 Part ID，也不是 Part Type。

```text
grip
crossguard
blade
```

Slot 名只在当前 Schema 内有效，使用小写路径风格：

```text
[a-z0-9._/-]+
```

每个 Slot v1 必须包含恰好一个 `part_type`。v1 所有 Slot 都是必需的，不实现 Optional Slot；需要不同拓扑时定义另一个 Schema。

同一种 Part Type 可以在一个 Schema 中出现多次，例如：

```text
left_blade  -> double_blade
right_blade -> double_blade
```

因此运行时绝不能使用 Part Type 作为实例身份。

### 5.4 `connections`

每条 Connection 都必须显式声明：

- `parent.slot`；
- `parent.connect`；
- `child.slot`；
- `child.connect`。

Endpoint 中的 `connect` 引用的是 WeaponPart JSON 的**语义 Connect Name**，不是 glTF Locator Name。

例如：

```json
"connects": {
  "guard": "guard_connect"
}
```

Schema 必须写 `guard`，不能写 `guard_connect`。

Connect 在 Part 资产层没有 parent/child 角色。只有它被 WeaponSchema 的某条 Connection 使用时，Schema 才赋予本次装配的方向。

### 5.5 `markers`

顶层 `markers` 将某个 Part Marker 暴露为整把武器的稳定 API。

例如：

```text
Schema marker "blade_tip"
-> Slot "blade"
-> Part marker "tip"
-> GLB node "blade_tip"
```

下游系统只查询 Schema Marker 名，不应硬编码 Slot 名、具体 Part ID 或 Locator Name。

Marker 不参与拓扑，也不能出现在 `connections` 中。

---

## 6. 为什么 Schema 与 Assembly 必须分开

错误设计：

```json
{
  "root": "crystal_fracture:oak_grip",
  "blade": "crystal_fracture:steel_blade"
}
```

这个对象把“武器类别的结构”和“一次具体配件选择”混在一起，会导致：

- 每种材质组合都要复制拓扑；
- Schema 无法先验证类型；
- 存档和网络需要重复传递静态关系；
- 替换一个 Part 时容易破坏连接；
- 资源热重载无法稳定复用结构。

正确关系：

```text
WeaponSchema
    定义 Slot + Type + Topology

WeaponAssembly
    定义 Schema ID + Slot -> Part ID

CompiledWeaponAssembly
    Schema + Assembly + Part Registry 的验证结果
```

---

## 7. 核心 Java 数据模型

建议所有 Definition 使用不可变 `record`，所有集合在构造时防御性复制。

```java
public record WeaponSchemaId(ResourceLocation value) {}

public record WeaponPartId(ResourceLocation value) {}

public record WeaponPartTypeId(ResourceLocation value) {}

public record WeaponSlotId(String value) {}

public record ConnectName(String value) {}

public record MarkerName(String value) {}
```

这些类型即使底层都是字符串，也不能合并，防止把 Locator Name、Slot、Connect 和 Marker 互相误传。

```java
public record WeaponSchemaDefinition(
        WeaponSchemaId id,
        int schemaVersion,
        WeaponSlotId root,
        Map<WeaponSlotId, WeaponSlotDefinition> slots,
        List<WeaponConnectionDefinition> connections,
        Map<MarkerName, WeaponMarkerExport> markers
) {}

public record WeaponSlotDefinition(
        WeaponPartTypeId partType
) {}

public record WeaponConnectEndpoint(
        WeaponSlotId slot,
        ConnectName connect
) {}

public record WeaponConnectionDefinition(
        WeaponConnectEndpoint parent,
        WeaponConnectEndpoint child
) {}

public record WeaponMarkerExport(
        WeaponSlotId slot,
        MarkerName marker
) {}
```

具体装配：

```java
public record WeaponAssembly(
        WeaponSchemaId schema,
        Map<WeaponSlotId, WeaponPartId> parts
) {}
```

编译结果至少提供：

```java
public interface CompiledWeaponAssembly {
    WeaponSchemaId schemaId();
    WeaponSlotId root();
    Map<WeaponSlotId, WeaponPartDefinition> parts();
    List<CompiledWeaponConnection> parentFirstConnections();
    Optional<ResolvedMarker> marker(MarkerName name);
}
```

外部模块不能修改 Definition 或编译结果。

---

## 8. 三层验证

### 8.1 WeaponPart Type 验证

- Type ID 合法且唯一；
- Required Connect 名合法且唯一；
- Required Marker 名合法且唯一；
- 同一个语义名不能同时被当成 Connect 和 Marker；
- 类型定义不能依赖客户端渲染类。

### 8.2 WeaponPart 验证

- Part ID 合法且与资源 ID 一致；
- Part Type 存在；
- `visual.model` 是合法 ResourceLocation；
- Connect/Marker 语义名合法且各自唯一；
- Locator/Node Name 非空；
- 同一个 Locator 不能同时绑定多个不允许的语义；
- Part 满足其 Type 的全部 Required Connect；
- Part 满足其 Type 的全部 Required Marker。

Blockbench 插件做编辑期验证，游戏 Loader 仍必须重复做可信边界验证。

### 8.3 WeaponSchema 验证

- Schema version 受支持；
- 至少有一个 Slot；
- Slot 名合法且唯一；
- Root Slot 存在；
- 每个 Slot 的 Part Type 存在；
- 每个 Connection 的两个 Slot 都存在；
- Connection 不能连接同一个 Slot；
- Endpoint 引用的 Connect 存在于对应 Part Type 契约；
- 一个 Connect Endpoint 最多使用一次；
- Root 入度为 0；
- 每个非 Root Slot 入度恰好为 1；
- 图中不存在环；
- 所有 Slot 都可以从 Root 到达；
- parent-first 遍历顺序可确定；
- Marker Export 名合法且唯一；
- Marker Export 引用的 Slot 和 Type Marker 存在。

v1 只接受一棵完整有根树。多根、环、共享子节点、悬空 Slot 和运行时可选边全部拒绝。

### 8.4 WeaponAssembly 验证

- Schema 存在；
- 每个 Schema Slot 恰好提供一个 Part；
- 不允许未知的额外 Slot；
- 每个 Part 存在；
- `Part.type == Slot.part_type`；
- Schema 使用的 Connect 在具体 Part 上存在；
- Schema 导出的 Marker 在具体 Part 上存在；
- 同一 Part ID 可以重复使用，但每个 Slot 仍是独立实例。

任一验证失败都不能产生半有效的 Compiled Assembly。

---

## 9. 确定性与排序规则

JSON Object 的原始字段顺序不能成为运行逻辑。

冻结以下规则：

1. Root 永远是第一个 Slot；
2. 子节点按 `WeaponSlotId` 字典序遍历；
3. Connection 编译为稳定的 parent-first 顺序；
4. Marker 按导出名排序；
5. 诊断按 `resource -> field path -> error code` 排序；
6. 网络和存档编码 Slot Map 时按 Slot ID 排序。

相同输入必须在客户端、服务端和测试环境产生相同编译结果与内容哈希。

---

## 10. 运行时 Transform 契约

对一条 Connection：

```text
parent.slot / parent.connect
    ->
child.slot / child.connect
```

运行时关系固定为：

```text
ChildWorld
= ParentWorld
× ParentConnectLocal
× inverse(ChildConnectLocal)
```

其中：

- `ParentWorld` 是父 Part Local Origin 到世界的矩阵；
- `ParentConnectLocal` 是父 Connect 相对父 Part Local Origin 的矩阵；
- `ChildConnectLocal` 是子 Connect 相对子 Part Local Origin 的矩阵；
- `ChildWorld` 是子 Part Local Origin 到世界的矩阵。

Root 的世界矩阵由装备表现层提供。WeaponSchema 不认识 Minecraft 玩家骨骼。

若通过 `main_hand_grip` 把整把武器挂到角色手部：

```text
WeaponRootWorld
= HandSocketWorld
× inverse(MainHandGripInWeaponRoot)
```

导出 Marker 的世界矩阵：

```text
MarkerWorld
= PartWorld(slot)
× PartMarkerLocal
```

### 10.1 Transform 数据来源

v2 WeaponPart JSON 只把语义名映射到 glTF Locator/Node Name，真正 Position/Rotation 位于 GLB Node Transform。

因此 v1 分为两个编译层：

- **逻辑编译**：服务端可执行，只验证 Type、Slot、Part ID 和拓扑，不解析 GLB；
- **视觉编译**：FractureClient/HaikalatHost 加载 GLB 后解析 Node Transform，生成最终 Part 与 Marker 矩阵。

服务端不得为了验证武器而加载 OpenGL、Haikalat 或客户端 GLB 类型，也不得信任客户端回传的矩阵。

---

## 11. 资源位置与加载顺序

建议数据资源：

```text
data/<namespace>/crystal_fracture/weapon_part_types/<path>.json
data/<namespace>/crystal_fracture/weapon_parts/<path>.json
data/<namespace>/crystal_fracture/weapon_schemas/<path>.json
```

客户端模型资源：

```text
assets/<namespace>/weapon/parts/<path>.glb
```

资源重载顺序：

```text
parse all files
    -> validate Part Types
    -> validate Parts against Types
    -> validate Schemas against Types
    -> build immutable registry snapshot
    -> atomically publish
```

任意 Error 都阻止新一代 Registry 发布。旧 Registry 继续有效，不能把运行中世界切换到半加载状态。

已经存在的 WeaponAssembly 保存稳定 ID。资源重载后首次使用时重新编译；若引用已失效，返回明确的 Invalid Assembly 结果，不能静默替换成另一件 Part。

---

## 12. Blockbench 插件与游戏侧的契约

Blockbench 插件只导出 WeaponPart，不导出 WeaponSchema。

插件可以读取游戏侧生成的 Part Type Catalog，例如：

```json
{
  "catalog_version": 1,
  "types": {
    "crystal_fracture:sword_blade": {
      "required_connects": ["guard"],
      "required_markers": ["tip", "base", "trail_start", "trail_end"]
    }
  }
}
```

插件用该 Catalog：

- 填充 Part Type 下拉框；
- 提醒缺失的 Required Connect/Marker；
- 在 Build 前验证 Type 契约。

插件不能：

- 根据 Locator 名自动生成完整 Schema；
- 根据空间距离配对 Connect；
- 决定 parent/child；
- 自动补写缺失 Slot；
- 修改 Group Origin 来让装配“看起来正确”。

---

## 13. 存档与网络

最小权威 Assembly 表示只需要：

```text
schema_id
slot -> part_id
```

不需要同步：

- Connection 列表；
- Locator Name；
- Transform Matrix；
- GLB 路径；
- Part Type 契约。

这些静态内容由客户端和服务端各自的资源 Registry 解析。

建议为 Registry Snapshot 计算内容哈希。客户端资源与服务端权威定义不一致时，应明确报告版本不匹配；客户端不能用自己的 Schema 改写服务端 Assembly。

第一版不实现运行时动态改拓扑。更换 Part 产生一个新的不可变 WeaponAssembly，并重新编译。

---

## 14. 与 ActionGuide 的连接点

WeaponSchema 不保存 ActionDefinition 或 CombatCue。

正确链路：

```text
玩家输入 Action Intent
    -> CrystalFracture 查询当前 WeaponAssembly
    -> 根据 Schema/Part/武器内容规则解析具体 Action
    -> ActionGuide 启动 ActionInstance
    -> Attack State 给出 Attack Slot
    -> CrystalFracture 用当前 WeaponAssembly 解析攻击定义
```

表现链路：

```text
ActionGuide Snapshot/Event
    -> FractureClient
    -> 查询 CompiledWeaponAssembly 的导出 Marker
    -> HaikalatHost 播放武器动画、Trail 或 VFX
```

`AttackSlotId`、`WeaponSlotId` 和 `MarkerName` 是三种不同概念，不能共用一个类型。

---

## 15. 错误与诊断

每个问题至少包含：

```text
severity
error_code
resource_id
json_path
message
related_resource_id (optional)
```

推荐稳定错误码：

```text
unsupported_schema_version
unknown_part_type
unknown_slot
unknown_connect
unknown_marker
duplicate_endpoint
root_has_parent
slot_has_multiple_parents
cyclic_topology
disconnected_slot
missing_assembly_slot
unexpected_assembly_slot
part_type_mismatch
missing_visual_node
non_invertible_connect_transform
```

JSON 路径必须尽可能精确，例如：

```text
connections[1].child.connect
parts.blade
markers.trail_start.marker
```

视觉编译失败不能破坏服务端的权威 Assembly；客户端应显示可诊断的缺失模型占位或跳过武器渲染，并记录具体 Slot 和 Part ID。

---

## 16. 推荐包结构

```text
org.hismeo.crystalfracture.weapon
├─ api
│  ├─ WeaponSchemaId
│  ├─ WeaponPartId
│  ├─ WeaponPartTypeId
│  ├─ WeaponSlotId
│  ├─ ConnectName
│  ├─ MarkerName
│  ├─ WeaponAssembly
│  └─ CompiledWeaponAssembly
├─ definition
│  ├─ WeaponPartTypeDefinition
│  ├─ WeaponPartDefinition
│  ├─ WeaponSchemaDefinition
│  ├─ WeaponSlotDefinition
│  ├─ WeaponConnectEndpoint
│  ├─ WeaponConnectionDefinition
│  └─ WeaponMarkerExport
├─ internal
│  ├─ loader
│  │  ├─ WeaponPartTypeLoader
│  │  ├─ WeaponPartLoader
│  │  ├─ WeaponSchemaLoader
│  │  └─ WeaponResourceReloadListener
│  ├─ validation
│  │  ├─ WeaponDefinitionValidator
│  │  ├─ WeaponAssemblyValidator
│  │  └─ WeaponDefinitionProblem
│  ├─ compile
│  │  ├─ WeaponAssemblyCompiler
│  │  └─ CompiledWeaponAssemblyImpl
│  └─ registry
│     ├─ WeaponDefinitionRegistry
│     └─ WeaponRegistrySnapshot
└─ integration
   ├─ action
   └─ network
```

客户端 GLB 与 Transform 解析器放在 `FractureClient`，不能放进上述 common/server 包。

---

## 17. 测试契约

### 17.1 Loader 测试

- 合法的标准剑 Schema 能加载；
- 不支持的 schema version 被拒绝；
- 非法 ResourceLocation 被拒绝；
- Slot、Connect 和 Marker 名称非法时给出准确 JSON Path；
- 未知字段的处理策略被冻结；
- Definition 集合不可从外部修改。

### 17.2 拓扑测试

- 单 Slot Root 合法；
- 标准 `grip -> crossguard -> blade` 合法；
- Root 有入边被拒绝；
- 非 Root 没有入边被拒绝；
- 一个 Slot 有两个 Parent 被拒绝；
- 环被拒绝；
- 不连通 Slot 被拒绝；
- 一个 Connect Endpoint 被两条边使用时拒绝；
- JSON 顺序变化不改变 parent-first 编译结果。

### 17.3 类型与 Assembly 测试

- Part 满足 Slot Type 时通过；
- Part Type 不匹配时拒绝；
- 缺少 Slot 时拒绝；
- 多余 Slot 时拒绝；
- 缺少被 Schema 使用的 Connect 时拒绝；
- 缺少导出 Marker 时拒绝；
- 同一 Part ID 放在两个 Slot 时生成两个独立实例。

### 17.4 Transform 测试

- 单位 Connect 产生预期 ChildWorld；
- Parent Position/Rotation 正确传递；
- Child Connect 的逆矩阵正确抵消；
- 非单位 Scale 给出 Warning 或 Error；
- 不可逆矩阵被拒绝；
- 多层树的结果与 parent-first 顺序一致；
- 导出 Marker 能正确变换到 Weapon Root 和 World。

### 17.5 边界测试

- 专用服务端不加载 `net.minecraft.client.*`；
- common/server 包不依赖 Haikalat 或 LWJGL；
- 客户端缺失 GLB 不改变服务端 Assembly；
- 资源重载失败保留上一代 Registry；
- 存档反序列化不会接受客户端提供的矩阵或拓扑。

---

## 18. 分阶段实施任务

### 阶段 0：冻结数据契约

- [x] 确认 Part Type 的 Required Connect/Marker 格式；
- [x] 确认 WeaponPart JSON 的资源位置；
- [x] 冻结 WeaponSchema v1 JSON；
- [x] 冻结 WeaponAssembly 的存档/网络格式；
- [x] 建立 `standard_sword` golden fixture；
- [x] 建立 grip、crossguard、blade 三个 WeaponPart fixture。

完成标准：不加载 Minecraft 或 GLB，也能完整表达一把标准剑的类型、拓扑和具体配件选择。

### 阶段 1：纯 Java 数据模型与验证器

- [x] 实现强类型 ID；
- [x] 实现不可变 Definition；
- [x] 实现 Type、Part、Schema Loader；
- [x] 实现结构和引用验证；
- [x] 实现有根树验证；
- [x] 实现稳定诊断；
- [x] 完成纯 Java 单元测试。

完成标准：合法 fixture 产生不可变 Registry Snapshot，所有非法 fixture 给出确定且精确的错误。

### 阶段 2：Assembly Compiler

- [x] 实现 Slot -> Part 解析；
- [x] 实现精确 Type 匹配；
- [x] 实现具体 Connect/Marker 引用验证；
- [x] 生成 parent-first Connection；
- [x] 生成导出 Marker 查询表；
- [x] 生成稳定内容哈希；
- [x] 完成编译器测试。

完成标准：`standard_sword + 三个 Part ID` 可以编译为完整、确定、只读的逻辑装配结果。

阶段 2 的测试同时覆盖 Blockbench 实际导出的 8 个 Part JSON；逻辑编译不读取对应 GLB。

### 阶段 3：NeoForge 数据资源接入

- [x] 注册 Reload Listener；
- [x] 按 Type -> Part -> Schema 顺序验证；
- [x] 原子发布 Registry Snapshot；
- [x] 处理 reload 失败；
- [x] 提供只读查询 API；
- [x] 验证专用服务端可加载。

完成标准：数据包重载后服务端能查询 Schema、校验 Assembly，且不加载任何客户端类。

### 阶段 4：FractureClient 视觉装配

- [x] 根据 Part `visual.model` 加载 GLB；
- [x] 解析 Connect/Marker 绑定到的 Node；
- [x] 读取 Position/Rotation；
- [x] 按 parent-first Connection 计算 Part Transform；
- [x] 解析整把武器的导出 Marker；
- [x] 挂到玩家 `hand_r/weapon_socket`；
- [x] 正确释放 Part Scene Instance；
- [x] 为缺失 Node 和不可逆 Transform 提供诊断。

完成标准：一把由三个独立 GLB Part 组成的剑能稳定跟随玩家右手，并且换任一同类型 Part 后仍按相同 Schema 正确组装。

阶段 4 运行纵切最初使用导出的 `wood_shaft + crossguard + heavy_sword`，并导入其余同类型可替换 Part。
阶段 5 默认权威装配已更新为 `wood_shaft + crossguard + sword`。当前 Avatar 的 `right_hand` Node
实现逻辑挂点 `hand_r/weapon_socket`，武器根变换通过 Schema 导出的 `main_hand_grip` Marker 校正。
Blockbench 5.1.6 会为零尺寸 Locator 导出非法的 `[null,null,null]` scale，并生成矩阵等价的同名
父子 Node；客户端加载边界把该特定 scale 规范化为单位 scale，只折叠矩阵等价的同名 Node，
不同矩阵的同名 Node 仍以 `ambiguous_visual_node` 拒绝。

客户端世界内可使用 `/fracture_weapon_preview` 打开完整三段武器调试屏幕；左键拖拽旋转、滚轮缩放、
空格切换自动旋转、`R` 重置视角。

### 阶段 5：存档、网络与内容纵切

- [x] 定义 Assembly 存档编码；
- [x] 定义服务端权威同步；
- [x] 客户端按 ID 重新编译视觉装配；
- [x] 通过 Schema Marker 取得 `grip`；
- [x] 通过 Schema Marker 取得 `trail_start/trail_end`；
- [ ] 让一次 ActionGuide 剑击驱动武器动画与 Trail；
- [x] 验证重连、换维度和卸载清理。

完成标准：服务端同步一把具体 Assembly，客户端组装并挂载模型，ActionGuide 攻击过程中能通过稳定 Marker API 驱动表现。

阶段 5 核心纵切使用 NeoForge Player Attachment 持久化并同步最小 Assembly（`schema_id + slot -> part_id`），
登录、换维度与数据包热重载后由服务端重新验证和同步。客户端从模组内置 Manifest 构建同构 Registry，
按每名玩家的权威 ID 独立缓存视觉装配；服务端同时同步 Registry SHA-256，指纹不一致时客户端明确拒绝渲染。
`PlayerWeaponMarkers` 提供 `main_hand_grip` 与世界空间 `trail_start/trail_end` 查询，世界卸载、资源重载和扩展关闭
都会释放 Scene Instance 与 GLB Catalog。按当前任务约束，ActionGuide 剑击动画及其 Trail 驱动等待动画资产后接入。

2026-08 更新导出包含 13 个 Part。`iron_shaft.json` 导出的 Marker 语义键 `grid` 在导入边界修正为 `grip`；
Blockbench 新版对 Locator 生成同名父子 Node 并附带显示缩放，客户端只对 JSON 明确引用的 Connect/Marker
去除该显示缩放后判断等价，位置或旋转不同的同名 Node 仍以 `ambiguous_visual_node` 拒绝。

---

## 19. WeaponSchema v1 明确不做什么

- Optional Slot；
- 运行时动态增加或删除 Slot；
- 多根图、环或共享子节点；
- Part Type 继承和类型标签表达式；
- 自动寻找最近 Connect；
- 自动根据 Locator Name 推断连接；
- 自动修正 Group Origin；
- 属性计算；
- 动作、伤害和 Combo；
- 服务端解析 GLB Transform；
- 客户端上报权威装配矩阵；
- 在 Schema 中硬编码 Minecraft 玩家骨骼名称。

---

## 20. WeaponSchema v1 完成定义

同时满足以下条件，才能认为 WeaponSchema v1 完成：

- WeaponPart Type 契约可被插件和游戏侧共同理解；
- WeaponPart、Schema 和 Assembly 三者身份严格分离；
- Schema 明确声明 Root、Slot、Type 和 parent/child Connection；
- 所有 Endpoint 使用语义 Connect Name，而不是 Locator Name；
- 完整拓扑是一棵经过验证的有根树；
- 不进行任何基于名称、距离或位置的自动推断；
- Assembly 中每个 Part 都满足 Slot Type；
- Schema Marker 为下游系统提供稳定接口；
- 服务端无需解析 GLB 即可验证逻辑装配；
- 客户端遵循统一矩阵公式完成视觉装配；
- 资源重载采用不可变 Snapshot 和原子发布；
- 存档与网络只同步权威 ID，不信任客户端矩阵；
- 专用服务端不依赖 FractureClient、Haikalat 或 OpenGL；
- 标准剑 golden fixture 通过 Loader、Validator、Compiler 和 Transform 测试；
- 替换任意一个同类型 Part 不需要修改 WeaponSchema。

达到这些要求之后，WeaponSchema 才是稳定的武器装配契约，而不是一段只能拼出某一把固定模型的客户端脚本。
