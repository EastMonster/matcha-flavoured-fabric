package monster.east.matchaff.enchantment;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

final class ShakudoRegenEnchantment {
	private static final Identifier SHAKUDO_REGEN = EnchantmentUtil.id("shakudo_armour");
	private static final Identifier SHAKUDO_ELYTRA = EnchantmentUtil.id("shakudo_elytra");
	private static final AttachmentType<Integer> COOLDOWN = AttachmentRegistry.create(
			EnchantmentUtil.id("shakudo_regen_cooldown"), builder -> builder.persistent(Codec.INT).copyOnDeath());

	private ShakudoRegenEnchantment() {
	}

	static int armourPower(ServerPlayer player, Registry<Enchantment> enchantments) {
		int score = EnchantmentUtil.countArmor(player, enchantments, SHAKUDO_REGEN);
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		if (EnchantmentUtil.maxLevel(chest, enchantments, SHAKUDO_REGEN) > 0
				&& SHAKUDO_ELYTRA.equals(BuiltInRegistries.ITEM.getKey(chest.getItem()))) {
			score += 4;
		}
		return score;
	}

	static void tick(ServerPlayer player, Registry<Enchantment> enchantments) {
		int score = armourPower(player, enchantments);
		int remaining = player.getAttachedOrElse(COOLDOWN, -1);
		if (score > 0 && remaining == 0) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, score >= 5 ? 1 : 0, false, false));
		}
		player.setAttached(COOLDOWN, nextCooldown(remaining, score));
	}

	static int interval(int score) {
		return switch (score) {
			case 1 -> 600;
			case 2 -> 520;
			case 3 -> 440;
			case 4 -> 360;
			case 5 -> 400;
			case 6 -> 320;
			case 7 -> 240;
			default -> 160;
		};
	}

	static int nextCooldown(int remaining, int score) {
		// Upstream returns after applying regeneration, so rearming happens on the following tick.
		return score > 0 && remaining < 0 ? interval(score) - 1 : Math.max(-1, remaining - 1);
	}
}
