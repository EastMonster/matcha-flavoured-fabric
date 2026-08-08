package monster.east.matchaff;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * World-level mechanics that replace the last datapack functions: mob spawn
 * control, village eerie ambience, boat/sulphur particles, floating divine
 * items, the slow day cycle, glass-bottle crafting bonus, infinite anvil
 * repairs and the global gamerules.
 */
public final class WorldMechanics {
	private static final Identifier EERIE_ADVANCEMENT = id("mechanics/enter_village_plains");
	private static final Identifier GLASS_BOTTLE_ADVANCEMENT = id("glass_bottle_from_crafting");
	private static final Identifier BOARDING_BOAT_ADVANCEMENT = id("particle/boarding_boat");
	private static final Identifier ENDLESS_REPAIRS_ADVANCEMENT =
			Identifier.fromNamespaceAndPath("endless_repairs", "inventory_changed");

	private static final TagKey<EntityType<?>> MUNDANE_HOSTILES = TagKey.create(Registries.ENTITY_TYPE, id("mundane_hostiles"));
	private static final List<ResourceKey<Structure>> VILLAGES = List.of(
			structure("village_plains"), structure("village_desert"), structure("village_savanna"),
			structure("village_snowy"), structure("village_taiga")
	);

	private static final AttachmentType<Boolean> EERIE = AttachmentRegistry.create(id("eerie"));
	private static final AttachmentType<Integer> EERIE_TIMER = AttachmentRegistry.create(id("eerie_timer"));

	private static final Map<UUID, Integer> LAST_BOATING_DISTANCE = new HashMap<>();
	private static final Set<ItemEntity> DIVINE_ITEMS =
			Collections.newSetFromMap(new IdentityHashMap<>());

