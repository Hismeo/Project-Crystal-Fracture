package org.hismeo.fractureclient.client.config;

import org.hismeo.crystallib.api.config.ConfigFormat;
import org.hismeo.crystallib.api.config.ConfigScope;
import org.hismeo.crystallib.api.config.ConfigValue;
import org.hismeo.crystallib.api.config.CrystalConfig;
import org.hismeo.fractureclient.FractureClient;

/**
 * Tunable controls for the HD-2D-inspired world presentation.
 */
@CrystalConfig(
        modId = FractureClient.MODID,
        fileName = "fracture_client_octopath_visual",
        scope = ConfigScope.CLIENT,
        format = ConfigFormat.TOML,
        autoLoad = true
)
public final class OctopathVisualConfig {
    @ConfigValue(comment = "启用八方旅人风格的世界后处理管线")
    public static boolean enabled = true;

    @ConfigValue(comment = "与原版 Minecraft 世界画面的混合比例", min = 0.0, max = 1.0)
    public static float effectStrength = 0.72F;

    @ConfigValue(comment = "高光压缩前的场景曝光", min = 0.1, max = 4.0)
    public static float exposure = 0.95F;

    @ConfigValue(comment = "受保护高光以外区域的色彩鲜艳度", min = 0.0, max = 1.0)
    public static float vibrance = 0.34F;

    @ConfigValue(comment = "场景色调预设：自动或强制指定（AUTO、DAY、FOREST、NIGHT）")
    public static TonePresetMode tonePresetMode = TonePresetMode.AUTO;

    @ConfigValue(comment = "保持亮度的场景色调预设强度", min = 0.0, max = 1.0)
    public static float tonePresetStrength = 0.66F;

    @ConfigValue(comment = "稳定的自动色调预设之间交叉淡化所用的游戏刻", min = 1.0, max = 200.0)
    public static int tonePresetTransitionTicks = 32;

    @ConfigValue(comment = "高光滚降宽度；值越低越护眼", min = 0.05, max = 1.0)
    public static float highlightCompression = 0.24F;

    @ConfigValue(comment = "泛光提取阈值", min = 0.0, max = 4.0)
    public static float bloomThreshold = 1.08F;

    @ConfigValue(comment = "泛光阈值的柔和过渡区", min = 0.0, max = 1.0)
    public static float bloomSoftKnee = 0.55F;

    @ConfigValue(comment = "泛光强度", min = 0.0, max = 2.0)
    public static float bloomIntensity = 0.26F;

    @ConfigValue(comment = "泛光金字塔层级数", min = 2.0, max = 6.0)
    public static int bloomLevels = 5;

    @ConfigValue(comment = "基础距离雾密度", min = 0.0, max = 0.1)
    public static float fogDensity = 0.0065F;

    @ConfigValue(comment = "下雨时额外增加的雾密度", min = 0.0, max = 0.1)
    public static float rainFogBoost = 0.008F;

    @ConfigValue(comment = "世界雾的最大不透明度", min = 0.0, max = 1.0)
    public static float fogMaximumOpacity = 0.48F;

    @ConfigValue(comment = "在可见水面及其岸边启用局部薄雾")
    public static boolean waterMistEnabled = true;

    @ConfigValue(comment = "水面和河岸局部薄雾强度", min = 0.0, max = 1.0)
    public static float waterMistStrength = 0.80F;

    @ConfigValue(comment = "局部薄雾可达到的水面上方最大高度", min = 0.5, max = 8.0)
    public static float waterMistHeight = 2.4F;

    @ConfigValue(comment = "每个聚类水雾区域的世界空间半径", min = 2.0, max = 12.0)
    public static float waterMistPatchRadius = 5.5F;

    @ConfigValue(comment = "扫描可见水面的时间间隔（游戏刻）", min = 1.0, max = 100.0)
    public static int waterMistScanIntervalTicks = 12;

    @ConfigValue(comment = "连续受光表面上的细微纯深度接触遮蔽", min = 0.0, max = 0.18)
    public static float depthContactOcclusionStrength = 0.10F;

    @ConfigValue(comment = "屏幕像素单位的深度接触遮蔽半径；较小值可避免树叶和实体伪影", min = 0.5, max = 4.0)
    public static float depthContactOcclusionRadiusPixels = 2.5F;

    @ConfigValue(comment = "在稳定的朝上地形表面启用半分辨率深度感知环境光遮蔽")
    public static boolean ambientOcclusionEnabled = true;

