package monster.east.matchaff.client;

import java.util.List;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.InvalidateRenderStateCallback;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import monster.east.matchaff.mechanic.FoodHealMechanics;
import monster.east.matchaff.compat.TrinketsCompat;
import monster.east.matchaff.MatchaFlavouredFabric;
import monster.east.matchaff.network.SleepFastForwardPayload;

public final class MatchaFlavouredClient implements ClientModInitializer {
	private static final int CLOUD_TIME_SCALE = 100;
	private static int sleepRate;
	private static ClientLevel trackedLevel;
	private static long extraCloudTicks;
	public static final String NO_LEAF_EXTENSIONS_PACK = "matcha-flavoured:no_leaf_extensions";
	public static final String VANILLA_PREVIEW_PACK = "matcha-flavoured:vanilla_preview";
	public static final String TRINKETS_MATCHA_PACK = "matcha-flavoured:trinkets_matcha";

	@Override
	public void onInitializeClient() {
		ItemTooltipCallback.EVENT.register(MatchaFlavouredClient::appendFoodHealingTooltip);
		ItemTooltipCallback.EVENT.register(MatchaFlavouredClient::appendRepairTooltip);
		ClientPlayNetworking.registerGlobalReceiver(SleepFastForwardPayload.TYPE, (payload, context) ->
				context.client().execute(() -> sleepRate = payload.active() ? CLOUD_TIME_SCALE : 0));
		ClientTickEvents.END_CLIENT_TICK.register(MatchaFlavouredClient::tickCloudTime);
		ClientTickEvents.END_CLIENT_TICK.register(DolabraVisuals::tick);
		if (FabricLoader.getInstance().isModLoaded(TrinketsCompat.MOD_ID)) {
			TrinketsCompatClient.init();
			ResourceLoader.registerBuiltinPack(
					Identifier.parse(TRINKETS_MATCHA_PACK),
					FabricLoader.getInstance().getModContainer("matcha-flavoured").orElseThrow(),
					Component.translatable("matcha.config.trinkets_matcha.pack"),
					PackActivationType.ALWAYS_ENABLED
			);
		}
		MatchaClientConfig.load();
		ClientLifecycleEvents.CLIENT_STARTED.register(MatchaFlavouredClient::refreshVanillaPreview);
		InvalidateRenderStateCallback.EVENT.register(() -> refreshVanillaPreview(Minecraft.getInstance()));
		ResourceLoader.registerBuiltinPack(
				Identifier.parse(NO_LEAF_EXTENSIONS_PACK),
				FabricLoader.getInstance().getModContainer("matcha-flavoured").orElseThrow(),
				Component.translatable("matcha.config.no_leaf_extensions.pack"),
				PackActivationType.NORMAL
		);
		ResourceLoader.registerBuiltinPack(
				Identifier.parse(VANILLA_PREVIEW_PACK),
				FabricLoader.getInstance().getModContainer("matcha-flavoured").orElseThrow(),
				Component.translatable("matcha.config.vanilla_preview.pack"),
				PackActivationType.NORMAL
		);
	}

	private static void appendFoodHealingTooltip(ItemStack stack, Item.TooltipContext context,
			TooltipFlag flag, List<Component> lines) {
		if (stack.get(DataComponents.FOOD) == null) {
			return;
		}
		TooltipDisplay display = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
		if (!display.shows(DataComponents.LORE)) {
			return;
		}
		Consumable consumable = stack.get(DataComponents.CONSUMABLE);
		if (consumable == null) {
			return;
		}

		int healingPoints = 0;
		for (var consumeEffect : consumable.onConsumeEffects()) {
			if (!(consumeEffect instanceof ApplyStatusEffectsConsumeEffect apply) || apply.probability() <= 0.0F) {
				continue;
			}
			List<MobEffectInstance> regens = apply.effects().stream()
					.filter(effect -> effect.getEffect() == MobEffects.REGENERATION)
					.toList();
			healingPoints += FoodHealMechanics.firstHealingPoints(regens);
		}
		if (healingPoints <= 0) {
			return;
		}

		for (int i = 0; i < lines.size(); i++) {
			String oldText = lines.get(i).getString();
			if (isHealingLine(oldText)) {
				lines.set(i, healingTooltip(healingPoints));
				return;
			}
		}
		lines.add(Math.min(1, lines.size()), healingTooltip(healingPoints));
	}

