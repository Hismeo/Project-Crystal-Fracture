package org.hismeo.nuquest.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.hismeo.nuquest.NuQuest;

public record CommandPacketC2S(String command) implements CustomPacketPayload {
    public static final Type<CommandPacketC2S> TYPE = new Type<>(NuQuest.byModResource("command"));
    public static final StreamCodec<ByteBuf, CommandPacketC2S> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, CommandPacketC2S::command, CommandPacketC2S::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer && serverPlayer != null) {
                String finalCommand = this.command.replace("playername", serverPlayer.getScoreboardName());
                MinecraftServer server = serverPlayer.server;
                CommandSourceStack commandSourceStack = server.createCommandSourceStack().withSuppressedOutput();

                server.getCommands().performPrefixedCommand(commandSourceStack, finalCommand);
            }
        });
    }

    public static void sendToServer(String command) {
        PacketDistributor.sendToServer(new CommandPacketC2S(command));
    }
}
