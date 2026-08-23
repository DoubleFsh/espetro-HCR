package com.example.espoints.client.gui;

import com.example.espoints.util.ModLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;
import org.espetro.client.aui.GuiElement;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * MD文件阅读界面。
 */
public class MDRenderScreen extends EspetroMenuScreen {
    private static final String MD_FOLDER_NAME = "HCRmdread";
    private static final int MARGIN = 18;
    private static final int HEADER_H = 48;
    private static final int LINE_HEIGHT = 15;
    private static final int FILE_ROW_H = 22;
    private static final int TOC_ROW_H = 18;
    private static final float LEFT_SIDE_RATIO = 0.32f;

    private enum ScreenState {
        FILE_LIST,
        FILE_CONTENT
    }

    private ScreenState currentState = ScreenState.FILE_LIST;
    private File currentFile;
    private List<File> mdFiles = new ArrayList<>();
    private List<FormattedCharSequence> fileContentLines = new ArrayList<>();
    private List<MdTitle> mdTitles = new ArrayList<>();
    private int contentScrollOffset = 0;

    private int lastWidth;
    private int lastHeight;

    public MDRenderScreen() {
        super(Component.translatable("gui.espoints.md_reader.title"));
    }

    @Override
    protected void init() {
        loadMDFiles();
        super.init();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        if (currentState == ScreenState.FILE_CONTENT && currentFile != null) {
            loadFileContent(currentFile, getContentWidth(width));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.width != lastWidth || this.height != lastHeight) {
            rebuildMenuRoot();
        }
    }

    @Override
    protected void buildMenuRoot(GuiElement root) {
        lastWidth = this.width;
        lastHeight = this.height;

        int pageX = MARGIN;
        int pageY = MARGIN;
        int pageW = Math.max(360, this.width - MARGIN * 2);
        int pageH = Math.max(220, this.height - MARGIN * 2);
        int leftW = Math.max(150, Math.min(260, (int) (pageW * LEFT_SIDE_RATIO)));
        int rightX = pageX + leftW + 12;
        int rightW = Math.max(120, pageW - leftW - 30);

        root.addChild(HcrAuiWidgets.panel(pageX, pageY, pageW, pageH,
            HcrAuiWidgets.PANEL, HcrAuiWidgets.BORDER));
        buildHeader(root, pageX, pageY, pageW);

        int contentY = pageY + HEADER_H + 12;
        int contentH = pageY + pageH - contentY - 18;

        root.addChild(HcrAuiWidgets.panel(pageX + 18, contentY, leftW, contentH,
            HcrAuiWidgets.PANEL_SOFT, HcrAuiWidgets.BORDER));
        root.addChild(HcrAuiWidgets.panel(rightX, contentY, rightW, contentH,
            HcrAuiWidgets.PANEL_SOFT, HcrAuiWidgets.BORDER));

        if (currentState == ScreenState.FILE_LIST) {
            buildFileList(root, pageX + 18, contentY, leftW, contentH, rightX, rightW);
        } else {
            buildFileContent(root, pageX + 18, contentY, leftW, contentH, rightX, rightW, contentH);
        }
    }

    private void buildHeader(GuiElement root, int pageX, int pageY, int pageW) {
        String subtitle = currentFile == null ? "读取 HCRmdread 文件夹中的 Markdown 文档" : currentFile.getName();
        root.addChild(HcrAuiWidgets.text(pageX + 18, pageY + 13, "MD 文件阅读器", HcrAuiWidgets.TEXT));
        root.addChild(HcrAuiWidgets.text(pageX + 18, pageY + 29,
            HcrAuiWidgets.trimToWidth(subtitle, Math.max(60, pageW - 150)), HcrAuiWidgets.MUTED));

        if (currentState == ScreenState.FILE_CONTENT) {
            root.addChild(HcrAuiWidgets.button(pageX + pageW - 126, pageY + 14, 50, 18, "列表", () -> {
                currentState = ScreenState.FILE_LIST;
                currentFile = null;
                rebuildMenuRoot();
            }).setTextColor(HcrAuiWidgets.GOLD));
        }
        root.addChild(HcrAuiWidgets.button(pageX + pageW - 68, pageY + 14, 50, 18, "关闭", this::onClose)
            .setTextColor(HcrAuiWidgets.MUTED));
        root.addChild(HcrAuiWidgets.rect(pageX + 18, pageY + HEADER_H, pageW - 36, 1, 0x35FFFFFF));
    }

