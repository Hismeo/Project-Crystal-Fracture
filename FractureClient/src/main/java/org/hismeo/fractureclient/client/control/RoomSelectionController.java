package org.hismeo.fractureclient.client.control;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.hismeo.fractureclient.client.init.KeyInit;
import org.hismeo.fractureclient.client.room.RoomRegion;
import org.hismeo.fractureclient.client.room.RoomRegionStore;

import java.util.ArrayDeque;
import java.util.Deque;

/** Client-only virtual selection wand for additive/subtractive room footprints. */
public final class RoomSelectionController {
    private static final int MAX_UNDO_STEPS = 32;
    private static final int AUTO_CEILING_SEARCH = 32;

    private static final Deque<RoomRegion> UNDO = new ArrayDeque<>();
    private static ClientLevel trackedLevel;
    private static RoomRegion draft;
    private static BlockPos anchor;

    private RoomSelectionController() {
    }

    public static void tick(Minecraft minecraft) {
        if (draft != null && (minecraft.level == null || minecraft.level != trackedLevel)) {
            cancel(minecraft, false);
        }

        while (KeyInit.ROOM_EDITOR.consumeClick()) {
            if (isActive()) {
                saveAndExit(minecraft);
            } else {
                begin(minecraft);
            }
        }
        while (KeyInit.ROOM_EDITOR_CANCEL.consumeClick()) {
            cancel(minecraft, true);
        }
        while (KeyInit.ROOM_EDITOR_UNDO.consumeClick()) {
            undo(minecraft);
        }
        while (KeyInit.ROOM_EDITOR_HEIGHT.consumeClick()) {
            setHeightFromTarget(minecraft);
        }
    }

    public static boolean begin(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            return false;
        }
        if (draft != null) {
            message(minecraft, "房间编辑器已经开启。", true);
            return true;
        }

        BlockPos playerPos = BlockPos.containing(minecraft.player.getBoundingBox().getCenter());
        RoomRegion existing = RoomRegionStore.findContaining(minecraft, playerPos);
        if (existing != null) {
            draft = existing.copy();
            message(minecraft, "正在编辑房间 “" + draft.name() + "”。", false);
        } else {
            int floorY = Mth.floor(minecraft.player.getBoundingBox().minY + 1.0e-4);
            int ceilingY = findAutomaticCeiling(
                    minecraft.level,
                    playerPos.getX(),
                    playerPos.getZ(),
                    floorY
            );
            draft = RoomRegionStore.createRoom(
                    minecraft,
                    RoomRegionStore.nextRoomName(minecraft),
                    floorY,
                    ceilingY
            );
            message(minecraft, "已新建 “" + draft.name() + "”，请左键设起点。", false);
        }