	private static Component healingTooltip(int healingPoints) {
		String text = "\uE030".repeat(healingPoints / 2)
				+ (healingPoints % 2 == 0 ? "" : "\uE032");
		return Component.literal(text)
				.withStyle(style -> style.withColor(ChatFormatting.RED).withItalic(false));
	}

	private static boolean isHealingLine(String text) {
		return !text.isEmpty() && text.chars().allMatch(character ->
				character == '❤' || character == '❣' || character == '\uE030' || character == '\uE032');
	}

	private static void appendRepairTooltip(ItemStack stack, Item.TooltipContext context,
			TooltipFlag flag, List<Component> lines) {
		Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if ("matcha".equals(itemId.getNamespace())
				&& (itemId.getPath().endsWith("_laurel")
						|| itemId.getPath().endsWith("_circlet")
						|| itemId.getPath().endsWith("_earrings"))) {
			return;
		}
		TooltipDisplay display = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
		if (!display.shows(DataComponents.REPAIRABLE)) {
			return;
		}

		Repairable repairable = stack.get(DataComponents.REPAIRABLE);
		if (repairable == null || repairable.items().size() == 0) {
			return;
		}
		boolean matchaItem = "matcha".equals(itemId.getNamespace());
		boolean customVanillaItem = "minecraft".equals(itemId.getNamespace())
				&& (itemId.getPath().startsWith("netherite_")
						|| itemId.getPath().equals("trident")
						|| (itemId.getPath().equals("elytra")
								&& containsMaterial(repairable, Items.HONEYCOMB)
								&& containsMaterial(repairable, Items.FEATHER)));
		if (!matchaItem && !customVanillaItem) {
			return;
		}

		Component header = Component.translatable("tooltip.matcha.repaired_with")
				.withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false));
		if (lines.contains(header)) {
			return;
		}
		int insertionIndex = lines.size();
		if (flag.isAdvanced()) {
			Component advancedId = Component.literal(itemId.toString()).withStyle(ChatFormatting.DARK_GRAY);
			int advancedIndex = lines.indexOf(advancedId);
			if (advancedIndex >= 0) {
				insertionIndex = advancedIndex;
			}
		}
		lines.add(insertionIndex++, header);
		for (Holder<Item> material : repairable.items()) {
			lines.add(insertionIndex++, new ItemStack(material).getHoverName()
					.copy().withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY).withItalic(false)));
		}
	}

	private static boolean containsMaterial(Repairable repairable, Item item) {
		for (Holder<Item> material : repairable.items()) {
			if (material.value() == item) {
				return true;
			}
		}
		return false;
	}

	public static long acceleratedCloudTime(long gameTime) {
		return gameTime + extraCloudTicks;
	}

	private static void tickCloudTime(Minecraft client) {
		if (client.level != trackedLevel) {
			trackedLevel = client.level;
			extraCloudTicks = 0;
			sleepRate = 0;
		}
		if (client.level != null && sleepRate > 0) {
			extraCloudTicks += CLOUD_TIME_SCALE - 1L;
		}
	}

	private static void refreshVanillaPreview(Minecraft client) {
		boolean active = client.getResourcePackRepository().getSelectedIds().contains(VANILLA_PREVIEW_PACK);
		boolean changed = active != MatchaFlavouredFabric.vanillaPreviewActive();
		MatchaFlavouredFabric.setVanillaPreviewActive(active);
		if (client.level != null && (active || changed)) {
			client.level.clearTintCaches();
		}
	}
}
