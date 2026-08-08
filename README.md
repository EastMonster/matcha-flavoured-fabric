# Matcha Flavoured Fabric

> [!WARNING]
> The core content of this unofficial Fabric port has been tested, but some edge cases may differ from the original data pack. Please back up your worlds before playing.

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

Use a new world when possible. Existing items from the data-pack version are not automatically converted into the mod's independently registered items.

## Improvements over the data pack

- Most custom equipment, food, fish, blessings, behavior items, and music discs are registered as independent items under the `matcha-flavoured` namespace instead of repurposing vanilla items, which makes the added items easier to distinguish and browse in inventory tools such as JEI and Jade. Item IDs and commands therefore differ from the data-pack version, and **existing data-pack stacks are not converted automatically**. Registered items are placed across the relevant vanilla creative tabs.
- Food healing is now scheduled per meal instead of relying on the regeneration effect the data pack uses. Vanilla regeneration refreshes in place (its duration never stacks), so eating the same food twice in a row silently lost part of the heal. Each food's hidden healing-simulation segment now ticks independently, so consecutive bites heal their full intended amount. 
- Breaking cracks no longer appear floating in the air around leaves and glow lichen: the breaking animation now drops quads that fall outside the block's bounds. The out-of-bounds leaf extension panels are also culled inside canopies to reduce the number of rendered faces, while the canopy keeps its solid look.
- Adds an AI-assisted Simplified Chinese translation.

## Changes that may cause behavioral differences

- Command-driven mechanics are reimplemented in Java. Tested gameplay is intended to match the data pack, but very short timing windows, multiplayer target selection, and server restarts during delayed actions may behave differently.
- Anemos uses an internal 20-tick player cooldown instead of the visible Unluck effect used as a timer by the data pack. Milk and cleansing cannot reset this cooldown.
- Gives selected vanilla food carriers Matcha components by default so creative-tab and plain `/give` stacks match their crafted or dropped counterparts. This can also apply Matcha food behavior to those vanilla IDs when another source creates an otherwise unmodified stack.
- Soul Sight stores one pending activation per player. Eating different Soul Sight foods within the 48-tick delay makes the later activation replace the earlier one. Repeating the same food behaves like the data pack's replaced scheduled function.
- The `endless_repairs` compatibility mechanic also clears prior-work penalties from armor and offhand slots; the data pack scans only the main inventory and hotbar. This has little practical effect while anvils are free.

Bug reports are welcome, but exact one-to-one compatibility with every data-pack edge case is not promised.

## Credits

This is an unofficial Fabric port of Klei's original [Matcha Flavoured](https://modrinth.com/datapack/matcha-flavoured) data pack. All credit for the original design, content, textures, and data pack goes to Klei and the contributors credited by the original project.

See [CREDITS.txt](CREDITS.txt) for the original acknowledgements and sources of inspiration.

Thanks to OpenAI and Deepseek.

## License

This project is distributed under the [CC BY-NC-SA 4.0 license](LICENSE), following the original project's licensing terms.
