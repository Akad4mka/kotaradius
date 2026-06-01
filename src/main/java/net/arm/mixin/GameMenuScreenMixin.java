package net.arm.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public class GameMenuScreenMixin extends Screen {

    private static final Identifier LOGO_ICON = Identifier.of("arm", "textures/logo.png");

    protected GameMenuScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        int optionsY = -1;
        int buttonHeight = 20;
        int buttonWidth = 204;

        for (var element : this.children()) {
            if (element instanceof ClickableWidget widget) {
                if (widget.getMessage().getContent() instanceof TranslatableTextContent translatable
                        && "menu.options".equals(translatable.getKey())) {
                    optionsY = widget.getY();
                    buttonHeight = widget.getHeight();
                    break;
                }
            }
        }

        if (optionsY != -1) {
            int spacing = 4;
            int shiftAmount = buttonHeight + spacing;

            for (var element : this.children()) {
                if (element instanceof ClickableWidget widget) {
                    if (widget.getY() >= optionsY) {
                        widget.setY(widget.getY() + shiftAmount);
                    }
                }
            }

            int btnX = this.width / 2 - buttonWidth / 2;

            this.addDrawableChild(new CustomDownloadButton(
                    btnX, optionsY, buttonWidth, buttonHeight,
                    Text.literal("Скачать больше модов!"),
                    button -> {
                        Util.getOperatingSystem().open("https://t.me/kotamods_bot");
                    }
            ));
        }
    }

    private static class CustomDownloadButton extends ButtonWidget {
        private float hoverProgress = 0.0f;
        private long lastTime = 0;

        public CustomDownloadButton(int x, int y, int width, int height, Text message, PressAction onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            long now = System.currentTimeMillis();
            if (lastTime == 0) lastTime = now;
            float elapsedSec = (now - lastTime) / 1000f;
            lastTime = now;
            if (elapsedSec > 0.1f) elapsedSec = 0.1f;

            if (this.isHovered()) {
                hoverProgress = Math.min(1.0f, hoverProgress + elapsedSec * 5.0f);
            } else {
                hoverProgress = Math.max(0.0f, hoverProgress - elapsedSec * 5.0f);
            }

            int borderColor = lerpColor(0xFF00746B, 0xFF00E3D2, hoverProgress);
            int fillColor = lerpColor(0xFF07524C, 0xFF00B8AA, hoverProgress);

            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();

            int cut = 3;
            int thick = 1;

            context.fill(x + cut, y, x + w - cut, y + h, borderColor);
            context.fill(x, y + cut, x + w, y + h - cut, borderColor);

            int fillOffset = cut + thick;
            context.fill(x + fillOffset, y + thick, x + w - fillOffset, y + h - thick, fillColor);
            context.fill(x + thick, y + fillOffset, x + w - thick, y + h - fillOffset, fillColor);

            int iconSize = 16;
            int iconPaddingLeft = 4;
            int iconX = x + thick + iconPaddingLeft;
            int iconY = y + (h - iconSize) / 2;

            context.drawTexture(RenderLayer::getGuiTextured, LOGO_ICON, iconX, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);

            var textRenderer = MinecraftClient.getInstance().textRenderer;
            int textWidth = textRenderer.getWidth(this.getMessage());

            int textX = x + (w - textWidth) / 2;
            int textY = y + (h - 8) / 2;

            context.drawText(textRenderer, this.getMessage(), textX, textY, 0xFFFFFFFF, false);
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
    }
}