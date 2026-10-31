package monster.east.matchaff.mechanic;

/** Run with assertions enabled to check the upstream version rename and one-time refresh. */
public final class RecipeUnlockVersionCheck {
	public static void main(String[] args) {
		assert WorldMechanics.shouldRefreshRecipeUnlocks(0);
		assert WorldMechanics.shouldRefreshRecipeUnlocks(1_12_08_001);
		assert WorldMechanics.shouldRefreshRecipeUnlocks(1_13_00_001);
		assert !WorldMechanics.shouldRefreshRecipeUnlocks(1_12_09_001);
		assert !WorldMechanics.shouldRefreshRecipeUnlocks(1_12_09_002);
		assert !WorldMechanics.shouldRefreshRecipeUnlocks(1_12_10_001);
	}
}
