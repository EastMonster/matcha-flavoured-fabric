package monster.east.matchaff.compat;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.Set;

public final class EnderscapeCompat {
	private static final ThreadLocal<ServerPlayer> ACTIVE_CRYSTAL_HEART = new ThreadLocal<>();

	private EnderscapeCompat() {
	}

	public static boolean hurtForCrystalHeart(ServerPlayer player) {
		ServerPlayer previous = ACTIVE_CRYSTAL_HEART.get();
		ACTIVE_CRYSTAL_HEART.set(player);
		try {
			return player.hurtServer(player.level(), player.level().damageSources().magic(), 999.0F);
		} finally {
			if (previous == null) {
				ACTIVE_CRYSTAL_HEART.remove();
			} else {
				ACTIVE_CRYSTAL_HEART.set(previous);
			}
		}
	}

	public static boolean isUsingCrystalHeart(LivingEntity entity) {
		return ACTIVE_CRYSTAL_HEART.get() == entity;
	}

	public static void init() {
		var loader = FabricLoader.getInstance();
		if (loader.isModLoaded("enderscape")) {
			Set<String> equipment = Set.of("dagger", "drift_leggings", "shadoline_helmet",
					"shadoline_chestplate", "shadoline_leggings", "shadoline_boots");
			DefaultItemComponentEvents.MODIFY.register(context -> context.modify(item -> {
				Identifier id = BuiltInRegistries.ITEM.getKey(item);
				return id.getNamespace().equals("enderscape") && equipment.contains(id.getPath());
			}, (builder, registries, item) -> {
				TooltipDisplay display = builder.build().getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
				builder.set(DataComponents.TOOLTIP_DISPLAY, display.withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true));
			}));
			ResourceLoader.registerBuiltinPack(
					Identifier.fromNamespaceAndPath("matcha", "enderscape_compat"),
					loader.getModContainer("matcha-flavoured").orElseThrow(),
					PackActivationType.ALWAYS_ENABLED);
		}
	}
}
