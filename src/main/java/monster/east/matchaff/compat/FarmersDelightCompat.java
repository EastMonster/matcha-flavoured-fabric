package monster.east.matchaff.compat;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.CustomData;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

public final class FarmersDelightCompat {
	private static final Set<Identifier> CUSTOM_LORE_ITEMS = new HashSet<>();
	private static final Set<Identifier> HIDDEN_ITEMS = Set.of(
			Identifier.parse("farmersdelight:milk_bottle"), Identifier.parse("farmersdelight:fried_egg"),
			Identifier.parse("farmersdelight:wheat_dough"));

	private FarmersDelightCompat() {
	}

	public static boolean isHidden(ItemStack stack) {
		return HIDDEN_ITEMS.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
	}

	public static boolean hasCustomLore(ItemStack stack) {
		return CUSTOM_LORE_ITEMS.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
	}

	public static void init() {
		if (!FabricLoader.getInstance().isModLoaded("farmersdelight")) {
			return;
		}
		CreativeModeTabEvents.MODIFY_OUTPUT_ALL.register((tab, output) -> {
			output.getDisplayStacks().removeIf(FarmersDelightCompat::isHidden);
			output.getSearchTabStacks().removeIf(FarmersDelightCompat::isHidden);
		});
		ResourceLoader.registerBuiltinPack(
				Identifier.fromNamespaceAndPath("matcha", "farmers_delight_compat"),
				FabricLoader.getInstance().getModContainer("matcha-flavoured").orElseThrow(),
				PackActivationType.ALWAYS_ENABLED
		);
		Replacement[] replacements;
		String path = "/matcha/compat/farmersdelight.json";
		try (var stream = FarmersDelightCompat.class.getResourceAsStream(path);
			 var reader = new InputStreamReader(Objects.requireNonNull(stream, path), StandardCharsets.UTF_8)) {
			replacements = Objects.requireNonNull(new Gson().fromJson(reader, Replacement[].class), path);
		} catch (Exception exception) {
			throw new IllegalStateException("Could not load Farmer's Delight component replacements", exception);
		}
		for (Replacement replacement : replacements) {
			if (replacement.lore != null) {
				CUSTOM_LORE_ITEMS.add(Identifier.parse(replacement.item));
			}
		}
		// Apply after Matcha defaults and FD's vanilla soup additions, regardless of entrypoint order.
		var phase = Identifier.fromNamespaceAndPath("matcha", "farmers_delight_compat");
		DefaultItemComponentEvents.MODIFY.addPhaseOrdering(Event.DEFAULT_PHASE, phase);
		DefaultItemComponentEvents.MODIFY.register(phase, context -> {
			for (Replacement replacement : replacements) {
				var source = replacement.source == null ? null
						: Objects.requireNonNull(BuiltInRegistries.ITEM.getValue(Identifier.parse(replacement.source)),
								"Unknown source item: " + replacement.source);
				var item = Objects.requireNonNull(BuiltInRegistries.ITEM.getValue(Identifier.parse(replacement.item)),
						"Unknown target item: " + replacement.item);
				context.modify(item, (builder, registries, ignored) -> {
					for (String id : replacement.components == null ? List.<String>of() : replacement.components) {
						var type = Objects.requireNonNull(BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse(id)),
								"Unknown component: " + id);
						copyComponent(builder, Objects.requireNonNull(source, "Missing component source").components(), type);
					}
					if (replacement.lore != null) {
						builder.set(DataComponents.LORE, ItemLore.CODEC.parse(JsonOps.INSTANCE, replacement.lore).getOrThrow());
					}
					if (replacement.consumable != null) {
						builder.set(DataComponents.CONSUMABLE, Consumable.CODEC.parse(
								RegistryOps.create(JsonOps.INSTANCE, registries), replacement.consumable).getOrThrow());
					}
					if (replacement.customData != null) {
						builder.set(DataComponents.CUSTOM_DATA, CustomData.CODEC.parse(
								JsonOps.INSTANCE, replacement.customData).getOrThrow());
					}
					if (replacement.enchantments != null) {
						builder.set(DataComponents.ENCHANTMENTS, DataComponents.ENCHANTMENTS.codec().parse(
								RegistryOps.create(JsonOps.INSTANCE, registries), replacement.enchantments).getOrThrow());
					}
					if (replacement.glint != null) {
						builder.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, replacement.glint);
					}
					if (replacement.itemName != null) {
						builder.set(DataComponents.ITEM_NAME, DataComponents.ITEM_NAME.codec().parse(
								JsonOps.INSTANCE, replacement.itemName).getOrThrow());
					}
				});
			}
		});
	}

	static <T> void copyComponent(DataComponentMap.Builder builder, DataComponentMap source, DataComponentType<T> type) {
		builder.set(type, Objects.requireNonNull(source.get(type), "Source item is missing component: " + type));
	}

	private record Replacement(String item, String source, List<String> components, JsonElement lore, JsonElement consumable, JsonElement customData, JsonElement enchantments, Boolean glint, JsonElement itemName) {
	}
}
