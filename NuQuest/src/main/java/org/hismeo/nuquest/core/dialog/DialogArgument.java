package org.hismeo.nuquest.core.dialog;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import org.hismeo.nuquest.core.data.dialog.DialogManager;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DialogArgument implements ArgumentType<String> {
    public static DialogArgument dialogue() {
        return new DialogArgument();
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        return reader.readString();
    }

    public static String getDialogueId(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, String.class);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(DialogManager.getDialogueMapView(), builder);
    }

    @Override
    public Collection<String> getExamples() {
        return List.of("npc_intro", "main_quest_1", "trader_talk");
    }
}
