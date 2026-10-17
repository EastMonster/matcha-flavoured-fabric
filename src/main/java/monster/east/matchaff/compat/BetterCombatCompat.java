package monster.east.matchaff.compat;

import java.lang.reflect.Method;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.slf4j.LoggerFactory;

public final class BetterCombatCompat {
	private static Access access;
	private static boolean initialized;

	private BetterCombatCompat() {
	}

	// Keep Better Combat optional without linking its classes when it is absent.
	public static double attackRange(ItemStack stack, double baseRange) {
		if (!initialized) {
			initialized = true;
			if (FabricLoader.getInstance().isModLoaded("bettercombat")) {
				try {
					Method getter = Class.forName("net.bettercombat.logic.WeaponRegistry")
							.getMethod("getAttributes", ItemStack.class);
					Class<?> attributes = getter.getReturnType();
					access = new Access(getter, attributes.getMethod("attacks"),
							attributes.getMethod("attackRange"), attributes.getMethod("rangeBonus"));
				} catch (ReflectiveOperationException | LinkageError exception) {
					warn(exception);
				}
			}
		}
		if (access == null) {
			return Double.NaN;
		}
		try {
			Object attributes = access.getter().invoke(null, stack);
			if (attributes == null) {
				return Double.NaN;
			}
			Object[] attacks = (Object[]) access.attacks().invoke(attributes);
			if (attacks == null || attacks.length == 0) {
				return Double.NaN;
			}
			return resolveRange(stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY),
					baseRange, (double) access.attackRange().invoke(attributes),
					(double) access.rangeBonus().invoke(attributes));
		} catch (ReflectiveOperationException | LinkageError | ClassCastException exception) {
			access = null;
			warn(exception);
			return Double.NaN;
		}
	}

	static double resolveRange(ItemAttributeModifiers modifiers, double baseRange, double absoluteRange, double bonus) {
		// Better Combat ignores its preset range whenever the item has a range modifier, in any slot.
		if (modifiers.modifiers().stream().anyMatch(entry -> entry.attribute().equals(Attributes.ENTITY_INTERACTION_RANGE))) {
			return modifiers.compute(Attributes.ENTITY_INTERACTION_RANGE, baseRange, EquipmentSlot.MAINHAND);
		}
		return absoluteRange != 0 ? absoluteRange : baseRange + bonus;
	}

	public static boolean isAttackRangeLine(Component line) {
		if (line.getContents() instanceof TranslatableContents contents) {
			if (contents.getKey().equals("attribute.name.generic.attack_range")) {
				return true;
			}
			for (Object argument : contents.getArgs()) {
				if (argument instanceof Component component && isAttackRangeLine(component)) {
					return true;
				}
			}
		}
		return line.getSiblings().stream().anyMatch(BetterCombatCompat::isAttackRangeLine);
	}

	private static void warn(Throwable exception) {
		LoggerFactory.getLogger("matcha").warn("Better Combat range Lore unavailable; keeping existing tooltips", exception);
	}

	private record Access(Method getter, Method attacks, Method attackRange, Method rangeBonus) {
	}
}
