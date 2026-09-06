package monster.east.matchaff.datafix.schema;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import monster.east.matchaff.datafix.fix.References;
import net.minecraft.util.datafix.schemas.V99;

import java.util.Map;
import java.util.function.Supplier;

public class V1 extends V99 {
	public V1(int versionKey, Schema parent) {
		super(versionKey, parent);
	}

	@Override
	public void registerTypes(Schema schema, Map<String, Supplier<TypeTemplate>> entityTypes, Map<String, Supplier<TypeTemplate>> blockEntityTypes) {
		super.registerTypes(schema, entityTypes, blockEntityTypes);
		schema.registerType(false, References.ROOT, DSL::remainder);
	}
}
