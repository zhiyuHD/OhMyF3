package com.ohmyf3.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.ohmyf3.client.font.TtfTextRenderer;
import com.ohmyf3.client.ui.Anim;
import com.ohmyf3.client.ui.M3;
import com.ohmyf3.client.ui.Shapes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.debug.DebugEntryCategory;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Material 3 replacement for the vanilla F3 display-options screen.
 *
 * <p>Vanilla's screen is a vanilla-styled checkbox list behind a search box. This keeps the same
 * data — the entry registry and its per-entry {@link DebugScreenEntryStatus} — but presents it the
 * way the rest of the mod does: grouped by category, one M3 switch per entry, with bulk actions for
 * the common "everything off" case that causes the "my F3 is missing rows" confusion.
 */
public final class M3DebugOptionsScreen extends Screen {
	private static final int LEFT_BUTTON = 1;
	private static final int PANEL_WIDTH = 420;
	private static final int PADDING = 20;
	/** Row pitch: the M3 list item is 44 tall, plus an 8px gap so rows never touch. */
	private static final int ROW_HEIGHT = 52;
	private static final int HEADER_HEIGHT = 26;
	private static final int FOOTER_HEIGHT = 32;

	private record Entry(Identifier id, DebugScreenEntry entry, Component label, DebugEntryCategory category) {
	}

	private final List<Entry> entries = new ArrayList<>();
	private final List<Entry> filtered = new ArrayList<>();
	private String query = "";
	private boolean searchFocused;
	/** Set when keyPressed already consumed this keystroke, so charTyped does not double-insert. */
	private boolean keyConsumed;
	private final List<Button> buttons = new ArrayList<>();

	private int scroll;

	private record Button(String key, String label, int x, int y, int width, int height, boolean filled) {
	}

	public M3DebugOptionsScreen() {
		super(Component.literal("F3 显示项"));
		load();
		com.ohmyf3.OhMyF3.LOGGER.info("M3 debug options opened ({} entries)", entries.size());
	}

	private void load() {
		for (Map.Entry<Identifier, DebugScreenEntry> item : DebugScreenEntries.allEntries().entrySet()) {
			DebugScreenEntry entry = item.getValue();
			DebugEntryCategory category = entry.category();
			entries.add(new Entry(item.getKey(), entry, category.label(), category));
		}

		entries.sort(Comparator.comparing((Entry e) -> e.category().sortKey())
				.thenComparing(e -> e.id().toString()));
		rebuildFiltered();
	}

	/** Re-applies the search filter; the list always renders {@link #filtered}. */
	private void rebuildFiltered() {
		String needle = query.trim().toLowerCase(Locale.ROOT);
		filtered.clear();

		for (Entry entry : entries) {
			if (needle.isEmpty() || entry.id().toString().toLowerCase(Locale.ROOT).contains(needle)
					|| entry.category().label().getString().toLowerCase(Locale.ROOT).contains(needle)) {
				filtered.add(entry);
			}
		}

		scroll = 0;
	}

	// ---------------------------------------------------------------- geometry

	private int panelHeight() {
		return Math.min(this.height - 32, 400);
	}

	private int panelX() {
		return this.width / 2 - PANEL_WIDTH / 2;
	}

	private int panelY() {
		return this.height / 2 - panelHeight() / 2;
	}

	/** First row starts below the search field, which sits under the subtitle. */
	private int listTop() {
		return panelY() + PADDING + 60;
	}

	private int listBottom() {
		return panelY() + panelHeight() - PADDING - FOOTER_HEIGHT - 8;
	}

	private int visibleRows() {
		return Math.max(1, (listBottom() - listTop()) / ROW_HEIGHT);
	}

	private int maxScroll() {
		return Math.max(0, filtered.size() - visibleRows());
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
		TtfTextRenderer.ensureDeviceScale(Minecraft.getInstance().getWindow().getGuiScale());
		M3.scrim(graphics, this.width, this.height, 1.0F);

		int x = panelX();
		int y = panelY();
		int height = panelHeight();
		M3.panel(graphics, x, y, PANEL_WIDTH, height);

		TtfTextRenderer.drawString(graphics, "F3 显示项", x + PADDING, y + PADDING + 10, M3.ON_SURFACE);
		TtfTextRenderer.drawString(graphics,
				"关掉的条目不会出现在 F3 里 · " + filtered.size() + "/" + entries.size() + " 项",
				x + PADDING, y + PADDING + 24, M3.ON_SURFACE_VARIANT_BRIGHT);

		renderSearch(graphics, mouseX, mouseY);
		renderList(graphics, mouseX, mouseY);
		renderFooter(graphics, mouseX, mouseY);
	}

	private int searchX() {
		return panelX() + PADDING;
	}

