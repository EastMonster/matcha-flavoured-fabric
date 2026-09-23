package monster.east.matchaff.datafix.fix;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.Map;

/** Refreshes migrated pickaxe tool rules and caps Fortune on non-Electrum tools. */
public final class V5Migration extends DataFix {
	private static final Map<String, List<ToolRule>> OLD_PICKAXE_RULES = Map.of(
			"minecraft:wooden_pickaxe", List.of(
				mines("#minecraft:copper_ores", 3), mines("#minecraft:coal_ores", 3),
				mines("minecraft:stone", 3), mines("minecraft:smoker", 4),
				mines("minecraft:mud_bricks", 3), mines("minecraft:packed_mud", 3),
				mines("minecraft:mud_brick_slab", 3), mines("minecraft:mud_brick_stairs", 3),
				mines("minecraft:cobblestone", 3),
				denies("#minecraft:iron_ores", 3), denies("#minecraft:gold_ores", 3),
				denies("#minecraft:diamond_ores", 3), denies("#minecraft:emerald_ores", 3),
				denies("#minecraft:redstone_ores", 3), denies("#minecraft:lapis_ores", 3),
				denies("minecraft:obsidian", 3), mines("#minecraft:mineable/pickaxe", 3)
			),
			"minecraft:copper_pickaxe", List.of(
				new ToolRule("#minecraft:emerald_ores", null, true),
				new ToolRule("#minecraft:redstone_ores", null, false),
				denies("#minecraft:diamond_ores", 4), denies("minecraft:obsidian", 3),
				mines("#minecraft:mineable/pickaxe", 6)
			),
			"minecraft:golden_pickaxe", List.of(
				denies("minecraft:obsidian", 3), mines("#minecraft:mineable/pickaxe", 12)
			),
			"minecraft:iron_pickaxe", List.of(
				denies("minecraft:obsidian", 3), mines("#minecraft:mineable/pickaxe", 7)
			)
	);
	private static final Map<String, List<ToolRule>> CURRENT_PICKAXE_RULES = Map.of(
			"minecraft:wooden_pickaxe", List.of(
				denies("#matcha:non_mineable/wood", 3), mines("#minecraft:mineable/pickaxe", 3)
			),
			"minecraft:copper_pickaxe", List.of(
				denies("#matcha:non_mineable/copper", 3), mines("#minecraft:mineable/pickaxe", 6)
			),
			"minecraft:golden_pickaxe", List.of(
				denies("#matcha:obsidian_based", 3), mines("#minecraft:mineable/pickaxe", 12)
			),
			"minecraft:iron_pickaxe", List.of(
				denies("#matcha:non_mineable/iron", 3), mines("#minecraft:mineable/pickaxe", 7)
			)
	);

	public V5Migration(Schema outputSchema) {
		super(outputSchema, false);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		Type<?> root = getInputSchema().getType(MatchaDataFixSupport.ROOT);
		return writeFixAndRead("Matcha V5 tool and Fortune migration", root, root, V5Migration::migrate);
	}

	static Dynamic<?> migrate(Dynamic<?> data) {
		return MatchaDataFixSupport.apply(data, tag -> migrateStacks(tag, false));
	}

	private static Tag migrateStacks(Tag tag, boolean customData) {
		if (tag instanceof CompoundTag compound) {
			for (String key : List.copyOf(compound.keySet())) {
				Tag child = compound.get(key);
				if (child != null) compound.put(key, migrateStacks(child, customData || key.equals("minecraft:custom_data")));
			}
			if (!customData && compound.contains("id")
					&& compound.get("components") instanceof CompoundTag components) {
				String itemId = compound.getStringOr("id", "");
				updatePickaxeTool(itemId, components);
				limitNonElectrumFortune(itemId, components);
				if (components.isEmpty()) compound.remove("components");
			}
		} else if (tag instanceof ListTag list) {
			for (int index = 0; index < list.size(); index++) list.set(index, migrateStacks(list.get(index), customData));
		}
		return tag;
	}

	private static void updatePickaxeTool(String itemId, CompoundTag components) {
		List<ToolRule> oldRules = OLD_PICKAXE_RULES.get(itemId);
		List<ToolRule> currentRules = CURRENT_PICKAXE_RULES.get(itemId);
		if (oldRules == null || currentRules == null || !(components.get("minecraft:tool") instanceof CompoundTag tool)
				|| !matchesRules(tool.get("rules"), oldRules)) return;
		ListTag rules = new ListTag();
		currentRules.forEach(rule -> rules.add(rule.toTag()));
		CompoundTag updatedTool = tool.copy();
		updatedTool.put("rules", rules);
		components.put("minecraft:tool", updatedTool);
	}

	private static void limitNonElectrumFortune(String itemId, CompoundTag components) {
		int separator = itemId.indexOf(':');
		String path = separator < 0 ? itemId : itemId.substring(separator + 1);
		if (path.startsWith("electrum_") || !isToolPath(path)
				|| !(components.get("minecraft:enchantments") instanceof CompoundTag enchantments)) return;
		if (enchantments.getIntOr("minecraft:fortune", 0) > 1) enchantments.putInt("minecraft:fortune", 1);
	}

	private static boolean isToolPath(String path) {
		return path.endsWith("_axe") || path.endsWith("_pickaxe") || path.endsWith("_shovel")
				|| path.endsWith("_hoe") || path.endsWith("_dolabra") || path.endsWith("_mattock");
	}

	private static boolean matchesRules(Tag tag, List<ToolRule> expected) {
		if (!(tag instanceof ListTag rules) || rules.size() != expected.size()) return false;
		for (int index = 0; index < rules.size(); index++) {
			if (!(rules.get(index) instanceof CompoundTag actual) || !expected.get(index).matches(actual)) return false;
		}
		return true;
	}

	private static ToolRule mines(String blocks, float speed) {
		return new ToolRule(blocks, speed, true);
	}

	private static ToolRule denies(String blocks, float speed) {
		return new ToolRule(blocks, speed, false);
	}

	private record ToolRule(String blocks, Float speed, Boolean correctForDrops) {
		private boolean matches(CompoundTag rule) {
			int expectedSize = 1 + (speed == null ? 0 : 1) + (correctForDrops == null ? 0 : 1);
			return rule.size() == expectedSize && blocks.equals(rule.getStringOr("blocks", ""))
					&& (speed == null ? !rule.contains("speed") : Float.compare(rule.getFloatOr("speed", Float.NaN), speed) == 0)
					&& (correctForDrops == null ? !rule.contains("correct_for_drops")
							: rule.getBooleanOr("correct_for_drops", !correctForDrops) == correctForDrops);
		}

		private CompoundTag toTag() {
			CompoundTag rule = new CompoundTag();
			rule.putString("blocks", blocks);
			if (speed != null) rule.putFloat("speed", speed);
			if (correctForDrops != null) rule.putBoolean("correct_for_drops", correctForDrops);
			return rule;
		}
	}
}
