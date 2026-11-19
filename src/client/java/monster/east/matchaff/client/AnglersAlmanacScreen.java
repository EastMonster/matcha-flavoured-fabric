package monster.east.matchaff.client;

import monster.east.matchaff.network.AnglersAlmanacPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.ArrayList;

final class AnglersAlmanacScreen extends Screen {
	private static final Identifier PAPER = Identifier.fromNamespaceAndPath("matcha", "textures/gui/anglers_almanac/background.png");
	private static final Identifier TAB = Identifier.withDefaultNamespace("recipe_book/tab");
	private static final Identifier SELECTED_TAB = Identifier.withDefaultNamespace("recipe_book/tab_selected");
	private static final Component UNKNOWN = Component.translatable("book.matcha.anglers_almanac.unknown");
	private static final int PAGE_WIDTH = 147;
	private static final int PAGE_HEIGHT = 166;
	private static final int RIGHT_PAGE = 139;
	private static final int TAB_RAIL = 29;
	private static final int VISIBLE_TABS = 5;
	private static final int VISIBLE_BIOMES = 2;
	private static final int BIOME_ROW_HEIGHT = 18;
	private static final int VISIBLE_DESCRIPTION_LINES = 4;
	private static final int GRID_COLUMNS = 3;
	private static final int INK = 0xff332519;
	private static final int GREEN = 0xff385b30;
	private final List<AnglersAlmanacPayload.Water> waters;
	private final Identifier currentBiome;
	private int waterIndex;
	private int catchIndex;
	private int tabStart;
	private int biomeStart;
	private int descriptionStart;
	private final List<Choice> catchChoices = new ArrayList<>();
	private PageButton back;
	private PageButton forward;

	AnglersAlmanacScreen(AnglersAlmanacPayload contents) {
		super(Component.translatable("advancements.matcha.anglers_almanac.title"));
		waters = contents.waters().isEmpty()
				? List.of(new AnglersAlmanacPayload.Water(getTitle(), ItemStack.EMPTY, List.of(), List.of()))
				: contents.waters();
		waterIndex = Math.clamp(contents.selectedWater(), 0, waters.size() - 1);
		currentBiome = contents.currentBiome().orElse(null);
		tabStart = clampScroll(waterIndex - VISIBLE_TABS + 1, waters.size(), VISIBLE_TABS);
		revealCurrentBiome();
	}

	private int left() {
		return (width - (RIGHT_PAGE + PAGE_WIDTH + TAB_RAIL)) / 2 + TAB_RAIL;
	}

	private int top() {
		return (height - (PAGE_HEIGHT + 26)) / 2;
	}

	private AnglersAlmanacPayload.Water water() {
		return waters.get(waterIndex);
	}

