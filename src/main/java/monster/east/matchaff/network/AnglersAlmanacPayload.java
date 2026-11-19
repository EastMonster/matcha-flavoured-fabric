package monster.east.matchaff.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public record AnglersAlmanacPayload(List<Water> waters, int selectedWater, Optional<Identifier> currentBiome) implements CustomPacketPayload {
	public static final Type<AnglersAlmanacPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("matcha", "anglers_almanac"));
	private static final StreamCodec<RegistryFriendlyByteBuf, AnglersAlmanacPayload> CODEC = StreamCodec.composite(
			Water.CODEC.apply(ByteBufCodecs.list()), AnglersAlmanacPayload::waters,
			ByteBufCodecs.VAR_INT, AnglersAlmanacPayload::selectedWater,
			Identifier.STREAM_CODEC.apply(ByteBufCodecs::optional), AnglersAlmanacPayload::currentBiome, AnglersAlmanacPayload::new);

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public record Water(Component title, ItemStack icon, List<Identifier> biomes, List<Catch> catches) {
		private static final StreamCodec<RegistryFriendlyByteBuf, Water> CODEC = StreamCodec.composite(
				ComponentSerialization.TRUSTED_STREAM_CODEC, Water::title,
				ItemStack.OPTIONAL_STREAM_CODEC, Water::icon,
				Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), Water::biomes,
				Catch.CODEC.apply(ByteBufCodecs.list()), Water::catches, Water::new);
	}

	public record Catch(ItemStack icon, int count, Component name, Component description) {
		private static final StreamCodec<RegistryFriendlyByteBuf, Catch> CODEC = StreamCodec.composite(
				ItemStack.OPTIONAL_STREAM_CODEC, Catch::icon,
				ByteBufCodecs.VAR_INT, Catch::count,
				ComponentSerialization.TRUSTED_STREAM_CODEC, Catch::name,
				ComponentSerialization.TRUSTED_STREAM_CODEC, Catch::description, Catch::new);
	}
}
