package org.hismeo.fractureclient.client.weapon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.hismeo.crystalfracture.CrystalFracture;
import org.hismeo.crystalfracture.weapon.WeaponAuthority;
import org.hismeo.crystalfracture.weapon.api.WeaponAssemblies;
import org.hismeo.crystalfracture.weapon.api.WeaponAssembly;
import org.hismeo.crystalfracture.weapon.api.WeaponPartId;
import org.hismeo.crystalfracture.weapon.api.WeaponSlotId;
import org.hismeo.crystalfracture.weapon.definition.WeaponConnectionDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponSchemaDefinition;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Debug workbench for selecting a schema and assembling a live weapon preview. */
public final class WeaponPreviewScreen extends Screen {
    private static final float MIN_ZOOM = 0.55F;
    private static final float MAX_ZOOM = 2.75F;
    private static final int MARGIN = 8;
    private static final int GAP = 7;
    private static final int PANEL_TOP = 40;
    private static final int PANEL_BOTTOM_MARGIN = 29;
    private static final int PANEL_HEADER_HEIGHT = 25;
    private static final int BLUEPRINT_ROW_HEIGHT = 31;
    private static final int SLOT_ROW_HEIGHT = 47;
    private static final int PART_ROW_HEIGHT = 39;

    private static final int PANEL_BACKGROUND = 0xE5161D28;
    private static final int PANEL_BORDER = 0xFF455168;
    private static final int ROW_BACKGROUND = 0xB7232C3A;
    private static final int ROW_HOVER = 0xD2344155;
    private static final int ROW_SELECTED = 0xE13A526B;
    private static final int TEXT = 0xFFF0F4F8;
    private static final int MUTED_TEXT = 0xFF9DAABD;
    private static final int ACCENT = 0xFF77C7FF;
    private static final int READY = 0xFF8FE388;
    private static final int WARNING = 0xFFFFB86C;

    private final Map<WeaponSlotId, WeaponPartId> selectedParts = new TreeMap<>();
    private List<WeaponSchemaDefinition> schemas = List.of();
    private WeaponSchemaDefinition selectedSchema;
    private WeaponSlotId selectedSlot;
    private long registryGeneration = Long.MIN_VALUE;
    private int blueprintScroll;
    private int slotScroll;
    private int partScroll;
    private float yawRadians = (float) Math.toRadians(30.0);
    private float pitchRadians = (float) Math.toRadians(-8.0);
    private float zoom = 1.0F;
    private boolean autoRotate = true;

    public WeaponPreviewScreen() {
        super(Component.literal("武器拼装台"));
    }

    @Override
    protected void init() {
        refreshRegistry(true);
    }

    @Override
    public void tick() {
        refreshRegistry(false);
    }

    void advance(float deltaSeconds) {
        if (autoRotate) {
            yawRadians += Math.min(0.1F, Math.max(0.0F, deltaSeconds)) * 0.55F;
        }
    }

    float yawRadians() {
        return yawRadians;
    }

    float pitchRadians() {
        return pitchRadians;
    }

    float zoom() {
        return zoom;
    }

    float previewCenterFraction() {
        if (width <= 0) {
            return 0.5F;
        }
        Layout layout = layout();
        return (layout.previewLeft() + layout.previewRight()) * 0.5F / width;
    }

    WeaponAssembly currentAssembly() {
        if (selectedSchema == null) {
            return null;
        }
        return new WeaponAssembly(selectedSchema.id(), selectedParts);
    }

