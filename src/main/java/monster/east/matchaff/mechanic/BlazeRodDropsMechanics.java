package monster.east.matchaff.mechanic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import monster.east.matchaff.network.BlazeRodDropsPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public final class BlazeRodDropsMechanics {
	private BlazeRodDropsMechanics() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE,
				Identifier.fromNamespaceAndPath("matcha", "blaze_rod_drops_enabled"), EnabledCondition.CODEC);
		BlazeRodDropsPayload.register();
		ServerPlayNetworking.registerGlobalReceiver(BlazeRodDropsPayload.Request.TYPE, (payload, context) -> {
			Settings settings = data(context.server());
			if (payload.update() && context.player().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
					&& settings.setEnabled(payload.enabled())) {
				for (ServerPlayer player : context.server().getPlayerList().getPlayers()) {
					sync(player, settings.enabled);
				}
			} else {
				sync(context.player(), settings.enabled);
			}
		});
	}

	private static Settings data(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(Settings.TYPE);
	}

	private static void sync(ServerPlayer player, boolean enabled) {
		if (ServerPlayNetworking.canSend(player, BlazeRodDropsPayload.TYPE)) {
			ServerPlayNetworking.send(player, new BlazeRodDropsPayload(enabled));
		}
	}

	private record EnabledCondition() implements LootItemCondition {
		private static final MapCodec<EnabledCondition> CODEC = MapCodec.unit(new EnabledCondition());

		@Override
		public MapCodec<EnabledCondition> codec() {
			return CODEC;
		}

		@Override
		public boolean test(LootContext context) {
			return data(context.getLevel().getServer()).enabled;
		}
	}

	static final class Settings extends SavedData {
		static final Codec<Settings> CODEC = Codec.BOOL.optionalFieldOf("blaze_rod_drops", false)
				.xmap(Settings::new, settings -> settings.enabled).codec();
		private static final SavedDataType<Settings> TYPE = new SavedDataType<>(
				Identifier.fromNamespaceAndPath("matcha", "blaze_rod_drops"),
				() -> new Settings(false), CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
		private boolean enabled;

		Settings(boolean enabled) {
			this.enabled = enabled;
		}

		boolean setEnabled(boolean enabled) {
			if (this.enabled == enabled) {
				return false;
			}
			this.enabled = enabled;
			setDirty();
			return true;
		}
	}
}
