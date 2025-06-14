package org.hismeo.nuquest.common.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.crystallib.util.client.MinecraftUtil;
import org.hismeo.nuquest.client.gui.screen.DialogScreen;
import org.hismeo.nuquest.core.dialog.DialogArgument;
import org.hismeo.nuquest.core.data.dialog.DialogManager;
import org.hismeo.nuquest.core.dialog.context.DialogDefinition;

public class DialogCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("dialog")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", DialogArgument.dialog())
                                .executes(ctx -> {
                                    ResourceLocation id = DialogArgument.getDialogId(ctx, "id");
                                    return openDialogScreen(id);
                                })
                        )
        );
    }

    private static int openDialogScreen(ResourceLocation id) {
        DialogDefinition definition = DialogManager.getValue(id.toString());
        MinecraftUtil.setScreen(new DialogScreen(definition));
        return 1;
    }
}
