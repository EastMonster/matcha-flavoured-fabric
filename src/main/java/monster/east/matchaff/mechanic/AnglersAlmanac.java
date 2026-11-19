package monster.east.matchaff.mechanic;

import monster.east.matchaff.mixin.CompositeLootEntryAccessor;
import monster.east.matchaff.mixin.LootPoolEntryAccessor;
import monster.east.matchaff.mixin.LootTableAccessor;
import monster.east.matchaff.mixin.NestedLootTableAccessor;
import monster.east.matchaff.network.AnglersAlmanacPayload;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.predicates.LocationPredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.StatsCounter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.AlternativesEntry;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LocationCheck;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class AnglersAlmanac {
	private static final Identifier ALMANAC = Identifier.fromNamespaceAndPath("matcha", "anglers_almanac");
	private static final Identifier ROOT = ALMANAC.withSuffix("/root");
	private static final String FISHING_PATH = "gameplay/fishing/";
	private static final ResourceKey<LootTable> BIOMES = ResourceKey.create(Registries.LOOT_TABLE,
			Identifier.withDefaultNamespace(FISHING_PATH + "biomes"));

	private AnglersAlmanac() {
	}

	public static void init() {
		AnglersAlmanacPayload.register();
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack book = player.getItemInHand(hand);
			if (hand == InteractionHand.MAIN_HAND && book.is(Items.FISHING_ROD)
					&& player.fishing != null && player.isShiftKeyDown()) {
				book = player.getOffhandItem();
			}
			if (player.isSpectator() || !ALMANAC.equals(BuiltInRegistries.ITEM.getKey(book.getItem()))) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer serverPlayer) {
				if (!ServerPlayNetworking.canSend(serverPlayer, AnglersAlmanacPayload.TYPE)) {
					return InteractionResult.PASS;
				}
				ItemStack rod = player.getMainHandItem().is(Items.FISHING_ROD) ? player.getMainHandItem() : player.getOffhandItem();
				ServerPlayNetworking.send(serverPlayer, contents(serverPlayer, rod));
			}
			return InteractionResult.SUCCESS;
		});
	}

	private static AnglersAlmanacPayload contents(ServerPlayer player, ItemStack rod) {
		var server = player.level().getServer();
		AdvancementNode root = server.getAdvancements().tree().get(ROOT);
		List<AnglersAlmanacPayload.Water> waters = new ArrayList<>();
		int selectedWater = 0;
		Optional<Identifier> currentBiome = Optional.empty();
		if (root != null) {
			LootTable biomeTable = server.reloadableRegistries().getLootTable(BIOMES);
			Map<Identifier, List<Identifier>> biomes = biomesByWater(biomeTable);
			Entity origin = lookupOrigin(player, rod);
			Optional<Identifier> currentWater = currentWater(player, rod, biomeTable, origin);
			for (AdvancementNode water : root.children()) {
				var display = water.advancement().display();
				if (display.isEmpty()) {
					continue;
				}
				List<AnglersAlmanacPayload.Catch> catches = new ArrayList<>();
				collectCatches(water, player.getStats(), catches);
				Identifier waterId = water.holder().id();
				if (currentWater.filter(waterId::equals).isPresent()) {
					selectedWater = waters.size();
					currentBiome = player.level().getBiome(origin.blockPosition()).unwrapKey().map(ResourceKey::identifier);
				}
				waters.add(new AnglersAlmanacPayload.Water(display.get().title(), display.get().icon().create(),
						biomes.getOrDefault(waterId, List.of()), catches));
			}
		}
		return new AnglersAlmanacPayload(waters, selectedWater, currentBiome);
	}

	private static void collectCatches(AdvancementNode category, StatsCounter stats, List<AnglersAlmanacPayload.Catch> catches) {
		for (AdvancementNode node : category.children()) {
			node.advancement().display().ifPresent(display -> {
				ItemStack icon = display.icon().create();
				int count = stats.getValue(FishingStatsMechanics.FISHING_CAUGHT_BY_ITEM, icon.getItem());
				catches.add(count > 0
						? new AnglersAlmanacPayload.Catch(icon, count, display.title(), display.description())
						: new AnglersAlmanacPayload.Catch(icon, 0, CommonComponents.EMPTY, CommonComponents.EMPTY));
			});
			collectCatches(node, stats, catches);
		}
	}

	private static Map<Identifier, List<Identifier>> biomesByWater(LootTable biomeTable) {
		Map<Identifier, List<Identifier>> result = new HashMap<>();
		Set<Identifier> assigned = new HashSet<>();
		for (var pool : ((LootTableAccessor) biomeTable).matcha$getPools()) {
			for (var entry : pool.entries) {
				if (!(entry instanceof AlternativesEntry)) {
					continue;
				}
				for (var branch : ((CompositeLootEntryAccessor) entry).matcha$getChildren()) {
					if (!(branch instanceof NestedLootTable nested)) {
						continue;
					}
					var category = categoryForTable((NestedLootTableAccessor) nested);
					var condition = ((LootPoolEntryAccessor) branch).matcha$getCondition().map(Holder::value);
					if (category.isPresent() && condition.orElse(null) instanceof LocationCheck location) {
						var listed = result.computeIfAbsent(category.get(), ignored -> new ArrayList<>());
						location.predicate().flatMap(LocationPredicate::biomes).ifPresent(biomes -> biomes.stream()
								.flatMap(biome -> biome.unwrapKey().stream()).map(ResourceKey::identifier)
								.filter(assigned::add).forEach(listed::add));
					}
				}
			}
		}
		return result;
	}

	private static Entity lookupOrigin(ServerPlayer player, ItemStack rod) {
		FishingHook hook = player.fishing;
		if (hook != null && !hook.isRemoved() && hook.level() == player.level() && rod.is(Items.FISHING_ROD)) {
			return hook;
		}
		return player;
	}

	private static Optional<Identifier> currentWater(ServerPlayer player, ItemStack rod, LootTable biomeTable, Entity origin) {
		LootParams params = new LootParams.Builder(player.level())
				.withParameter(LootContextParams.ORIGIN, origin.position())
				.withParameter(LootContextParams.TOOL, rod.is(Items.FISHING_ROD) ? rod : ItemStack.EMPTY)
				.withOptionalParameter(LootContextParams.THIS_ENTITY, origin == player ? null : origin)
				.create(LootContextParamSets.FISHING);
		LootContext context = new LootContext.Builder(params)
				.withOptionalRandomSource(RandomSource.create(0L)).create(Optional.empty());
		// shortcut: Matcha's biome selector is an alternatives entry of table references, revisit if it changes shape.
		for (var pool : ((LootTableAccessor) biomeTable).matcha$getPools()) {
			if (pool.condition.isPresent() && !pool.condition.get().value().test(context)) {
				continue;
			}
			for (var entry : pool.entries) {
				if (!(entry instanceof AlternativesEntry)) {
					continue;
				}
				var condition = ((LootPoolEntryAccessor) entry).matcha$getCondition();
				if (condition.isPresent() && !condition.get().value().test(context)) {
					continue;
				}
				for (var branch : ((CompositeLootEntryAccessor) entry).matcha$getChildren()) {
					var branchCondition = ((LootPoolEntryAccessor) branch).matcha$getCondition();
					if (branchCondition.isEmpty() || branchCondition.get().value().test(context)) {
						return branch instanceof NestedLootTable ? categoryForTable((NestedLootTableAccessor) branch) : Optional.empty();
					}
				}
			}
		}
		return Optional.empty();
	}

	private static Optional<Identifier> categoryForTable(NestedLootTableAccessor nested) {
		return nested.matcha$getTables().stream().findFirst().flatMap(table -> table.unwrapKey())
				.map(ResourceKey::identifier)
				.filter(id -> id.getNamespace().equals("minecraft") && id.getPath().startsWith(FISHING_PATH))
				.map(id -> id.getPath().substring(FISHING_PATH.length()).replaceFirst("^(freshwater|saltwater)_", "$1/"))
				.map(path -> ALMANAC.withSuffix("/" + path + "/root"));
	}
}
