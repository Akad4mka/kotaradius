package net.arm.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

public class AreaConfigScreen extends Screen {

    private static final int REF_W = 3840;
    private static final int REF_H = 2160;

    private long lastRenderTime = 0;
    private final List<ToggleButton> toggleButtons = new ArrayList<>();

    // Список для анимированных вкладок
    private final List<ProfileTab> profileTabs = new ArrayList<>();

    private String selectedItemKey = null;
    private final List<ItemClickZone> itemClickZones = new ArrayList<>();

    private String hoveredItemName = null;

    private float buttonStateProgress = 0.0f;

    private boolean isClosing = false;
    private float fadeProgress = 0.0f;
    private Screen pendingScreen = null;

    private float scrollX = 0.0f;
    private boolean draggingScroll = false;

    public AreaConfigScreen() {
        super(Text.literal("KotaRadius Config"));
    }

    @Override
    protected void init() {
        toggleButtons.clear();
        int startY = 864;

        OutlineConfig config = OutlineConfig.getInstance();

        // Оставляем только кнопку включения обводки.
        toggleButtons.add(new ToggleButton(startY,
                () -> config.globalOutlineEnabled,
                (val) -> {
                    config.globalOutlineEnabled = val;
                    config.save();
                }
        ));

        for (ToggleButton btn : toggleButtons) {
            btn.initProgress();
        }

        // Инициализация анимированных вкладок
        profileTabs.clear();
        profileTabs.add(new ProfileTab(OutlineConfig.Profile.CUSTOM, "К", "Кастом"));
        profileTabs.add(new ProfileTab(OutlineConfig.Profile.FUNTIME, "Ф", "Фантайм"));
        profileTabs.add(new ProfileTab(OutlineConfig.Profile.HOLYWORLD, "Х", "Холик"));
        profileTabs.add(new ProfileTab(OutlineConfig.Profile.REALLYWORLD, "Р", "Риллик"));

        // Устанавливаем изначальный размер для уже выбранной вкладки
        for (ProfileTab tab : profileTabs) {
            if (tab.profile == config.currentProfile) {
                tab.progress = 1.0f;
                tab.currentW = 210;
            }
        }
    }

    private int modifyAlpha(int color, float alpha) {
        int a = (color >> 24) & 0xFF;
        if (a == 0) a = 255;
        int newAlpha = (int) (a * alpha);
        return (color & 0x00FFFFFF) | (newAlpha << 24);
    }