	private WorldMechanics() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(WorldMechanics::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> DIVINE_ITEMS.clear());
		ServerEntityEvents.ENTITY_LOAD.register(WorldMechanics::trackDivineItem);
		ServerEntityEvents.ENTITY_LOAD.register(WorldMechanics::initializeMundaneHostile);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> DIVINE_ITEMS.remove(entity));
		ServerTickEvents.END_SERVER_TICK.register(WorldMechanics::tick);
		ServerPlayerEvents.JOIN.register(WorldMechanics::welcome);
	}

	private static void onServerStarted(MinecraftServer server) {
		var rules = server.getGameRules();
		rules.set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
		rules.set(GameRules.ADVANCE_TIME, false, server);
		rules.set(GameRules.SPAWN_PHANTOMS, false, server);
		rules.set(GameRules.KEEP_INVENTORY, true, server);
		rules.set(GameRules.BLOCK_EXPLOSION_DROP_DECAY, false, server);
		rules.set(GameRules.MOB_EXPLOSION_DROP_DECAY, false, server);
		rules.set(GameRules.ENDER_PEARLS_VANISH_ON_DEATH, false, server);
		if (server.getScoreboard().getObjective("gamerule_safe_surface") == null) {
			server.getScoreboard().addObjective("gamerule_safe_surface", ObjectiveCriteria.DUMMY,
					Component.literal("gamerule_safe_surface"), ObjectiveCriteria.RenderType.INTEGER, true,
					StyledFormat.NO_STYLE);
		}
	}

	private static void tick(MinecraftServer server) {
		int tick = server.getTickCount();
		if (tick % 3 == 0) {
			var clock = server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD);
			server.clockManager().addTicks(clock, 1);
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			eerie(player);
			boatParticles(player);
			sulfurousHellstone(player);
			glassBottleReward(player);
			endlessRepairs(player);
		}
		divineFavour(tick);
	}

	private static void welcome(ServerPlayer player) {
		player.sendSystemMessage(Component.translatable("matcha.message.welcome")
				.withStyle(style -> style.withColor(TextColor.fromRgb(0x65E082))));
		player.sendSystemMessage(Component.translatable("matcha.message.welcome.lost")
				.withStyle(style -> style.withColor(TextColor.fromRgb(0x8FB398)))
				.append(Component.translatable("matcha.message.welcome.advancements")
						.withStyle(style -> style.withBold(true).withColor(TextColor.fromRgb(0x61BB78)))));
	}

	private static void eerie(ServerPlayer player) {
		if (advancementDone(player, EERIE_ADVANCEMENT)) {
			player.setAttached(EERIE, true);
			revoke(player, EERIE_ADVANCEMENT);
		}
		if (!player.getAttachedOrElse(EERIE, false)) {
			return;
		}
		var level = (ServerLevel) player.level();
		if (!inVillage(level, player.blockPosition())) {
			player.setAttached(EERIE, false);
			player.setAttached(EERIE_TIMER, 0);
			return;
		}
		if (player.tickCount % 20 == 0) {
			player.connection.send(new ClientboundStopSoundPacket(null, SoundSource.MUSIC));
			int seconds = player.getAttachedOrElse(EERIE_TIMER, 0) + 1;
			if (seconds > 150) {
				seconds = 1;
			}
			player.setAttached(EERIE_TIMER, seconds);
			var ground = level.getBlockState(player.blockPosition().below());
			if (seconds <= 4) {
				eerieCue(player, level, ground, seconds);
			}
			if (seconds == 100) {
				eerieCue(player, level, ground, 100);
			}
		}
	}

	private static void eerieCue(ServerPlayer player, ServerLevel level, BlockState ground, int step) {
		Vec3 pos = player.position();
		if (ground.is(Blocks.COARSE_DIRT) && step <= 4) {
			playAt(level, pos.x + step - 3, pos.y + 4, pos.z, SoundEvents.WOOD_STEP, 0.5F);
		}
		if (ground.is(Blocks.OAK_PLANKS)) {
			if (step == 1 || step == 100) {
				Vec3 right = localOffset(player, 5, 0, 0);
				playAt(level, right.x, right.y, right.z, SoundEvents.GRASS_BREAK, 1.0F);
			}
			if (step == 1) {
				playAt(level, pos.x, pos.y, pos.z, SoundEvents.AMBIENT_CAVE.value(), 1.0F);
			}
		}
		if (ground.is(Blocks.GRAVEL)) {
			playAt(level, pos.x, pos.y, pos.z, step == 1 ? SoundEvents.GRAVEL_BREAK : SoundEvents.STONE_PLACE, 1.0F);
		}
		if (ground.is(Blocks.SUSPICIOUS_GRAVEL) && step == 1) {
			Vec3 behind = localOffset(player, 0, 0, -3);
			playAt(level, behind.x, behind.y, behind.z, SoundEvents.WOODEN_DOOR_OPEN, 1.0F);
		}
		if (ground.is(Blocks.GRASS_BLOCK) && step <= 4) {
			playAt(level, pos.x, pos.y - 4, pos.z, SoundEvents.GRASS_BREAK, 0.5F);
			if (step > 1) {
				playAt(level, pos.x + (4 - step), pos.y - 4, pos.z, SoundEvents.STONE_PLACE, 0.5F);
			}
		}
	}

	private static void playAt(ServerLevel level, double x, double y, double z, net.minecraft.sounds.SoundEvent sound, float volume) {
		level.playSound(null, x, y, z, sound, SoundSource.PLAYERS, volume, 1.0F);
	}

	private static Vec3 localOffset(Entity entity, double right, double up, double forward) {
		Vec3 look = entity.getLookAngle();
		Vec3 f = new Vec3(look.x, 0, look.z).normalize();
		Vec3 r = new Vec3(-f.z, 0, f.x);
		return entity.position().add(r.scale(right)).add(0, up, 0).add(f.scale(forward));
	}

	private static boolean inVillage(ServerLevel level, BlockPos pos) {
		var lookup = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		var holders = VILLAGES.stream().map(lookup::getOrThrow).toList();
		return level.structureManager().getStructureWithPieceAt(pos, HolderSet.direct(holders)).isValid();
	}

	private static void boatParticles(ServerPlayer player) {
		boolean justBoarded = advancementDone(player, BOARDING_BOAT_ADVANCEMENT);
		if (!(player.getVehicle() instanceof AbstractBoat boat)) {
			if (justBoarded) {
				revoke(player, BOARDING_BOAT_ADVANCEMENT);
			}
			return;
		}
		var level = (ServerLevel) player.level();
		BlockPos waterPos = BlockPos.containing(boat.getX(), boat.getY() - 0.1, boat.getZ());
		boolean onWater = level.getBlockState(waterPos).is(Blocks.WATER);
		if (justBoarded) {
			if (onWater) {
				boatParticle(level, boat, ParticleTypes.SPLASH, 0.75, 0.5, 1.0, 5, 0.1, 0.1, 0.1, 1.0);
				boatParticle(level, boat, ParticleTypes.SPLASH, -0.75, 0.5, 1.0, 5, 0.1, 0.1, 0.1, 1.0);
				boatParticle(level, boat, ParticleTypes.SPLASH, 0.0, 0.5, 1.0, 5, 0.1, 0.1, 0.1, 1.0);
			}
			revoke(player, BOARDING_BOAT_ADVANCEMENT);
		}

		int currentDistance = player.getStats().getValue(Stats.CUSTOM, Stats.BOAT_ONE_CM);
		Integer previousDistance = LAST_BOATING_DISTANCE.put(player.getUUID(), currentDistance);
		if (!onWater || previousDistance == null) {
			return;
		}
		int travelled = Math.max(0, currentDistance - previousDistance);
		if (travelled >= 12) {
			boatParticle(level, boat, ParticleTypes.SPLASH, 0.75, 0.5, 1.0, 3, 0.1, 0.1, 0.1, 1.0);
			boatParticle(level, boat, ParticleTypes.SPLASH, -0.75, 0.5, 1.0, 3, 0.1, 0.1, 0.1, 1.0);
			boatParticle(level, boat, ParticleTypes.SPLASH, 0.0, 0.5, -1.1, 10, 0.25, 0.1, 0.25, 1.0);
			boatParticle(level, boat, ParticleTypes.SULFUR_BUBBLES, -0.75, 0.5, -1.0, 3, 0.1, 0.1, 0.1, 0.0);
			boatParticle(level, boat, ParticleTypes.SULFUR_BUBBLES, 0.75, 0.5, -1.0, 3, 0.1, 0.1, 0.1, 0.0);
		}
		if (travelled >= 1) {
			boatParticle(level, boat, ParticleTypes.SPLASH, 0.75, 0.1, 1.0, 1, 0.1, 0.1, 0.1, 0.1);
			boatParticle(level, boat, ParticleTypes.SPLASH, -0.75, 0.1, 1.0, 1, 0.1, 0.1, 0.1, 0.1);
		}
	}

	private static void boatParticle(
			ServerLevel level, AbstractBoat boat, net.minecraft.core.particles.ParticleOptions particle,
			double side, double up, double forward, int count,
			double spreadX, double spreadY, double spreadZ, double speed
	) {
		Vec3 position = localOffset(boat, side, up, forward);
		level.sendParticles(particle, position.x, position.y, position.z,
				count, spreadX, spreadY, spreadZ, speed);
	}

	private static void sulfurousHellstone(ServerPlayer player) {
		if (player.tickCount % 3 != 0) {
			return;
		}
		var level = (ServerLevel) player.level();
		if (level.getBlockState(player.blockPosition().below()).is(Blocks.NETHER_QUARTZ_ORE)) {
			level.sendParticles(ParticleTypes.NOXIOUS_GAS,
					player.getX(), player.getY() + 0.1, player.getZ(), 1, 0.5, 0, 0.5, 0);
		}
	}

	private static void glassBottleReward(ServerPlayer player) {
		if (advancementDone(player, GLASS_BOTTLE_ADVANCEMENT)) {
			player.addItem(new ItemStack(Items.GLASS_BOTTLE, 1));
			revoke(player, GLASS_BOTTLE_ADVANCEMENT);
		}
	}

	private static void endlessRepairs(ServerPlayer player) {
		if (!advancementDone(player, ENDLESS_REPAIRS_ADVANCEMENT)) {
			return;
		}
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.has(DataComponents.REPAIR_COST)) {
				stack.set(DataComponents.REPAIR_COST, 0);
			}
		}
		revoke(player, ENDLESS_REPAIRS_ADVANCEMENT);
	}

	private static void initializeMundaneHostile(Entity entity, ServerLevel level) {
		if (entity instanceof Mob mob
				&& mob.getType().builtInRegistryHolder().is(MUNDANE_HOSTILES)
				&& !mob.entityTags().contains("SpawnChecked")) {
			modifyMob(mob);
			mob.addTag("SpawnChecked");
		}
	}

	/**
	 * Datapack spawn rule: natural non-undead mundane hostiles may not spawn in
	 * open overworld areas. After the ender dragon dies (safe surface mode) any
	 * mundane hostile at the surface or above sea level is forbidden.
	 */
	public static boolean isForbiddenSpawn(EntityType<?> type, ServerLevel level, BlockPos pos) {
		if (level.dimension() != Level.OVERWORLD || !type.builtInRegistryHolder().is(MUNDANE_HOSTILES)) {
			return false;
		}
		boolean sky = level.canSeeSky(pos);
		if (isSafeSurface(level)) {
			return sky || (pos.getY() >= 63 && pos.getY() <= 350);
		}
		return !type.builtInRegistryHolder().is(EntityTypeTags.UNDEAD) && sky;
	}

	private static boolean isSafeSurface(ServerLevel level) {
		var objective = level.getServer().getScoreboard().getObjective("gamerule_safe_surface");
		return objective != null && level.getServer().getScoreboard()
				.getOrCreatePlayerScore(ScoreHolder.forNameOnly("gamerule"), objective).get() >= 1;
	}

	private static void modifyMob(Mob mob) {
		var type = mob.getType();
		if (type.builtInRegistryHolder().is(MUNDANE_HOSTILES)
				|| type == EntityTypes.ZOMBIFIED_PIGLIN || type == EntityTypes.PIGLIN) {
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				mob.setDropChance(slot, 0);
			}
		}
		boolean baby = mob instanceof AgeableMob ageable && ageable.isBaby();
		if (type.builtInRegistryHolder().is(EntityTypeTags.SKELETONS)) {
			setBase(mob, Attributes.MAX_HEALTH, 10);
		}
		if (type == EntityTypes.CREEPER) {
			setBase(mob, Attributes.MAX_HEALTH, 16);
		}
		if (type == EntityTypes.CAVE_SPIDER) {
			setBase(mob, Attributes.MAX_HEALTH, 4);
			setBase(mob, Attributes.MOVEMENT_SPEED, 0.4);
		}
		if (type == EntityTypes.ZOMBIE && !baby) {
			setBase(mob, Attributes.MOVEMENT_SPEED, 0.4);
		}
		if (type.builtInRegistryHolder().is(EntityTypeTags.ZOMBIES) && !baby) {
			setBase(mob, Attributes.STEP_HEIGHT, 1);
		}
		if (type == EntityTypes.HUSK && !baby) {
			setBase(mob, Attributes.MOVEMENT_SPEED, 0.28);
			setBase(mob, Attributes.ATTACK_DAMAGE, 7);
		}
	}

	private static void setBase(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
		var instance = mob.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	private static void trackDivineItem(Entity entity, ServerLevel level) {
		if (entity instanceof ItemEntity item && isDivineItem(item.getItem())) {
			DIVINE_ITEMS.add(item);
		}
	}

	private static boolean isDivineItem(ItemStack stack) {
		return stack.is(Items.NETHER_STAR)
				|| stack.is(Items.ENDER_EYE)
				|| stack.is(Items.TURTLE_SCUTE)
				|| stack.is(net.minecraft.core.registries.BuiltInRegistries.ITEM
						.getValue(Identifier.fromNamespaceAndPath("matcha-flavoured", "heart_container")));
	}

	private static void divineFavour(int tick) {
		boolean everyTenTicks = tick % 10 == 0;
		boolean everyThreeSeconds = tick % 60 == 0;
		DIVINE_ITEMS.removeIf(Entity::isRemoved);
		for (ItemEntity item : DIVINE_ITEMS) {
			if (!(item.level() instanceof ServerLevel level)) {
				continue;
			}
			ItemStack stack = item.getItem();
			if (stack.is(Items.NETHER_STAR)) {
				item.setNoGravity(true);
				item.needsSync = true;
				if (everyTenTicks) {
					level.sendParticles(ParticleTypes.END_ROD, item.getX(), item.getY() + 0.4, item.getZ(), 1, 0, 0, 0, 0.05);
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, item.getX(), item.getY() + 0.4, item.getZ(), 1, 0.15, 0.15, 0.15, 0);
				}
				if (everyThreeSeconds) {
					level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 1.0F, 1.0F, 1.0F),
							item.getX(), item.getY() + 0.4, item.getZ(), 1, 0, 0, 0, 0);
				}
				if (level.getBlockState(BlockPos.containing(item.getX(), item.getY() - 3, item.getZ())).isAir()) {
					item.setDeltaMovement(0, -0.05, 0);
				} else if (!level.getBlockState(BlockPos.containing(item.getX(), item.getY() - 0.5, item.getZ())).isAir()) {
					item.setDeltaMovement(0, 0.025, 0);
				}
			} else if (stack.is(Items.ENDER_EYE)) {
				item.setNoGravity(true);
				item.needsSync = true;
				if (everyTenTicks) {
					level.sendParticles(ParticleTypes.PORTAL, item.getX(), item.getY() + 0.3, item.getZ(), 1, 0.1, 0.1, 0.1, 0.5);
				}
				if (level.getBlockState(BlockPos.containing(item.getX(), item.getY() - 3, item.getZ())).isAir()) {
					item.setDeltaMovement(0, -0.1, 0);
				} else if (!level.getBlockState(BlockPos.containing(item.getX(), item.getY() - 0.5, item.getZ())).isAir()) {
					item.setDeltaMovement(0, 0.025, 0);
				}
			} else if (stack.is(Items.TURTLE_SCUTE) || stack.is(net.minecraft.core.registries.BuiltInRegistries.ITEM
					.getValue(Identifier.fromNamespaceAndPath("matcha-flavoured", "heart_container")))) {
				if (everyTenTicks) {
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, item.getX(), item.getY() + 0.4, item.getZ(), 1, 0.1, 0.1, 0.1, 0);
				}
			}
		}
	}

	private static boolean advancementDone(ServerPlayer player, Identifier advancementId) {
		AdvancementHolder advancement = player.level().getServer().getAdvancements().get(advancementId);
		return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
	}

	private static void revoke(ServerPlayer player, Identifier advancementId) {
		AdvancementHolder advancement = player.level().getServer().getAdvancements().get(advancementId);
		if (advancement != null) {
			for (String criterion : advancement.value().criteria().keySet()) {
				player.getAdvancements().revoke(advancement, criterion);
			}
		}
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("main", path);
	}

	private static ResourceKey<Structure> structure(String path) {
		return ResourceKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath("minecraft", path));
	}
}
