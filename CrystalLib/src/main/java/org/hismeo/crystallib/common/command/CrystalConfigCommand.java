package org.hismeo.crystallib.common.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.hismeo.crystallib.api.config.CrystalConfigApi;

public final class CrystalConfigCommand {
    private CrystalConfigCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("crystalconfig")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("reload")
                                .executes(ctx -> reload(ctx.getSource())))
        );
    }

    private static int reload(CommandSourceStack source) {
        int total = CrystalConfigApi.registered().size();
        int success = CrystalConfigApi.reloadAll();
        int failed = total - success;
        source.sendSuccess(
                () -> Component.literal("CrystalConfig reloaded: " + success + "/" + total + (failed > 0 ? (" (failed " + failed + ")") : "")),
                true
        );
        return failed == 0 ? 1 : 0;
    }
}
