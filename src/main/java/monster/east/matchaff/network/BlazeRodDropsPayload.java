package monster.east.matchaff.network;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record BlazeRodDropsPayload(boolean enabled) implements CustomPacketPayload {
	public static final Type<BlazeRodDropsPayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath("matcha", "blaze_rod_drops"));
	public static final StreamCodec<ByteBuf, BlazeRodDropsPayload> CODEC =
			ByteBufCodecs.BOOL.map(BlazeRodDropsPayload::new, BlazeRodDropsPayload::enabled);

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Request.TYPE, Request.CODEC);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public record Request(boolean update, boolean enabled) implements CustomPacketPayload {
		public static final Type<Request> TYPE = new Type<>(
				Identifier.fromNamespaceAndPath("matcha", "blaze_rod_drops_request"));
		public static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, Request::update,
				ByteBufCodecs.BOOL, Request::enabled, Request::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