    @ConfigValue(comment = "深度感知环境光遮蔽强度；压暗缝隙与接触区域，但不改变直射光", min = 0.0, max = 0.55)
    public static float ambientOcclusionStrength = 0.28F;

    @ConfigValue(comment = "屏幕像素单位的环境光遮蔽搜索半径；更大值会加宽地形缝隙的阴影", min = 1.0, max = 12.0)
    public static float ambientOcclusionRadiusPixels = 7.0F;

    @ConfigValue(comment = "移轴景深强度", min = 0.0, max = 1.0)
    public static float depthOfFieldStrength = 0.58F;

    @ConfigValue(comment = "以玩家为中心的倾斜焦平面在世界空间中的宽度", min = 0.5, max = 64.0)
    public static float depthOfFieldFocusRange = 8.0F;

    @ConfigValue(comment = "景深的最大模糊半径（像素）", min = 0.0, max = 12.0)
    public static float depthOfFieldMaxRadius = 4.5F;

    @ConfigValue(comment = "从旧版深度模糊过渡到微缩移轴焦点模型的混合比例", min = 0.0, max = 1.0)
    public static float tiltShiftStrength = 0.82F;

    @ConfigValue(comment = "清晰的水平移轴焦点带半高", min = 0.03, max = 0.45)
    public static float tiltShiftFocusBand = 0.13F;

    @ConfigValue(comment = "移轴焦点带外侧的柔和过渡宽度", min = 0.01, max = 0.60)
    public static float tiltShiftTransition = 0.20F;

    @ConfigValue(comment = "以玩家为中心的移轴焦点带水平斜率", min = -0.5, max = 0.5)
    public static float tiltShiftFocusLineTilt = -0.035F;

    @ConfigValue(comment = "深度焦平面在相机空间中的垂直倾斜", min = -1.0, max = 1.0)
    public static float tiltShiftDepthPlaneTilt = -0.28F;

    @ConfigValue(comment = "屏幕边缘的额外模糊", min = 0.0, max = 1.0)
    public static float tiltShiftEdgeBlur = 0.30F;

    @ConfigValue(comment = "以角色为中心的舞台聚光强度；只改变局部构图，不改变全局曝光", min = 0.0, max = 1.0)
    public static float stageSpotlightStrength = 0.52F;

    @ConfigValue(comment = "以角色为中心的舞台聚光在屏幕空间中的半径", min = 0.15, max = 1.4)
    public static float stageSpotlightRadius = 0.78F;

    @ConfigValue(comment = "聚光之后舞台边缘的压暗强度", min = 0.0, max = 1.0)
    public static float stageVignetteStrength = 0.42F;

    @ConfigValue(comment = "厚实或特殊方块无法安全裁切时，为本地玩家绘制轮廓")
    public static boolean playerOcclusionIndicator = true;

    @ConfigValue(comment = "显示玩家轮廓所需的最小剔除回退置信度", min = 0.0, max = 1.0)
    public static float playerOcclusionOutlineThreshold = 0.16F;

    @ConfigValue(comment = "玩家遮挡轮廓的 RGB 颜色", min = 0.0, max = 16777215.0)
    public static int playerOcclusionOutlineColor = 0xFFC15A;

    @ConfigValue(comment = "在可见实体下方绘制柔和的接地阴影")
    public static boolean entityGroundShadows = true;

    @ConfigValue(comment = "八方旅人风格实体接地阴影的不透明度", min = 0.0, max = 0.35)
    public static float entityGroundShadowStrength = 0.14F;

    @ConfigValue(comment = "每个实体接地阴影足迹的世界空间缩放", min = 0.5, max = 2.0)
    public static float entityGroundShadowRadius = 1.0F;

    @ConfigValue(comment = "查找实体阴影接收表面时向下射线的最大距离", min = 1.0, max = 12.0)
    public static float entityGroundShadowMaximumDrop = 6.0F;

    @ConfigValue(comment = "自定义阴影启用时隐藏 Minecraft 原版不透明实体阴影贴花")
    public static boolean suppressNativeEntityShadows = true;

    @ConfigValue(comment = "绘制局部太阳方向深度图，用于投射地形和实体阴影")
    public static boolean directionalShadowMapEnabled = true;

    @ConfigValue(comment = "投射方向阴影图结果的不透明度", min = 0.0, max = 0.70)
    public static float directionalShadowStrength = 0.34F;

    @ConfigValue(comment = "方向阴影图覆盖的局部半范围（方块）", min = 12.0, max = 64.0)
    public static float directionalShadowWorldExtent = 30.0F;

    @ConfigValue(comment = "光源空间深度纹理的分辨率", min = 256.0, max = 2048.0)
    public static int directionalShadowMapResolution = 1024;

