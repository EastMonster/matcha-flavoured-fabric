package monster.east.matchaff.mechanic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import monster.east.matchaff.network.BlazeRodDropsPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
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
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (FabricLoader.getInstance().isModLoaded("enderscape")) {
				data(server);
			}
		});
		ServerPlayerEvents.JOIN.register(player -> {
			if (FabricLoader.getInstance().isModLoaded("enderscape")) {
				data(player.level().getServer()).notifyIfPending(player);
			}
		});
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
		Settings settings = server.overworld().getDataStorage().computeIfAbsent(Settings.TYPE);
		settings.initializeEnderscape();
		return settings;
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

	private static final class Settings extends SavedData {
		private static final Codec<Settings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.BOOL.optionalFieldOf("blaze_rod_drops", false).forGetter(settings -> settings.enabled),
				Codec.BOOL.optionalFieldOf("enderscape_initialized", false).forGetter(settings -> settings.enderscapeInitialized),
				Codec.BOOL.optionalFieldOf("enderscape_notice_pending", false).forGetter(settings -> settings.enderscapeNoticePending)
		).apply(instance, Settings::new));
		private static final SavedDataType<Settings> TYPE = new SavedDataType<>(
				Identifier.fromNamespaceAndPath("matcha", "blaze_rod_drops"),
				() -> new Settings(false, false, false), CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
		private boolean enabled;
		private boolean enderscapeInitialized;
		private boolean enderscapeNoticePending;

		private Settings(boolean enabled, boolean enderscapeInitialized, boolean enderscapeNoticePending) {
			this.enabled = enabled;
			this.enderscapeInitialized = enderscapeInitialized;
			this.enderscapeNoticePending = enderscapeNoticePending;
		}

		private void initializeEnderscape() {
			if (!enderscapeInitialized && FabricLoader.getInstance().isModLoaded("enderscape")) {
				enderscapeInitialized = true;
				enderscapeNoticePending = !enabled;
				enabled = true;
				setDirty();
			}
		}

		private void notifyIfPending(ServerPlayer player) {
			if (enderscapeNoticePending) {
				player.sendSystemMessage(Component.translatable("message.matcha.blaze_rod_drops_auto_enabled", "Enderscape"));
				enderscapeNoticePending = false;
				setDirty();
			}
		}

		private boolean setEnabled(boolean enabled) {
			if (this.enabled == enabled) {
				return false;
			}
			this.enabled = enabled;
			setDirty();
			return true;
		}
	}
}
