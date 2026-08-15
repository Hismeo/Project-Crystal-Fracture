# Haikalat：共享骨架的外部 GLB 动画

## 已确认的资产关系

`player_wild.gltf` 与 `player_sile.gltf` 不是两套角色骨架。它们共享完全相同的：

- 节点名称和顺序；
- 父子层级；
- bind translation、rotation、scale；
- skin joint 顺序和 inverse bind matrices。

两者只允许 Mesh、UV、材质和贴图布局不同。`player.glb` 是这套骨架唯一的动画来源，包含 `stand`、`move`、`run`、`jump`、`dash`。

理想资源关系是：

```text
player.glb                 一份 Skeleton + AnimationClip 数据
├── player_wild.gltf       Wild Mesh / UV / Material
└── player_sile.gltf       Sile Mesh / UV / Material
```

## Haikalat 已经具备的能力

`GltfAssetLoader.load(...)` 已支持 `.gltf` 和 `.glb`，也能读取 GLB 内嵌的动画。当前缺口只在“把一个独立 GLB 的动画绑定到另一个模型”这一层。

## Haikalat 需要增加的能力

1. 提供独立、不可变、可共享的动画资产，例如 `GltfAnimationSet`。它应从 GLB 读取 Skeleton/Clip，但不要求使用该 GLB 的 Mesh。
2. 允许把同一 `GltfAnimationSet` 绑定到多个 `LoadedGltfScene`/`GltfSceneAsset`，不复制关键帧数据。
3. 在绑定时执行严格 Rig 兼容校验：节点名唯一、父节点一致、bind TRS 一致、skin joint 顺序与 inverse bind matrices 一致。Mesh、UV、材质不参与兼容判断。
4. 扩展 animation-library importer。当前 `animations[].file` 只接受 JSON sidecar；需要允许一个 `.glb` 作为动画源，并支持按名称选择其中多个 clip。
5. 明确资源所有权：共享动画资产随 catalog/reload 关闭；每个角色实例只持有自己的播放时间、PoseBuffer、状态机和蒙皮 palette。
6. 保留动画附带的 marker、window 和 CombatCue/root-motion 元数据，并让 clip 名称保持稳定。
7. 增加测试：GLB 直接动画源、一个动画源绑定两个不同 Mesh、Rig 不兼容拒绝、资源重载/关闭，以及确认关键帧存储没有按模型复制。

建议 API 形态：

```java
GltfAnimationSet animations = loader.loadAnimationSet(AssetRef.of("player.glb"));
LoadedGltfScene wild = animations.bind(loader.load(AssetRef.of("player_wild.gltf")));
LoadedGltfScene sile = animations.bind(loader.load(AssetRef.of("player_sile.gltf")));
```

## 当前项目已经能做的过渡方案

在 Haikalat API 扩展前，导入脚本会从 `player.glb` 生成五个轻量 JSON 动画描述，但所有描述共同引用唯一的 `animations/player_animation.bin`。Wild/Sile manifest 只重复模型绑定清单，不复制动画关键帧。

现在还会在两处阻止骨架漂移：

- 导入脚本比较动画 GLB、Wild、Sile 的节点顺序、父子关系、bind TRS 和 skin joint 顺序；
- FractureClient 加载时比较 Wild/Sile 的完整 Rig，包括 inverse bind matrices。

因此当前格式虽然不是最终的“直接共享 GLB API”，其数据语义已经是一份动画、多份 Mesh。