    @ConfigValue(comment = "用作方向阴影投射体的最大地形高度列数", min = 49.0, max = 4096.0)
    public static int directionalShadowMaximumTerrainColumns = 4096;

    @ConfigValue(comment = "方向阴影图地形高度扫描的时间间隔（游戏刻）", min = 1.0, max = 100.0)
    public static int directionalShadowTerrainScanIntervalTicks = 8;

    @ConfigValue(comment = "用于避免投射阴影痤疮的光源空间深度偏移", min = 0.0001, max = 0.02)
    public static float directionalShadowBias = 0.0005F;

    @ConfigValue(comment = "阴影图纹素单位的 PCF 过滤半径", min = 0.5, max = 3.0)
    public static float directionalShadowSoftness = 1.25F;

    @ConfigValue(comment = "根据方向阴影图绘制克制的阳光光束")
    public static boolean volumetricSunlightEnabled = true;

    @ConfigValue(comment = "受深度阴影影响的阳光光束强度；这是空气散射，不是场景曝光", min = 0.0, max = 0.50)
    public static float volumetricSunlightStrength = 0.22F;

    @ConfigValue(comment = "阳光光束沿相机射线采样的最大距离；实际距离会受采样数限制以避免条带", min = 4.0, max = 64.0)
    public static float volumetricSunlightDistance = 24.0F;

    @ConfigValue(comment = "每条阳光光束使用的深度采样数；更多采样可柔化阴影条带", min = 4.0, max = 12.0)
    public static int volumetricSunlightSampleCount = 10;

    @ConfigValue(comment = "彩色玻璃对穿过它的阳光光束的染色强度", min = 0.0, max = 1.0)
    public static float stainedGlassSunlightTintStrength = 0.88F;

    @ConfigValue(comment = "附近发光方块光照强度", min = 0.0, max = 5.0)
    public static float localLightIntensity = 1.15F;

    @ConfigValue(comment = "经过美术控制的附近发光方块光池范围；扩大衰减范围但不提高全局曝光", min = 0.4, max = 2.5)
    public static float localLightReach = 0.85F;

    @ConfigValue(comment = "晴朗白天保留的局部后处理光照比例；方块仍保留原生自发光纹理", min = 0.0, max = 1.0)
    public static float localLightDaylightMultiplier = 0.02F;

    @ConfigValue(comment = "为最强的可见局部光源绘制深度立方体阴影")
    public static boolean localLightShadowsEnabled = true;

    @ConfigValue(comment = "使用共享图集深度立方体阴影的最强可见局部光源数量", min = 1.0, max = 16.0)
    public static int localLightShadowCount = 16;

    @ConfigValue(comment = "局部光源深度立方体每个面的分辨率；128 是兼顾十六盏光源的默认值", min = 128.0, max = 256.0)
    public static int localLightShadowMapResolution = 128;

    @ConfigValue(comment = "局部光源阴影立方体覆盖的最大世界空间范围", min = 2.0, max = 12.0)
    public static float localLightShadowRange = 5.5F;

    @ConfigValue(comment = "每个局部光源深度立方体阴影的压暗强度", min = 0.0, max = 1.0)
    public static float localLightShadowStrength = 0.48F;

    @ConfigValue(comment = "局部光源阴影比较使用的深度偏移", min = 0.0001, max = 0.02)
    public static float localLightShadowBias = 0.0020F;

    @ConfigValue(comment = "局部光源深度阴影的 PCF 过滤半径（纹素）", min = 0.5, max = 3.0)
    public static float localLightShadowSoftness = 1.80F;

    @ConfigValue(comment = "局部光源深度立方体过期前的最小游戏刻数；十六个光源轮流刷新", min = 1.0, max = 80.0)
    public static int localLightShadowUpdateIntervalTicks = 8;

    @ConfigValue(comment = "每个游戏刻最多绘制的局部光源深度立方体数量；用于限制十六个投影光源的开销", min = 1.0, max = 16.0)
    public static int localLightShadowUpdatesPerTick = 2;

    @ConfigValue(comment = "自动覆盖视口之外额外扫描的玩家周边半径", min = 2.0, max = 48.0)
    public static int localLightScanRadius = 12;

    @ConfigValue(comment = "扫描发光方块的时间间隔（游戏刻）", min = 1.0, max = 100.0)
    public static int localLightScanIntervalTicks = 10;

    private OctopathVisualConfig() {
    }

    public enum TonePresetMode {
        AUTO,
        DAY,
        FOREST,
        NIGHT
    }
}
