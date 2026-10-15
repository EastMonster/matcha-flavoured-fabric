package monster.east.matchaff.enchantment;

/** Run with assertions enabled to check Warding source selection and caps. */
public final class WardingEnchantmentCheck {
	private WardingEnchantmentCheck() {
	}

	public static void main(String[] args) {
		assert WardingEnchantment.slownessAmplifier(1) == 1;
		assert WardingEnchantment.slownessAmplifier(2) == 1;
		assert WardingEnchantment.slownessAmplifier(3) == 2;
		assert WardingEnchantment.slownessAmplifier(4) == 2;
		assert WardingEnchantment.effectiveLevel(1, 0) == 1;
		assert WardingEnchantment.effectiveLevel(1 + 2, 0) == 3;
		assert WardingEnchantment.effectiveLevel(2 + 2, 0) == 3;
		assert WardingEnchantment.effectiveLevel(1, 1) == 1;
		assert WardingEnchantment.effectiveLevel(3, 2) == 3;
		assert WardingEnchantment.effectiveLevel(2, 3) == 3;
		assert WardingEnchantment.effectiveLevel(4, 4) == 4;
	}
}
