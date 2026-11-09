# CliffTree compatibility

Source: CliffTree 4.0.1 Fabric for Minecraft 26.3, under CC BY-NC-SA 4.0.
See the mod's CREDITS.txt for attribution and source links.

This server data pack is registered as `matcha:clifftree_compat`, always enabled
only when the `clifftree` mod is installed. The standalone CliffTree data pack
is not detected. No additional dependency or Mixin is needed.

## Merge policy

- Start with CliffTree biome definitions. Preserve its climate, visuals, custom
  features and intentional removal of vanilla terrain features.
- Compare Matcha biome features with vanilla Minecraft 26.3 resources. Add
  Matcha-specific additions and retain Matcha's removal/replacement of default
  beach flowers, desert flowers and swamp trees.
- Keep CliffTree-only mobs, add Matcha-only spawn entries relative to vanilla,
  and use Matcha's entries for mobs shared by both definitions. This includes
  Matcha's Husk additions without adding ordinary farm animals to cave biomes.
  Also apply spawn parameters to the five sky biomes with ordinary hostile
  spawns, despite their empty terrain-feature lists. Explicitly retain Matcha's
  Husk entry in warm_caves even though Husk already exists in vanilla desert
  and would otherwise be missed by an additions-only comparison.
- Keep Matcha's empty monster list in pale_garden and its removal of chickens
  from plains, sunflower_plains and swamp. Preserve both distinct slime entries
  in swamp, mangrove_swamp and bog: weight 100/count 4 and weight 1/count 1.
  pale_grotto retains its CliffTree spawn table; do not apply pale_garden's
  empty monster list to it. Title panorama resources are not overridden.
- Preserve Matcha feature ordering. Reconcile CliffTree's remaining feature
  ordering globally, including its custom biomes, to avoid FeatureSorter cycles.
  Keep placed-feature tags intact so Lithostitched's conditional tag overrides
  still work.
- Use CliffTree's six poplar feature definitions unchanged, including sapling
  growth rules. Use Matcha's emerald placed feature and snow-block loot unchanged.
  Restore the vanilla emerald ore feature used by standalone Matcha: size 3,
  no air-exposure discard and no CliffTree rock-patch overlay. This also applies
  to Matcha's deep and mountain emerald placements, which reference this feature.
- Keep Matcha's fossil (redstone ore) placement and loot. Retain CliffTree's
  redstone ore feature, including its rock-patch overlay and extra placements.
- Keep all five Matcha Beta village structure definitions except `biomes`, which
  uses CliffTree's corresponding vanilla biome tags.
- Add `clifftree:shipwreck` to `minecraft:on_shipwreck_explorer_maps` because
  CliffTree 4.0.1 generates shipwrecks under that structure ID. This preserves
  Matcha's cartographer shipwreck-map destination.
- Keep Matcha's dimension gameplay attributes and 72000-tick day, time markers,
  gameplay tracks and linear sun angle. Use CliffTree's visual attributes and
  other visual timeline tracks, with keyframe times multiplied by three.
- Do not override the dimension generator, multi-noise biome parameter list or
  optional overlay configuration. CliffTree controls whether sky biomes occur.
- Vanilla Preview's water-color restoration yields to biome colors whenever
  the CliffTree mod is installed, including the optional Sodium path.
- Append CliffTree biomes to Matcha's fishing categories, including artificial
  water pools in sky biomes. Route pale_grotto and sulfur_crags to Matcha's pale
  garden and sulfur cave fishing tables through compatibility-only Matcha tags.
  Keep the existing fishing weights, drops and vanilla-biome classifications.
- Append CliffTree's snowy biome tag and frozen/jagged sky summits to
  `minecraft:is_frozen` for Matcha's freezing-water effects. This includes
  ice_caves through CliffTree's snowy tag. Do not expand Abbey biome eligibility.

## Fishing classifications

These categories follow water type and climate independently of the terrain
counterparts below. Existing tags are extended without `replace`.

| Category | CliffTree biomes |
| --- | --- |
| Cold freshwater | glacier_cliff, glacier_valley, mantled_forest, snowy_old_growth_taiga, tundra, sky/frozen_summit, sky/jagged_summit; also minecraft:ice_caves |
| Cool freshwater | cold_caves, cold_river, sky/stony_summit |
| Temperate freshwater | caves, dirt_caves, dusty_river, lukewarm_caves, mushroom_caves, sparse_forest; all seven empty sky biomes |
| Hot wet freshwater | tropical_river |
| Hot dry freshwater | coniferous_badlands, desert_cliff, inferno, oasis, shrubland, warm_caves, warm_river |
| Cold saltwater | snowy_diorite_shore |
| Cool saltwater | diorite_shore, gravelly_beach, kelp_forest |
| Temperate saltwater | granite_shore, stone_ocean |
| Warm saltwater | tropical_beach |
| Swamp | bog |
| Pale garden | pale_grotto |
| Sulfur caves | sulfur_crags |

## Matcha additions in CliffTree-only biomes

These counterparts supply only Matcha's changes relative to vanilla; they do not
replace CliffTree's terrain, climate, colors or distinct mobs.

| CliffTree biome | Matcha counterpart |
| --- | --- |
| bog | swamp |
| caves, dirt_caves, lukewarm_caves | plains |
| cold_caves, glacier_valley, tundra | snowy_plains |
| cold_river, dusty_river, tropical_river, warm_river | river |
| coniferous_badlands | wooded_badlands |
| desert_cliff, oasis, shrubland, warm_caves | desert |
| diorite_shore, granite_shore, gravelly_beach | stony_shore |
| glacier_cliff, sky/frozen_summit | frozen_peaks |
| inferno, sulfur_crags | badlands |
| kelp_forest | cold_ocean |
| mantled_forest, snowy_old_growth_taiga | old_growth_spruce_taiga |
| mushroom_caves | mushroom_fields |
| pale_grotto | pale_garden |
| snowy_diorite_shore | snowy_beach |
| sparse_forest | forest |
| stone_ocean | ocean |
| tropical_beach | beach |
| sky/jagged_summit | jagged_peaks |
| sky/stony_summit | stony_peaks |
| sky/azure_skies, sky/celadon_skies, sky/indigo_skies, sky/viridian_skies | plains (spawns only) |
| sky/cerulean_skies | river (spawns only) |

CliffTree's `minecraft:ice_caves` and `minecraft:sulfur_caves`, which have no
Matcha biome file, use snowy_plains and badlands changes respectively. The
atmospore and the_frontier sky biomes and two deprecated empty placeholder
biomes remain untouched. Mushroom caves and pale grotto retain their existing
Husk exclusions, matching their Matcha counterparts.

When updating either mod, recompare all overlapping resources and recheck the
resolved placed-feature graph with and without the Lithostitched overlay.
Successful JSON parsing alone does not verify registry loading or generation.
