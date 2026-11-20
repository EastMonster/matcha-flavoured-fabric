package monster.east.matchaff.mechanic;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Sheep trampling and feeding from upstream 5b00bd4c, with feeding scoped to the interacted sheep. */
public final class SheepMechanics {
	public static final TagKey<Item> EDIBLE = TagKey.create(Registries.ITEM,
			Identifier.fromNamespaceAndPath("matcha", "sheep_edible"));
	private static final AttachmentType<Integer> TRAMPLE_TIMER = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath("matcha", "sheep_trample_timer"),
			builder -> builder.persistent(Codec.INT));
	private static final Set<Sheep> SHEEP = Collections.newSetFromMap(new IdentityHashMap<>());

	private SheepMechanics() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Sheep sheep) {
				SHEEP.add(sheep);
				if (level.dimension().equals(Level.OVERWORLD) && !sheep.hasHome()) setHome(sheep);
				if (!sheep.hasAttached(TRAMPLE_TIMER)) sheep.setAttached(TRAMPLE_TIMER, delay(sheep));
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> SHEEP.remove(entity));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SHEEP.clear());
		ServerTickEvents.END_SERVER_TICK.register(SheepMechanics::tick);
	}

	public static void onFed(Sheep sheep, ServerLevel level) {
		sheep.setAttached(TRAMPLE_TIMER, sheep.getAttachedOrThrow(TRAMPLE_TIMER) + 60);
		setHome(sheep);
		level.playSound(null, sheep.getX(), sheep.getY(), sheep.getZ(),
				SoundEvents.SHEEP_AMBIENT, SoundSource.NEUTRAL, 0.25F, 1.0F);
	}

	private static void setHome(Sheep sheep) {
		sheep.setHomeTo(sheep.blockPosition(), 13);
		sheep.getAttribute(Attributes.TEMPT_RANGE).setBaseValue(15.0);
	}

	private static int delay(Sheep sheep) {
		return 60 + sheep.getRandom().nextInt(61);
	}

	private static void tick(MinecraftServer server) {
		if (!server.tickRateManager().runsNormally() || server.getTickCount() % 20 != 0) return;
		for (Sheep sheep : SHEEP) {
			ServerLevel level = (ServerLevel) sheep.level();
			if (!sheep.isAlive() || sheep.isRemoved() || !level.isPositionEntityTicking(sheep.blockPosition())) continue;
			int remaining = sheep.getAttachedOrThrow(TRAMPLE_TIMER) - 1;
			if (remaining <= 0) {
				BlockPos ground = BlockPos.containing(sheep.getX(), sheep.getY() - 0.5, sheep.getZ());
				if (level.getBlockState(ground).is(Blocks.DIRT)) {
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
							sheep.getX(), sheep.getY(), sheep.getZ(), 20, 0.25, 0.25, 0.25, 0.1);
					level.setBlockAndUpdate(ground, Blocks.COARSE_DIRT.defaultBlockState());
				}
				remaining = delay(sheep);
			}
			sheep.setAttached(TRAMPLE_TIMER, remaining);
		}
	}
}
