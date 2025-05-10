package org.hismeo.nuquest.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.hismeo.crystallib.client.util.MinecraftUtil;
import org.hismeo.nuquest.client.gui.screen.DialogueScreen;
import org.hismeo.nuquest.core.dialogue.DialogueArgument;
import org.hismeo.nuquest.core.dialogue.DialogueManager;
import org.hismeo.nuquest.core.dialogue.context.DialogueDefinition;

public class DialogueCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dialogue").requires(stack -> {
                    return stack.hasPermission(2);
                }).then(Commands.argument("suggestId", DialogueArgument.dialogue()).executes(context -> {
                    return openDialogueScreen(context.getInput());
                })).then(Commands.argument("id", StringArgumentType.string()).executes(context -> {
                    return openDialogueScreen(context.getInput());
                }))
        );
    }

    public static int openDialogueScreen(String id) {
        DialogueDefinition dialogueDefinition = DialogueManager.getValue(id);
        MinecraftUtil.getMinecraft().setScreen(new DialogueScreen(dialogueDefinition));
        return 0;
    }
}