    private void buildFileList(GuiElement root, int leftX, int top, int leftW, int height, int rightX, int rightW) {
        root.addChild(HcrAuiWidgets.text(leftX + 12, top + 10, "文件列表", HcrAuiWidgets.GOLD));
        root.addChild(HcrAuiWidgets.text(leftX + 12, top + 26, "共 " + mdFiles.size() + " 个文件", HcrAuiWidgets.MUTED));

        ScrollableList list = new ScrollableList(leftX + 8, top + 46, leftW - 16, Math.max(30, height - 54))
            .setScrollStep(FILE_ROW_H)
            .setAlwaysShowScrollbar(true);
        root.addChild(list);

        if (mdFiles.isEmpty()) {
            list.addChild(HcrAuiWidgets.textBlock(4, 4, leftW - 32,
                "未找到 .md 文件。", HcrAuiWidgets.MUTED));
        } else {
            int y = 0;
            for (File file : mdFiles) {
                list.addChild(HcrAuiWidgets.button(0, y, Math.max(40, leftW - 28), 18,
                    HcrAuiWidgets.trimToWidth(file.getName(), Math.max(32, leftW - 42)),
                    () -> openFile(file)).setTextColor(HcrAuiWidgets.TEXT));
                y += FILE_ROW_H;
            }
        }

        root.addChild(HcrAuiWidgets.centeredText(rightX, top + Math.max(20, height / 2 - 12), rightW,
            "选择左侧文件开始阅读", HcrAuiWidgets.MUTED));
    }

    private void buildFileContent(GuiElement root, int leftX, int top, int leftW, int leftH,
                                  int rightX, int rightW, int rightH) {
        root.addChild(HcrAuiWidgets.text(leftX + 12, top + 10, "目录", HcrAuiWidgets.GOLD));
        ScrollableList toc = new ScrollableList(leftX + 8, top + 30, leftW - 16, Math.max(30, leftH - 38))
            .setScrollStep(TOC_ROW_H)
            .setAlwaysShowScrollbar(true);
        root.addChild(toc);

        if (mdTitles.isEmpty()) {
            toc.addChild(HcrAuiWidgets.text(4, 4, "无标题", HcrAuiWidgets.MUTED));
        } else {
            int y = 0;
            for (MdTitle title : mdTitles) {
                int indent = Math.min(30, Math.max(0, (title.level - 1) * 8));
                toc.addChild(HcrAuiWidgets.button(indent, y, Math.max(36, leftW - 28 - indent), 15,
                    HcrAuiWidgets.trimToWidth(title.text, Math.max(24, leftW - 42 - indent)),
                    () -> {
                        contentScrollOffset = title.lineIndex;
                    }).setTextColor(title.level == 1 ? HcrAuiWidgets.GOLD : HcrAuiWidgets.MUTED));
                y += TOC_ROW_H;
            }
        }

        root.addChild(new ContentPane(rightX + 10, top + 10, rightW - 20, rightH - 20));
    }

    private void openFile(File file) {
        currentFile = file;
        loadFileContent(file, getContentWidth(this.width));
        currentState = ScreenState.FILE_CONTENT;
        rebuildMenuRoot();
    }

    private int getContentWidth(int screenWidth) {
        int pageW = Math.max(360, screenWidth - MARGIN * 2);
        int leftW = Math.max(150, Math.min(260, (int) (pageW * LEFT_SIDE_RATIO)));
        return Math.max(120, pageW - leftW - 30) - 28;
    }

