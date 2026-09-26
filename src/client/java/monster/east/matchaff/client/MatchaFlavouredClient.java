package monster.east.matchaff.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import monster.east.matchaff.mechanic.FoodHealMechanics;
import monster.east.matchaff.compat.TrinketsCompat;
import monster.east.matchaff.MatchaFlavouredFabric;
import monster.east.matchaff.network.SleepFastForwardPayload;

public final class MatchaFlavouredClient implements ClientModInitializer {
	private static final int CLOUD_TIME_SCALE = 100;
	// Attack damage and attack speed show resulting values; other entries show the item's bonus.
	// ponytail: absolute values use vanilla player bases; read live bases if another mod changes tooltip math.
	private static final List<AttributeLore> EQUIPMENT_ATTRIBUTE_LORE = List.of(
		new AttributeLore(Attributes.ATTACK_DAMAGE, "desc.matcha.attack_damage", 1.0, true, false),
		new AttributeLore(Attributes.ATTACK_SPEED, "desc.matcha.cooldown", 4.0, true, false),
		new AttributeLore(Attributes.ATTACK_KNOCKBACK, "desc.matcha.knockback_attribute", 0.0, false, false),
		new AttributeLore(Attributes.ARMOR, "desc.matcha.armour", 0.0, false, false),
		new AttributeLore(Attributes.ARMOR_TOUGHNESS, "desc.matcha.armour_toughness", 0.0, false, false),
		new AttributeLore(Attributes.MOVEMENT_SPEED, "desc.matcha.speed_attribute", 0.1, false, true),
		new AttributeLore(Attributes.ENTITY_INTERACTION_RANGE, "desc.matcha.entity_interaction_range", 3.0, false, false),
		new AttributeLore(Attributes.SAFE_FALL_DISTANCE, "desc.matcha.fall_height_attribute", 3.0, false, false),
		new AttributeLore(Attributes.KNOCKBACK_RESISTANCE, "desc.matcha.knockback_resistance", 0.0, false, false),
		new AttributeLore(Attributes.STEP_HEIGHT, "desc.matcha.step_height", 0.6, false, false)
	);
	private static int sleepRate;
	private static ClientLevel trackedLevel;
	private static long extraCloudTicks;
	public static final String NO_LEAF_EXTENSIONS_PACK = "matcha-flavoured:no_leaf_extensions";
	public static final String VANILLA_PREVIEW_PACK = "matcha-flavoured:vanilla_preview";
	public static final String TRINKETS_MATCHA_PACK = "matcha-flavoured:trinkets_matcha";

	private record AttributeLore(Holder<Attribute> attribute, String translationKey, double baseValue,
		boolean absoluteValue, boolean explicitPositiveSign) {
	}

	@Override
	public void onInitializeClient() {
		ItemTooltipCallback.EVENT.register(MatchaFlavouredClient::appendFoodHealingTooltip);
		ItemTooltipCallback.EVENT.register(MatchaFlavouredClient::appendDynamicEquipmentLore);
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

	private static void appendDynamicEquipmentLore(ItemStack stack, Item.TooltipContext context,
			TooltipFlag flag, List<Component> lines) {
		TooltipDisplay display = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
		// Matcha equipment hides vanilla modifiers; that stays as the opt-in for generated Lore.
		if (!display.shows(DataComponents.LORE) || display.shows(DataComponents.ATTRIBUTE_MODIFIERS)) {
			return;
		}

		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		EquipmentSlot slot = equippable == null ? EquipmentSlot.MAINHAND : equippable.slot();
		ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);

		List<Component> generated = new ArrayList<>();
		appendMiningSpeedLore(stack, generated);
		appendBlockingLore(stack, generated);
		for (AttributeLore lore : EQUIPMENT_ATTRIBUTE_LORE) {
			appendAttributeLore(generated, modifiers, slot, lore);
		}
		appendSetBonusLore(stack, generated);

		int insertionIndex = -1;
		for (int i = 0; i < lines.size(); i++) {
			if (isDynamicEquipmentLore(lines.get(i))) {
				if (insertionIndex < 0) {
					insertionIndex = i;
				}
				lines.remove(i--);
			}
		}
		if (generated.isEmpty()) {
			return;
		}
		Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if ("matcha".equals(itemId.getNamespace())
				&& TrinketsCompat.EARRING_IDS.contains(itemId.getPath())) {
			for (int i = 0; i < lines.size(); i++) {
				if (lines.get(i).getContents() instanceof TranslatableContents contents
						&& contents.getKey().equals("item.unbreakable")) {
					insertionIndex = i;
					break;
				}
			}
		}
		if (insertionIndex < 0) {
			insertionIndex = lines.size();
			if (flag.isAdvanced()) {
				for (int i = 0; i < lines.size(); i++) {
					if (lines.get(i).getContents() instanceof TranslatableContents contents
							&& contents.getKey().equals("item.durability")) {
						insertionIndex = i;
						break;
					}
				}
				if (insertionIndex == lines.size()) {
					int advancedIdIndex = lines.indexOf(
							Component.literal(itemId.toString()).withStyle(ChatFormatting.DARK_GRAY));
					if (advancedIdIndex >= 0) {
						insertionIndex = advancedIdIndex;
					}
				}
			}
		}
		lines.addAll(insertionIndex, generated);
	}

