package monster.east.matchaff.enchantment;

/** Run with assertions enabled to check Doom tier values. */
public final class AdamantArmourEnchantmentCheck {
	private AdamantArmourEnchantmentCheck() {
	}

	public static void main(String[] args) {
		assert AdamantArmourEnchantment.radius(1) == 12;
		assert AdamantArmourEnchantment.radius(2) == 16;
		assert AdamantArmourEnchantment.radius(3) == 20;
		assert AdamantArmourEnchantment.radius(4) == 28;
		assert AdamantArmourEnchantment.damage(1) == 3.0F;
		assert AdamantArmourEnchantment.damage(2) == 6.0F;
		assert AdamantArmourEnchantment.damage(3) == 9.0F;
		assert AdamantArmourEnchantment.damage(4) == 16.0F;
	}
}
