package monster.east.matchaff.datafix.fix;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;

public class NazarItemFix extends DataFix {
	public NazarItemFix(Schema outputSchema) {
		super(outputSchema, false);
	}

	@Override
	protected TypeRewriteRule makeRule() {
		Type<?> root = getInputSchema().getType(References.ROOT);
		return writeFixAndRead("Matcha nazar carrier migration", root, root, MatchaStackMigration::migrate);
	}
}