    @Override
    public void close() {
        if (!isClosing) {
            isClosing = true;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        long currentTime = System.currentTimeMillis();
        if (lastRenderTime == 0) lastRenderTime = currentTime;
        float elapsedSec = (currentTime - lastRenderTime) / 1000f;
        lastRenderTime = currentTime;

        if (elapsedSec > 0.1f) elapsedSec = 0.1f;

        float fadeSpeed = 5.0f;
        if (isClosing) {
            fadeProgress = Math.max(0.0f, fadeProgress - elapsedSec * fadeSpeed);
            if (fadeProgress <= 0.0f) {
                if (this.client != null && pendingScreen != null) {
                    this.client.setScreen(pendingScreen);
                } else {
                    super.close();
                }
                return;
            }
        } else {
            fadeProgress = Math.min(1.0f, fadeProgress + elapsedSec * fadeSpeed);
        }

        for (ToggleButton btn : toggleButtons) {
            btn.updateAnimation(elapsedSec);
        }

        float speed = 5.0f;
        if (selectedItemKey != null) {
            if (buttonStateProgress < 1.0f) buttonStateProgress = Math.min(1.0f, buttonStateProgress + elapsedSec * speed);
        } else {
            if (buttonStateProgress > 0.0f) buttonStateProgress = Math.max(0.0f, buttonStateProgress - elapsedSec * speed);
        }

        float scale = Math.min((float) this.width / REF_W, (float) this.height / REF_H);
        float offsetX = (this.width - (REF_W * scale)) / 2.0f;
        float offsetY = (this.height - (REF_H * scale)) / 2.0f;
        double vMouseX = (mouseX - offsetX) / scale;
        double vMouseY = (mouseY - offsetY) / scale;

        context.getMatrices().push();
        context.getMatrices().translate(offsetX, offsetY, 0);
        context.getMatrices().scale(scale, scale, 1.0f);

        // Верхняя плашка Academy
        int academyX = REF_W - 766 - 3042;
        boolean hoveredAcademy = vMouseX >= academyX && vMouseX <= academyX + 766 && vMouseY >= 32 && vMouseY <= 32 + 52;
        int academyBorder = hoveredAcademy ? 0xFF3A3F4B : 0xFF1E2127;
        drawCustomButton(context, academyX, 32, 766, 75, academyBorder, 0xFF111316);
        drawCustomText(context, "Сделано учениками Kota Academy", REF_W - 3770, 54, 0xFFFFFFFF, 4.0f, false);

        // Основные фоны блоков настроек
        drawCustomButton(context, REF_W - 850 - 1495, 774, 850, 330, 0xFF1E2127, 0xFF111316);
        drawCustomButton(context, REF_W - 850 - 1495, 1124, 850, 200, 0xFF1E2127, 0xFF111316);

        int settingsX = REF_W - 2313; // 1527

        // --- Верхний блок ---
        drawCustomText(context, "Настройки зон", settingsX, 806, 0xFFFFFFFF, 4.0f, false);
        drawCustomText(context, "Обводка включена:", settingsX, 877, 0xFFC0C0C0, 4.0f, false);

        // Отрисовка вкладок профилей
        drawCustomText(context, "Выберите профиль:", settingsX, 950, 0xFFC0C0C0, 4.0f, false);
        OutlineConfig config = OutlineConfig.getInstance();

        // Рендер анимированных вкладок с передачей мыши и времени
        renderProfileTabs(context, config, vMouseX, vMouseY, elapsedSec);

        // --- Нижний блок ---
        drawCustomText(context, "Предметы в профиле", settingsX, 1150, 0xFFFFFFFF, 4.0f, false);

        itemClickZones.clear();
        hoveredItemName = null;

        List<OutlineConfig.OutlineEntry> list = config.getCurrentList();
        int boxX = REF_W - 850 - 1495; // 1495
        int boxW = 850;
        int buttonCenterX = boxX + (boxW / 2);

        if (list == null || list.isEmpty()) {
            String emptyStr = "Пусто";
            int renderedTextWidth = this.textRenderer.getWidth(emptyStr) * 4;
            drawCustomText(context, emptyStr, buttonCenterX - (renderedTextWidth / 2), 1225, 0xFF676767, 4.0f, false);
        } else {
            int slotSize = 80;
            int itemGap = 20;
            int step = slotSize + itemGap;
            int totalWidth = (list.size() * step) - itemGap;

            int padding = 25;
            int allowedWidth = boxW - (padding * 2);

            int currentX;
            if (totalWidth > allowedWidth) {
                int maxScrollOffset = totalWidth - allowedWidth;
                currentX = (boxX + padding) - (int) (scrollX * maxScrollOffset);
            } else {
                currentX = buttonCenterX - (totalWidth / 2);
                scrollX = 0.0f;
            }

            int slotY = 1200;

            context.enableScissor(boxX + padding, 1124, boxX + boxW - padding, 1124 + 200);

            for (int i = 0; i < list.size(); i++) {
                String fullId = list.get(i).item;
                boolean isSelected = fullId.equals(selectedItemKey);

                int borderColor = isSelected ? 0xFF00E3D2 : 0xFF32363F;
                int fillColor = isSelected ? 0xFF07524D : 0xFF1E2127;

                drawCustomButton(context, currentX, slotY, slotSize, slotSize, borderColor, fillColor);

                Identifier id = Identifier.tryParse(fullId);
                Item item = id != null && Registries.ITEM.containsId(id) ? Registries.ITEM.get(id) : Items.BARRIER;
                ItemStack stack = new ItemStack(item);

                context.getMatrices().push();
                context.getMatrices().translate(currentX + 8, slotY + 8, 0);
                context.getMatrices().scale(4.0f, 4.0f, 1.0f);
                context.drawItem(stack, 0, 0);
                context.getMatrices().pop();

                itemClickZones.add(new ItemClickZone(fullId, currentX, slotY, currentX + slotSize, slotY + slotSize));

                if (vMouseX >= currentX && vMouseX <= currentX + slotSize && vMouseY >= slotY && vMouseY <= slotY + slotSize) {
                    hoveredItemName = stack.getName().getString();
                }

                currentX += step;
            }

            context.disableScissor();

            if (totalWidth > allowedWidth) {
                int barY = 1295;
                int barH = 3;
                int trackX = boxX + padding;
                int trackW = allowedWidth;

                int thumbW = Math.max(40, (int) ((float) trackW / totalWidth * trackW));
                int maxThumbTravel = trackW - thumbW;
                int thumbX = trackX + (int) (scrollX * maxThumbTravel);

                int trackColor = modifyAlpha(0xFF32363F, fadeProgress);
                context.fill(trackX, barY, trackX + trackW, barY + barH, trackColor);

                int thumbColor = modifyAlpha(0xFF8E929C, fadeProgress);
                context.fill(thumbX, barY, thumbX + thumbW, barY + barH, thumbColor);
            }
        }

        for (ToggleButton btn : toggleButtons) {
            btn.render(context);
        }

        int addButtonX = (int) (1495 + (431 * buttonStateProgress));
        int addButtonWidth = (int) (850 - (431 * buttonStateProgress));
        int addTextX = (int) (1824 + (216 * buttonStateProgress));

        drawCustomButton(context, addButtonX, 1333, addButtonWidth, 75, 0xFF1E2127, 0xFF111316);
        drawCustomText(context, "Добавить", addTextX, 1355, 0xFFFFFFFF, 4.0f, false);

        if (buttonStateProgress > 0.01f) {
            int deleteButtonX = 1495;
            int deleteTextX = 1621;

            context.getMatrices().push();
            int alpha = (int) (buttonStateProgress * 255) << 24;
            int deleteBorder = (0xFFE73D4B & 0x00FFFFFF) | alpha;
            int deleteFill = (0xFFA91925 & 0x00FFFFFF) | alpha;
            int deleteTextColor = (0xFFFFFFFF & 0x00FFFFFF) | alpha;

            drawCustomButton(context, deleteButtonX, 1333, 419, 75, deleteBorder, deleteFill);
            drawCustomText(context, "Удалить", deleteTextX, 1355, deleteTextColor, 4.0f, false);
            context.getMatrices().pop();
        }

        if (hoveredItemName != null) {
            context.getMatrices().push();
            context.getMatrices().translate(0.0f, 0.0f, 400.0f);

            int tw = this.textRenderer.getWidth(hoveredItemName) * 3;
            int tipX = (int) vMouseX + 15;
            int tipY = (int) vMouseY + 15;

            context.fill(tipX, tipY, tipX + tw + 20, tipY + 36, modifyAlpha(0xFF000000, fadeProgress * 0.8f));
            context.drawBorder(tipX, tipY, tw + 20, 36, modifyAlpha(0xFF32363F, fadeProgress));

            context.getMatrices().push();
            context.getMatrices().translate(tipX + 10, tipY + 8, 0);
            context.getMatrices().scale(3.0f, 3.0f, 1.0f);
            context.drawText(this.textRenderer, hoveredItemName, 0, 0, modifyAlpha(0xFFFFFFFF, fadeProgress), true);
            context.getMatrices().pop();

            context.getMatrices().pop();
        }

        context.getMatrices().pop();
    }

    private void renderProfileTabs(DrawContext context, OutlineConfig config, double vMouseX, double vMouseY, float elapsedSec) {
        int y = 1005;
        int h = 60;
        int gap = 15;
        int collapsedW = 60;
        int expandedW = 210;

        // 1. Сначала обновляем анимацию всех вкладок (логика)
        for (ProfileTab tab : profileTabs) {
            boolean isSelected = config.currentProfile == tab.profile;
            // Используем размеры с ПРОШЛОГО кадра для проверки наведения,
            // чтобы мышка не "теряла" вкладку во время расширения
            boolean isHovered = vMouseX >= tab.currentX && vMouseX <= tab.currentX + tab.currentW && vMouseY >= y && vMouseY <= y + h;
            tab.updateAnimation(elapsedSec, isHovered, isSelected);
            tab.currentW = (int) (collapsedW + (expandedW - collapsedW) * tab.progress);
        }

        // 2. Вычисляем общую ширину для центровки
        float totalWidth = 0;
        for (ProfileTab tab : profileTabs) {
            totalWidth += tab.currentW;
        }
        totalWidth += (gap * (profileTabs.size() - 1));

        // 3. Рендерим и обновляем координаты X
        float currentX = (REF_W - totalWidth) / 2.0f;

        for (ProfileTab tab : profileTabs) {
            tab.currentX = (int) currentX; // Сохраняем координату для следующего кадра

            boolean isSelected = config.currentProfile == tab.profile;
            int borderColor = isSelected ? 0xFF00E3D2 : 0xFF32363F;
            int fillColor = isSelected ? 0xFF07524D : 0xFF1E2127;
            int baseTextColor = isSelected ? 0xFFFFFFFF : 0xFF8E929C;

            drawCustomButton(context, tab.currentX, y, tab.currentW, h, borderColor, fillColor);

            // Текст
            float alphaFull = tab.progress;
            float alphaShort = 1.0f - tab.progress;

            context.enableScissor(tab.currentX, y, tab.currentX + tab.currentW, y + h);

            if (alphaShort > 0.01f) {
                int textW = this.textRenderer.getWidth(tab.shortName) * 4;
                drawCustomText(context, tab.shortName, tab.currentX + (tab.currentW - textW) / 2, y + (h - 28) / 2, modifyAlpha(baseTextColor, alphaShort * fadeProgress), 4.0f, false);
            }
            if (alphaFull > 0.01f) {
                int textW = this.textRenderer.getWidth(tab.fullName) * 4;
                drawCustomText(context, tab.fullName, tab.currentX + (tab.currentW - textW) / 2, y + (h - 28) / 2, modifyAlpha(baseTextColor, alphaFull * fadeProgress), 4.0f, false);
            }

            context.disableScissor();
            currentX += tab.currentW + gap;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float scale = Math.min((float) this.width / REF_W, (float) this.height / REF_H);
        float offsetX = (this.width - (REF_W * scale)) / 2.0f;
        float offsetY = (this.height - (REF_H * scale)) / 2.0f;

        double vMouseX = (mouseX - offsetX) / scale;
        double vMouseY = (mouseY - offsetY) / scale;

        if (button == 0) {
            int boxX = REF_W - 850 - 1495;
            int padding = 25;
            int trackX = boxX + padding;
            int trackW = boxWFromCode(boxX);
            int barY = 1295;

            if (vMouseX >= trackX && vMouseX <= trackX + trackW && vMouseY >= barY - 10 && vMouseY <= barY + 15) {
                draggingScroll = true;
                updateScrollFromMouse(vMouseX, trackX, trackW);
                return true;
            }

            int academyX = REF_W - 850 - 2953;
            if (vMouseX >= academyX && vMouseX <= academyX + 850 && vMouseY >= 32 && vMouseY <= 32 + 75) {
                playClickSound();
                net.minecraft.util.Util.getOperatingSystem().open("https://t.me/kotaacademy");
                return true;
            }

            for (ToggleButton btn : toggleButtons) {
                if (btn.checkClick(vMouseX, vMouseY)) return true;
            }

            // Динамический клик по вкладкам профилей
            int tabY = 1005, tabH = 60;
            OutlineConfig config = OutlineConfig.getInstance();

            if (vMouseY >= tabY && vMouseY <= tabY + tabH) {
                for (ProfileTab tab : profileTabs) {
                    if (vMouseX >= tab.currentX && vMouseX <= tab.currentX + tab.currentW) {
                        setProfile(config, tab.profile);
                        return true;
                    }
                }
            }

            for (ItemClickZone zone : itemClickZones) {
                if (vMouseX >= zone.x1 && vMouseX <= zone.x2 && vMouseY >= zone.y1 && vMouseY <= zone.y2) {
                    selectedItemKey = zone.itemKey.equals(selectedItemKey) ? null : zone.itemKey;
                    playClickSound();
                    return true;
                }
            }

            int currentAddX = (int) (1495 + (431 * buttonStateProgress));
            int addButtonWidth = (int) (850 - (431 * buttonStateProgress));
            if (vMouseX >= currentAddX && vMouseX <= currentAddX + addButtonWidth && vMouseY >= 1333 && vMouseY <= 1333 + 75) {
                playClickSound();
                this.pendingScreen = new net.minecraft.client.gui.screen.ChatScreen("/kotaradius add ");
                this.close();
                return true;
            }

            if (selectedItemKey != null && buttonStateProgress >= 0.9f) {
                int deleteX = 1495;
                if (vMouseX >= deleteX && vMouseX <= deleteX + 419 && vMouseY >= 1333 && vMouseY <= 1333 + 75) {
                    Identifier id = Identifier.tryParse(selectedItemKey);
                    if (id != null && Registries.ITEM.containsId(id)) {
                        Item item = Registries.ITEM.get(id);
                        config.removeEntry(item);
                        selectedItemKey = null;
                    }
                    playClickSound();
                    return true;
                }
            }

            if (vMouseX >= 1495 && vMouseX <= 1495 + 850 && vMouseY >= 1124 && vMouseY <= 1124 + 200) {
                selectedItemKey = null;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void setProfile(OutlineConfig config, OutlineConfig.Profile profile) {
        if (config.currentProfile != profile) {
            config.currentProfile = profile;
            config.rebuildEntryMap();
            config.save();
            selectedItemKey = null;
            scrollX = 0.0f;
            playClickSound();
        }
    }

    private int boxWFromCode(int boxX) {
        return 850 - (25 * 2);
    }

    private void updateScrollFromMouse(double vMouseX, int trackX, int trackW) {
        List<OutlineConfig.OutlineEntry> list = OutlineConfig.getInstance().getCurrentList();
        if (list == null || list.isEmpty()) return;

        int slotSize = 80;
        int itemGap = 20;
        int step = slotSize + itemGap;
        int totalWidth = (list.size() * step) - itemGap;

        int thumbW = Math.max(40, (int) ((float) trackW / totalWidth * trackW));
        int maxThumbTravel = trackW - thumbW;

        if (maxThumbTravel <= 0) return;

        double mousePosInTravel = vMouseX - trackX - (thumbW / 2.0);
        scrollX = (float) (mousePosInTravel / maxThumbTravel);
        scrollX = MathHelper.clamp(scrollX, 0.0f, 1.0f);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        float scale = Math.min((float) this.width / REF_W, (float) this.height / REF_H);
        float offsetX = (this.width - (REF_W * scale)) / 2.0f;
        double vMouseX = (mouseX - offsetX) / scale;

        if (draggingScroll) {
            int boxX = REF_W - 850 - 1495;
            int padding = 25;
            updateScrollFromMouse(vMouseX, boxX + padding, boxWFromCode(boxX));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            draggingScroll = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    public void drawCustomButton(DrawContext context, int x, int y, int width, int height, int borderColor, int fillColor) {
        borderColor = modifyAlpha(borderColor, fadeProgress);
        fillColor = modifyAlpha(fillColor, fadeProgress);

        int cut = 8;
        int thick = 4;
        context.fill(x + cut, y, x + width - cut, y + height, borderColor);
        context.fill(x, y + cut, x + width, y + height - cut, borderColor);
        int fillOffset = cut + thick;
        context.fill(x + fillOffset, y + thick, x + width - fillOffset, y + height - thick, fillColor);
        context.fill(x + thick, y + fillOffset, x + width - thick, y + height - fillOffset, fillColor);
    }

    public void drawCustomText(DrawContext context, String text, int x, int y, int color, float scale, boolean shadow) {
        color = modifyAlpha(color, fadeProgress);

        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.drawText(this.textRenderer, text, 0, 0, color, shadow);
        context.getMatrices().pop();
    }

    private int lerpColor(int startColor, int endColor, float fraction) {
        int startA = (startColor >> 24) & 0xFF;
        int startR = (startColor >> 16) & 0xFF;
        int startG = (startColor >> 8) & 0xFF;
        int startB = startColor & 0xFF;

        int endA = (endColor >> 24) & 0xFF;
        int endR = (endColor >> 16) & 0xFF;
        int endG = (endColor >> 8) & 0xFF;
        int endB = endColor & 0xFF;

        int r = (int) (startR + fraction * (endR - startR));
        int g = (int) (startG + fraction * (endG - startG));
        int b = (int) (startB + fraction * (endB - startB));
        int a = (int) (startA + fraction * (endA - startA));

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // --- Класс-помощник для вкладок ---
    private class ProfileTab {
        public OutlineConfig.Profile profile;
        public String shortName;
        public String fullName;
        public float progress;
        public int currentX;
        public int currentW;

        public ProfileTab(OutlineConfig.Profile profile, String shortName, String fullName) {
            this.profile = profile;
            this.shortName = shortName;
            this.fullName = fullName;
            this.progress = 0.0f;
            this.currentX = 0;
            this.currentW = 60;
        }

        public void updateAnimation(float elapsedSec, boolean isHovered, boolean isSelected) {
            float target = (isHovered || isSelected) ? 1.0f : 0.0f;
            float speed = 8.0f; // Скорость расширения/сужения

            if (progress < target) {
                progress = Math.min(target, progress + elapsedSec * speed);
            } else if (progress > target) {
                progress = Math.max(target, progress - elapsedSec * speed);
            }
        }
    }

    private class ToggleButton {
        private final int y;
        private final java.util.function.Supplier<Boolean> getter;
        private final java.util.function.Consumer<Boolean> setter;
        private float progress = 0.0f;
        private static final int textYOffset = 10;

        public ToggleButton(int y, java.util.function.Supplier<Boolean> getter, java.util.function.Consumer<Boolean> setter) {
            this.y = y;
            this.getter = getter;
            this.setter = setter;
        }

        public void initProgress() {
            this.progress = getter.get() ? 1.0f : 0.0f;
        }

        public void updateAnimation(float elapsedSec) {
            float target = getter.get() ? 1.0f : 0.0f;
            float speed = 6.0f;
            if (progress < target) progress = Math.min(target, progress + elapsedSec * speed);
            else if (progress > target) progress = Math.max(target, progress - elapsedSec * speed);
        }

        public void render(DrawContext context) {
            int bgX = REF_W - 104 - 1527;
            int bgBorderColor = lerpColor(0xFF7E161F, 0xFF00746B, progress);
            int bgFillColor = lerpColor(0xFF3A1115, 0xFF07524D, progress);
            int knobBorderColor = lerpColor(0xFFE73D4B, 0xFF00E3D2, progress);
            int knobFillColor = lerpColor(0xFFA91925, 0xFF00B8AA, progress);

            int startKnobX = REF_W - 52 - 1579;
            int knobX = startKnobX + (int) (52 * progress);

            drawCustomButton(context, bgX, y, 104, 52, bgBorderColor, bgFillColor);
            drawCustomButton(context, knobX, y, 52, 52, knobBorderColor, knobFillColor);

            String symbol = progress > 0.5f ? "o" : "x";
            int textX = knobX + (progress > 0.5f ? 17 : 16);
            drawCustomText(context, symbol, textX, y + textYOffset, 0xFFFFFFFF, 4.0f, false);
        }

        public boolean checkClick(double vMouseX, double vMouseY) {
            int bgX = REF_W - 104 - 1527;
            if (vMouseX >= bgX && vMouseX <= bgX + 104 && vMouseY >= y && vMouseY <= y + 52) {
                setter.accept(!getter.get());
                playClickSound();
                return true;
            }
            return false;
        }
    }

    private static class ItemClickZone {
        String itemKey; int x1, y1, x2, y2;
        public ItemClickZone(String itemKey, int x1, int y1, int x2, int y2) {
            this.itemKey = itemKey; this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
        }
    }

    private void playClickSound() {
        if (this.client != null && this.client.player != null) {
            this.client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(
                    net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }
}