	@Override
	protected void init() {
		int left = left();
		int top = top();
		int railX = left - TAB_RAIL;
		if (waters.size() > VISIBLE_TABS) {
			addRenderableWidget(Button.builder(Component.literal("▲"), button -> scrollTabs(-1))
					.bounds(railX + 3, top + 2, 21, 13).build()).active = tabStart > 0;
			addRenderableWidget(Button.builder(Component.literal("▼"), button -> scrollTabs(1))
					.bounds(railX + 3, top + 143, 21, 13).build()).active =
					tabStart + VISIBLE_TABS < waters.size();
		}
		for (int index = tabStart; index < Math.min(tabStart + VISIBLE_TABS, waters.size()); index++) {
			addRenderableWidget(new Choice(railX, top + 18 + (index - tabStart) * 24, 31, 24, index, true));
		}

		catchChoices.clear();
		addCatchGrid();

		int right = left + RIGHT_PAGE;
		back = addRenderableWidget(new PageButton(right + 14, top + 145, false, button -> selectCatch(catchIndex - 1), true));
		forward = addRenderableWidget(new PageButton(right + 110, top + 145, true, button -> selectCatch(catchIndex + 1), true));
		back.visible = catchIndex > 0;
		forward.visible = catchIndex + 1 < water().catches().size();
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds((width - 132) / 2, top + PAGE_HEIGHT + 5, 132, 20).build());
	}

	private void selectWater(int index) {
		if (index != waterIndex) {
			waterIndex = index;
			catchIndex = 0;
			descriptionStart = 0;
			revealCurrentBiome();
			rebuildWidgets();
		}
	}

	private void revealCurrentBiome() {
		List<Identifier> biomes = water().biomes();
		biomeStart = currentBiome == null ? 0 : clampScroll(biomes.indexOf(currentBiome), biomes.size(), VISIBLE_BIOMES);
	}

	private void selectCatch(int index) {
		if (index >= 0 && index < water().catches().size() && index != catchIndex) {
			boolean changePage = index / gridSize() != catchIndex / gridSize();
			boolean gridFocused = catchChoices.contains(getFocused());
			catchIndex = index;
			descriptionStart = 0;
			if (changePage) {
				catchChoices.forEach(this::removeWidget);
				catchChoices.clear();
				addCatchGrid();
				if (gridFocused) {
					setFocused(catchChoices.get(index % gridSize()));
				}
			}
			back.visible = catchIndex > 0;
			forward.visible = catchIndex + 1 < water().catches().size();
			setFocused(getFocused());
		}
	}

	@Override
	public void setFocused(GuiEventListener focused) {
		// Mouse clicks assign focus after the button callback, so reject hidden arrows here too.
		if (focused != null) {
			if (focused == back && !back.visible) {
				focused = forward;
			} else if (focused == forward && !forward.visible) {
				focused = back;
			}
		}
		super.setFocused(focused);
	}

	private int gridSize() {
		return GRID_COLUMNS * (water().biomes().isEmpty() ? 5 : 3);
	}

	private void addCatchGrid() {
		int gridTop = top() + (water().biomes().isEmpty() ? 26 : 89);
		int first = catchIndex / gridSize() * gridSize();
		int end = Math.min(first + gridSize(), water().catches().size());
		for (int index = first; index < end; index++) {
			int slot = index - first;
			catchChoices.add(addRenderableWidget(new Choice(left() + 12 + slot % GRID_COLUMNS * 40,
					gridTop + slot / GRID_COLUMNS * 24, 40, 24, index, false)));
		}
	}

	private void scrollTabs(int amount) {
		int next = clampScroll(tabStart + amount, waters.size(), VISIBLE_TABS);
		if (next != tabStart) {
			tabStart = next;
			rebuildWidgets();
		}
	}

	private static int clampScroll(int offset, int size, int capacity) {
		return Math.clamp(offset, 0, Math.max(0, size - capacity));
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		int direction = (int) -Math.signum(scrollY);
		int left = left();
		int dy = (int) y - top();
		if (direction != 0 && dy >= 0 && dy < PAGE_HEIGHT) {
			if (x >= left - TAB_RAIL && x < left + 2) {
				scrollTabs(direction);
				return true;
			}
			if (!water().biomes().isEmpty() && x >= left + 10 && x < left + 135
					&& dy >= 31 && dy < 43 + VISIBLE_BIOMES * BIOME_ROW_HEIGHT) {
				biomeStart = clampScroll(biomeStart + direction, water().biomes().size(), VISIBLE_BIOMES);
				return true;
			}
			if (x >= left + RIGHT_PAGE + 10 && x < left + RIGHT_PAGE + 133 && dy >= 95 && dy < 139) {
				descriptionStart = clampScroll(descriptionStart + direction, descriptionLines().size(), VISIBLE_DESCRIPTION_LINES);
				return true;
			}
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int left = left();
		int top = top();
		int bookWidth = RIGHT_PAGE + PAGE_WIDTH;
		graphics.blit(RenderPipelines.GUI_TEXTURED, PAPER, left, top, 0.0F, 0.0F,
				bookWidth, PAGE_HEIGHT, bookWidth, PAGE_HEIGHT);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int left = left();
		int top = top();
		drawHeading(graphics, water().title(), left + 72, top + 14);
		drawBiomes(graphics, left, top, mouseX, mouseY);
		drawCatch(graphics, left + RIGHT_PAGE, top, mouseX, mouseY);
	}

	private void drawBiomes(GuiGraphicsExtractor graphics, int left, int top, int mouseX, int mouseY) {
		List<Identifier> biomes = water().biomes();
		if (biomes.isEmpty()) {
			return;
		}
		graphics.text(font, Component.translatable("book.matcha.anglers_almanac.biomes"), left + 14, top + 32, GREEN, false);
		for (int index = biomeStart; index < Math.min(biomeStart + VISIBLE_BIOMES, biomes.size()); index++) {
			Identifier biome = biomes.get(index);
			Component name = Component.translatable(biome.toLanguageKey("biome"));
			int y = top + 43 + (index - biomeStart) * BIOME_ROW_HEIGHT;
			boolean current = biome.equals(currentBiome);
			if (current) {
				graphics.fill(left + 12, y - 1, left + 126, y + BIOME_ROW_HEIGHT - 1, 0x40698b53);
			}
			Identifier icon = Identifier.fromNamespaceAndPath("matcha", "textures/gui/anglers_almanac/biome/" + biome.getPath() + ".png");
			if (biome.getNamespace().equals("minecraft") && minecraft.getResourceManager().getResource(icon).isPresent()) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, icon, left + 14, y, 0.0F, 0.0F, 16, 16, 16, 16);
			}
			String label = name.getString();
			boolean truncated = font.width(label) > 90;
			if (truncated) {
				label = font.plainSubstrByWidth(label, 90 - font.width("…")) + "…";
			}
			graphics.text(font, label, left + 34, y + (16 - font.lineHeight) / 2, current ? GREEN : INK, false);
			if (truncated && mouseX >= left + 12 && mouseX < left + 126
					&& mouseY >= y - 1 && mouseY < y + BIOME_ROW_HEIGHT - 1) {
				graphics.setTooltipForNextFrame(font, name, mouseX, mouseY);
			}
		}
		int listTop = top + 42;
		int listBottom = listTop + VISIBLE_BIOMES * BIOME_ROW_HEIGHT;
		for (int row = 0; row < 3; row++) {
			if (biomeStart > 0) {
				graphics.fill(left + 132 - row, listTop + row, left + 133 + row, listTop + row + 1, GREEN);
			}
			if (biomeStart + VISIBLE_BIOMES < biomes.size()) {
				graphics.fill(left + 130 + row, listBottom - 3 + row, left + 135 - row, listBottom - 2 + row, GREEN);
			}
		}
	}

	private void drawCatch(GuiGraphicsExtractor graphics, int right, int top, int mouseX, int mouseY) {
		List<AnglersAlmanacPayload.Catch> catches = water().catches();
		if (catches.isEmpty()) {
			return;
		}
		var entry = catches.get(catchIndex);
		int center = right + 74;
		graphics.pose().pushMatrix();
		graphics.pose().translate(center - 16, top + 15);
		graphics.pose().scale(2.0F, 2.0F);
		if (entry.count() > 0) {
			graphics.fakeItem(entry.icon(), 0, 0);
		} else {
			AlmanacSilhouettes.draw(graphics, entry.icon(), 0, 0);
		}
		graphics.pose().popMatrix();
		if (entry.count() <= 0) {
			drawHeading(graphics, UNKNOWN, center, top + 54);
		} else {
			drawHeading(graphics, entry.name(), center, top + 54);
			drawCatchDetails(graphics, entry.description(), center, top + 72, mouseX, mouseY);
			drawCentered(graphics, Component.translatable("book.matcha.anglers_almanac.caught", entry.count()).getVisualOrderText(),
					center, top + 84, GREEN);
			var lines = descriptionLines();
			for (int index = descriptionStart; index < Math.min(descriptionStart + VISIBLE_DESCRIPTION_LINES, lines.size()); index++) {
				graphics.text(font, lines.get(index), right + 15, top + 98 + (index - descriptionStart) * 9, INK, false);
			}
			drawScrollMarkers(graphics, right + 128, top + 98, top + 125, descriptionStart, lines.size(), VISIBLE_DESCRIPTION_LINES);
		}
		drawCentered(graphics, Component.translatable("book.pageIndicator", catchIndex + 1, catches.size()).getVisualOrderText(),
				center, top + 147, GREEN);
	}

	private List<FormattedCharSequence> descriptionLines() {
		// Matcha descriptions keep the prose first, followed by rarity and chance in sibling components.
		return water().catches().isEmpty() ? List.of() : font.split(water().catches().get(catchIndex).description().plainCopy(), 112);
	}

	private void drawCatchDetails(GuiGraphicsExtractor graphics, Component description, int center, int y, int mouseX, int mouseY) {
		String chance = "";
		Component rarity = CommonComponents.EMPTY;
		for (Component part : description.getSiblings()) {
			if (part.getContents() instanceof TranslatableContents text) {
				if (text.getKey().equals("advancements.matcha.anglers_almanac.chance") && text.getArgs().length > 0) {
					Object value = text.getArgs()[0];
					chance = value instanceof Component component ? component.getString() : String.valueOf(value);
				} else if (text.getKey().startsWith("lore.matcha.fish_rarity.")) {
					rarity = part.plainCopy();
				}
			}
		}
		var details = Component.empty();
		if (rarity != CommonComponents.EMPTY) {
			details.append(rarity);
			if (!chance.isEmpty()) {
				details.append(" · ");
			}
		}
		details.append(chance);
		drawCentered(graphics, details.getVisualOrderText(), center, y, GREEN);
		int chanceRight = center - font.width(details) / 2 + font.width(details);
		if (!chance.isEmpty() && mouseX >= chanceRight - font.width(chance) && mouseX < chanceRight
				&& mouseY >= y && mouseY < y + font.lineHeight) {
			graphics.setTooltipForNextFrame(font,
					Component.translatable("advancements.matcha.anglers_almanac.chance", chance), mouseX, mouseY);
		}
	}

	private void drawHeading(GuiGraphicsExtractor graphics, Component title, int center, int y) {
		var lines = font.split(title, 115);
		for (int line = 0; line < Math.min(2, lines.size()); line++) {
			drawCentered(graphics, lines.get(line), center, y + line * 9, INK);
		}
	}

	private void drawCentered(GuiGraphicsExtractor graphics, FormattedCharSequence line, int center, int y, int color) {
		graphics.text(font, line, center - font.width(line) / 2, y, color, false);
	}

	private void drawScrollMarkers(GuiGraphicsExtractor graphics, int x, int up, int down, int start, int size, int capacity) {
		if (start > 0) {
			graphics.text(font, "▴", x, up, GREEN, false);
		}
		if (start + capacity < size) {
			graphics.text(font, "▾", x, down, GREEN, false);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	private final class Choice extends AbstractButton {
		private final int index;
		private final boolean tab;

		private Choice(int x, int y, int width, int height, int index, boolean tab) {
			super(x, y, width, height, tab ? waters.get(index).title()
					: water().catches().get(index).count() <= 0 ? Component.literal("?") : water().catches().get(index).name());
			this.index = index;
			this.tab = tab;
			setTooltip(Tooltip.create(getMessage()));
		}

		@Override
		public void onPress(InputWithModifiers input) {
			if (tab) {
				selectWater(index);
			} else {
				selectCatch(index);
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			int x = getX();
			int y = getY();
			if (tab) {
				boolean selected = index == waterIndex;
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, selected || isHoveredOrFocused() ? SELECTED_TAB : TAB,
						x, y, getWidth(), getHeight());
				graphics.fakeItem(waters.get(index).icon(), x + 5, y + 4);
				return;
			}
			boolean selected = index == catchIndex || isHoveredOrFocused();
			if (selected) {
				graphics.fill(x + 10, y + 2, x + 30, y + 22, 0x40698b53);
			}
			var entry = water().catches().get(index);
			boolean discovered = entry.count() > 0;
			if (discovered) {
				graphics.fakeItem(entry.icon(), x + 12, y + 4);
			} else {
				AlmanacSilhouettes.draw(graphics, entry.icon(), x + 12, y + 4);
			}
		}
	}
}