	private static void appendMiningSpeedLore(ItemStack stack, List<Component> lines) {
		Tool tool = stack.get(DataComponents.TOOL);
		if (tool == null) {
			return;
		}

		double speed = tool.rules().stream()
				.filter(rule -> rule.blocks().unwrapKey().map(tag ->
						tag.location().getNamespace().equals("minecraft")
								&& tag.location().getPath().startsWith("mineable/"))
						.orElse(false))
				.flatMapToDouble(rule -> rule.speed().stream().mapToDouble(Float::doubleValue))
				.max().orElse(Double.NaN);
		if (Double.isFinite(speed)) {
			lines.add(loreLine("desc.matcha.mining_speed", formatLoreNumber(speed), ChatFormatting.BLUE));
		}
	}

	private static void appendBlockingLore(ItemStack stack, List<Component> lines) {
		BlocksAttacks blocksAttacks = stack.get(DataComponents.BLOCKS_ATTACKS);
		if (blocksAttacks == null) {
			return;
		}

		Integer melee = null;
		Integer projectile = null;
		for (BlocksAttacks.DamageReduction reduction : blocksAttacks.damageReductions()) {
			if (reduction.base() != 0.0F || reduction.type().isEmpty()) {
				continue;
			}
			int percentage = Math.round(reduction.factor() * 100.0F);
			boolean isProjectile = reduction.type().orElseThrow().stream()
					.anyMatch(type -> type.is(DamageTypeTags.IS_PROJECTILE));
			if (isProjectile) {
				projectile = percentage;
			} else {
				melee = percentage;
			}
		}

		if (melee != null || projectile != null) {
			lines.add(loreLine("tooltip.matcha.when_blocking", ChatFormatting.GRAY));
			if (melee != null) {
				lines.add(loreLine("desc.matcha.melee_blocking", melee.toString(), ChatFormatting.DARK_GRAY));
			}
			if (projectile != null) {
				lines.add(loreLine("desc.matcha.projectile_blocking", projectile.toString(), ChatFormatting.DARK_GRAY));
			}
		}
	}

