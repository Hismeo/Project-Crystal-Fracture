package org.hismeo.crystalfracture.dash;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.hismeo.crystalfracture.CrystalFracture;

/** Synchronizes each collision-resolved authoritative dash delta to the owning client. */
public final class DashNetwork {
    private DashNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(DashStepS2C.TYPE, DashStepS2C.CODEC, DashStepS2C::handle);
    }

    public static void sendStep(ServerPlayer player, Vec3 delta) {
        if (delta.lengthSqr() > 0.0) {
            PacketDistributor.sendToPlayer(player, new DashStepS2C(delta.x, delta.y, delta.z));
        }
    }

    public record DashStepS2C(double x, double y, double z) implements CustomPacketPayload {
        public static final Type<DashStepS2C> TYPE = new Type<>(
                CrystalFracture.packRL("dash_step"));
        public static final StreamCodec<ByteBuf, DashStepS2C> CODEC = StreamCodec.ofMember(
                DashStepS2C::encode, DashStepS2C::decode);

        private void encode(ByteBuf buffer) {
            buffer.writeDouble(x).writeDouble(y).writeDouble(z);
        }

        private static DashStepS2C decode(ByteBuf buffer) {
            return new DashStepS2C(
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        }

        private void handle(IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player().isLocalPlayer()) {
                    context.player().move(MoverType.SELF, new Vec3(x, y, z));
                }
            });
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
