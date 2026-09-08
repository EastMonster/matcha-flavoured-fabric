package monster.east.matchaff.mixin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
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

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Renames old main advancement progress without invoking advancement rewards. */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
	@Unique
	private static final Logger MATCHA$LOGGER = LoggerFactory.getLogger("matcha");
	@Unique
	private static final Gson MATCHA$GSON = new GsonBuilder().setPrettyPrinting().create();

	@Shadow @Final private Path playerSavePath;

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
				MATCHA$LOGGER.info("Migrated {} legacy advancement entries in {}", migratedEntries, path);
			} finally {
				Files.deleteIfExists(temporary);
			}
		} catch (IOException | JsonParseException exception) {
			MATCHA$LOGGER.error("Couldn't migrate legacy advancements in {}", path, exception);
		}
	}
}
