package org.hismeo.fractureclient.client.config;

import org.hismeo.crystallib.api.config.ConfigFormat;
import org.hismeo.crystallib.api.config.ConfigScope;
import org.hismeo.crystallib.api.config.ConfigValue;
import org.hismeo.crystallib.api.config.CrystalConfig;
import org.hismeo.fractureclient.FractureClient;

@CrystalConfig(
        modId = FractureClient.MODID,
        fileName = "fracture_client_orthographic_camera",
        scope = ConfigScope.CLIENT,
        format = ConfigFormat.TOML,
        autoLoad = true
)
public class OrthographicCameraConfig {
    @ConfigValue(comment = "相机模式：VANILLA_FIRST_PERSON（原版第一人称）或 FRACTURE_ORTHOGRAPHIC（Fracture 正交视角）")
    public static volatile CameraMode cameraMode = CameraMode.FRACTURE_ORTHOGRAPHIC;

    @ConfigValue(comment = "室外正交相机俯仰角", min = -90.0, max = 90.0)
    public static float pitch = 25.0F;

    @ConfigValue(comment = "正交相机水平朝向角", min = -180.0, max = 180.0)
    public static float yaw = 25.0F;

    @ConfigValue(
            comment = "键盘旋转镜头完成一次平滑补间所需的秒数",
            min = 0.05,
            max = 1.0
    )
    public static float cameraRotationTransitionSeconds = 0.18F;

    @ConfigValue(
            comment = "玩家越过死区外圈后，相机回到内圈附近所需的秒数",
            min = 0.05,
            max = 3.0
    )
    public static float deadZoneTransitionSeconds = 0.35F;

    @ConfigValue(comment = "正交相机视野大小；数值越大，画面显示的范围越广")
    public static float size = 10.0F;

    @ConfigValue(comment = "进入房间后，让正交相机自动跟随当前楼层")
    public static boolean floorAwareCamera = true;

    @ConfigValue(
            comment = "处于室内楼层时使用的向下俯视角",
            min = 25.0,
            max = 85.0
    )
    public static float floorCameraPitch = 60.0F;

    @ConfigValue(
            comment = "相机高出当前楼层天花板顶面的距离",
            min = 0.0,
            max = 6.0
    )
    public static float floorCameraCeilingClearance = 0.75F;

    @ConfigValue(
            comment = "室内正交视野倍率；大于 1 时会显示更大的范围",
            min = 0.1,
            max = 2.0
    )
    public static float floorCameraSizeMultiplier = 1.15F;

    @ConfigValue(
            comment = "室内构图时为房间边缘预留的方块距离",
            min = 0.0,
            max = 4.0
    )
    public static float floorCameraRoomMargin = 1.0F;

    @ConfigValue(
            comment = "相机在不同楼层之间平滑过渡所需的秒数",
            min = 0.05,
            max = 3.0
    )
    public static float floorCameraTransitionSeconds = 0.45F;

    @ConfigValue(
            comment = "楼层相机沿视线方向移动的最大方块距离",
            min = 4.0,
            max = 96.0
    )
    public static float floorCameraMaximumDistance = 64.0F;

    @ConfigValue(comment = "是否启用遮挡方块剔除")
    public static boolean isCull = true;

    @ConfigValue(
            comment = "剔除射线在玩家投影周围额外增加的横向宽度",
            min = 0.0,
            max = 2.0
    )
    public static float cullHorizontalMargin = 0.32F;

    @ConfigValue(
            comment = "剔除射线在玩家投影周围额外增加的纵向高度",
            min = 0.0,
            max = 2.0
    )
    public static float cullVerticalMargin = 0.20F;

    @ConfigValue(
            comment = "确认屋顶遮挡玩家时使用的开口半径；数值越大，局部开口越宽",
            min = 0.0,
            max = 6.0
    )
    public static float cullOpeningRadius = 3.25F;

    @ConfigValue(comment = "确认遮挡后，剔除检测到的完整楼板或屋顶表面")
    public static boolean cullWholeRoomRoof = true;

    @ConfigValue(comment = "楼层相机模式下，同时剔除朝向相机的两面房墙")
    public static boolean cullNearestRoomWalls = true;

    @ConfigValue(
            comment = "从选中墙面向外扫描的深度，用于包含墙体厚度和外墙装饰",
            min = 0.0,
            max = 6.0
    )
    public static int cullRoomWallExteriorDepth = 2;

    @ConfigValue(
            comment = "完整楼板或屋顶剔除允许的最大方块数；超过后只保留局部开口",
            min = 64.0,
            max = 8192.0
    )
    public static int cullWholeRoofMaximumBlocks = 4096;

    @ConfigValue(
            comment = "室外最多允许剔除的连续实体层数；更厚的地形改用可见性回退效果",
            min = 1.0,
            max = 16.0
    )
    public static int cullMaximumSolidLayers = 6;

    @ConfigValue(
            comment = "确认处于封闭房间后，最多允许剔除的连续实体层数",
            min = 1.0,
            max = 32.0
    )
    public static int cullRoomMaximumSolidLayers = 16;

    @ConfigValue(
            comment = "虚拟相机剔除射线的最大方块距离",
            min = 8.0,
            max = 128.0
    )
    public static float cullMaximumDistance = 96.0F;

    @ConfigValue(
            comment = "方块不再遮挡后继续保持剔除的 Tick 数，用于防止边界闪烁",
            min = 0.0,
            max = 20.0
    )
    public static int cullReleaseDelayTicks = 6;

    @ConfigValue(
            comment = "方块在正常与剔除状态之间渐变所需的 Tick 数；设为 0 可关闭渐变",
            min = 0.0,
            max = 40.0
    )
    public static int cullFadeDurationTicks = 20;

    @ConfigValue(
            comment = "剔除区域下方方块的亮度：VANILLA（原版）、ENVIRONMENT（环境亮度）或 FULL_BRIGHT（满亮度）"
    )
    public static CullExposedLightMode cullExposedLightMode = CullExposedLightMode.ENVIRONMENT;

    @ConfigValue(
            comment = "以玩家为中心的房间扫描初始宽度和深度；大型房间被截断时会自动扩大",
            min = 16.0,
            max = 80.0
    )
    public static int cullRoomScanHorizontalSize = 48;

    @ConfigValue(
            comment = "以玩家为中心的房间扫描初始高度；遇到高楼层时会自动扩大",
            min = 12.0,
            max = 48.0
    )
    public static int cullRoomScanVerticalSize = 24;

    @ConfigValue(
            comment = "两次房间连通性扫描之间的最小 Tick 间隔",
            min = 4.0,
            max = 40.0
    )
    public static int cullRoomScanIntervalTicks = 10;

    @ConfigValue(
            comment = "用于排除真实天花板下方吊灯、横梁等悬挂物的检测高度",
            min = 1.0,
            max = 5.0
    )
    public static int cullRoomRoofExpansionLayers = 3;

    @ConfigValue(
            comment = "从房间空气轮廓向外扩张的水平格数，用于包含斜屋顶墙体和屋檐",
            min = 0.0,
            max = 6.0
    )
    public static int cullRoomRoofHorizontalExpansion = 2;

    public enum CameraMode {
        VANILLA_FIRST_PERSON,
        FRACTURE_ORTHOGRAPHIC
    }

    public enum CullExposedLightMode {
        VANILLA,
        ENVIRONMENT,
        FULL_BRIGHT
    }
}
