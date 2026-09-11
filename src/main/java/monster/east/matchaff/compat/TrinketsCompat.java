package monster.east.matchaff.compat;

import eu.pb4.trinkets.api.TrinketSlotAccess;
import eu.pb4.trinkets.api.TrinketsApi;
import eu.pb4.trinkets.api.component.TrinketDataComponents;
import eu.pb4.trinkets.api.component.TrinketEquippable;
import eu.pb4.trinkets.api.event.TrinketEquipmentAttributeModifiersCallback;
import monster.east.matchaff.mechanic.EnchantmentMechanics;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;

import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.List;
import java.util.Objects;

public final class TrinketsCompat {
	public static final String MOD_ID = "trinkets_updated";
	public static final List<String> EARRING_IDS = List.of(
			"amber_earrings", "opal_earrings", "topaz_earrings"
	);
	private static final String EARRING_SLOT = "head/earring";
	private static final TagKey<Item> EARRINGS = TagKey.create(
			Registries.ITEM, Identifier.fromNamespaceAndPath("matcha", "earrings")
	);

	private TrinketsCompat() {
	}

	public static void init() {
		DefaultItemComponentEvents.MODIFY.register(context -> {
			for (String id : EARRING_IDS) {
				Item item = Objects.requireNonNull(BuiltInRegistries.ITEM.getValue(
						Identifier.fromNamespaceAndPath("matcha", id)));
				context.modify(item, (builder, registries, ignored) -> builder.set(
						TrinketDataComponents.EQUIPMENT,
						TrinketEquippable.DEFAULT.withSlots("head/earring")));
			}
		});
		EnchantmentMechanics.registerExtraHeadItems(player -> TrinketsApi.getAttachment(player)
				.equipped(stack -> stack.is(EARRINGS), true)
				.stream()
				.map(TrinketSlotAccess::get)
				.toList());
		TrinketEquipmentAttributeModifiersCallback.EVENT.register((stack, slot, entity, slotIdentifier, consumer) -> {
			if (!stack.is(EARRINGS)) {
				return;
			}
			ItemAttributeModifiers modifiers = stack.getOrDefault(
					DataComponents.ATTRIBUTE_MODIFIERS,
					ItemAttributeModifiers.EMPTY);
			for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
				EquipmentSlotGroup group = entry.slot();
				if (group != EquipmentSlotGroup.HEAD
						&& group != EquipmentSlotGroup.ARMOR
						&& group != EquipmentSlotGroup.ANY) {
					continue;
				}
				if (entry.attribute().equals(Attributes.ARMOR)
						&& isEarringSlot(slot)
						&& !entity.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
					continue;
				}
				var modifier = entry.modifier();
				consumer.accept(entry.attribute(), new AttributeModifier(
						modifier.id().withSuffix("/" + slot.getAsIdentifierPath()),
						modifier.amount(), modifier.operation()));
			}
		});
	}

	public static void refreshEarringArmor(LivingEntity entity) {
		var armor = entity.getAttribute(Attributes.ARMOR);
		if (armor == null) {
			return;
		}

		boolean hasHelmet = !entity.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
		TrinketsApi.getAttachment(entity).equipped(stack -> stack.is(EARRINGS), true).forEach(slot -> {
			if (!isEarringSlot(slot)) {
				return;
			}
			ItemAttributeModifiers modifiers = slot.get().getOrDefault(
					DataComponents.ATTRIBUTE_MODIFIERS,
					ItemAttributeModifiers.EMPTY);
			for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
				if (!entry.attribute().equals(Attributes.ARMOR)) {
					continue;
				}
				var modifier = entry.modifier();
				var id = modifier.id().withSuffix("/" + slot.getAsIdentifierPath());
				armor.removeModifier(id);
				if (!hasHelmet) {
					armor.addTransientModifier(new AttributeModifier(id, modifier.amount(), modifier.operation()));
				}
			}
		});
	}

	private static boolean isEarringSlot(TrinketSlotAccess slot) {
		return slot.slotType().getId().equals(EARRING_SLOT);
	}
}