	private int searchY() {
		return panelY() + PADDING + 32;
	}

	private int searchWidth() {
		return PANEL_WIDTH - PADDING * 2 - 96;
	}

	private int[] profileChip() {
		return new int[] {panelX() + PANEL_WIDTH - PADDING - 92, searchY(), 92, 24};
	}

	private void renderSearch(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = searchX();
		int y = searchY();
		int width = searchWidth();
		boolean hovered = M3.inside(mouseX, mouseY, x, y, width, 24);
		Shapes.outlined(graphics, x, y, width, 24, 12,
				searchFocused ? M3.SURFACE_CONTAINER_HIGHEST : M3.SURFACE_CONTAINER,
				searchFocused ? M3.PRIMARY : M3.OUTLINE_VARIANT);

		String text = query.isEmpty() ? "搜索条目…" : query;
		int color = query.isEmpty() ? M3.OUTLINE : M3.ON_SURFACE;
		TtfTextRenderer.drawString(graphics, text, x + 12, y + 16, color);

		if (searchFocused && !query.isEmpty() && (System.currentTimeMillis() / 500) % 2 == 0) {
			int caretX = x + 12 + TtfTextRenderer.stringWidth(query);
			graphics.fill(caretX + 1, y + 5, caretX + 2, y + 19, M3.PRIMARY);
		}

		int[] chip = profileChip();
		boolean chipHovered = M3.inside(mouseX, mouseY, chip[0], chip[1], chip[2], chip[3]);
		boolean performance = Minecraft.getInstance().debugEntries
				.isUsingProfile(net.minecraft.client.gui.components.debug.DebugScreenProfile.PERFORMANCE);
		Shapes.filled(graphics, chip[0], chip[1], chip[2], chip[3], 12,
				performance ? M3.PRIMARY_CONTAINER : chipHovered ? M3.SURFACE_CONTAINER_HIGHEST : M3.SURFACE_CONTAINER);
		int labelWidth = TtfTextRenderer.stringWidth("性能档");
		TtfTextRenderer.drawString(graphics, "性能档", chip[0] + (chip[2] - labelWidth) / 2.0F, chip[1] + 16,
				performance ? M3.ON_PRIMARY_CONTAINER : M3.ON_SURFACE_VARIANT_BRIGHT);
	}

	private void renderList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		DebugScreenEntryList statuses = Minecraft.getInstance().debugEntries;
		int x = panelX() + PADDING;
		int width = PANEL_WIDTH - PADDING * 2;
		int rowY = listTop();
		int limit = listBottom();

		graphics.enableScissor(x, listTop(), x + width, limit);

		DebugEntryCategory lastCategory = null;

		for (int i = scroll; i < filtered.size() && rowY < limit; i++) {
			Entry entry = filtered.get(i);

			if (lastCategory == null || !lastCategory.equals(entry.category())) {
				lastCategory = entry.category();

				if (rowY + HEADER_HEIGHT > limit) {
					rowY += HEADER_HEIGHT;
					continue;
				}

				M3.section(graphics, entry.label().getString(), x + 4, rowY + 9);
				rowY += HEADER_HEIGHT;
			}

			if (rowY + ROW_HEIGHT > limit) {
				break;
			}

			renderRow(graphics, entry, statuses.getStatus(entry.id()), x, rowY, width, mouseX, mouseY);
			rowY += ROW_HEIGHT;
		}

		graphics.disableScissor();

