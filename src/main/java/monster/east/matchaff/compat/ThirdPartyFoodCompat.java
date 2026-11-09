package monster.east.matchaff.compat;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ThirdPartyFoodCompat {
	private static final Set<String> EXCLUDED_NAMESPACES = Set.of("minecraft", "matcha", "farmersdelight");

	private ThirdPartyFoodCompat() {
	}

	public static void init() {
		var phase = Identifier.fromNamespaceAndPath("matcha", "third_party_food_compat");
		DefaultItemComponentEvents.MODIFY.addPhaseOrdering(Event.DEFAULT_PHASE, phase);
		DefaultItemComponentEvents.MODIFY.addPhaseOrdering(
				Identifier.fromNamespaceAndPath("matcha", "farmers_delight_compat"), phase);
		DefaultItemComponentEvents.MODIFY.register(phase, context -> context.modify(
				item -> !EXCLUDED_NAMESPACES.contains(BuiltInRegistries.ITEM.getKey(item).getNamespace()),
				(builder, registries, item) -> applyDefaults(builder)));
	}

	static int healingPoints(FoodProperties food) {
		return Math.clamp((int) Math.floor(food.nutrition() / 2.0 + Math.floor(food.saturation() / 3.0)), 0, 12);
	}

	static void applyDefaults(DataComponentMap.Builder builder) {
		var components = builder.build();
		var food = components.get(DataComponents.FOOD);
		var consumable = components.get(DataComponents.CONSUMABLE);
		if (food == null || consumable == null) {
			return;
		}
		int points = healingPoints(food);
		if (points == 0) {
			return;
		}
		for (var effect : consumable.onConsumeEffects()) {
			if (effect instanceof ApplyStatusEffectsConsumeEffect apply && apply.probability() > 0.0F
					&& apply.effects().stream().anyMatch(status -> status.getEffect() == MobEffects.INSTANT_HEALTH
							|| status.getEffect() == MobEffects.REGENERATION && status.getAmplifier() >= 2)) {
				return;
			}
		}
		var heal = new MobEffectInstance(MobEffects.REGENERATION, points * 12, 2, false, false, false);
		List<ConsumeEffect> effects = new ArrayList<>(consumable.onConsumeEffects());
		boolean merged = false;
		for (int i = 0; i < effects.size(); i++) {
			if (effects.get(i) instanceof ApplyStatusEffectsConsumeEffect apply && apply.probability() == 1.0F
					&& apply.effects().stream().anyMatch(status -> status.getEffect() == MobEffects.REGENERATION)) {
				// Keep genuine regeneration in the same chain so it follows the fast healing segment.
				var statuses = new ArrayList<MobEffectInstance>();
				for (var status : apply.effects()) {
					statuses.add(status.getEffect() == MobEffects.REGENERATION
							? new MobEffectInstance(status.getEffect(), status.mapDuration(duration -> duration + points * 12),
									status.getAmplifier(), status.isAmbient(), status.isVisible(), status.showIcon())
							: status);
				}
				statuses.addFirst(heal);
				effects.set(i, new ApplyStatusEffectsConsumeEffect(statuses, 1.0F));
				merged = true;
				break;
			}
		}
		if (!merged) {
			effects.addFirst(new ApplyStatusEffectsConsumeEffect(List.of(heal), 1.0F));
		}
		builder.set(DataComponents.CONSUMABLE, new Consumable(consumable.consumeSeconds(), consumable.animation(),
				consumable.sound(), consumable.hasConsumeParticles(), List.copyOf(effects)));
	}
}
