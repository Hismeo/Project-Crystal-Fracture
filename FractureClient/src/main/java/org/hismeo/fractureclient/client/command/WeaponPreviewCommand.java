package org.hismeo.fractureclient.client.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.hismeo.fractureclient.client.weapon.WeaponPreviewScreen;

/** Opens the in-world client-only assembled weapon preview. */
public final class WeaponPreviewCommand {
    public static final String NAME = "fracture_weapon_preview";

    private WeaponPreviewCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(NAME).executes(context -> open(context.getSource())));
    }

    private static int open(CommandSourceStack source) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            source.sendFailure(Component.literal("该预览需要先进入一个客户端世界。"));
            return 0;
        }
        minecraft.setScreen(new WeaponPreviewScreen());
        source.sendSuccess(
                () -> Component.literal("已打开配件武器调试预览。"),
                false);
        return Command.SINGLE_SUCCESS;
    }
}
