package monster.east.matchaff.mixin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import monster.east.matchaff.mechanic.GameplayMechanics;
import monster.east.matchaff.mechanic.MechanicAdvancementTriggers;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.util.StrictJsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

/** Migrates saved advancement criteria without invoking advancement rewards. */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
	@Unique
	private static final Logger MATCHA$LOGGER = LoggerFactory.getLogger("matcha");
	@Unique
	private static final Gson MATCHA$GSON = new GsonBuilder().setPrettyPrinting().create();

	@Shadow @Final private Path playerSavePath;
	@Shadow private ServerPlayer player;

	@Inject(method = "award", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/advancements/AdvancementRewards;grant(Lnet/minecraft/server/level/ServerPlayer;)V",
			shift = At.Shift.AFTER))
	private void matcha$completedAdvancement(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> callbackInfo) {
		MechanicAdvancementTriggers.completed(this.player, holder.id());
		GameplayMechanics.rewardDragonAdvancement(this.player, holder.id());
	}

	@Inject(method = "load", at = @At("HEAD"))
	private void matcha$migrateLegacyNamespace(ServerAdvancementManager manager, CallbackInfo callbackInfo) {
		Path path = this.playerSavePath;
		if (!Files.isRegularFile(path)) {
			return;
		}

		try {
			JsonObject migrated;
			int migratedEntries = 0;
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				JsonElement root = StrictJsonParser.parse(reader);
				if (!root.isJsonObject()) {
					return;
				}

				JsonObject source = root.getAsJsonObject();
				migrated = new JsonObject();
				boolean changed = false;
				for (var entry : source.entrySet()) {
					String key = entry.getKey();
					Identifier id = Identifier.tryParse(key);
					if (id != null && id.getNamespace().equals("main")) {
						Identifier replacement = Identifier.fromNamespaceAndPath("matcha", id.getPath());
						if (manager.get(replacement) != null) {
							changed = true;
							String replacementKey = replacement.toString();
							if (!source.has(replacementKey)) {
								migrated.add(replacementKey, entry.getValue());
								migratedEntries++;
							}
							continue;
						}
					}
					migrated.add(key, entry.getValue());
				}
				changed = matcha$migrateTutorialProgress(migrated, manager) || changed;

				if (!changed) {
					return;
				}
			}

			Path temporary = path.resolveSibling(path.getFileName() + ".matcha.tmp");
			try {
				try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
					MATCHA$GSON.toJson(migrated, writer);
				}
				try {
					Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				} catch (AtomicMoveNotSupportedException exception) {
					Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
				}
				MATCHA$LOGGER.info("Migrated advancement progress ({} legacy namespace entries) in {}", migratedEntries, path);
			} finally {
				Files.deleteIfExists(temporary);
			}
		} catch (IOException | JsonParseException exception) {
			MATCHA$LOGGER.error("Couldn't migrate legacy advancements in {}", path, exception);
		}
	}

	@Unique
	private static boolean matcha$migrateTutorialProgress(JsonObject saved, ServerAdvancementManager manager) {
		boolean changed = false;
		if (manager.get(Identifier.parse("matcha:tutorial/cook_all_curries")) != null
				&& manager.get(Identifier.parse("matcha:tutorial/cook_any_curry")) != null) {
			for (var entry : Map.of(
					"cook_pumpkin_curry", "pumpkin_curry", "obtain_green_curry", "green_curry",
					"obtain_japanese_curry", "japanese_curry", "obtain_paneer_mahkani", "paneer_makhani",
					"obtain_ramen", "ramen").entrySet()) {
				for (String namespace : List.of("matcha", "main")) {
					String oldId = namespace + ":tutorial/" + entry.getKey();
					if (!(saved.get(oldId) instanceof JsonObject old)) continue;
					if (old.get("criteria") instanceof JsonObject criteria && criteria.has("get_food")) {
						matcha$copyCriterion(saved, "matcha:tutorial/cook_any_curry", entry.getValue(), criteria.get("get_food"));
						matcha$copyCriterion(saved, "matcha:tutorial/cook_all_curries", entry.getValue(), criteria.get("get_food"));
					}
					saved.remove(oldId);
					changed = true;
				}
			}
		}
		var tradeHolder = manager.get(Identifier.parse("matcha:tutorial/trade_everything"));
		if (tradeHolder != null && saved.get("matcha:tutorial/trade_everything") instanceof JsonObject trade
				&& trade.has("done") && trade.get("done").getAsBoolean()
				&& trade.get("criteria") instanceof JsonObject criteria && !criteria.entrySet().isEmpty()) {
			JsonElement date = criteria.entrySet().iterator().next().getValue();
			for (String criterion : tradeHolder.value().criteria().keySet()) {
				if (!criteria.has(criterion)) {
					criteria.add(criterion, date.deepCopy());
					changed = true;
				}
			}
		}
		return changed;
	}

	@Unique
	private static void matcha$copyCriterion(JsonObject saved, String id, String criterion, JsonElement date) {
		JsonObject progress;
		if (saved.get(id) instanceof JsonObject existing) {
			progress = existing;
		} else {
			progress = new JsonObject();
			saved.add(id, progress);
		}
		JsonObject criteria;
		if (progress.get("criteria") instanceof JsonObject existing) {
			criteria = existing;
		} else {
			criteria = new JsonObject();
			progress.add("criteria", criteria);
		}
		if (!criteria.has(criterion)) criteria.add(criterion, date.deepCopy());
	}
}
