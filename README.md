# Matcha Flavoured Fabric

> [!WARNING]
> - The core content of this unofficial Fabric port has been tested, but some edge cases may differ from the original data pack. Please back up your worlds before playing.
> - This mod does not support playing directly in worlds previously used with the data pack. Please create a new world.
> - Multiplayer behavior has not been tested. Use this mod with caution in multiplayer.

[中文说明](README_ZH.md)

A Fabric mod port of Klei Wright's [**Matcha Flavoured**](https://modrinth.com/datapack/matcha-flavoured) data and resource pack for Minecraft 26.2.

I made this port because I am not particularly fond of using heavily modified vanilla items as new items, as the original data pack does. That approach also makes browsing and using the content with JEI inconvenient. The Fabric version therefore registers its equipment, food, fish, blessings, music discs, and most other custom content as proper mod items.

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.3 or newer
- Fabric API 0.156.0+26.2 or newer
- Java 25

## Installation

Install Fabric Loader and Fabric API, then place the mod JAR in your `mods` folder.

Create a new world to play this mod. Existing items from the data-pack version are not automatically converted into the mod's independently registered items.

## Improvements over the data pack

- Most custom equipment, food, fish, blessings, behavior items, and music discs are registered as independent items under the `matcha-flavoured` namespace instead of repurposing vanilla items, which makes the added items easier to distinguish and browse in inventory tools such as JEI and Jade. Item IDs and commands therefore differ from the data-pack version, and **existing data-pack stacks are not converted automatically**. Registered items are placed across the relevant vanilla creative tabs.
- Food healing is now scheduled per meal instead of relying on the regeneration effect the data pack uses. Vanilla regeneration refreshes in place (its duration never stacks), so eating the same food twice in a row silently lost part of the heal. Each food's hidden healing-simulation segment now ticks independently, so consecutive bites heal their full intended amount. 
- Breaking cracks no longer appear floating in the air around leaves and glow lichen: the breaking animation now drops quads that fall outside the block's bounds. The out-of-bounds leaf extension panels are also culled inside canopies to reduce the number of rendered faces, while the canopy keeps its solid look.
- The Mod Menu settings screen can disable Overworld True Darkness per client without changing server gameplay.
- Adds an AI-assisted Simplified Chinese translation.

## Intentional behavior differences

- Vanilla carrier items and Matcha items are separate; only Matcha recipes, loot, and trades produce the corresponding mod items.
- Incidental carrier behavior unrelated to an item's design is not retained. For example, Cheese does not poison parrots or enter composters.
- When the Adamant set's Divinity count reaches five, it uses the four-piece maximum bonus. The data pack defines only one through four pieces, leaving five pieces with no effect.
- The Happy Ghast horn selects the nearest Happy Ghast within 80 blocks. The data pack selects an arbitrary matching entity.
- Hidden food-healing simulations are stored only for the current server session. If a single-player world is exited or the server stops before the healing finishes, the remaining hidden healing is discarded. Normally applied status effects are still saved by vanilla.

## Changes that may cause behavioral differences

- Command-driven mechanics are reimplemented in Java. Tested gameplay is intended to match the data pack, but very short timing windows, multiplayer target selection, and server restarts during delayed actions may behave differently.
- Gives selected vanilla food carriers Matcha components by default so creative-tab and plain `/give` stacks match their crafted or dropped counterparts. This can also apply Matcha food behavior to those vanilla IDs when another source creates an otherwise unmodified stack.

Bug reports are welcome, but exact one-to-one compatibility with every data-pack edge case is not promised.

## Credits

This is an unofficial Fabric port of Klei's original [Matcha Flavoured](https://modrinth.com/datapack/matcha-flavoured) data pack. All credit for the original design, content, textures, and data pack goes to Klei and the contributors credited by the original project.

See [CREDITS.txt](CREDITS.txt) for the original acknowledgements and sources of inspiration.

Thanks to OpenAI and Deepseek.

## License

This project is distributed under the [CC BY-NC-SA 4.0 license](LICENSE), following the original project's licensing terms.
