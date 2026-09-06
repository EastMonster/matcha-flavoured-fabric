package monster.east.matchaff.datafix.fix;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;

public class ItemNamespaceFix extends DataFix {
	public ItemNamespaceFix(Schema outputSchema) {
		super(outputSchema, false);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		Type<?> root = getInputSchema().getType(References.ROOT);
		return writeFixAndRead("Matcha item namespace migration", root, root, MatchaStackMigration::migrate);
	}
}
