package monster.east.matchaff.mechanic;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import monster.east.matchaff.item.GudiItem;
import java.util.*;

public final class GudiMechanics {
	private static final TagKey<EntityType<?>> TARGETS = TagKey.create(Registries.ENTITY_TYPE, id("gudi_summons"));
	private static final AttachmentType<List<UUID>> BINDINGS = AttachmentRegistry.create(id("gudi_bindings"),
			builder -> builder.persistent(UUIDUtil.CODEC.listOf()));
	private static final Set<LivingEntity> PETS = Collections.newSetFromMap(new IdentityHashMap<>());

	private static final TicketType RECALL_TICKET = net.minecraft.core.Registry.register(
			net.minecraft.core.registries.BuiltInRegistries.TICKET_TYPE, id("gudi_recall"),
			new TicketType(220, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION));
	private static final Deque<Recall> PENDING = new ArrayDeque<>();
	private static final Set<Recall> BATCH = new LinkedHashSet<>();
	private static ServerLevel ticketLevel;
	private static ChunkPos ticketChunk;
	private static int deadline;
	private static boolean stopping;

	private record PetLocation(UUID pet, ResourceKey<Level> dimension, int x, int z, List<UUID> bindings) {
		private static final Codec<PetLocation> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("pet").forGetter(PetLocation::pet),
				Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(PetLocation::dimension),
				Codec.INT.fieldOf("x").forGetter(PetLocation::x),
				Codec.INT.fieldOf("z").forGetter(PetLocation::z),
				UUIDUtil.CODEC.listOf().fieldOf("bindings").forGetter(PetLocation::bindings)
		).apply(i, PetLocation::new));
	}

	private static final class PetData extends SavedData {
		private static final Codec<PetData> CODEC = PetLocation.CODEC.listOf().fieldOf("pets")
				.codec().xmap(PetData::new, data -> List.copyOf(data.pets.values()));
		private static final SavedDataType<PetData> TYPE = new SavedDataType<>(
				id("gudi_pets"), () -> new PetData(List.of()), CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
		private final Map<UUID, PetLocation> pets = new HashMap<>();
		private PetData(List<PetLocation> entries) { entries.forEach(entry -> pets.put(entry.pet(), entry)); }
	}

	private record Recall(UUID player, UUID pet, UUID key, ResourceKey<Level> dimension, int expires) {}

	private GudiMechanics() {}

	public static void init() {
		for (String song : List.of("endless", "os_piano", "sweden", "labyrinthine", "subwoofer_lullaby", "infinite_amethyst")) {
			Identifier sound = id("gudi." + song);
			net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT,
					sound, net.minecraft.sounds.SoundEvent.createVariableRangeEvent(sound));
		}
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof LivingEntity pet) {
				if (pet.hasAttached(BINDINGS)) PETS.add(pet);
				if (!stopping && (pet.hasAttached(BINDINGS) || data(level.getServer()).pets.containsKey(pet.getUUID()))) remember(pet, level);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			boolean tracked = PETS.remove(entity);
			if (!stopping && tracked && entity instanceof LivingEntity pet
					&& pet.getRemovalReason() != Entity.RemovalReason.CHANGED_DIMENSION) remember(pet, level);
		});
		ServerLifecycleEvents.SERVER_STARTING.register(server -> stopping = false);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (LivingEntity pet : List.copyOf(PETS)) remember(pet, (ServerLevel) pet.level());
			stopping = true;
			PENDING.clear();
			releaseTicket();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> { PETS.clear(); PENDING.clear(); });
		ServerTickEvents.END_SERVER_TICK.register(GudiMechanics::tickRecalls);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!player.isSpectator() && entity instanceof LivingEntity pet
					&& pet.getType().builtInRegistryHolder().is(TARGETS)
					&& net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id("amnestic"))) {
				if (level instanceof ServerLevel serverLevel && canForget(player, pet)) {
					forgetPet(pet, serverLevel);
					if (!player.isCreative()) stack.shrink(1);
				}
				return InteractionResult.SUCCESS;
			}
			if (player.isSpectator() || !(stack.getItem() instanceof GudiItem)
					|| !(entity instanceof LivingEntity pet) || !pet.getType().builtInRegistryHolder().is(TARGETS)) return InteractionResult.PASS;
			if (level instanceof ServerLevel serverLevel) {
				boolean success = false;
				if (canBind(player, pet)) {
					UUID key = key(stack, !player.isShiftKeyDown());
					List<UUID> bindings = new ArrayList<>(pet.getAttachedOrElse(BINDINGS, List.of()));
					if (key != null) success = player.isShiftKeyDown() ? bindings.remove(key)
							: !bindings.contains(key) && bindings.add(key);
					if (success) {
						if (bindings.isEmpty()) { pet.removeAttached(BINDINGS); PETS.remove(pet); }
						else { pet.setAttached(BINDINGS, List.copyOf(bindings)); PETS.add(pet); }
						remember(pet, serverLevel);
					}
				}
				serverLevel.sendParticles(success ? (player.isShiftKeyDown() ? ParticleTypes.SMOKE : ParticleTypes.NOTE)
						: ParticleTypes.ANGRY_VILLAGER, pet.getX(), pet.getY() + 1, pet.getZ(), 2, 0.2, 0.2, 0.2, 0);
				if (success && !player.isShiftKeyDown()) {
					var instrument = stack.get(DataComponents.INSTRUMENT);
					if (instrument != null) serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
							instrument.instrument().value().soundEvent().value(), SoundSource.RECORDS, 1, 1);
				} else serverLevel.playSound(null, pet.getX(), pet.getY(), pet.getZ(),
						SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), SoundSource.PLAYERS, 1, 0.5F);
			}
			return InteractionResult.SUCCESS;
		});
	}

	private static boolean canBind(Player player, LivingEntity pet) {
		if (pet instanceof OwnableEntity ownable && ownable.getOwnerReference() != null) {
			return ownable.getOwnerReference().getUUID().equals(player.getUUID());
		}
		return pet instanceof AbstractHorse horse && horse.isTamed()
				|| !pet.getItemBySlot(EquipmentSlot.SADDLE).isEmpty()
				|| pet.getItemBySlot(EquipmentSlot.BODY).is(net.minecraft.tags.ItemTags.HARNESSES);
	}

	private static boolean canForget(Player player, LivingEntity pet) {
		if (pet instanceof OwnableEntity ownable && ownable.getOwnerReference() != null) {
			return ownable.getOwnerReference().getUUID().equals(player.getUUID());
		}
		return pet instanceof TamableAnimal animal && animal.isTame()
				|| pet instanceof AbstractHorse horse && horse.isTamed();
	}

	private static void forgetPet(LivingEntity pet, ServerLevel level) {
		if (pet instanceof TamableAnimal animal) {
			animal.setOwner(null);
			animal.setOrderedToSit(false);
			animal.setInSittingPose(false);
			animal.setTame(false, true);
		} else if (pet instanceof AbstractHorse horse) {
			horse.setOwner(null);
			if (horse.getType() != EntityTypes.SKELETON_HORSE) horse.setTamed(false);
		}
		pet.removeAttached(BINDINGS);
		PETS.remove(pet);
		PENDING.removeIf(request -> request.pet().equals(pet.getUUID()));
		BATCH.retainAll(PENDING);
		if (BATCH.isEmpty()) releaseTicket();
		remember(pet, level);
		level.playSound(null, pet.getX(), pet.getY(), pet.getZ(),
				SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.NEUTRAL, 1, 0.7F);
	}

	private static UUID key(ItemStack stack, boolean create) {
		String text = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr("gudi_id", "");
		if (!text.isEmpty()) {
			try { return UUID.fromString(text); } catch (IllegalArgumentException ignored) { }
		}
		if (!create) return null;
		UUID key = UUID.randomUUID();
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("gudi_id", key.toString()));
		return key;
	}

	private static PetData data(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(PetData.TYPE);
	}

	private static void remember(LivingEntity pet, ServerLevel level) {
		PetData data = data(level.getServer());
		List<UUID> bindings = pet.getAttachedOrElse(BINDINGS, List.of());
		// Chunk unloading marks an entity removed even though it is still alive in the save.
		if (pet.getHealth() <= 0 || bindings.isEmpty()
				|| pet.getRemovalReason() != null && pet.getRemovalReason().shouldDestroy()) {
			if (data.pets.remove(pet.getUUID()) != null) data.setDirty();
		} else {
			ChunkPos chunk = ChunkPos.containing(pet.blockPosition());
			PetLocation entry = new PetLocation(pet.getUUID(), level.dimension(), chunk.x(), chunk.z(), List.copyOf(bindings));
			if (!entry.equals(data.pets.put(pet.getUUID(), entry))) data.setDirty();
		}
	}

	public static void recall(Player player, ItemStack stack) {
		UUID key = key(stack, false);
		if (key == null || !(player.level() instanceof ServerLevel level)) return;
		for (LivingEntity pet : List.copyOf(PETS)) {
			if (!pet.isAlive() || pet.isRemoved() || pet.level() != level
					|| !pet.getAttachedOrElse(BINDINGS, List.of()).contains(key)) continue;
			teleportPet(pet, player, level);
		}
		for (PetLocation entry : List.copyOf(data(level.getServer()).pets.values())) {
			if (!entry.dimension().equals(level.dimension()) || !entry.bindings().contains(key)
					|| level.getEntity(entry.pet()) != null || PENDING.stream().anyMatch(r -> r.pet().equals(entry.pet()))) continue;
			if (PENDING.size() >= 64) {
				player.sendSystemMessage(Component.translatable("message.matcha.gudi.recall_queue_full"));
				break;
			}
			PENDING.addLast(new Recall(player.getUUID(), entry.pet(), key, level.dimension(), level.getServer().getTickCount() + 1200));
		}
	}

	private static void teleportPet(LivingEntity pet, Player player, ServerLevel level) {
		if (pet instanceof TamableAnimal animal) {
			animal.setOrderedToSit(false);
			animal.setInSittingPose(false);
		}
		pet.teleportTo(player.getX(), player.getY(), player.getZ());
		remember(pet, level);
		level.sendParticles(ParticleTypes.NOTE, pet.getX(), pet.getY() + 1, pet.getZ(), 2, 0.3, 0.3, 0.3, 0);
	}

	private static void tickRecalls(MinecraftServer server) {
		if (stopping || !server.tickRateManager().runsNormally()) return;
		int tick = server.getTickCount();
		PENDING.removeIf(request -> {
			var player = server.getPlayerList().getPlayer(request.player());
			return player == null || !player.isAlive() || !player.level().dimension().equals(request.dimension())
					|| tick >= request.expires();
		});
		BATCH.retainAll(PENDING);
		if (BATCH.isEmpty()) {
			releaseTicket();
			Recall first = PENDING.peekFirst();
			if (first == null) return;
			PetLocation location = data(server).pets.get(first.pet());
			ServerLevel level = server.getLevel(first.dimension());
			if (location == null || level == null || !location.dimension().equals(first.dimension())) {
				PENDING.removeFirst();
				return;
			}
			ticketLevel = level;
			ticketChunk = new ChunkPos(location.x(), location.z());
		}
		// Include queued pets from this chunk, even when other chunks are between them in the queue.
		for (Recall request : PENDING) {
			PetLocation location = data(server).pets.get(request.pet());
			if (location != null && request.dimension().equals(ticketLevel.dimension())
					&& location.dimension().equals(request.dimension())
					&& location.x() == ticketChunk.x() && location.z() == ticketChunk.z()) BATCH.add(request);
		}
		for (Recall request : List.copyOf(BATCH)) {
			var player = server.getPlayerList().getPlayer(request.player());
			PetLocation entry = data(server).pets.get(request.pet());
			if (entry == null || !entry.dimension().equals(request.dimension())
					|| !entry.bindings().contains(request.key())) { finishRecall(request); continue; }
			if (entry.x() != ticketChunk.x() || entry.z() != ticketChunk.z()) {
				BATCH.remove(request);
				continue;
			}
			Entity entity = ticketLevel.getEntity(request.pet());
			if (entity instanceof LivingEntity pet && pet.isAlive() && !pet.isRemoved()) {
				if (pet.getAttachedOrElse(BINDINGS, List.of()).contains(request.key())) teleportPet(pet, player, ticketLevel);
				else remember(pet, ticketLevel);
				finishRecall(request);
			} else if (deadline != 0 && tick >= deadline) {
				player.sendSystemMessage(Component.translatable("message.matcha.gudi.recall_failed"));
				finishRecall(request);
			} else if (ticketLevel.areEntitiesLoaded(ticketChunk.pack())) {
				// Only remove a stale index after the entity storage has actually finished loading.
				PetData saved = data(server);
				saved.pets.remove(request.pet());
				saved.setDirty();
				player.sendSystemMessage(Component.translatable("message.matcha.gudi.recall_failed"));
				finishRecall(request);
			}
		}
		if (BATCH.isEmpty()) releaseTicket();
		else if (deadline == 0) {
			deadline = tick + 200;
			ticketLevel.getChunkSource().addTicketWithRadius(RECALL_TICKET, ticketChunk, 2);
		}
	}

	private static void finishRecall(Recall request) { PENDING.remove(request); BATCH.remove(request); }

	private static void releaseTicket() {
		if (ticketLevel != null) ticketLevel.getChunkSource().removeTicketWithRadius(RECALL_TICKET, ticketChunk, 2);
		ticketLevel = null;
		ticketChunk = null;
		deadline = 0;
		BATCH.clear();
	}

	private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("matcha", path); }
}
