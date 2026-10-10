package monster.east.matchaff.mechanic;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

/** Run with assertions enabled to check world defaults and persisted toggles. */
public final class BlazeRodDropsSettingsCheck {
	private BlazeRodDropsSettingsCheck() {
	}

	public static void main(String[] args) {
		var settings = BlazeRodDropsMechanics.Settings.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
		assert !settings.setEnabled(false);
		assert !settings.isDirty();
		assert settings.setEnabled(true);
		assert settings.isDirty();

		var saved = BlazeRodDropsMechanics.Settings.CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow();
		assert saved.getAsJsonObject().get("blaze_rod_drops").getAsBoolean();
		var restored = BlazeRodDropsMechanics.Settings.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
		assert !restored.setEnabled(true);
		assert !restored.isDirty();
		assert restored.setEnabled(false);
		assert restored.isDirty();

		var anotherWorld = BlazeRodDropsMechanics.Settings.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
		assert !anotherWorld.setEnabled(false);
	}
}
