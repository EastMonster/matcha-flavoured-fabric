package monster.east.matchaff;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class MatchaSounds {
	private static final Identifier CHIME_ID = Identifier.fromNamespaceAndPath("matcha", "entity.experience_orb.pickup");
	public static final SoundEvent CHIME = Registry.register(BuiltInRegistries.SOUND_EVENT, CHIME_ID,
			SoundEvent.createVariableRangeEvent(CHIME_ID));

	private MatchaSounds() { }

	public static void init() { }
}
