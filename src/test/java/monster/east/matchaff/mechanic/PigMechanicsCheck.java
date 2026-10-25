package monster.east.matchaff.mechanic;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Run with assertions enabled to check animation timing, boundaries and the pig resource chain. */
public final class PigMechanicsCheck {
	public static void main(String[] args) throws Exception {
		assert PigMechanics.animationRemaining(0, 1000) == 0;
		assert PigMechanics.animationRemaining(1030, 1000) == 30;
		assert PigMechanics.animationRemaining(1030, 1015) == 15;
		assert PigMechanics.animationRemaining(1030, 1030) == 0;
		assert PigMechanics.animationRemaining(1030, 1031) == 0;
		assert PigMechanics.animationRemaining(-1040, 1000) == -40;
		assert PigMechanics.animationRemaining(-1040, 1025) == -15;
		assert PigMechanics.animationRemaining(-1040, 1041) == 0;
		for (int duration : new int[]{30, -40}) {
			long end = (1000L + Math.abs(duration)) * Integer.signum(duration);
			for (int elapsed = 0; elapsed <= Math.abs(duration); elapsed++) {
				int remaining = PigMechanics.animationRemaining(end, 1000L + elapsed);
				assert remaining == duration - elapsed * Integer.signum(duration);
				if (remaining != 0) {
					assert (1000L + elapsed + Math.abs(remaining)) * Integer.signum(remaining) == end;
				}
			}
		}
		// Rebasing after an unload preserves the saved phase without replaying earlier frames.
		assert PigMechanics.animationRemaining(5015, 5000) == PigMechanics.animationRemaining(1030, 1015);
		assert PigMechanics.animationRemaining(-5015, 5000) == PigMechanics.animationRemaining(-1040, 1025);

		assert PigMechanics.diggingPitch(31) == 0;
		assert PigMechanics.diggingPitch(29) == 90;
		assert PigMechanics.diggingPitch(24) == 40;
		assert PigMechanics.diggingPitch(22) == 20;
		assert PigMechanics.diggingPitch(20) == 90;
		assert PigMechanics.diggingPitch(10) == 90;
		assert PigMechanics.diggingPitch(3) == -90;
		assert PigMechanics.diggingPitch(0) == 0;

		var foods = json("data/matcha/tags/item/pig_food_not_breedable.json").getAsJsonObject().getAsJsonArray("values").asList();
		assert foods.stream().anyMatch(food -> food.getAsString().equals("#c:foods/tomato"));
		assert foods.stream().noneMatch(food -> food.getAsString().equals("minecraft:golden_carrot")
				|| food.getAsString().equals("minecraft:golden_apple") || food.getAsString().equals("minecraft:potion"));
		var pigFood = json("data/minecraft/tags/item/pig_food.json").getAsJsonObject().getAsJsonArray("values").asList();
		assert pigFood.stream().anyMatch(food -> food.getAsString().equals("#matcha:pig_food_not_breedable"));
		assert pigFood.stream().anyMatch(food -> food.getAsString().equals("minecraft:golden_carrot"));
		assert pigFood.stream().anyMatch(food -> food.getAsString().equals("minecraft:golden_apple"));

		var gifts = json("data/matcha/loot_table/gameplay/pig_gift/muddy.json").getAsJsonObject()
				.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries");
		assert gifts.get(0).getAsJsonObject().get("weight").getAsInt() == 5;
		assert gifts.get(1).getAsJsonObject().get("weight").getAsInt() == 1;
		assert gifts.get(1).getAsJsonObject().get("value").getAsString().equals("matcha:food/matcha/truffle");
		var truffle = json("data/matcha/loot_table/food/matcha/truffle.json").getAsJsonObject()
				.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
		assert truffle.get("name").getAsString().equals("matcha:truffle");
		assert !truffle.has("modifier");
		for (String variant : new String[]{"temperate", "warm", "cold"}) {
			JsonObject definition = json("data/matcha/pig_variant/muddy_" + variant + ".json").getAsJsonObject();
			assert definition.getAsJsonArray("spawn_conditions").isEmpty();
			for (String field : new String[]{"asset_id", "baby_asset_id"}) {
				String texture = definition.get(field).getAsString().replace("matcha:", "assets/matcha/textures/") + ".png";
				assert PigMechanicsCheck.class.getClassLoader().getResource(texture) != null : texture;
			}
		}
	}

	private static JsonElement json(String path) throws Exception {
		try (var stream = PigMechanicsCheck.class.getClassLoader().getResourceAsStream(path)) {
			assert stream != null : path;
			return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
		}
	}
}
