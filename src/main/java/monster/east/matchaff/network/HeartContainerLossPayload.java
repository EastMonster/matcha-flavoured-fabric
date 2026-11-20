package monster.east.matchaff.network;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record HeartContainerLossPayload(boolean enabled) implements CustomPacketPayload {
	public static final Type<HeartContainerLossPayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath("matcha", "heart_container_loss_on_death"));
	public static final StreamCodec<ByteBuf, HeartContainerLossPayload> CODEC =
			ByteBufCodecs.BOOL.map(HeartContainerLossPayload::new, HeartContainerLossPayload::enabled);

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
				Identifier.fromNamespaceAndPath("matcha", "heart_container_loss_on_death_request"));
		public static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, Request::update,
				ByteBufCodecs.BOOL, Request::enabled, Request::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
