package monster.east.matchaff.datafix;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Run with assertions enabled to check Estus upgrades and post-upgrade vanilla items. */
public final class MatchaEstusMigrationCheck {
	private MatchaEstusMigrationCheck() {
	}

	public static void main(String[] args) {
		CompoundTag rod = stack("minecraft:blaze_rod", 12);
		CompoundTag components = new CompoundTag();
		components.putString("minecraft:item_model", "minecraft:blaze_rod");
		CompoundTag name = new CompoundTag();
		name.putString("translate", "item.minecraft.blaze_rod");
		components.put("minecraft:item_name", name);
		CompoundTag customData = stack("minecraft:blaze_powder", 3);
		components.put("minecraft:custom_data", customData.copy());
		rod.put("components", components);

		CompoundTag bundle = stack("minecraft:bundle", 1);
		ListTag contents = new ListTag();
		contents.add(stack("minecraft:blaze_powder", 5));
		CompoundTag bundleComponents = new CompoundTag();
		bundleComponents.put("minecraft:bundle_contents", contents);
		bundle.put("components", bundleComponents);

		CompoundTag data = new CompoundTag();
		data.putInt(MatchaItemDataFixer.DATA_VERSION_KEY, 7);
		ListTag inventory = new ListTag();
		inventory.add(rod);
		inventory.add(bundle);
		inventory.add(stack("minecraft:resin_brick", 2));
		data.put("Inventory", inventory);
		CompoundTag upgraded = MatchaItemDataFixer.updateIfNeeded(data);
		assert upgraded.getIntOr(MatchaItemDataFixer.DATA_VERSION_KEY, 0) == 8;
		ListTag items = upgraded.getList("Inventory").orElseThrow();
		CompoundTag stabilized = items.getCompound(0).orElseThrow();
		assert stabilized.getStringOr("id", "").equals("matcha:stabilized_estus");
		assert stabilized.getIntOr("count", 0) == 12;
		CompoundTag fixedComponents = stabilized.getCompound("components").orElseThrow();
		assert fixedComponents.getStringOr("minecraft:item_model", "").equals("matcha:stabilized_estus");
		assert fixedComponents.getCompound("minecraft:item_name").orElseThrow()
				.getStringOr("translate", "").equals("item.matcha.stabilized_estus");
		assert fixedComponents.getCompound("minecraft:custom_data").orElseThrow().equals(customData);
		CompoundTag raw = items.getCompound(1).orElseThrow().getCompound("components").orElseThrow()
				.getList("minecraft:bundle_contents").orElseThrow().getCompound(0).orElseThrow();
		assert raw.getStringOr("id", "").equals("matcha:raw_estus");
		assert raw.getIntOr("count", 0) == 5;
		assert items.getCompound(2).orElseThrow().getStringOr("id", "").equals("minecraft:resin_brick");

		items.add(stack("minecraft:blaze_rod", 1));
		items.add(stack("minecraft:blaze_powder", 1));
		CompoundTag snapshot = upgraded.copy();
		assert MatchaItemDataFixer.updateIfNeeded(upgraded).equals(snapshot);
	}

	private static CompoundTag stack(String id, int count) {
		CompoundTag stack = new CompoundTag();
		stack.putString("id", id);
		stack.putInt("count", count);
		return stack;
	}
}
