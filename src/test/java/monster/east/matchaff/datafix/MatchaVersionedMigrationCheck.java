package monster.east.matchaff.datafix;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Run with assertions enabled to check that upgrades start at the saved version. */
public final class MatchaVersionedMigrationCheck {
	private MatchaVersionedMigrationCheck() {
	}

	public static void main(String[] args) {
		CompoundTag current = MatchaItemDataFixer.updateIfNeeded(playerData(5));
		assert itemId(current).equals("minecraft:resin_brick");
		assert current.getIntOr(MatchaItemDataFixer.DATA_VERSION_KEY, 0) > 5;

		CompoundTag legacy = MatchaItemDataFixer.updateIfNeeded(playerData(0));
		assert itemId(legacy).equals("matcha:steel");
	}

	private static CompoundTag playerData(int version) {
		CompoundTag data = new CompoundTag();
		data.putInt(MatchaItemDataFixer.DATA_VERSION_KEY, version);
		CompoundTag item = new CompoundTag();
		item.putString("id", "minecraft:resin_brick");
		ListTag inventory = new ListTag();
		inventory.add(item);
		data.put("Inventory", inventory);
		return data;
	}

	private static String itemId(CompoundTag data) {
		return data.getList("Inventory").orElseThrow().getCompound(0).orElseThrow().getStringOr("id", "");
	}
}