    private void loadMDFiles() {
        File minecraftDir = Minecraft.getInstance().gameDirectory;
        Path mdFolderPath = Paths.get(minecraftDir.getAbsolutePath(), MD_FOLDER_NAME);
        File mdFolder = mdFolderPath.toFile();

        if (!mdFolder.exists() && !mdFolder.mkdirs()) {
            ModLogger.error("无法创建MD文件文件夹: " + mdFolderPath);
            mdFiles = new ArrayList<>();
            return;
        }

        File[] files = mdFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".md"));
        mdFiles = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    mdFiles.add(file);
                }
            }
            mdFiles.sort(Comparator.comparing(File::getName));
        }
    }

    private void loadFileContent(File file, int maxContentWidth) {
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            fileContentLines = new ArrayList<>();
            mdTitles = new ArrayList<>();

            for (String line : lines) {
                if (line.startsWith("#")) {
                    int level = 0;
                    while (level < line.length() && line.charAt(level) == '#') {
                        level++;
                    }
                    String titleText = line.substring(level).trim();
                    String rendered = level == 1 ? "=== " + titleText + " ==="
                        : level == 2 ? "--- " + titleText + " ---"
                        : "   ".repeat(Math.max(0, level - 3)) + "- " + titleText;
                    processAndAddText(rendered, maxContentWidth);
                    mdTitles.add(new MdTitle(titleText, level, Math.max(0, fileContentLines.size() - 1)));
                } else {
                    processAndAddText(line, maxContentWidth);
                }
            }
            contentScrollOffset = 0;
        } catch (IOException e) {
            ModLogger.error("读取MD文件失败: " + e.getMessage());
            fileContentLines = new ArrayList<>();
            mdTitles = new ArrayList<>();
            fileContentLines.add(Component.literal("无法读取文件内容: " + e.getMessage()).getVisualOrderText());
        }
    }

    private void processAndAddText(String text, int maxWidth) {
        if (text.trim().isEmpty()) {
            fileContentLines.add(Component.literal("").getVisualOrderText());
            return;
        }

        Font font = Minecraft.getInstance().font;
        StringBuilder currentLine = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int codePoint = text.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            String currentChar = text.substring(i, i + charCount);
            String testLine = currentLine + currentChar;

            if (font.width(addColorToText(testLine)) <= maxWidth) {
                currentLine.append(currentChar);
                i += charCount;
                continue;
            }

            if (currentLine.length() > 0) {
                fileContentLines.add(addColorToText(currentLine.toString()).getVisualOrderText());
                currentLine = new StringBuilder(currentChar);
            } else {
                fileContentLines.add(addColorToText(currentChar).getVisualOrderText());
            }
            i += charCount;
        }

        if (currentLine.length() > 0) {
            fileContentLines.add(addColorToText(currentLine.toString()).getVisualOrderText());
        }
    }

    private Component addColorToText(String text) {
        if (text.contains("警告") || text.contains("Warning") || text.contains("WARN")) {
            return Component.literal(text).withStyle(style -> style.withColor(0xFFFFAA00));
        } else if (text.contains("错误") || text.contains("Error") || text.contains("ERR") || text.contains("ERROR")) {
            return Component.literal(text).withStyle(style -> style.withColor(0xFFFF5555));
        } else if (text.contains("提示") || text.contains("Tip") || text.contains("注意") || text.contains("Note")) {
            return Component.literal(text).withStyle(style -> style.withColor(0xFF55FFFF));
        } else if (text.startsWith("- ") || text.startsWith("* ")) {
            return Component.literal(text).withStyle(style -> style.withColor(0xFFAAAAAA));
        }
        return Component.literal(text).withStyle(style -> style.withColor(0xFFFFFFFF));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (currentState == ScreenState.FILE_CONTENT && root != null) {
            int pageW = Math.max(360, this.width - MARGIN * 2);
            int leftW = Math.max(150, Math.min(260, (int) (pageW * LEFT_SIDE_RATIO)));
            int rightX = MARGIN + leftW + 12;
            if (mouseX >= rightX) {
                int visibleLines = getVisibleContentLines();
                int maxScroll = Math.max(0, fileContentLines.size() - visibleLines);
                contentScrollOffset = Math.max(0, Math.min(maxScroll, contentScrollOffset - (int) Math.signum(delta) * 3));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private int getVisibleContentLines() {
        int pageH = Math.max(220, this.height - MARGIN * 2);
        int contentH = pageH - HEADER_H - 30;
        return Math.max(1, contentH / LINE_HEIGHT);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (currentState == ScreenState.FILE_CONTENT) {
                currentState = ScreenState.FILE_LIST;
                currentFile = null;
                rebuildMenuRoot();
                return true;
            }
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private class ContentPane extends GuiElement {
        ContentPane(int x, int y, int width, int height) {
            super(x, y, width, height);
        }

        @Override
        public void draw(GuiGraphics graphics, int x, int y, int width, int height,
                         int mouseX, int mouseY, float partialTick) {
            if (!isVisible()) {
                return;
            }

            int bx = x + getX();
            int by = y + getY();
            int visibleLines = Math.max(1, getHeight() / LINE_HEIGHT);
            int maxScroll = Math.max(0, fileContentLines.size() - visibleLines);
            contentScrollOffset = Math.max(0, Math.min(maxScroll, contentScrollOffset));

            graphics.enableScissor(bx, by, bx + getWidth(), by + getHeight());
            int endIndex = Math.min(contentScrollOffset + visibleLines, fileContentLines.size());
            for (int i = contentScrollOffset; i < endIndex; i++) {
                int lineIndex = i - contentScrollOffset;
                graphics.drawString(Minecraft.getInstance().font, fileContentLines.get(i),
                    bx, by + lineIndex * LINE_HEIGHT, 0xFFFFFFFF);
            }
            graphics.disableScissor();

            if (maxScroll > 0) {
                int trackX = bx + getWidth() - 3;
                graphics.fill(trackX, by, trackX + 1, by + getHeight(), 0x35FFFFFF);
                int handleH = Math.max(12, (int) ((double) visibleLines / fileContentLines.size() * getHeight()));
                int travel = Math.max(0, getHeight() - handleH);
                int handleY = by + (int) ((double) contentScrollOffset / maxScroll * travel);
                graphics.fill(trackX - 1, handleY, trackX + 3, handleY + handleH, 0xB0FFFFFF);
            }

            super.draw(graphics, x, y, width, height, mouseX, mouseY, partialTick);
        }
    }

    private static class MdTitle {
        private final String text;
        private final int level;
        private final int lineIndex;

        private MdTitle(String text, int level, int lineIndex) {
            this.text = text;
            this.level = level;
            this.lineIndex = lineIndex;
        }
    }
}
