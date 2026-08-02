package org.hismeo.fractureclient.client.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.hismeo.fractureclient.client.control.ExplicitRoomController;
import org.hismeo.fractureclient.client.control.RoomSelectionController;
import org.hismeo.fractureclient.client.room.RoomRegion;
import org.hismeo.fractureclient.client.room.RoomRegionStore;

import java.util.List;

/** Small command surface for operations that should not be bound to destructive hotkeys. */
public final class RoomRegionCommand {
    private RoomRegionCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fracture_room")
                .executes(context -> status(context.getSource()))
                .then(Commands.literal("edit")
                        .executes(context -> edit(context.getSource())))
                .then(Commands.literal("save")
                        .executes(context -> save(context.getSource())))
                .then(Commands.literal("cancel")
                        .executes(context -> cancel(context.getSource())))
                .then(Commands.literal("delete")
                        .executes(context -> delete(context.getSource())))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource())))
                .then(Commands.literal("reload")
                        .executes(context -> reload(context.getSource())))
                .then(Commands.literal("height")
                        .then(Commands.argument("floor", IntegerArgumentType.integer())
                                .then(Commands.argument("ceiling", IntegerArgumentType.integer())
                                        .executes(context -> height(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "floor"),
                                                IntegerArgumentType.getInteger(context, "ceiling")
                                        ))))));
    }

    private static int status(CommandSourceStack source) {
        Minecraft minecraft = Minecraft.getInstance();
        if (RoomSelectionController.isActive()) {
            RoomRegion room = RoomSelectionController.draft();
            source.sendSuccess(
                    () -> Component.literal(
                            "正在编辑 “" + room.name() + "”：" + room.columnCount()
                                    + " 格，Y=" + room.floorY() + ".." + room.ceilingY()
                    ),
                    false
            );
        } else {
            int rooms = RoomRegionStore.roomsForCurrentWorld(minecraft).size();
            source.sendSuccess(
                    () -> Component.literal("房间编辑器未开启；当前世界共有 " + rooms + " 个显式房间。"),
                    false
            );
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int edit(CommandSourceStack source) {
        return RoomSelectionController.begin(Minecraft.getInstance())
                ? Command.SINGLE_SUCCESS
                : 0;
    }

    private static int save(CommandSourceStack source) {
        return RoomSelectionController.saveAndExit(Minecraft.getInstance())
                ? Command.SINGLE_SUCCESS
                : 0;
    }

    private static int cancel(CommandSourceStack source) {
        if (!RoomSelectionController.isActive()) {
            source.sendFailure(Component.literal("房间编辑器未开启。"));
            return 0;
        }
        RoomSelectionController.cancel(Minecraft.getInstance(), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int delete(CommandSourceStack source) {
        return RoomSelectionController.deleteContaining(Minecraft.getInstance())
                ? Command.SINGLE_SUCCESS
                : 0;
    }

    private static int list(CommandSourceStack source) {
        List<RoomRegion> rooms = RoomRegionStore.roomsForCurrentWorld(Minecraft.getInstance());
        if (rooms.isEmpty()) {
            source.sendSuccess(() -> Component.literal("当前世界还没有显式房间。"), false);
            return Command.SINGLE_SUCCESS;
        }
        source.sendSuccess(() -> Component.literal("显式房间（" + rooms.size() + "）："), false);
        for (int index = 0; index < Math.min(rooms.size(), 24); index++) {
            RoomRegion room = rooms.get(index);
            source.sendSuccess(
                    () -> Component.literal(
                            "- " + room.name() + "：" + room.columnCount()
                                    + " 格，Y=" + room.floorY() + ".." + room.ceilingY()
                    ),
                    false
            );
        }
        if (rooms.size() > 24) {
            source.sendSuccess(
                    () -> Component.literal("……另有 " + (rooms.size() - 24) + " 个房间。"),
                    false
            );
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int reload(CommandSourceStack source) {
        if (RoomSelectionController.isActive()) {
            source.sendFailure(Component.literal("请先保存或取消当前编辑。"));
            return 0;
        }
        RoomRegionStore.reload();
        ExplicitRoomController.reset();
        source.sendSuccess(() -> Component.literal("已重新读取显式房间文件。"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int height(CommandSourceStack source, int floorY, int ceilingY) {
        return RoomSelectionController.setHeights(Minecraft.getInstance(), floorY, ceilingY)
                ? Command.SINGLE_SUCCESS
                : 0;
    }
}
