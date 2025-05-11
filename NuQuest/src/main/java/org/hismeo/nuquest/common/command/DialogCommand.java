package org.hismeo.nuquest.common.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.crystallib.client.util.MinecraftUtil;
import org.hismeo.nuquest.client.gui.screen.DialogScreen;
import org.hismeo.nuquest.core.dialog.DialogArgument;
import org.hismeo.nuquest.core.data.dialog.DialogManager;
import org.hismeo.nuquest.core.dialog.context.DialogDefinition;

public class DialogCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("dialog")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", DialogArgument.dialogue())
                                .executes(ctx -> {
                                    ResourceLocation id = DialogArgument.getDialogueId(ctx, "id");
                                    return openDialogueScreen(id);
                                })
                        )
        );
    }

    private static int openDialogueScreen(ResourceLocation id) {
        DialogDefinition def = DialogManager.getValue(id.toString());
        MinecraftUtil.getMinecraft().setScreen(new DialogScreen(def));
        return 1;
    }
}
