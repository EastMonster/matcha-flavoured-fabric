package monster.east.matchaff.mixin.compat;

import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public abstract class EnderscapeElytraSoundMixin {
	@Unique
	private static final Set<Identifier> MATCHA_ELYTRA = Set.of(
			Identifier.fromNamespaceAndPath("matcha", "hepatizon_elytra"),
			Identifier.fromNamespaceAndPath("matcha", "shakudo_elytra"));
	@Unique
	private static final Identifier BREAK_SOUND = Identifier.fromNamespaceAndPath("enderscape", "item.elytra.break");

	@Inject(method = "applyDamage", at = @At("TAIL"), require = 0)
	private void matcha$playElytraBreakSound(int damage, @Nullable ServerPlayer player,
			Consumer<ItemStack> onBreak, CallbackInfo ci) {
		ItemStack stack = (ItemStack) (Object) this;
		if (player == null || stack.getDamageValue() != stack.getMaxDamage() - 1
				|| !MATCHA_ELYTRA.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
			return;
		}
		BuiltInRegistries.SOUND_EVENT.get(BREAK_SOUND).ifPresent(sound ->
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
						sound.value(), player.getSoundSource(), 4.0F, 1.0F));
	}
}
