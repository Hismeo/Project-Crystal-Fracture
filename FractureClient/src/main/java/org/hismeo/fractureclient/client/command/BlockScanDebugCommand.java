package org.hismeo.fractureclient.client.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.hismeo.fractureclient.FractureClient;
import org.hismeo.fractureclient.client.control.BlockCullController;
import org.hismeo.fractureclient.client.control.RoomCullScanner;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Client-only cuboid block dump used to diagnose room and cutaway classification. */
public final class BlockScanDebugCommand {
    private static final long MAX_SCAN_BLOCKS = 262_144L;
    private static final int BLOCKS_PER_TICK = 1_024;
    private static long nextScanId = 1L;
    private static ScanJob activeJob;

    private BlockScanDebugCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fracture_scan")
                .then(Commands.argument("from", BlockPosArgument.blockPos())
                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                .executes(context -> start(
                                        context.getSource(),
                                        BlockPosArgument.getBlockPos(context, "from"),
                                        BlockPosArgument.getBlockPos(context, "to")
                                ))))
                .then(Commands.literal("cancel")
                        .executes(context -> cancel(context.getSource())))
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource()))));
    }

    public static void tick(Minecraft minecraft) {
        ScanJob job = activeJob;
        if (job == null) {
            return;
        }
        if (minecraft.level == null || minecraft.level != job.level) {
            job.logCancelled("client level changed");
            activeJob = null;
            return;
        }

        if (job.scanBatch()) {
            job.finish(minecraft);
            activeJob = null;
        }
    }

    private static int start(CommandSourceStack source, BlockPos first, BlockPos second) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            source.sendFailure(Component.literal("当前没有客户端世界，无法扫描。"));
            return 0;
        }
        if (activeJob != null) {
            source.sendFailure(Component.literal(
                    "已有方块扫描正在运行；使用 /fracture_scan status 或 /fracture_scan cancel。"
            ));
            return 0;
        }

        int minX = Math.min(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxX = Math.max(first.getX(), second.getX());
        int maxY = Math.max(first.getY(), second.getY());
        int maxZ = Math.max(first.getZ(), second.getZ());
        long sizeX = (long)maxX - minX + 1L;
        long sizeY = (long)maxY - minY + 1L;
        long sizeZ = (long)maxZ - minZ + 1L;
        long volume;
        try {
            volume = Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ);
        } catch (ArithmeticException exception) {
            source.sendFailure(Component.literal("扫描范围体积溢出，请缩小范围。"));
            return 0;
        }
        if (volume > MAX_SCAN_BLOCKS) {
            source.sendFailure(Component.literal(
                    "扫描范围共 " + volume + " 格，超过上限 " + MAX_SCAN_BLOCKS + "。"
            ));
            return 0;
        }

        long scanId = nextScanId++;
        activeJob = new ScanJob(
                scanId,
                level,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                volume
        );
        activeJob.logStarted();
        Path logPath = minecraft.gameDirectory.toPath()
                .resolve("logs")
                .resolve("latest.log")
                .toAbsolutePath();
        source.sendSuccess(
                () -> Component.literal(
                        "已开始方块扫描 #" + scanId + "，共 " + volume
                                + " 格；结果写入 " + logPath
                ),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int cancel(CommandSourceStack source) {
        ScanJob job = activeJob;
        if (job == null) {
            source.sendFailure(Component.literal("当前没有正在运行的方块扫描。"));
            return 0;
        }
        job.logCancelled("cancelled by command");
        activeJob = null;
        source.sendSuccess(
                () -> Component.literal("已取消方块扫描 #" + job.scanId + "。"),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int status(CommandSourceStack source) {
        ScanJob job = activeJob;
        if (job == null) {
            source.sendSuccess(() -> Component.literal("当前没有正在运行的方块扫描。"), false);
            return Command.SINGLE_SUCCESS;
        }
        source.sendSuccess(
                () -> Component.literal(
                        "方块扫描 #" + job.scanId + "：" + job.scanned + "/" + job.volume
                                + "（" + job.progressPercent() + "%）"
                ),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static final class ScanJob {
        private final long scanId;
        private final ClientLevel level;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;
        private final long volume;
        private final Object2LongOpenHashMap<ResourceLocation> blockCounts =
                new Object2LongOpenHashMap<>();
        private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        private int x;
        private int y;
        private int z;
        private long scanned;
        private long nonAir;
        private long roomPassable;
        private long outsideFloodPassable;
        private long skyVisible;
        private long outsideSeeds;
        private long hardCullSafe;
        private long blockEntities;
        private long unloaded;
        private long outsideBuildHeight;

        private ScanJob(
                long scanId,
                ClientLevel level,
                int minX,
                int minY,
                int minZ,
                int maxX,
                int maxY,
                int maxZ,
                long volume
        ) {
            this.scanId = scanId;
            this.level = level;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.volume = volume;
            this.x = minX;
            this.y = minY;
            this.z = minZ;
        }

        private void logStarted() {
            FractureClient.LOGGER.info(
                    "FRACTURE_BLOCK_SCAN #{} BEGIN dimension={} from=({}, {}, {}) to=({}, {}, {}) size={}x{}x{} volume={} blocks_per_tick={}",
                    scanId,
                    level.dimension().location(),
                    minX,
                    minY,
                    minZ,
                    maxX,
                    maxY,
                    maxZ,
                    (long)maxX - minX + 1L,
                    (long)maxY - minY + 1L,
                    (long)maxZ - minZ + 1L,
                    volume,
                    BLOCKS_PER_TICK
            );
        }

        private boolean scanBatch() {
            int batchSize = 0;
            long batchStart = scanned;
            StringBuilder output = new StringBuilder(BLOCKS_PER_TICK * 180);
            while (batchSize < BLOCKS_PER_TICK && scanned < volume) {
                scanCurrent(output);
                scanned++;
                batchSize++;
                advance();
            }
            if (!output.isEmpty()) {
                FractureClient.LOGGER.info(
                        "FRACTURE_BLOCK_SCAN #{} BLOCKS {}..{}\n{}",
                        scanId,
                        batchStart,
                        scanned - 1L,
                        output
                );
            }
            return scanned >= volume;
        }

        private void scanCurrent(StringBuilder output) {
            mutablePos.set(x, y, z);
            output.append("pos=(")
                    .append(x).append(',').append(y).append(',').append(z).append(") ");
            if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                outsideBuildHeight++;
                output.append("status=outside_build_height\n");
                return;
            }
            if (!level.hasChunkAt(mutablePos)) {
                unloaded++;
                output.append("status=unloaded\n");
                return;
            }

            BlockState state = level.getBlockState(mutablePos);
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            blockCounts.addTo(blockId, 1L);
            if (!state.isAir()) {
                nonAir++;
            }

            boolean collisionEmpty = state.getCollisionShape(level, mutablePos).isEmpty();
            boolean occlusionEmpty = state.getOcclusionShape(level, mutablePos).isEmpty();
            boolean visualShapeEmpty = state.getShape(level, mutablePos).isEmpty();
            boolean isRoomPassable = RoomCullScanner.isRoomPassable(level, mutablePos, state);
            boolean isOutsideFloodPassable = RoomCullScanner.isOutsideFloodPassable(
                    level,
                    mutablePos,
                    state
            );
            boolean isHardCullSafe = BlockCullController.isSafeHardCullState(state);
            boolean isSkyVisible = level.canSeeSky(mutablePos);
            boolean isScanBoundary = x == minX
                    || x == maxX
                    || y == minY
                    || y == maxY
                    || z == minZ
                    || z == maxZ;
            boolean isOutsideSeed = isScanBoundary
                    && isOutsideFloodPassable
                    && isSkyVisible;
            if (isRoomPassable) {
                roomPassable++;
            }
            if (isOutsideFloodPassable) {
                outsideFloodPassable++;
            }
            if (isHardCullSafe) {
                hardCullSafe++;
            }
            if (isSkyVisible) {
                skyVisible++;
            }
            if (isOutsideSeed) {
                outsideSeeds++;
            }

            FluidState fluidState = state.getFluidState();
            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluidState.getType());
            String blockEntityId = "none";
            if (state.hasBlockEntity()) {
                BlockEntity blockEntity = level.getBlockEntity(mutablePos);
                if (blockEntity == null) {
                    blockEntityId = "missing";
                } else {
                    blockEntities++;
                    blockEntityId = String.valueOf(
                            BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType())
                    );
                }
            }

            output.append("block=").append(blockId)
                    .append(" state=").append(state)
                    .append(" air=").append(state.isAir())
                    .append(" room_passable=").append(isRoomPassable)
                    .append(" outside_flood_passable=").append(isOutsideFloodPassable)
                    .append(" sky_visible=").append(isSkyVisible)
                    .append(" scan_boundary=").append(isScanBoundary)
                    .append(" outside_seed=").append(isOutsideSeed)
                    .append(" hard_cull_safe=").append(isHardCullSafe)
                    .append(" can_occlude=").append(state.canOcclude())
                    .append(" render_shape=").append(state.getRenderShape())
                    .append(" collision_empty=").append(collisionEmpty)
                    .append(" occlusion_empty=").append(occlusionEmpty)
                    .append(" visual_shape_empty=").append(visualShapeEmpty)
                    .append(" fluid=").append(fluidId)
                    .append(" block_entity=").append(blockEntityId)
                    .append('\n');
        }

        private void advance() {
            if (x < maxX) {
                x++;
                return;
            }
            x = minX;
            if (z < maxZ) {
                z++;
                return;
            }
            z = minZ;
            if (y < maxY) {
                y++;
            }
        }

        private void finish(Minecraft minecraft) {
            List<Map.Entry<ResourceLocation, Long>> counts = new ArrayList<>();
            for (Object2LongMap.Entry<ResourceLocation> entry
                    : blockCounts.object2LongEntrySet()) {
                counts.add(Map.entry(entry.getKey(), entry.getLongValue()));
            }
            counts.sort(Comparator
                    .<Map.Entry<ResourceLocation, Long>>comparingLong(Map.Entry::getValue)
                    .reversed()
                    .thenComparing(entry -> entry.getKey().toString()));

            StringBuilder summary = new StringBuilder();
            for (Map.Entry<ResourceLocation, Long> entry : counts) {
                summary.append(entry.getKey())
                        .append('=')
                        .append(entry.getValue())
                        .append('\n');
            }
            FractureClient.LOGGER.info(
                    "FRACTURE_BLOCK_SCAN #{} END scanned={} non_air={} room_passable={} outside_flood_passable={} sky_visible={} outside_seeds={} hard_cull_safe={} block_entities={} unloaded={} outside_build_height={} unique_blocks={}\n{}",
                    scanId,
                    scanned,
                    nonAir,
                    roomPassable,
                    outsideFloodPassable,
                    skyVisible,
                    outsideSeeds,
                    hardCullSafe,
                    blockEntities,
                    unloaded,
                    outsideBuildHeight,
                    counts.size(),
                    summary
            );

            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.literal(
                                "方块扫描 #" + scanId + " 完成：" + scanned
                                        + " 格，" + counts.size()
                                        + " 种方块。日志中搜索 FRACTURE_BLOCK_SCAN #" + scanId
                        ),
                        false
                );
            }
        }

        private void logCancelled(String reason) {
            FractureClient.LOGGER.info(
                    "FRACTURE_BLOCK_SCAN #{} CANCELLED scanned={}/{} reason={}",
                    scanId,
                    scanned,
                    volume,
                    reason
            );
        }

        private int progressPercent() {
            return volume == 0L ? 100 : (int)Math.min(100L, scanned * 100L / volume);
        }
    }
}
