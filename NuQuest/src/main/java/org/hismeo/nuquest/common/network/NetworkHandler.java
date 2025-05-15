package org.hismeo.nuquest.common.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.hismeo.nuquest.NuQuest;
import org.hismeo.nuquest.common.network.c2s.CommandPacketC2S;

public final class NetworkHandler {
    public static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(NuQuest.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        CHANNEL.registerMessage(packetId++, CommandPacketC2S.class, CommandPacketC2S::encode, CommandPacketC2S::decode, CommandPacketC2S::handle);
    }
}
