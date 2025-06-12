package org.hismeo.nuquest.common.network.s2c;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.hismeo.crystallib.util.client.MinecraftUtil;
import org.hismeo.nuquest.NuQuest;
import org.hismeo.nuquest.client.gui.screen.DialogScreen;
import org.hismeo.nuquest.core.data.dialog.DialogManager;
import org.hismeo.nuquest.core.dialog.context.DialogDefinition;
import org.jetbrains.annotations.NotNull;

public record DialogShowPacketS2C(String dialogID) implements CustomPacketPayload {
    public static final Type<DialogShowPacketS2C> TYPE = new Type<>(NuQuest.byModResource("dialog_show"));
    public static final StreamCodec<ByteBuf, DialogShowPacketS2C> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, DialogShowPacketS2C::dialogID, DialogShowPacketS2C::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(()->{
            Player player = context.player();
            if (player.isLocalPlayer()){
                DialogDefinition definition = DialogManager.getValue(dialogID);
                MinecraftUtil.setScreen(new DialogScreen(definition));
            }
        }).exceptionally(throwable -> {
            context.disconnect(Component.translatable("neoforge.network.invalid_flow", throwable.getMessage()));
            return null;
        });
    }

    public static void dialogShow(ServerPlayer serverPlayer, String dialogID){
        PacketDistributor.sendToPlayer(serverPlayer, new DialogShowPacketS2C(dialogID));
    }
}