		if (maxScroll() > 0) {
			int trackHeight = limit - listTop();
			int thumbHeight = Math.max(18, trackHeight * visibleRows() / Math.max(1, filtered.size()));
			int thumbY = listTop() + (trackHeight - thumbHeight) * scroll / maxScroll();
			Shapes.filled(graphics, x + width - 4, thumbY, 3, thumbHeight, 2, M3.OUTLINE_VARIANT);
		}
	}

	/** Vanilla's three states, as Material 3 segmented chips. */
	private static final DebugScreenEntryStatus[] STATES = {DebugScreenEntryStatus.ALWAYS_ON,
			DebugScreenEntryStatus.IN_OVERLAY, DebugScreenEntryStatus.NEVER};

	private static final String[] STATE_LABELS = {"常显", "F3", "关闭"};

	private static final int CHIP_WIDTH = 46;
	private static final int CHIP_HEIGHT = 22;
	private static final int CHIP_GAP = 6;

	/** Left edge of the chip row for a list item of the given width. */
	private static int chipX(int x, int width, int index) {
		int total = STATES.length * CHIP_WIDTH + (STATES.length - 1) * CHIP_GAP;
		return x + width - 16 - total + index * (CHIP_WIDTH + CHIP_GAP);
	}

	private void renderRow(GuiGraphicsExtractor graphics, Entry entry, DebugScreenEntryStatus status,
			int x, int rowY, int width, int mouseX, int mouseY) {
		boolean hovered = M3.inside(mouseX, mouseY, x, rowY, width, M3.ROW_HEIGHT);
		Shapes.filled(graphics, x, rowY, width, M3.ROW_HEIGHT, M3.RADIUS_LARGE,
				hovered ? M3.SURFACE_CONTAINER_HIGHEST : M3.SURFACE_CONTAINER_HIGH);

		TtfTextRenderer.drawString(graphics, shortName(entry.id()), x + 16, rowY + 17, M3.ON_SURFACE);
		TtfTextRenderer.drawString(graphics, entry.id().getNamespace(), x + 16, rowY + 30,
				M3.ON_SURFACE_VARIANT_BRIGHT);

		for (int i = 0; i < STATES.length; i++) {
			boolean active = status == STATES[i];
			boolean chipHovered = M3.inside(mouseX, mouseY, chipX(x, width, i), rowY + (M3.ROW_HEIGHT - CHIP_HEIGHT) / 2,
					CHIP_WIDTH, CHIP_HEIGHT);
			int fill = active ? M3.PRIMARY_CONTAINER
					: chipHovered ? M3.SURFACE_CONTAINER_HIGHEST : M3.SURFACE_CONTAINER_HIGH;

			Shapes.filled(graphics, chipX(x, width, i), rowY + (M3.ROW_HEIGHT - CHIP_HEIGHT) / 2, CHIP_WIDTH,
					CHIP_HEIGHT, CHIP_HEIGHT / 2, fill);

			int textWidth = TtfTextRenderer.stringWidth(STATE_LABELS[i]);
			TtfTextRenderer.drawString(graphics, STATE_LABELS[i],
					chipX(x, width, i) + (CHIP_WIDTH - textWidth) / 2.0F,
					rowY + M3.ROW_HEIGHT / 2 + TtfTextRenderer.lineHeight() / 2 - 2,
					active ? M3.ON_PRIMARY_CONTAINER : M3.ON_SURFACE_VARIANT_BRIGHT);
		}
	}

	private void renderFooter(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = panelX() + PADDING;
		int y = panelY() + panelHeight() - PADDING - FOOTER_HEIGHT;
		int doneWidth = 72;
		int resetWidth = 96;
		int allOnWidth = 88;
		int allOffWidth = 88;

		buttons.clear();
		buttons.add(new Button("done", "完成", x, y, doneWidth, FOOTER_HEIGHT, true));
		buttons.add(new Button("reset", "恢复默认", x + doneWidth + 8, y, resetWidth, FOOTER_HEIGHT, false));
		buttons.add(new Button("allOn", "全部开启", x + doneWidth + resetWidth + 16, y, allOnWidth, FOOTER_HEIGHT,
				false));
		buttons.add(new Button("allOff", "全部关闭", x + doneWidth + resetWidth + allOnWidth + 24, y, allOffWidth,
				FOOTER_HEIGHT, false));

		for (Button button : buttons) {
			boolean hovered = M3.inside(mouseX, mouseY, button.x(), button.y(), button.width(), button.height());

			if (button.filled()) {
				M3.filledButton(graphics, button.x(), button.y(), button.width(), button.height(), button.label(),
						hovered);
			} else {
				M3.textButton(graphics, button.x(), button.y(), button.width(), button.height(), button.label(),
						hovered);
			}

			M3.drawRipple(graphics, "btn:" + button.key(), button.x(), button.y(), button.width(), button.height());
		}
	}

	/** Shows the part of the id that differs; the namespace is drawn as the supporting line. */
	private static String shortName(Identifier id) {
		return id.getPath().replace('_', ' ');
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean quickMove) {
		double mouseX = event.x();
		double mouseY = event.y();

		if (event.button() != LEFT_BUTTON) {
			return super.mouseClicked(event, quickMove);
		}

		if (M3.inside(mouseX, mouseY, searchX(), searchY(), searchWidth(), 24)) {
			searchFocused = true;
			return true;
		}

		int[] chip = profileChip();

		if (M3.inside(mouseX, mouseY, chip[0], chip[1], chip[2], chip[3])) {
			DebugScreenEntryList statuses = Minecraft.getInstance().debugEntries;
			statuses.loadProfile(net.minecraft.client.gui.components.debug.DebugScreenProfile.PERFORMANCE);
			apply(statuses);
			return true;
		}

		searchFocused = false;

		for (Button button : buttons) {
			if (M3.inside(mouseX, mouseY, button.x(), button.y(), button.width(), button.height())) {
				M3.ripple("btn:" + button.key(), mouseX, mouseY);
				onButton(button.key());
				return true;
			}
		}

		Entry clicked = entryAt(mouseX, mouseY);

		if (clicked != null) {
			DebugScreenEntryList statuses = Minecraft.getInstance().debugEntries;
			int x = panelX() + PADDING;
			int width = PANEL_WIDTH - PADDING * 2;
			int rowY = rowTopY(clicked);

			for (int i = 0; i < STATES.length; i++) {
				if (M3.inside(mouseX, mouseY, chipX(x, width, i), rowY + (M3.ROW_HEIGHT - CHIP_HEIGHT) / 2, CHIP_WIDTH,
						CHIP_HEIGHT)) {
					statuses.setStatus(clicked.id(), STATES[i]);
					apply(statuses);
					return true;
				}
			}

			return true;
		}

		return super.mouseClicked(event, quickMove);
	}

	/** Top edge of the row currently drawn for {@code entry}. */
	private int rowTopY(Entry entry) {
		DebugEntryCategory lastCategory = null;
		int rowY = listTop();

		for (int i = scroll; i < filtered.size(); i++) {
			Entry current = filtered.get(i);

			if (lastCategory == null || !lastCategory.equals(current.category())) {
				lastCategory = current.category();
				rowY += HEADER_HEIGHT;
			}

			if (current == entry) {
				return rowY;
			}

			rowY += ROW_HEIGHT;
		}

		return rowY;
	}

	/** Which entry's row is under the pointer, using the same layout the renderer just drew. */
	private Entry entryAt(double mouseX, double mouseY) {
		int x = panelX() + PADDING;
		int width = PANEL_WIDTH - PADDING * 2;
		int rowY = listTop();
		DebugEntryCategory lastCategory = null;

		for (int i = scroll; i < filtered.size(); i++) {
			Entry entry = filtered.get(i);

			if (lastCategory == null || !lastCategory.equals(entry.category())) {
				lastCategory = entry.category();
				rowY += HEADER_HEIGHT;
			}

			if (rowY + ROW_HEIGHT > listBottom()) {
				return null;
			}

			if (M3.inside(mouseX, mouseY, x, rowY, width, ROW_HEIGHT - 4)) {
				return entry;
			}

			rowY += ROW_HEIGHT;
		}

		return null;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (mouseY >= listTop() && mouseY <= listBottom()) {
			scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(vertical)));
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	/**
	 * Commits a status change.
	 *
	 * <p>{@code setStatus} only edits the per-entry map; the overlay reads the cached enabled list,
	 * so without {@code rebuildCurrentList} the saved change had no visible effect until something
	 * else happened to refresh it — which is exactly why toggling an entry appeared to do nothing.
	 */
	private static void apply(DebugScreenEntryList statuses) {
		statuses.rebuildCurrentList();
		statuses.save();
	}

	private void onButton(String key) {
		DebugScreenEntryList statuses = Minecraft.getInstance().debugEntries;

		switch (key) {
			case "done" -> close();
			case "reset" -> {
				statuses.loadProfile(net.minecraft.client.gui.components.debug.DebugScreenProfile.DEFAULT);
				apply(statuses);
			}
			case "allOn" -> {
				for (Entry entry : entries) {
					statuses.setStatus(entry.id(), DebugScreenEntryStatus.IN_OVERLAY);
				}

				apply(statuses);
			}
			case "allOff" -> {
				for (Entry entry : entries) {
					statuses.setStatus(entry.id(), DebugScreenEntryStatus.NEVER);
				}

				apply(statuses);
			}
			default -> {
			}
		}
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!searchFocused) {
			return super.charTyped(event);
		}

		if (keyConsumed) {
			keyConsumed = false;
			return true;
		}

		String typed = event.codepointAsString();

		if (typed.isEmpty() || !typed.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_' || c == ':')) {
			return false;
		}

		query += typed;
		rebuildFiltered();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.input();

		if (key == 256) {
			if (searchFocused && !query.isEmpty()) {
				query = "";
				rebuildFiltered();
				return true;
			}

			close();
			return true;
		}

		if (searchFocused) {
			String typed = com.ohmyf3.client.ui.TextKeys.character(key,
					com.ohmyf3.client.ui.TextKeys.shift(event));

			if (typed != null && typed.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_' || c == ':')) {
				query += typed;
				rebuildFiltered();
				keyConsumed = true;
				return true;
			}
		}

		if (searchFocused && (key == 259 || key == 261)) {
			// Backspace / Delete: drop the last character.
			if (!query.isEmpty()) {
				query = query.substring(0, query.offsetByCodePoints(query.length(), -1));
				rebuildFiltered();
			}

			return true;
		}

		return super.keyPressed(event);
	}

	private void close() {
		Minecraft.getInstance().setScreenAndShow(null);
	}

	@Override
	public void onClose() {
		close();
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}
}
