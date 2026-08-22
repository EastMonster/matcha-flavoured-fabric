package monster.east.matchaff.mechanic;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

final class DivineItemMechanics {
	private static final Set<ItemEntity> DIVINE_ITEMS =
			Collections.newSetFromMap(new IdentityHashMap<>());

	private DivineItemMechanics() {
	}

	static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> DIVINE_ITEMS.clear());
		ServerEntityEvents.ENTITY_LOAD.register(DivineItemMechanics::trackDivineItem);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> DIVINE_ITEMS.remove(entity));
	}

	static void tick(int tick) {
		boolean everyTenTicks = tick % 10 == 0;
		boolean everyThreeSeconds = tick % 60 == 0;
		DIVINE_ITEMS.removeIf(Entity::isRemoved);
		for (ItemEntity item : DIVINE_ITEMS) {
			if (!(item.level() instanceof ServerLevel level)) {
				continue;
			}
			ItemStack stack = item.getItem();
			if (stack.is(Items.NETHER_STAR)) {
				item.setNoGravity(true);
				item.needsSync = true;
				if (everyTenTicks) {
					level.sendParticles(ParticleTypes.END_ROD, item.getX(), item.getY() + 0.4, item.getZ(), 1, 0, 0, 0, 0.05);
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, item.getX(), item.getY() + 0.4, item.getZ(), 1, 0.15, 0.15, 0.15, 0);
				}
				if (everyThreeSeconds) {
					level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 1.0F, 1.0F, 1.0F),
							item.getX(), item.getY() + 0.4, item.getZ(), 1, 0, 0, 0, 0);
				}
				if (level.getBlockState(BlockPos.containing(item.getX(), item.getY() - 3, item.getZ())).isAir()) {
					item.setDeltaMovement(0, -0.05, 0);
				} else if (!level.getBlockState(BlockPos.containing(item.getX(), item.getY() - 0.5, item.getZ())).isAir()) {
					item.setDeltaMovement(0, 0.025, 0);
				}
			} else if (stack.is(Items.ENDER_EYE)) {
				item.setNoGravity(true);
				item.needsSync = true;
				if (everyTenTicks) {
					level.sendParticles(ParticleTypes.PORTAL, item.getX(), item.getY() + 0.3, item.getZ(), 1, 0.1, 0.1, 0.1, 0.5);
				}
				if (level.getBlockState(BlockPos.containing(item.getX(), item.getY() - 3, item.getZ())).isAir()) {
					item.setDeltaMovement(0, -0.1, 0);
				} else if (!level.getBlockState(BlockPos.containing(item.getX(), item.getY() - 0.5, item.getZ())).isAir()) {
					item.setDeltaMovement(0, 0.025, 0);
				}
			} else if (stack.is(Items.BLAZE_POWDER)) {
				if (everyTenTicks) {
					level.sendParticles(ParticleTypes.SMOKE, item.getX(), item.getY() + 0.75, item.getZ(),
							1, 0.05, 0.05, 0.05, 0);
				}
			} else if (stack.is(Items.TURTLE_SCUTE) || stack.is(BuiltInRegistries.ITEM
					.getValue(Identifier.fromNamespaceAndPath("matcha-flavoured", "heart_container")))) {
				if (everyTenTicks) {
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, item.getX(), item.getY() + 0.4, item.getZ(), 1, 0.1, 0.1, 0.1, 0);
				}
			}
		}
	}

	private static void trackDivineItem(Entity entity, ServerLevel level) {
		if (entity instanceof ItemEntity item && isDivineItem(item.getItem())) {
			DIVINE_ITEMS.add(item);
		}
	}

	private static boolean isDivineItem(ItemStack stack) {
		return stack.is(Items.NETHER_STAR)
				|| stack.is(Items.ENDER_EYE)
				|| stack.is(Items.BLAZE_POWDER)
				|| stack.is(Items.TURTLE_SCUTE)
				|| stack.is(BuiltInRegistries.ITEM
						.getValue(Identifier.fromNamespaceAndPath("matcha-flavoured", "heart_container")));
	}
}
