package monster.east.matchaff.network;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SleepFastForwardPayload(boolean active) implements CustomPacketPayload {
	public static final Type<SleepFastForwardPayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath("matcha-flavoured", "sleep_fast_forward"));
	public static final StreamCodec<ByteBuf, SleepFastForwardPayload> CODEC =
			ByteBufCodecs.BOOL.map(SleepFastForwardPayload::new, SleepFastForwardPayload::active);

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
