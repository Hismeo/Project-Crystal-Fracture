package org.hismeo.nuquest.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.hismeo.crystallib.client.util.MinecraftUtil;
import org.hismeo.nuquest.client.gui.screen.DialogScreen;
import org.hismeo.nuquest.core.dialog.DialogArgument;
import org.hismeo.nuquest.core.data.dialog.DialogManager;
import org.hismeo.nuquest.core.dialog.context.DialogDefinition;

public class DialogCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dialog").requires(stack -> {
                    return stack.hasPermission(2);
                }).then(Commands.argument("suggestId", DialogArgument.dialogue()).executes(context -> {
                    return openDialogueScreen(context.getInput());
                })).then(Commands.argument("id", StringArgumentType.string()).executes(context -> {
                    return openDialogueScreen(context.getInput());
                }))
        );
    }

    public static int openDialogueScreen(String id) {
        DialogDefinition dialogDefinition = DialogManager.getValue(id);
        MinecraftUtil.getMinecraft().setScreen(new DialogScreen(dialogDefinition));
        return 0;
    }
}
