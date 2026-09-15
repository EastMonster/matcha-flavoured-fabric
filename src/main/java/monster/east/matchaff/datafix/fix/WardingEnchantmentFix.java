package monster.east.matchaff.datafix.fix;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;

public class WardingEnchantmentFix extends DataFix {
	public WardingEnchantmentFix(Schema outputSchema) {
		super(outputSchema, false);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		Type<?> root = getInputSchema().getType(References.ROOT);
		return writeFixAndRead("Matcha Warding and Adamant intrinsic migration", root, root, MatchaStackMigration::migrateV4);
	}
}
