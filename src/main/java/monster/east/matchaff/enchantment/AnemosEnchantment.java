package monster.east.matchaff.enchantment;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

public final class AnemosEnchantment {
	private static final AttachmentType<Integer> READY_TICK = AttachmentRegistry.create(
			net.minecraft.resources.Identifier.fromNamespaceAndPath("matcha-flavoured", "anemos_ready_tick")
	);
	private static final net.minecraft.resources.Identifier ANEMOS = EnchantmentUtil.id("anemos");

	private AnemosEnchantment() {
	}

	static void init() {
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			maybeLaunch(player, player.getMainHandItem());
			return InteractionResult.PASS;
		});
	}

	public static void onPostAttack(ServerPlayer player, ItemStack weapon) {
		maybeLaunch(player, weapon);
	}

	private static void maybeLaunch(Player player, ItemStack weapon) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		Registry<Enchantment> enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		if (EnchantmentUtil.maxLevel(weapon, enchantments, ANEMOS) == 0) {
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
