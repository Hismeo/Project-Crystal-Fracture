package org.hismeo.nuquest.common.network.c2s;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record CommandPacketC2S(String command) {
    public static void encode(CommandPacketC2S packet, FriendlyByteBuf friendlyByteBuf) {
        friendlyByteBuf.writeUtf(packet.command);
    }

    public static CommandPacketC2S decode(FriendlyByteBuf friendlyByteBuf) {
        return new CommandPacketC2S(friendlyByteBuf.readUtf());
    }

    public static void handle(CommandPacketC2S packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer serverPlayer = context.getSender();
            if (serverPlayer != null) {
                String finalCommand = packet.command.replace("playername", serverPlayer.getScoreboardName());
                MinecraftServer server = serverPlayer.server;
                CommandSourceStack commandSourceStack = server.createCommandSourceStack().withSuppressedOutput();

                server.getCommands().performPrefixedCommand(commandSourceStack, finalCommand);
            }
        });
    }
}
