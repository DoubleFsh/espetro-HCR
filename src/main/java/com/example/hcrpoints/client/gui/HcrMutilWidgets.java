package com.example.hcrpoints.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import se.mickelus.mutil.gui.GuiElement;
import se.mickelus.mutil.gui.GuiRect;

import java.util.regex.Pattern;

final class HcrMutilWidgets {

    static final int BACKDROP = 0x7A3A3A3A;
    static final int PANEL = 0x88363636;
    static final int PANEL_SOFT = 0x66363636;
    static final int BORDER = 0xA06D7482;
    static final int BORDER_ACTIVE = 0xFFE8B85C;
    static final int TEXT = 0xFFF3F1E8;
    static final int MUTED = 0xFFA8AEB8;
    static final int DIM = 0xFF737987;
    static final int GOLD = 0xFFFFC766;
    static final int POSITIVE = 0xFF75D58A;
    static final int WARNING = 0xFFFFB44C;
    static final int NEGATIVE = 0xFFFF6666;
    static final int BLUE = 0xFF5F8DFF;
    static final int CYAN = 0xFF25D6D2;
    static final int PURPLE = 0xFFB17CFF;
    static final int ATTACK = 0xFFFF5E56;
    static final int DEFEND = 0xFF5F8DFF;

    private static final Pattern FORMAT_CODE = Pattern.compile("(?i)\u00a7[0-9A-FK-OR]");

    private HcrMutilWidgets() {
    }

    static String stripFormatting(String text) {
        return text == null ? "" : FORMAT_CODE.matcher(text).replaceAll("");
    }

    static GuiRect rect(int x, int y, int width, int height, int color) {
        return new GuiRect(x, y, width, height, color);
    }

