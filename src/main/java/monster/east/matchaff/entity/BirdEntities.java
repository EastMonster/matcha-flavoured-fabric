package monster.east.matchaff.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class BirdEntities {
	public static final EntityType<Bird.Duck> DUCK = register("duck", Bird.Duck::new);
	public static final EntityType<Bird.Seagull> SEAGULL = register("seagull", Bird.Seagull::new);
	public static final EntityType<Bird.Bobwhite> BOBWHITE = register("bobwhite", Bird.Bobwhite::new);

	private BirdEntities() {
	}

	private static <T extends Bird> EntityType<T> register(String name, EntityType.EntityFactory<T> factory) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("matcha", name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
				EntityType.Builder.of(factory, MobCategory.CREATURE).sized(0.4F, 0.7F).eyeHeight(0.644F)
						.passengerAttachments(new Vec3(0.0, 0.7, -0.1)).clientTrackingRange(10).build(key));
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(DUCK, Chicken.createAttributes().add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0));
		FabricDefaultAttributeRegistry.register(SEAGULL, Chicken.createAttributes().add(Attributes.SCALE, 0.8).add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0));
		FabricDefaultAttributeRegistry.register(BOBWHITE, Chicken.createAttributes().add(Attributes.SCALE, 0.8));
		for (EntityType<? extends Bird> type : List.of(DUCK, SEAGULL, BOBWHITE)) {
			SpawnPlacements.register(type, SpawnPlacementTypes.ON_GROUND,
					Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules);
		}
		for (String species : new String[]{"duck", "seagull", "bobwhite"}) {
			for (String sound : new String[]{"ambient", "hurt"}) {
				Identifier id = Identifier.fromNamespaceAndPath("matcha", "entity." + species + "." + sound);
				Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
			}
		}
	}
}
