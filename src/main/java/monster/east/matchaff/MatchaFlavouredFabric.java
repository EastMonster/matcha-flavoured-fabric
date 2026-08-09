package monster.east.matchaff;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.RemoveStatusEffectsConsumeEffect;
import net.minecraft.world.food.FoodProperties;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class MatchaFlavouredFabric implements ModInitializer {
	private static final List<String> SIMPLE_ITEMS = List.of(
			"amber", "opal", "ruby", "topaz",
			"fish_bones", "fox_pelt", "sulfur_goo", "tallow",
			"avesta", "divine_comedy", "enoch", "paradise_lost", "quran", "solomon", "tanakh"
	);
	@Override
	public void onInitialize() {
		VanillaFoodDefaults.init();
		PlayerMechanics.init();
		EnchantmentMechanics.init();
		EffectsMechanics.init();
		MechanicMechanics.init();
		WorldMechanics.init();
		FoodHealMechanics.init();
		List<Item> items = new ArrayList<>(SIMPLE_ITEMS.stream().map(MatchaFlavouredFabric::register).toList());
		List<Item> foods = registerFoods();
		List<EquipmentRegistrar.EquipmentItem> equipment = EquipmentRegistrar.registerAll();
		List<SimpleItemRegistrar.BatchItem> fishAndSimple = SimpleItemRegistrar.registerAll();
		List<CreativeOrder.Entry> creativeItems = new ArrayList<>();
		equipment.forEach(entry -> creativeItems.add(new CreativeOrder.Entry(entry.item(), entry.tab())));
		fishAndSimple.forEach(entry -> creativeItems.add(new CreativeOrder.Entry(entry.item(), entry.tab())));
		foods.forEach(item -> creativeItems.add(new CreativeOrder.Entry(item, CreativeModeTabs.FOOD_AND_DRINKS)));
		items.forEach(item -> creativeItems.add(new CreativeOrder.Entry(item, CreativeModeTabs.INGREDIENTS)));
		CreativeOrder.register(creativeItems);
	}

	private static Item register(String name) {
		Item.Properties properties = new Item.Properties();
		switch (name) {
			case "avesta" -> properties.rarity(Rarity.RARE);
			case "divine_comedy" -> book(properties, Rarity.UNCOMMON,
					Component.translatable("item.kleispack.divine_comedy.desc"));
			case "enoch" -> book(properties, Rarity.RARE,
					Component.translatable("item.kleispack.enoch.desc"));
			case "paradise_lost" -> book(properties, Rarity.UNCOMMON,
					Component.translatable("item.kleispack.paradise_lost.desc"));
			case "quran" -> book(properties, Rarity.RARE, Component.literal("القرآن"));
			case "solomon" -> book(properties, Rarity.EPIC,
					Component.translatable("item.kleispack.key_of_solomon.desc"));
			case "tanakh" -> book(properties, Rarity.RARE,
					Component.translatable("item.kleispack.tanakh.desc"));
			default -> {
			}
		}
		return register(name, properties);
	}

	private static void book(Item.Properties properties, Rarity rarity, Component lore) {
		properties.rarity(rarity).component(DataComponents.LORE, new ItemLore(List.of(
				lore.copy().withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false))
		)));
	}

	private static Item register(String name, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("matcha-flavoured", name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties.setId(key)));
	}

	private static List<Item> registerFoods() {
		try (var stream = MatchaFlavouredFabric.class.getResourceAsStream("/matcha/foods.json")) {
			FoodDefinition[] definitions = new Gson().fromJson(
					new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8),
					FoodDefinition[].class
			);
			return Arrays.stream(definitions).map(MatchaFlavouredFabric::registerFood).toList();
		} catch (Exception exception) {
			throw new IllegalStateException("Could not load Matcha food definitions", exception);
		}
	}

	private static Item registerFood(FoodDefinition definition) {
		Consumable.Builder consumable = Consumable.builder();
		if (definition.consumeSeconds != null) consumable.consumeSeconds(definition.consumeSeconds);
		if (definition.animation != null) consumable.animation(ItemUseAnimation.valueOf(definition.animation.toUpperCase()));
		if (definition.sound != null) consumable.sound(sound(definition.sound));
		if (definition.particles != null) consumable.hasConsumeParticles(definition.particles);

		for (ConsumeEffectDefinition effect : definition.effects) {
			if (effect.type.equals("apply")) {
				List<MobEffectInstance> instances = effect.effects.stream()
						.map(value -> new MobEffectInstance(
								mobEffect(value.id), value.duration, value.amplifier,
								value.ambient, value.particles, value.icon
						))
						.toList();
				consumable.onConsume(new ApplyStatusEffectsConsumeEffect(instances, effect.probability));
			} else if (effect.type.equals("remove")) {
				consumable.onConsume(new RemoveStatusEffectsConsumeEffect(
						HolderSet.direct(effect.effectIds.stream().map(MatchaFlavouredFabric::mobEffect).toList())
				));
			} else if (effect.type.equals("clear")) {
				consumable.onConsume(new ClearAllStatusEffectsConsumeEffect());
			} else {
				throw new IllegalArgumentException("Unknown consume effect: " + effect.type);
			}
		}

		Item.Properties properties = new Item.Properties().food(
				new FoodProperties(definition.nutrition, definition.saturation, definition.alwaysEdible),
				consumable.build()
		);
		if (definition.maxStackSize != null) properties.stacksTo(definition.maxStackSize);
		if (definition.remainder != null) {
			properties.usingConvertsTo(Objects.requireNonNull(
					BuiltInRegistries.ITEM.getValue(Identifier.parse(definition.remainder))
			));
		}
		if (definition.lore != null) {
			JsonElement lore = definition.lore;
			if (lore.isJsonObject()) {
				JsonArray lines = new JsonArray();
				lines.add(lore);
				lore = lines;
			}
			ItemComponents.apply(properties, "minecraft:lore", lore);
		}
		return register(definition.id, properties);
	}

	private static Holder<MobEffect> mobEffect(String id) {
		return BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(id)).orElseThrow();
	}

	private static Holder<SoundEvent> sound(String id) {
		return BuiltInRegistries.SOUND_EVENT.get(Identifier.parse(id)).orElseThrow();
	}

	private record FoodDefinition(
			String id, int nutrition, float saturation, boolean alwaysEdible,
			Float consumeSeconds, String animation, String sound, Boolean particles,
			String remainder, Integer maxStackSize, JsonElement lore, List<ConsumeEffectDefinition> effects
	) {}

	private record ConsumeEffectDefinition(
			String type, float probability, List<StatusEffectDefinition> effects, List<String> effectIds
	) {}

	private record StatusEffectDefinition(
			String id, int duration, int amplifier, boolean ambient, boolean particles, boolean icon
	) {}
}