    static void drawScreenShade(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, BACKDROP);
    }

    static Panel panel(int x, int y, int width, int height) {
        return new Panel(x, y, width, height, PANEL, BORDER);
    }

    static Panel panel(int x, int y, int width, int height, int color, int borderColor) {
        return new Panel(x, y, width, height, color, borderColor);
    }

    static Text text(int x, int y, String value, int color) {
        return new Text(x, y, 0, value, color, false);
    }

    static Text text(int x, int y, int width, String value, int color) {
        return new Text(x, y, width, value, color, false);
    }

    static Text centeredText(int x, int y, int width, String value, int color) {
        return new Text(x, y, width, value, color, true);
    }

    static TextBlock textBlock(int x, int y, int width, String value, int color) {
        return new TextBlock(x, y, width, value, color);
    }

    static ActionButton button(int x, int y, int width, int height, String label, Runnable action) {
        return new ActionButton(x, y, width, height, label, action);
    }

    static ActionButton textButton(int x, int y, String label, Runnable action) {
        return button(x, y, textButtonWidth(label), 13, label, action);
    }

    static int textButtonWidth(String label) {
        return Minecraft.getInstance().font.width(stripFormatting(label)) + 12;
    }

    static int teamColor(String team) {
        return "ATTACK".equals(team) ? ATTACK : DEFEND;
    }

    static String teamName(String team) {
        return "ATTACK".equals(team) ? "进攻方" : "防守方";
    }

    static String teamPrefix(String team) {
        return "ATTACK".equals(team) ? "\u00a7c" : "\u00a79";
    }

    static String trimToWidth(String value, int maxWidth) {
        if (value == null) {
            return "";
        }
        if (Minecraft.getInstance().font.width(stripFormatting(value)) <= maxWidth) {
            return value;
        }

        String plain = stripFormatting(value);
        String suffix = "...";
        int suffixWidth = Minecraft.getInstance().font.width(suffix);
        return Minecraft.getInstance().font.plainSubstrByWidth(plain, Math.max(0, maxWidth - suffixWidth)) + suffix;
    }

    static class Panel extends GuiElement {
        private int color;
        private int borderColor;

        Panel(int x, int y, int width, int height, int color, int borderColor) {
            super(x, y, width, height);
            this.color = color;
            this.borderColor = borderColor;
        }

        Panel setColor(int color) {
            this.color = color;
            return this;
        }

        Panel setBorderColor(int borderColor) {
            this.borderColor = borderColor;
            return this;
        }

        @Override
        public void draw(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY, float partialTick) {
            if (!isVisible()) {
                return;
            }

            int bx = x + getX();
            int by = y + getY();
            if (hasAlpha(color)) {
                graphics.fill(bx, by, bx + getWidth(), by + getHeight(), color);
            }
            if (hasAlpha(borderColor)) {
                graphics.renderOutline(bx, by, getWidth(), getHeight(), borderColor);
            }
            super.draw(graphics, x, y, width, height, mouseX, mouseY, partialTick);
        }
    }

    static class Text extends GuiElement {
        private String value;
        private int color;
        private final boolean centered;

        Text(int x, int y, int width, String value, int color, boolean centered) {
            super(x, y, width > 0 ? width : Minecraft.getInstance().font.width(stripFormatting(value)),
                Minecraft.getInstance().font.lineHeight);
            this.value = value == null ? "" : value;
            this.color = color;
            this.centered = centered;
        }

        void setText(String value) {
            this.value = value == null ? "" : value;
            if (!centered) {
                setWidth(Minecraft.getInstance().font.width(stripFormatting(this.value)));
            }
        }

        void setColor(int color) {
            this.color = color;
        }

        @Override
        public void draw(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY, float partialTick) {
            if (!isVisible()) {
                return;
            }

            int tx = x + getX();
            if (centered) {
                tx += Math.max(0, (getWidth() - Minecraft.getInstance().font.width(stripFormatting(value))) / 2);
            }
            graphics.drawString(Minecraft.getInstance().font, Component.literal(value),
                tx, y + getY(), color, false);
            super.draw(graphics, x, y, width, height, mouseX, mouseY, partialTick);
        }
    }

    static class TextBlock extends GuiElement {
        private String value;
        private int color;

        TextBlock(int x, int y, int width, String value, int color) {
            super(x, y, width, Minecraft.getInstance().font.lineHeight);
            this.value = value == null ? "" : value;
            this.color = color;
            updateHeight();
        }

        void setText(String value) {
            this.value = value == null ? "" : value;
            updateHeight();
        }

        void setColor(int color) {
            this.color = color;
        }

        private void updateHeight() {
            setHeight(Minecraft.getInstance().font.wordWrapHeight(stripFormatting(value), getWidth()));
        }

        @Override
        public void draw(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY, float partialTick) {
            if (!isVisible()) {
                return;
            }

            graphics.drawWordWrap(Minecraft.getInstance().font, Component.literal(value),
                x + getX(), y + getY(), getWidth(), color);
            super.draw(graphics, x, y, width, height, mouseX, mouseY, partialTick);
        }
    }

    static class ActionButton extends GuiElement {
        private final Runnable action;
        private String label;
        private boolean enabled = true;
        private boolean selected = false;
        private int normalColor = 0x60404040;
        private int hoverColor = 0x80585858;
        private int selectedColor = 0x80564022;
        private int disabledColor = 0x38404040;
        private int borderColor = 0x706D7482;
        private int textColor = TEXT;

        ActionButton(int x, int y, int width, int height, String label, Runnable action) {
            super(x, y, width, height);
            this.label = label == null ? "" : label;
            this.action = action;
        }

        ActionButton setLabel(String label) {
            this.label = label == null ? "" : label;
            return this;
        }

        ActionButton setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        ActionButton setSelected(boolean selected) {
            this.selected = selected;
            return this;
        }

        ActionButton setColors(int normalColor, int hoverColor, int selectedColor) {
            this.normalColor = normalColor;
            this.hoverColor = hoverColor;
            this.selectedColor = selectedColor;
            return this;
        }

        ActionButton setDisabledColor(int disabledColor) {
            this.disabledColor = disabledColor;
            return this;
        }

        ActionButton setBorderColor(int borderColor) {
            this.borderColor = borderColor;
            return this;
        }

        ActionButton setTextColor(int textColor) {
            this.textColor = textColor;
            return this;
        }

        @Override
        public boolean onMouseClick(int mouseX, int mouseY, int button) {
            if (button != 0 || !enabled || !isVisible() || !hasFocus()) {
                return false;
            }

            if (action != null) {
                action.run();
            }
            return true;
        }

        @Override
        public void draw(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY, float partialTick) {
            if (!isVisible()) {
                return;
            }

            int bx = x + getX();
            int by = y + getY();
            int fillColor = !enabled ? disabledColor : selected ? selectedColor : hasFocus() ? hoverColor : normalColor;
            int outline = selected ? 0x90E8B85C : hasFocus() && enabled ? 0x60C2C8D5 : borderColor;

            if (hasAlpha(fillColor)) {
                graphics.fill(bx, by, bx + getWidth(), by + getHeight(), fillColor);
            }
            if (hasAlpha(outline)) {
                graphics.renderOutline(bx, by, getWidth(), getHeight(), outline);
            }

            String drawnLabel = trimToWidth(label, Math.max(8, getWidth() - 10));
            int labelWidth = Minecraft.getInstance().font.width(stripFormatting(drawnLabel));
            int color = enabled ? textColor : DIM;
            graphics.drawString(Minecraft.getInstance().font, Component.literal(drawnLabel),
                bx + Math.max(4, (getWidth() - labelWidth) / 2),
                by + Math.max(1, (getHeight() - Minecraft.getInstance().font.lineHeight) / 2),
                color,
                false);

            super.draw(graphics, x, y, width, height, mouseX, mouseY, partialTick);
        }
    }

    static class TextInput extends GuiElement {
        private static TextInput activeInput;

        private String value;
        private final int maxLength;
        private final Pattern filter;
        private final java.util.function.Consumer<String> responder;
        private boolean focused;
        private long lastBlinkMs = System.currentTimeMillis();
        private boolean cursorVisible = true;

        TextInput(int x, int y, int width, int height, String value, int maxLength,
                  Pattern filter, java.util.function.Consumer<String> responder) {
            super(x, y, width, height);
            this.value = value == null ? "" : value;
            this.maxLength = Math.max(1, maxLength);
            this.filter = filter;
            this.responder = responder;
        }

        String getValue() {
            return value;
        }

        void setValue(String value) {
            String next = value == null ? "" : value;
            if (next.length() > maxLength) {
                next = next.substring(0, maxLength);
            }
            if (filter == null || filter.matcher(next).matches()) {
                this.value = next;
            }
        }

        @Override
        public boolean onMouseClick(int mouseX, int mouseY, int button) {
            if (button != 0 || !isVisible()) {
                return false;
            }
            if (hasFocus()) {
                if (activeInput != null && activeInput != this) {
                    activeInput.focused = false;
                }
                activeInput = this;
                focused = true;
            } else if (activeInput == this) {
                activeInput = null;
                focused = false;
            }
            cursorVisible = true;
            lastBlinkMs = System.currentTimeMillis();
            return focused;
        }

        @Override
        public boolean onKeyPress(int keyCode, int scanCode, int modifiers) {
            if (activeInput != this || !focused || !isVisible()) {
                return false;
            }

            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!value.isEmpty()) {
                    value = value.substring(0, value.length() - 1);
                    notifyResponder();
                }
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                if (!value.isEmpty()) {
                    value = "";
                    notifyResponder();
                }
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                    || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                activeInput = null;
                focused = false;
                return keyCode != GLFW.GLFW_KEY_ESCAPE;
            }

            return false;
        }

        @Override
        public boolean onCharType(char codePoint, int modifiers) {
            if (activeInput != this || !focused || !isVisible() || Character.isISOControl(codePoint)) {
                return false;
            }

            if (value.length() >= maxLength) {
                return true;
            }

            String next = value + codePoint;
            if (filter == null || filter.matcher(next).matches()) {
                value = next;
                notifyResponder();
            }
            return true;
        }

        @Override
        protected void onBlur() {
            // GuiElement focus tracks hover; keep keyboard focus until Enter/Esc or another click updates it.
        }

        @Override
        public void draw(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY, float partialTick) {
            if (!isVisible()) {
                return;
            }

            int bx = x + getX();
            int by = y + getY();
            graphics.fill(bx, by, bx + getWidth(), by + getHeight(), focused ? 0xAA272A31 : 0x88404040);
            graphics.renderOutline(bx, by, getWidth(), getHeight(), focused ? BORDER_ACTIVE : BORDER);

            String drawn = trimToWidth(value, Math.max(8, getWidth() - 12));
            graphics.drawString(Minecraft.getInstance().font, Component.literal(drawn),
                bx + 5, by + Math.max(1, (getHeight() - Minecraft.getInstance().font.lineHeight) / 2),
                TEXT, false);

            long now = System.currentTimeMillis();
            if (now - lastBlinkMs > 450) {
                cursorVisible = !cursorVisible;
                lastBlinkMs = now;
            }

            if (focused && cursorVisible) {
                int cursorX = Math.min(bx + getWidth() - 6, bx + 5 + Minecraft.getInstance().font.width(stripFormatting(drawn)));
                graphics.fill(cursorX, by + 4, cursorX + 1, by + getHeight() - 4, TEXT);
            }

            super.draw(graphics, x, y, width, height, mouseX, mouseY, partialTick);
        }

        private void notifyResponder() {
            if (responder != null) {
                responder.accept(value);
            }
        }
    }

    private static boolean hasAlpha(int color) {
        return (color & 0xFF000000) != 0;
    }
}
