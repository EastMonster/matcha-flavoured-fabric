package monster.east.matchaff.mechanic;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Run with assertions enabled to check the detonation boundary and the spawned entity marker. */
public final class BedrockBusterCheck {
	public static void main(String[] args) throws Exception {
		assert !TimedMechanics.shouldBreakBedrock(80);
		assert !TimedMechanics.shouldBreakBedrock(2);
		assert TimedMechanics.shouldBreakBedrock(1);
		assert TimedMechanics.shouldBreakBedrock(0);
		assert !TimedMechanics.shouldBreakBedrock(-1);
		try (var stream = BedrockBusterCheck.class.getResourceAsStream("/matcha/behavior_music.json")) {
			assert stream != null;
			var definitions = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray();
			boolean found = false;
			for (var definition : definitions) {
				var item = definition.getAsJsonObject();
				if (!item.get("id").getAsString().equals("bedrock_buster")) continue;
				var entity = item.getAsJsonObject("components").getAsJsonObject("minecraft:entity_data");
				assert entity.get("id").getAsString().equals("minecraft:tnt");
				assert entity.getAsJsonObject("block_state").get("Name").getAsString().equals("matcha:bedrock_buster");
				assert entity.getAsJsonArray("Tags").asList().stream()
						.anyMatch(tag -> tag.getAsString().equals("matcha:bedrock_buster"));
				found = true;
			}
			assert found;
		}
	}
}