	private static void appendSetBonusLore(ItemStack stack, List<Component> lines) {
		ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		if (hasEnchantment(enchantments, "adamant_armour")) {
			lines.add(loreLine("tooltip.matcha.set_bonus", ChatFormatting.DARK_GRAY));
			lines.add(loreLine("enchantment.matcha.adamant_armour.set_bonus", ChatFormatting.RED));
			lines.add(loreLine("enchantment.matcha.divinity.set_bonus", ChatFormatting.AQUA));
		} else if (hasEnchantment(enchantments, "adamant_tool")) {
			lines.add(loreLine("tooltip.matcha.set_bonus", ChatFormatting.DARK_GRAY));
			lines.add(loreLine("enchantment.matcha.divinity.set_bonus", ChatFormatting.AQUA));
		}

		if (hasEnchantment(enchantments, "electrum_armour")) {
			lines.add(loreLine("tooltip.matcha.set_bonus", ChatFormatting.DARK_GRAY));
			lines.add(loreLine("enchantment.matcha.warding3.set_bonus_apotropaic", ChatFormatting.YELLOW));
			lines.add(loreLine("enchantment.matcha.divinity.set_bonus", ChatFormatting.AQUA));
		}

		if (hasEnchantment(enchantments, "shakudo_armour")) {
			boolean elytra = Identifier.fromNamespaceAndPath("matcha", "shakudo_elytra")
					.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
			if (elytra) {
				lines.add(loreLine("tooltip.matcha.elytra_bonus", ChatFormatting.DARK_GRAY));
				lines.add(loreLine("enchantment.matcha.shakudo_elytra_regen_bonus", TextColor.fromRgb(0xE85E8F)));
			} else {
				lines.add(loreLine("tooltip.matcha.per_equipment_bonus", ChatFormatting.DARK_GRAY));
				lines.add(loreLine("enchantment.matcha.shakudo_regen_bonus", TextColor.fromRgb(0xE85E8F)));
			}
		}
	}

	private static boolean hasEnchantment(ItemEnchantments enchantments, String path) {
		Identifier id = Identifier.fromNamespaceAndPath("matcha", path);
		return enchantments.keySet().stream().anyMatch(holder ->
            enchantments.getLevel(holder) > 0
                    && holder.unwrapKey().map(key -> key.identifier().equals(id)).orElse(false));
}

	private static Component loreLine(String key, ChatFormatting color) {
		return Component.translatable(key).withStyle(style -> style.withColor(color).withItalic(false));
	}

	private static Component loreLine(String key, TextColor color) {
		return Component.translatable(key).withStyle(style -> style.withColor(color).withItalic(false));
	}

	private static Component loreLine(String key, String value, ChatFormatting color) {
		return Component.translatable(key, value).withStyle(style -> style.withColor(color).withItalic(false));
	}

	private static String formatLoreNumber(double value) {
		return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
	}

	private static void appendAttributeLore(List<Component> lines, ItemAttributeModifiers modifiers,
			EquipmentSlot slot, AttributeLore lore) {
		if (modifiers.modifiers().stream().noneMatch(entry -> entry.attribute() == lore.attribute()
				&& entry.slot().test(slot))) {
			return;
		}

		double value = modifiers.compute(lore.attribute(), lore.baseValue(), slot);
		if (!lore.absoluteValue()) {
			value -= lore.baseValue();
		}
		BigDecimal rounded = BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros();
		if (rounded.signum() == 0) {
			return;
		}

		String amount = rounded.toPlainString();
		if (lore.explicitPositiveSign() && rounded.signum() > 0) {
			amount = "+" + amount;
		}
		double neutralValue = lore.absoluteValue() ? lore.baseValue() : 0.0;
		ChatFormatting color = lore.attribute() == Attributes.ENTITY_INTERACTION_RANGE && value < neutralValue
				? ChatFormatting.RED : ChatFormatting.DARK_GREEN;
		lines.add(loreLine(lore.translationKey(), amount, color));
	}

	private static boolean isDynamicEquipmentLore(Component line) {
		if (!(line.getContents() instanceof TranslatableContents contents)) {
			return false;
		}
		String key = contents.getKey();
		return EQUIPMENT_ATTRIBUTE_LORE.stream().anyMatch(lore -> lore.translationKey().equals(key))
				|| switch (key) {
					case "desc.matcha.mining_speed", "tooltip.matcha.set_bonus",
							"tooltip.matcha.per_equipment_bonus", "tooltip.matcha.elytra_bonus",
							"tooltip.matcha.when_blocking", "desc.matcha.melee_blocking",
							"desc.matcha.projectile_blocking", "enchantment.matcha.adamant_armour.set_bonus",
							"enchantment.matcha.divinity.set_bonus",
							"enchantment.matcha.warding3.set_bonus_apotropaic",
							"enchantment.matcha.shakudo_regen_bonus",
							"enchantment.matcha.shakudo_elytra_regen_bonus" -> true;
					default -> false;
				};
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
