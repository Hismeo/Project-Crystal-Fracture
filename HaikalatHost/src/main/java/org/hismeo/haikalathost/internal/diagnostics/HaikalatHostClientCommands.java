package org.hismeo.haikalathost.internal.diagnostics;

import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.hismeo.haikalathost.internal.api.MinecraftHaikalatAssets;
import org.hismeo.haikalathost.internal.runtime.MinecraftHaikalatRuntime;

import java.util.Objects;

/**
 * Developer-facing commands for inspecting and exercising the Host runtime.
 */
public final class HaikalatHostClientCommands {
    private static final MinecraftHaikalatAssets ASSETS = new MinecraftHaikalatAssets();

    private HaikalatHostClientCommands() {
    }

    public static void register(
            RegisterClientCommandsEvent event,
            MinecraftHaikalatRuntime runtime
    ) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(runtime, "runtime");

        event.getDispatcher().register(
                Commands.literal("haikalathost")
                        .then(Commands.literal("status")
                                .executes(context -> {
                                    HostDiagnosticsFormatter.format(
                                                    runtime.status(),
                                                    runtime.renderTarget(),
                                                    runtime.sceneRepositorySnapshot(),
                                                    HostVersionSnapshot.capture(),
                                                    runtime.presentationTarget(),
                                                    runtime.camera(),
                                                    runtime.embeddedRendererSnapshot())
                                            .forEach(line -> context.getSource().sendSuccess(
                                                    () -> Component.literal(line),
                                                    false));
                                    return 1;
                                }))
                        .then(Commands.literal("assets")
                                .executes(context -> {
                                    HostDiagnosticsFormatter.formatAssets(ASSETS.all())
                                            .forEach(line -> context.getSource().sendSuccess(
                                                    () -> Component.literal(line),
                                                    false));
                                    return 1;
                                }))
                        .then(Commands.literal("extensions")
                                .executes(context -> {
                                    HostDiagnosticsFormatter.formatExtensions(
                                                    runtime.extensionSnapshots())
                                            .forEach(line -> context.getSource().sendSuccess(
                                                    () -> Component.literal(line),
                                                    false));
                                    return 1;
                                }))
                        .then(Commands.literal("embedded_probe")
                                .then(Commands.argument(
                                                "enabled",
                                                BoolArgumentType.bool())
                                        .executes(context -> {
                                            boolean enabled =
                                                    BoolArgumentType.getBool(
                                                            context,
                                                            "enabled");
                                            System.setProperty(
                                                    "haikalathost.embeddedPipelineProbe",
                                                    Boolean.toString(enabled));
                                            context.getSource().sendSuccess(
                                                    () -> Component.literal(
                                                            "Haikalat embedded pipeline probe "
                                                                    + (enabled
                                                                    ? "enabled"
                                                                    : "disabled")),
                                                    false);
                                            return 1;
                                        })))
                        .then(Commands.literal("reload")
                                .executes(context -> {
                                    context.getSource().sendSuccess(
                                            () -> Component.literal(
                                                    "Starting Minecraft resource reload"),
                                            false);
                                    Minecraft minecraft = Minecraft.getInstance();
                                    minecraft.reloadResourcePacks().whenComplete(
                                            (ignored, failure) -> minecraft.execute(() -> {
                                                if (failure == null) {
                                                    context.getSource().sendSuccess(
                                                            () -> Component.literal(
                                                                    "Minecraft resources reloaded; "
                                                                            + "extension notification "
                                                                            + "and optional Scene CPU "
                                                                            + "preflight are queued"),
                                                            false);
                                                } else {
                                                    context.getSource().sendFailure(
                                                            Component.literal(
                                                                    "Minecraft resource reload "
                                                                            + "failed: "
                                                                            + messageOf(failure)));
                                                }
                                            }));
                                    return 1;
                                })));
    }

    private static String messageOf(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null
                && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }
}
