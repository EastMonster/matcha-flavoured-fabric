package monster.east.matchaff.enchantment;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;

final class SlaughterEnchantment {
	private static final net.minecraft.resources.Identifier SLAUGHTER = EnchantmentUtil.id("slaughter");
	private static final TagKey<EntityType<?>> LIVESTOCK = TagKey.create(
			Registries.ENTITY_TYPE, EnchantmentUtil.id("livestock")
	);

	private SlaughterEnchantment() {
	}

	static void tick(ServerPlayer player, Registry<Enchantment> enchantments) {
		if (EnchantmentUtil.maxLevel(player.getMainHandItem(), enchantments, SLAUGHTER) == 0) {
			return;
		}
		for (LivingEntity nearby : player.level().getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().inflate(3.0),
				entity -> entity.is(LIVESTOCK) && entity.distanceToSqr(player) <= 3.0 * 3.0)) {
			nearby.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 9, true, false));
		}
	}
}