    @Override
    public void render(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        refreshRegistry(false);
        renderBackground(graphics, mouseX, mouseY, partialTick);
        Layout layout = layout();
        renderHeader(graphics);
        renderBlueprintPanel(graphics, layout, mouseX, mouseY);
        renderSlotPanel(graphics, layout, mouseX, mouseY);
        renderPreviewPanel(graphics, layout);
        renderPartPanel(graphics, layout, mouseX, mouseY);
        graphics.drawCenteredString(
                font,
                "预览区域拖拽旋转 · 滚轮缩放 · 空格自动旋转 · R 重置 · ESC 关闭",
                width / 2,
                height - 19,
                MUTED_TEXT);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        // Keep the 3D preview untouched; individual workbench panels provide their own backdrop.
//        graphics.fill(0, 0, width, height, -10000, 0xA80A0E15);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        Layout layout = layout();
        if (inside(mouseX, mouseY, layout.blueprintLeft(), layout.top(),
                layout.blueprintRight(), layout.bottom())) {
            int index = rowAt(mouseY, layout.listTop(), BLUEPRINT_ROW_HEIGHT, blueprintScroll);
            if (index >= 0 && index < schemas.size()) {
                selectSchema(schemas.get(index), null);
            }
            return true;
        }
        if (inside(mouseX, mouseY, layout.slotLeft(), layout.top(),
                layout.slotRight(), layout.bottom())) {
            List<WeaponSlotId> slots = orderedSlots();
            int index = rowAt(mouseY, layout.listTop(), SLOT_ROW_HEIGHT, slotScroll);
            if (index >= 0 && index < slots.size()) {
                WeaponSlotId slot = slots.get(index);
                int rowTop = layout.listTop() + (index - slotScroll) * SLOT_ROW_HEIGHT;
                List<ConnectionTarget> targets = connectionTargets(slot);
                for (int connectionIndex = 0; connectionIndex < targets.size(); connectionIndex++) {
                    int buttonRight = layout.slotRight() - 7 - connectionIndex * 18;
                    if (inside(mouseX, mouseY, buttonRight - 15, rowTop + 5,
                            buttonRight, rowTop + 20)) {
                        selectSlot(targets.get(connectionIndex).otherSlot());
                        return true;
                    }
                }
                selectSlot(slot);
            }
            return true;
        }
        if (inside(mouseX, mouseY, layout.partLeft(), layout.top(),
                layout.partRight(), layout.bottom())) {
            List<WeaponPartDefinition> parts = compatibleParts(selectedSlot);
            int index = rowAt(mouseY, layout.listTop(), PART_ROW_HEIGHT, partScroll);
            if (index >= 0 && index < parts.size() && selectedSlot != null) {
                selectedParts.put(selectedSlot, parts.get(index).id());
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        Layout layout = layout();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && inside(mouseX, mouseY, layout.previewLeft(), layout.top(),
                layout.previewRight(), layout.bottom())) {
            autoRotate = false;
            yawRadians += (float) dragX * 0.012F;
            pitchRadians = clamp(
                    pitchRadians + (float) dragY * 0.012F,
                    (float) Math.toRadians(-80.0),
                    (float) Math.toRadians(80.0));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        Layout layout = layout();
        if (inside(mouseX, mouseY, layout.blueprintLeft(), layout.top(),
                layout.blueprintRight(), layout.bottom())) {
            blueprintScroll = scrolled(
                    blueprintScroll,
                    schemas.size(),
                    visibleRows(layout, BLUEPRINT_ROW_HEIGHT),
                    scrollY);
            return true;
        }
        if (inside(mouseX, mouseY, layout.slotLeft(), layout.top(),
                layout.slotRight(), layout.bottom())) {
            slotScroll = scrolled(
                    slotScroll,
                    orderedSlots().size(),
                    visibleRows(layout, SLOT_ROW_HEIGHT),
                    scrollY);
            return true;
        }
        if (inside(mouseX, mouseY, layout.partLeft(), layout.top(),
                layout.partRight(), layout.bottom())) {
            partScroll = scrolled(
                    partScroll,
                    compatibleParts(selectedSlot).size(),
                    visibleRows(layout, PART_ROW_HEIGHT),
                    scrollY);
            return true;
        }
        if (inside(mouseX, mouseY, layout.previewLeft(), layout.top(),
                layout.previewRight(), layout.bottom())) {
            zoom = clamp(zoom * (float) Math.pow(1.12, scrollY), MIN_ZOOM, MAX_ZOOM);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            autoRotate = !autoRotate;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_R) {
            yawRadians = (float) Math.toRadians(30.0);
            pitchRadians = (float) Math.toRadians(-8.0);
            zoom = 1.0F;
            autoRotate = true;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void renderHeader(GuiGraphics graphics) {
        graphics.fill(0, 0, width, 34, 0xED111722);
        graphics.fill(0, 33, width, 34, PANEL_BORDER);
        graphics.drawString(font, title, MARGIN + 2, 10, TEXT, false);
        String status = PlayerWeaponExtension.previewStatus();
        graphics.drawString(
                font,
                fit(status, Math.max(0, width - 160)),
                144,
                10,
                PlayerWeaponExtension.previewAvailable() ? READY : WARNING,
                false);
    }

    private void renderBlueprintPanel(GuiGraphics graphics, Layout layout, int mouseX, int mouseY) {
        drawPanel(graphics, layout.blueprintLeft(), layout.top(), layout.blueprintRight(), layout.bottom(),
                "1 选择蓝图");
        if (schemas.isEmpty()) {
            graphics.drawString(font, "没有已注册蓝图", layout.blueprintLeft() + 7,
                    layout.listTop() + 7, WARNING, false);
            return;
        }
        int visible = visibleRows(layout, BLUEPRINT_ROW_HEIGHT);
        int end = Math.min(schemas.size(), blueprintScroll + visible);
        for (int index = blueprintScroll; index < end; index++) {
            WeaponSchemaDefinition schema = schemas.get(index);
            int top = layout.listTop() + (index - blueprintScroll) * BLUEPRINT_ROW_HEIGHT;
            boolean selected = schema == selectedSchema;
            boolean hovered = inside(mouseX, mouseY, layout.blueprintLeft() + 4, top,
                    layout.blueprintRight() - 4, top + BLUEPRINT_ROW_HEIGHT - 3);
            drawRow(graphics, layout.blueprintLeft() + 4, top,
                    layout.blueprintRight() - 4, top + BLUEPRINT_ROW_HEIGHT - 3, selected, hovered);
            graphics.drawString(font,
                    fit(schema.id().value().getPath(), layout.blueprintWidth() - 17),
                    layout.blueprintLeft() + 9, top + 6, selected ? ACCENT : TEXT, false);
            graphics.drawString(font,
                    schema.slots().size() + " 个部件槽",
                    layout.blueprintLeft() + 9, top + 17, MUTED_TEXT, false);
        }
    }

    private void renderSlotPanel(GuiGraphics graphics, Layout layout, int mouseX, int mouseY) {
        drawPanel(graphics, layout.slotLeft(), layout.top(), layout.slotRight(), layout.bottom(),
                "2 填入部件");
        List<WeaponSlotId> slots = orderedSlots();
        if (slots.isEmpty()) {
            graphics.drawString(font, "请先选择蓝图", layout.slotLeft() + 7,
                    layout.listTop() + 7, MUTED_TEXT, false);
            return;
        }
        int visible = visibleRows(layout, SLOT_ROW_HEIGHT);
        int end = Math.min(slots.size(), slotScroll + visible);
        for (int index = slotScroll; index < end; index++) {
            WeaponSlotId slot = slots.get(index);
            int top = layout.listTop() + (index - slotScroll) * SLOT_ROW_HEIGHT;
            boolean selected = slot.equals(selectedSlot);
            boolean hovered = inside(mouseX, mouseY, layout.slotLeft() + 4, top,
                    layout.slotRight() - 4, top + SLOT_ROW_HEIGHT - 3);
            drawRow(graphics, layout.slotLeft() + 4, top,
                    layout.slotRight() - 4, top + SLOT_ROW_HEIGHT - 3, selected, hovered);
            boolean root = selectedSchema != null && slot.equals(selectedSchema.root());
            String slotLabel = (root ? "根 · " : "") + slot.value();
            graphics.drawString(font,
                    fit(slotLabel, layout.slotWidth() - 55),
                    layout.slotLeft() + 9, top + 5, selected ? ACCENT : TEXT, false);
            WeaponPartId part = selectedParts.get(slot);
            graphics.drawString(font,
                    fit(part == null ? "未选择" : part.value().getPath(), layout.slotWidth() - 18),
                    layout.slotLeft() + 9, top + 18, part == null ? WARNING : READY, false);
            String type = selectedSchema.slots().get(slot).partType().value().getPath();
            graphics.drawString(font,
                    fit("类型: " + type, layout.slotWidth() - 18),
                    layout.slotLeft() + 9, top + 31, MUTED_TEXT, false);

            List<ConnectionTarget> targets = connectionTargets(slot);
            for (int connectionIndex = 0; connectionIndex < targets.size(); connectionIndex++) {
                int buttonRight = layout.slotRight() - 7 - connectionIndex * 18;
                boolean buttonHovered = inside(mouseX, mouseY, buttonRight - 15, top + 5,
                        buttonRight, top + 20);
                graphics.fill(buttonRight - 15, top + 5, buttonRight, top + 20,
                        buttonHovered ? 0xFF527395 : 0xFF34465C);
                drawBorder(graphics, buttonRight - 15, top + 5, buttonRight, top + 20,
                        buttonHovered ? ACCENT : PANEL_BORDER);
                drawConnectorIcon(
                        graphics,
                        buttonRight - 15,
                        top + 5,
                        buttonRight,
                        top + 20,
                        buttonHovered ? 0xFFFFFFFF : TEXT);
            }
        }
    }

    private void renderPreviewPanel(GuiGraphics graphics, Layout layout) {
        drawBorder(graphics, layout.previewLeft(), layout.top(), layout.previewRight(), layout.bottom(),
                PANEL_BORDER);
        graphics.fill(layout.previewLeft(), layout.top(), layout.previewRight(),
                layout.top() + PANEL_HEADER_HEIGHT, 0xC51D2633);
        graphics.drawCenteredString(font, "实时装配预览", (layout.previewLeft() + layout.previewRight()) / 2,
                layout.top() + 8, TEXT);
        if (selectedSchema != null) {
            int total = selectedSchema.slots().size();
            int filled = (int) selectedSchema.slots().keySet().stream()
                    .filter(selectedParts::containsKey)
                    .count();
            graphics.drawCenteredString(font,
                    selectedSchema.id().value().getPath() + " · " + filled + "/" + total,
                    (layout.previewLeft() + layout.previewRight()) / 2,
                    layout.bottom() - 17,
                    filled == total ? READY : WARNING);
        }
    }

    private void renderPartPanel(GuiGraphics graphics, Layout layout, int mouseX, int mouseY) {
        String title = selectedSlot == null ? "3 可用部件" : "3 " + selectedSlot.value() + " 可用部件";
        drawPanel(graphics, layout.partLeft(), layout.top(), layout.partRight(), layout.bottom(), title);
        List<WeaponPartDefinition> parts = compatibleParts(selectedSlot);
        if (selectedSlot == null) {
            graphics.drawString(font, "选择一个部件槽", layout.partLeft() + 7,
                    layout.listTop() + 7, MUTED_TEXT, false);
            return;
        }
        if (parts.isEmpty()) {
            graphics.drawString(font, "没有匹配该类型的部件", layout.partLeft() + 7,
                    layout.listTop() + 7, WARNING, false);
            return;
        }
        int visible = visibleRows(layout, PART_ROW_HEIGHT);
        int end = Math.min(parts.size(), partScroll + visible);
        WeaponPartId current = selectedParts.get(selectedSlot);
        for (int index = partScroll; index < end; index++) {
            WeaponPartDefinition part = parts.get(index);
            int top = layout.listTop() + (index - partScroll) * PART_ROW_HEIGHT;
            boolean selected = part.id().equals(current);
            boolean hovered = inside(mouseX, mouseY, layout.partLeft() + 4, top,
                    layout.partRight() - 4, top + PART_ROW_HEIGHT - 3);
            drawRow(graphics, layout.partLeft() + 4, top,
                    layout.partRight() - 4, top + PART_ROW_HEIGHT - 3, selected, hovered);
            graphics.drawString(font,
                    fit(part.id().value().getPath(), layout.partWidth() - 18),
                    layout.partLeft() + 9, top + 7, selected ? ACCENT : TEXT, false);
            graphics.drawString(font,
                    fit(part.visualModel().getPath(), layout.partWidth() - 18),
                    layout.partLeft() + 9, top + 21, MUTED_TEXT, false);
        }
    }

    private void refreshRegistry(boolean force) {
        long generation = CrystalFracture.weaponSchemas().generation();
        if (!force && registryGeneration == generation) {
            return;
        }
        WeaponAssembly previous = currentAssembly();
        registryGeneration = generation;
        schemas = List.copyOf(CrystalFracture.weaponSchemas().schemas().values());
        if (schemas.isEmpty()) {
            selectedSchema = null;
            selectedSlot = null;
            selectedParts.clear();
            return;
        }

        WeaponAssembly seed = previous == null ? initialAssembly() : previous;
        WeaponSchemaDefinition next = schemas.stream()
                .filter(schema -> seed != null && schema.id().equals(seed.schema()))
                .findFirst()
                .orElse(schemas.getFirst());
        selectSchema(next, seed);
    }

    private WeaponAssembly initialAssembly() {
        var player = Minecraft.getInstance().player;
        WeaponAssembly authoritative = player == null
                ? null
                : WeaponAuthority.current(player).orElse(null);
        if (authoritative != null && CrystalFracture.weaponSchemas().schema(authoritative.schema()).isPresent()) {
            return authoritative;
        }
        return WeaponAssemblies.DEFAULT_SWORD;
    }

    private void selectSchema(WeaponSchemaDefinition schema, WeaponAssembly seed) {
        selectedSchema = schema;
        selectedParts.clear();
        for (WeaponSlotId slot : orderedSlots()) {
            List<WeaponPartDefinition> compatible = compatibleParts(slot);
            WeaponPartId seededPart = seed != null && schema.id().equals(seed.schema())
                    ? seed.parts().get(slot)
                    : null;
            boolean seedCompatible = seededPart != null && compatible.stream()
                    .anyMatch(part -> part.id().equals(seededPart));
            if (seedCompatible) {
                selectedParts.put(slot, seededPart);
            } else if (!compatible.isEmpty()) {
                selectedParts.put(slot, compatible.getFirst().id());
            }
        }
        selectedSlot = schema.root();
        blueprintScroll = revealIndex(schemas.indexOf(schema), blueprintScroll,
                visibleRows(layout(), BLUEPRINT_ROW_HEIGHT));
        slotScroll = 0;
        partScroll = 0;
    }

    private void selectSlot(WeaponSlotId slot) {
        selectedSlot = slot;
        partScroll = 0;
        int index = orderedSlots().indexOf(slot);
        slotScroll = revealIndex(index, slotScroll, visibleRows(layout(), SLOT_ROW_HEIGHT));
    }

    private List<WeaponSlotId> orderedSlots() {
        if (selectedSchema == null) {
            return List.of();
        }
        LinkedHashSet<WeaponSlotId> ordered = new LinkedHashSet<>();
        ordered.add(selectedSchema.root());
        for (WeaponConnectionDefinition connection : selectedSchema.connections()) {
            ordered.add(connection.parent().slot());
            ordered.add(connection.child().slot());
        }
        ordered.addAll(selectedSchema.slots().keySet());
        return List.copyOf(ordered);
    }

    private List<WeaponPartDefinition> compatibleParts(WeaponSlotId slot) {
        if (selectedSchema == null || slot == null || !selectedSchema.slots().containsKey(slot)) {
            return List.of();
        }
        var requiredType = selectedSchema.slots().get(slot).partType();
        return CrystalFracture.weaponSchemas().parts().values().stream()
                .filter(part -> part.type().equals(requiredType))
                .toList();
    }

    private List<ConnectionTarget> connectionTargets(WeaponSlotId slot) {
        if (selectedSchema == null) {
            return List.of();
        }
        List<ConnectionTarget> result = new ArrayList<>();
        for (WeaponConnectionDefinition connection : selectedSchema.connections()) {
            if (connection.parent().slot().equals(slot)) {
                result.add(new ConnectionTarget(
                        connection.parent().connect().value(),
                        connection.child().slot()));
            } else if (connection.child().slot().equals(slot)) {
                result.add(new ConnectionTarget(
                        connection.child().connect().value(),
                        connection.parent().slot()));
            }
        }
        return List.copyOf(result);
    }

    private Layout layout() {
        int bottom = Math.max(PANEL_TOP + 80, height - PANEL_BOTTOM_MARGIN);
        int blueprintWidth = clamp(width * 16 / 100, 70, 170);
        int slotWidth = clamp(width * 22 / 100, 96, 220);
        int partWidth = clamp(width * 21 / 100, 96, 220);
        int blueprintLeft = MARGIN;
        int blueprintRight = blueprintLeft + blueprintWidth;
        int slotLeft = blueprintRight + GAP;
        int slotRight = slotLeft + slotWidth;
        int partRight = width - MARGIN;
        int partLeft = partRight - partWidth;
        int previewLeft = slotRight + GAP;
        int previewRight = Math.max(previewLeft + 8, partLeft - GAP);
        return new Layout(
                PANEL_TOP,
                bottom,
                blueprintLeft,
                blueprintRight,
                slotLeft,
                slotRight,
                previewLeft,
                previewRight,
                partLeft,
                partRight);
    }

    private void drawPanel(GuiGraphics graphics, int left, int top, int right, int bottom, String title) {
        graphics.fill(left, top, right, bottom, PANEL_BACKGROUND);
        drawBorder(graphics, left, top, right, bottom, PANEL_BORDER);
        graphics.fill(left, top, right, top + PANEL_HEADER_HEIGHT, 0xF01D2633);
        graphics.drawString(font, fit(title, Math.max(0, right - left - 12)),
                left + 6, top + 8, TEXT, false);
    }

    private static void drawRow(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom,
            boolean selected,
            boolean hovered
    ) {
        graphics.fill(left, top, right, bottom,
                selected ? ROW_SELECTED : hovered ? ROW_HOVER : ROW_BACKGROUND);
        if (selected) {
            graphics.fill(left, top, left + 2, bottom, ACCENT);
        }
    }

    private static void drawBorder(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom,
            int color
    ) {
        graphics.fill(left, top, right, top + 1, color);
        graphics.fill(left, bottom - 1, right, bottom, color);
        graphics.fill(left, top, left + 1, bottom, color);
        graphics.fill(right - 1, top, right, bottom, color);
    }

    private static void drawConnectorIcon(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom,
            int color
    ) {
        int insetLeft = left + 3;
        int insetTop = top + 3;
        int insetRight = right - 3;
        int insetBottom = bottom - 3;
        int centerX = (insetLeft + insetRight) / 2;
        int centerY = (insetTop + insetBottom) / 2;
        drawBorder(graphics, insetLeft, insetTop, insetRight, insetBottom, color);
        graphics.fill(centerX, insetTop + 1, centerX + 1, insetBottom - 1, color);
        graphics.fill(insetLeft + 1, centerY, insetRight - 1, centerY + 1, color);
    }

    private String fit(String value, int maximumWidth) {
        if (maximumWidth <= 0 || font.width(value) <= maximumWidth) {
            return maximumWidth <= 0 ? "" : value;
        }
        String suffix = "…";
        int end = value.length();
        while (end > 0 && font.width(value.substring(0, end) + suffix) > maximumWidth) {
            end--;
        }
        return value.substring(0, end) + suffix;
    }

    private static int rowAt(double mouseY, int listTop, int rowHeight, int scroll) {
        if (mouseY < listTop) {
            return -1;
        }
        return scroll + (int) ((mouseY - listTop) / rowHeight);
    }

    private static boolean inside(
            double mouseX,
            double mouseY,
            int left,
            int top,
            int right,
            int bottom
    ) {
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    private static int visibleRows(Layout layout, int rowHeight) {
        return Math.max(1, (layout.bottom() - layout.listTop() - 3) / rowHeight);
    }

    private static int scrolled(int current, int total, int visible, double scrollY) {
        int direction = scrollY > 0.0 ? -1 : scrollY < 0.0 ? 1 : 0;
        return clamp(current + direction, 0, Math.max(0, total - visible));
    }

    private static int revealIndex(int index, int current, int visible) {
        if (index < 0) {
            return current;
        }
        if (index < current) {
            return index;
        }
        if (index >= current + visible) {
            return Math.max(0, index - visible + 1);
        }
        return current;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record ConnectionTarget(String connectName, WeaponSlotId otherSlot) {
    }

    private record Layout(
            int top,
            int bottom,
            int blueprintLeft,
            int blueprintRight,
            int slotLeft,
            int slotRight,
            int previewLeft,
            int previewRight,
            int partLeft,
            int partRight
    ) {
        int listTop() {
            return top + PANEL_HEADER_HEIGHT + 4;
        }

        int blueprintWidth() {
            return blueprintRight - blueprintLeft;
        }

        int slotWidth() {
            return slotRight - slotLeft;
        }

        int partWidth() {
            return partRight - partLeft;
        }
    }
}
