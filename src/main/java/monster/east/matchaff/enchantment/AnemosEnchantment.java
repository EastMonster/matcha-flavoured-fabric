package monster.east.matchaff.enchantment;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;

final class AnemosEnchantment {
	private static final AttachmentType<Integer> READY_TICK = AttachmentRegistry.create(
			net.minecraft.resources.Identifier.fromNamespaceAndPath("matcha-flavoured", "anemos_ready_tick")
	);
	private static final net.minecraft.resources.Identifier ANEMOS = EnchantmentUtil.id("anemos");

	private AnemosEnchantment() {
	}

	static void init() {
		AttackEntityCallback.EVENT.register((player, level, hand, target, hitResult) -> {
			maybeLaunch(player);
			return InteractionResult.PASS;
		});
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			maybeLaunch(player);
			return InteractionResult.PASS;
		});
	}

	private static void maybeLaunch(Player player) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		Registry<Enchantment> enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		if (EnchantmentUtil.maxLevel(player.getMainHandItem(), enchantments, ANEMOS) == 0) {
			return;
		}
		int currentTick = serverPlayer.level().getServer().getTickCount();
		if (currentTick < serverPlayer.getAttachedOrElse(READY_TICK, 0)) {
			return;
		}
		var level = serverPlayer.level();
		var charge = EntityTypes.WIND_CHARGE.create(level, EntitySpawnReason.EVENT);
		var eye = serverPlayer.getEyePosition().add(serverPlayer.getLookAngle().scale(0.75));
		charge.setPos(eye.x, eye.y, eye.z);
		charge.setDeltaMovement(serverPlayer.getLookAngle().scale(2.5));
		charge.setOwner(serverPlayer);
		level.addFreshEntity(charge);
		serverPlayer.setAttached(READY_TICK, currentTick + 10);
	}
}