        trackedLevel = minecraft.level;
        anchor = null;
        UNDO.clear();
        ExplicitRoomController.reset();
        return true;
    }

    public static boolean saveAndExit(Minecraft minecraft) {
        if (draft == null) {
            return false;
        }
        if (draft.isEmpty()) {
            message(minecraft, "选区为空，无法保存。", false);
            return false;
        }
        if (!draft.isConnected()) {
            message(minecraft, "选区存在互不连接的孤岛；请连接它们或潜行右键删除多余部分。", false);
            return false;
        }

        RoomRegion saved = draft;
        RoomRegionStore.upsert(saved);
        boolean persisted = RoomRegionStore.save();
        clearEditorState();
        ExplicitRoomController.reset();
        message(
                minecraft,
                persisted
                        ? "已保存房间 “" + saved.name() + "”（" + saved.columnCount() + " 格）。"
                        : "房间已应用，但写入本地文件失败；详情见日志。",
                false
        );
        return persisted;
    }

    public static void cancel(Minecraft minecraft, boolean announce) {
        if (draft == null) {
            return;
        }
        String name = draft.name();
        clearEditorState();
        ExplicitRoomController.reset();
        if (announce) {
            message(minecraft, "已取消对房间 “" + name + "” 的修改。", false);
        }
    }

    public static boolean deleteContaining(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || isActive()) {
            return false;
        }
        BlockPos playerPos = BlockPos.containing(minecraft.player.getBoundingBox().getCenter());
        RoomRegion removed = RoomRegionStore.removeContaining(minecraft, playerPos);
        if (removed == null) {
            message(minecraft, "当前位置没有显式房间。", false);
            return false;
        }
        RoomRegionStore.save();
        ExplicitRoomController.reset();
        message(minecraft, "已删除房间 “" + removed.name() + "”。", false);
        return true;
    }

    public static boolean setHeights(Minecraft minecraft, int floorY, int ceilingY) {
        if (draft == null) {
            message(minecraft, "请先开启房间编辑器。", false);
            return false;
        }
        RoomRegion before = draft.copy();
        if (!draft.setHeights(floorY, ceilingY)) {
            message(
                    minecraft,
                    "高度无效：天花板必须高于地板，且房间高度不能超过 "
                            + RoomRegion.MAX_HEIGHT + " 格。",
                    false
            );
            return false;
        }
        pushUndo(before);
        message(minecraft, "房间高度已设为 Y=" + floorY + ".." + ceilingY + "。", true);
        return true;
    }

    public static void handleAttack(Minecraft minecraft) {
        BlockHitResult hit = blockHit(minecraft);
        if (draft == null || hit == null) {
            message(minecraft, "请瞄准一个方块。", true);
            return;
        }

        BlockPos selectedBlock = hit.getBlockPos();
        BlockPos selectedColumn = selectedBlock.relative(hit.getDirection());
        if (draft.isEmpty() && hit.getDirection() == Direction.UP) {
            int floorY = selectedBlock.getY() + 1;
            int ceilingY = findAutomaticCeiling(
                    minecraft.level,
                    selectedColumn.getX(),
                    selectedColumn.getZ(),
                    floorY
            );
            draft.setHeights(floorY, ceilingY);
        }
        anchor = new BlockPos(selectedColumn.getX(), draft.floorY(), selectedColumn.getZ());
        message(
                minecraft,
                "起点：" + anchor.getX() + ", " + anchor.getZ()
                        + "；右键添加，潜行+右键减去。",
                true
        );
    }

    public static void handleUse(Minecraft minecraft) {
        BlockHitResult hit = blockHit(minecraft);
        if (draft == null || hit == null) {
            message(minecraft, "请瞄准一个方块。", true);
            return;
        }
        if (anchor == null) {
            BlockPos selected = hit.getBlockPos().relative(hit.getDirection());
            anchor = new BlockPos(selected.getX(), draft.floorY(), selected.getZ());
            message(minecraft, "尚未设置起点，已将当前方块设为起点。", true);
            return;
        }

        BlockPos target = hit.getBlockPos().relative(hit.getDirection());
        RoomRegion before = draft.copy();
        int beforeCount = draft.columnCount();
        boolean subtract = minecraft.player != null && minecraft.player.isShiftKeyDown();
        if (subtract) {
            draft.removeRectangle(anchor.getX(), anchor.getZ(), target.getX(), target.getZ());
        } else if (!draft.addRectangle(
                anchor.getX(),
                anchor.getZ(),
                target.getX(),
                target.getZ()
        )) {
            message(
                    minecraft,
                    "矩形过大；单个房间最多允许 " + RoomRegion.MAX_COLUMNS + " 个地面格。",
                    false
            );
            return;
        }

        if (draft.columnCount() != beforeCount) {
            pushUndo(before);
        }
        message(
                minecraft,
                (subtract ? "已减去矩形，当前 " : "已添加矩形，当前 ")
                        + draft.columnCount() + " 格。",
                true
        );
        anchor = null;
    }

    public static void undo(Minecraft minecraft) {
        if (draft == null || UNDO.isEmpty()) {
            if (draft != null) {
                message(minecraft, "没有可以撤销的操作。", true);
            }
            return;
        }
        draft = UNDO.removeLast();
        anchor = null;
        message(minecraft, "已撤销，当前 " + draft.columnCount() + " 格。", true);
    }

    public static void setHeightFromTarget(Minecraft minecraft) {
        BlockHitResult hit = blockHit(minecraft);
        if (draft == null || hit == null || minecraft.player == null) {
            return;
        }
        RoomRegion before = draft.copy();
        boolean floor = minecraft.player.isShiftKeyDown();
        int nextFloor = floor ? hit.getBlockPos().getY() + 1 : draft.floorY();
        int nextCeiling = floor ? draft.ceilingY() : hit.getBlockPos().getY();
        if (!draft.setHeights(nextFloor, nextCeiling)) {
            message(
                    minecraft,
                    floor
                            ? "新地板必须低于当前天花板。"
                            : "请瞄准高于地板、且不超过 64 格的天花板方块。",
                    false
            );
            return;
        }
        pushUndo(before);
        message(
                minecraft,
                floor
                        ? "已将内部地板设为 Y=" + nextFloor + "。"
                        : "已将天花板方块设为 Y=" + nextCeiling + "。",
                true
        );
    }

    public static void renderHud(GuiGraphics graphics, Minecraft minecraft) {
        if (draft == null || minecraft.player == null) {
            return;
        }
        int x = 8;
        int y = 8;
        graphics.drawString(minecraft.font, "[Fracture 房间编辑] " + draft.name(), x, y, 0xFFFFD866, true);
        graphics.drawString(
                minecraft.font,
                "格数 " + draft.columnCount() + "  高度 Y=" + draft.floorY() + ".." + draft.ceilingY(),
                x,
                y + 11,
                0xFFFFFFFF,
                true
        );
        graphics.drawString(
                minecraft.font,
                anchor == null
                        ? "左键设起点；右键应用；潜行+右键减去"
                        : "起点 " + anchor.getX() + "," + anchor.getZ() + "；右键完成矩形",
                x,
                y + 22,
                0xFFBFE8FF,
                true
        );
        graphics.drawString(
                minecraft.font,
                "F6 保存  F7 取消  Ctrl+Z/中键撤销  H 天花板  潜行+H 地板",
                x,
                y + 33,
                0xFFB8B8B8,
                true
        );
    }

    public static boolean isActive() {
        return draft != null;
    }

    public static RoomRegion draft() {
        return draft;
    }

    public static BlockPos anchor() {
        return anchor;
    }

    private static BlockHitResult blockHit(Minecraft minecraft) {
        if (minecraft.hitResult instanceof BlockHitResult blockHit
                && blockHit.getType() == HitResult.Type.BLOCK) {
            return blockHit;
        }
        return null;
    }

    private static int findAutomaticCeiling(ClientLevel level, int x, int z, int floorY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int maximumY = Math.min(level.getMaxBuildHeight() - 1, floorY + AUTO_CEILING_SEARCH);
        for (int y = floorY + 2; y <= maximumY; y++) {
            pos.set(x, y, z);
            var state = level.getBlockState(pos);
            if (!RoomCullScanner.isRoomPassable(level, pos, state)) {
                return y;
            }
        }
        return Math.min(maximumY, floorY + 5);
    }

    private static void pushUndo(RoomRegion before) {
        if (UNDO.size() >= MAX_UNDO_STEPS) {
            UNDO.removeFirst();
        }
        UNDO.addLast(before);
    }

    private static void clearEditorState() {
        trackedLevel = null;
        draft = null;
        anchor = null;
        UNDO.clear();
    }

    private static void message(Minecraft minecraft, String text, boolean actionBar) {
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(text), actionBar);
        }
    }
}
