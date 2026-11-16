package monster.east.matchaff.client;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class MatchaClientConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("matcha");
	private static final Path PATH = FabricLoader.getInstance().getConfigDir()
			.resolve("matcha-flavoured-client.properties");
	private static boolean trueDarkness = true;
	private static boolean dolabraVisuals = true;
	private static boolean foodHealingPreview = true;

	private MatchaClientConfig() {
	}

	static void load() {
		if (!Files.exists(PATH)) {
			return;
		}
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(PATH)) {
			properties.load(reader);
			trueDarkness = Boolean.parseBoolean(properties.getProperty("true_darkness", "true"));
			dolabraVisuals = Boolean.parseBoolean(properties.getProperty("dolabra_visuals", "true"));
			foodHealingPreview = Boolean.parseBoolean(properties.getProperty("food_healing_preview", "true"));
		} catch (IOException exception) {
			LOGGER.warn("Could not read Matcha client config", exception);
		}
	}

	public static boolean trueDarkness() {
		return trueDarkness;
	}

	static void toggleTrueDarkness() {
		trueDarkness = !trueDarkness;
		save();
	}

	public static boolean dolabraVisuals() {
		return dolabraVisuals;
	}

	static void toggleDolabraVisuals() {
		dolabraVisuals = !dolabraVisuals;
		save();
	}

	public static boolean foodHealingPreview() {
		return foodHealingPreview;
	}

	static void toggleFoodHealingPreview() {
		foodHealingPreview = !foodHealingPreview;
		save();
	}

	private static void save() {
		Properties properties = new Properties();
		properties.setProperty("true_darkness", Boolean.toString(trueDarkness));
		properties.setProperty("dolabra_visuals", Boolean.toString(dolabraVisuals));
		properties.setProperty("food_healing_preview", Boolean.toString(foodHealingPreview));
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				properties.store(writer, "Matcha Flavoured client settings");
			}
		} catch (IOException exception) {
			LOGGER.warn("Could not save Matcha client config", exception);
		}
	}
}
