package com.example.espoints.client.gui;

import com.example.espoints.capturepoint.CapturePoint;
import com.example.espoints.client.ClientBattleState;
import com.example.espoints.capturepoint.DisplayState;
import com.example.espoints.network.NetworkHandler;
import com.example.espoints.network.RequestCapturePointOverviewMessage;
import com.example.espoints.util.EspetroTeamBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import se.mickelus.mutil.gui.GuiElement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class CapturePointDetailsScreen extends MutilScreen {

    private static final int MARGIN = 18;
    private static final int PANEL_MIN_W = 340;
    private static final int PANEL_MIN_H = 220;
    private static final int HEADER_H = 48;
    private static final int TABLE_HEADER_H = 34;
    private static final int ROW_H = 28;
    private static final int ROW_GAP = 3;
    private static final int SCROLLBAR_RESERVED_W = 8;

    private static final int ROW_BG = 0xAA0E1726;
    private static final int ROW_BG_ALT = 0xAA111C2D;

    private static final List<CapturePoint> overviewPoints = new ArrayList<>();

    private int lastDataHash;
    private int lastWidth;
    private int lastHeight;

    public CapturePointDetailsScreen() {
        super(Component.literal("据点占领情况"));
    }

    public static void syncOverviewFromServer(List<CapturePoint.SerializableCapturePoint> serializedPoints) {
        updateOverview(serializedPoints);
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CapturePointDetailsScreen screen) {
            screen.rebuildForData();
        }
    }

    public static void openFromServer(List<CapturePoint.SerializableCapturePoint> serializedPoints) {
        updateOverview(serializedPoints);
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CapturePointDetailsScreen screen) {
            screen.rebuildForData();
        } else if (mc.screen == null) {
            // A delayed overview response must never replace Espetro's mandatory
            // team, formation, deployment or death-redeployment screens.
            mc.setScreen(new CapturePointDetailsScreen());
        }
    }

    private static void updateOverview(List<CapturePoint.SerializableCapturePoint> serializedPoints) {
        List<CapturePoint> points = new ArrayList<>();
        for (CapturePoint.SerializableCapturePoint sp : serializedPoints) {
            CapturePoint point = new CapturePoint(sp.name, sp.pos1, sp.pos2, sp.batch);
            point.restoreFromSerializable(sp);
            points.add(point);
        }
        points.sort(Comparator.comparingInt(CapturePoint::getBatch).thenComparing(CapturePoint::getName));

        synchronized (overviewPoints) {
            overviewPoints.clear();
            overviewPoints.addAll(points);
        }
    }

    @Override
    public void tick() {
        int dataHash = getDataHash();
        if (dataHash != lastDataHash || this.width != lastWidth || this.height != lastHeight) {
            rebuildForData();
        }
    }

    private void rebuildForData() {
        if (this.minecraft != null && this.minecraft.screen == this && this.width > 0 && this.height > 0) {
            rebuildMutilRoot();
        }
    }

    @Override
    protected void buildMutilRoot(GuiElement root) {
        this.lastWidth = this.width;
        this.lastHeight = this.height;
        this.lastDataHash = getDataHash();

        List<CapturePoint> points = getOverviewPoints();
        int pageX = MARGIN;
        int pageY = MARGIN;
        int pageW = Math.max(PANEL_MIN_W, width - MARGIN * 2);
        int pageH = Math.max(PANEL_MIN_H, height - MARGIN * 2);

        root.addChild(HcrMutilWidgets.panel(pageX, pageY, pageW, pageH,
            HcrMutilWidgets.PANEL, HcrMutilWidgets.BORDER));

        buildHeader(root, pageX, pageY, pageW);

        int contentX = pageX + 18;
        int contentY = pageY + HEADER_H + 12;
        int contentW = pageW - 36;
        int contentH = pageY + pageH - contentY - 18;

        buildTable(root, points, contentX, contentY, contentW, contentH);
    }

    private void buildHeader(GuiElement root, int pageX, int pageY, int pageW) {
        root.addChild(HcrMutilWidgets.text(pageX + 18, pageY + 13, "据点占领情况", HcrMutilWidgets.TEXT));
        root.addChild(HcrMutilWidgets.text(pageX + 18, pageY + 29, "仅显示据点占领状态", HcrMutilWidgets.MUTED));

        HcrMutilWidgets.ActionButton refresh = HcrMutilWidgets.button(
                pageX + pageW - 126, pageY + 14, 50, 18, "刷新", this::requestOverview)
            .setColors(0x00000000, 0x40323B4A, 0x503A3020)
            .setBorderColor(HcrMutilWidgets.BORDER)
            .setTextColor(HcrMutilWidgets.GOLD);
        root.addChild(refresh);

        HcrMutilWidgets.ActionButton close = HcrMutilWidgets.button(
                pageX + pageW - 68, pageY + 14, 50, 18, "关闭", this::onClose)
            .setColors(0x00000000, 0x40323B4A, 0x503A3020)
            .setBorderColor(HcrMutilWidgets.BORDER)
            .setTextColor(HcrMutilWidgets.MUTED);
        root.addChild(close);

        root.addChild(HcrMutilWidgets.rect(pageX + 18, pageY + HEADER_H, pageW - 36, 1, 0x35FFFFFF));
    }

    private void buildTable(GuiElement root, List<CapturePoint> points, int x, int y, int width, int height) {
        root.addChild(HcrMutilWidgets.panel(x, y, width, height,
            HcrMutilWidgets.PANEL_SOFT, HcrMutilWidgets.BORDER));

        int rowW = width - 24 - SCROLLBAR_RESERVED_W;
        boolean narrow = rowW < 520;
        int listX = x + 12;
        int listY = y + TABLE_HEADER_H;
        int listW = width - 24;
        int listH = Math.max(ROW_H, height - TABLE_HEADER_H - 12);

        addTableHeaders(root, x + 12, y + 12, rowW, narrow);
        root.addChild(HcrMutilWidgets.rect(x + 12, y + 29, width - 24, 1, HcrMutilWidgets.BORDER));

        ScrollableList list = new ScrollableList(listX, listY, listW, listH)
            .setScrollStep(ROW_H + ROW_GAP)
            .setAlwaysShowScrollbar(true);
        root.addChild(list);

        if (points.isEmpty()) {
            list.addChild(HcrMutilWidgets.text(10, 10, "暂无据点数据", HcrMutilWidgets.MUTED));
            return;
        }

        int yOffset = 0;
        for (int i = 0; i < points.size(); i++) {
            CapturePoint point = points.get(i);
            addPointRow(list, point, 0, yOffset, rowW, ROW_H, i % 2 == 0, narrow);
            yOffset += ROW_H + ROW_GAP;
        }
    }

    private void addTableHeaders(GuiElement root, int x, int y, int rowW, boolean narrow) {
        int[] cols = getColumns(rowW, narrow);
        root.addChild(HcrMutilWidgets.text(x + cols[0], y, "据点", HcrMutilWidgets.MUTED));
        root.addChild(HcrMutilWidgets.text(x + cols[1], y, "占领情况", HcrMutilWidgets.MUTED));
        if (!narrow) {
            root.addChild(HcrMutilWidgets.text(x + cols[2], y, "占领方", HcrMutilWidgets.MUTED));
        }
        root.addChild(HcrMutilWidgets.text(x + cols[3], y, "进度", HcrMutilWidgets.MUTED));
    }

    private void addPointRow(GuiElement parent, CapturePoint point, int x, int y, int w, int h,
                             boolean even, boolean narrow) {
        int[] cols = getColumns(w, narrow);
        int stateColor = getStateColor(point);
        parent.addChild(HcrMutilWidgets.rect(x, y, w, h, even ? ROW_BG : ROW_BG_ALT));
        parent.addChild(HcrMutilWidgets.rect(x, y, 3, h, stateColor));

        parent.addChild(HcrMutilWidgets.text(x + cols[0], y + 6,
            HcrMutilWidgets.trimToWidth(point.getName(), cols[1] - cols[0] - 8),
            HcrMutilWidgets.TEXT));

        String statusText = narrow ? getOccupancyText(point) : getStatusText(point);
        int statusMaxW = (narrow ? cols[3] : cols[2]) - cols[1] - 8;
        parent.addChild(HcrMutilWidgets.text(x + cols[1], y + 6,
            HcrMutilWidgets.trimToWidth(statusText, statusMaxW), stateColor));

        if (!narrow) {
            parent.addChild(HcrMutilWidgets.text(x + cols[2], y + 6,
                HcrMutilWidgets.trimToWidth(getCaptorText(point),
                    Math.max(48, cols[3] - cols[2] - 8)),
                getCaptorColor(point)));
        }

        String progressText = point.getProgress() + "%";
        int progressTextW = Minecraft.getInstance().font.width(progressText);
        int barX = x + cols[3];
        int barY = y + 9;
        int barW = Math.max(42, w - cols[3] - progressTextW - 8);
        parent.addChild(HcrMutilWidgets.rect(barX, barY, barW, 8, 0xFF27364B));
        int progressW = point.getProgress() * barW / 100;
        if (progressW > 0) {
            parent.addChild(HcrMutilWidgets.rect(barX, barY, progressW, 8, stateColor));
        }
        parent.addChild(HcrMutilWidgets.text(barX + barW + 5, y + 6,
            progressText, HcrMutilWidgets.MUTED));
    }

    private int[] getColumns(int rowW, boolean narrow) {
        if (narrow) {
            return new int[] {10, Math.max(82, rowW * 30 / 100), 0, Math.max(180, rowW - 96)};
        }
        return new int[] {
            10,
            Math.max(110, rowW * 22 / 100),
            Math.max(240, rowW * 45 / 100),
            Math.max(380, rowW - 120)
        };
    }

    private List<CapturePoint> getOverviewPoints() {
        synchronized (overviewPoints) {
            if (!overviewPoints.isEmpty()) {
                return new ArrayList<>(overviewPoints);
            }
        }

        List<CapturePoint> fallback = new ArrayList<>(ClientBattleState.get().points());
        fallback.sort(Comparator.comparingInt(CapturePoint::getBatch).thenComparing(CapturePoint::getName));
        return fallback;
    }

    private int getDataHash() {
        List<CapturePoint> points = getOverviewPoints();
        int hash = points.size();
        for (CapturePoint point : points) {
            hash = 31 * hash + point.getName().hashCode();
            hash = 31 * hash + point.getBatch();
            hash = 31 * hash + point.getDisplayState().hashCode();
            hash = 31 * hash + point.getProgress();
            hash = 31 * hash + Objects.hashCode(point.getCaptorName());
        }
        ClientBattleState state = ClientBattleState.get();
        hash = 31 * hash + state.currentBatch();
        hash = 31 * hash + state.totalBatches();
        return hash;
    }

    private void requestOverview() {
        if (Minecraft.getInstance().getConnection() != null) {
            NetworkHandler.INSTANCE.sendToServer(new RequestCapturePointOverviewMessage());
        }
    }

    private boolean isContested(CapturePoint point) {
        return point.getDisplayState() == DisplayState.CAPTURING_FLAG_SINGLE
            || point.getDisplayState() == DisplayState.CAPTURING_CONTESTED_MULTI
            || point.getDisplayState() == DisplayState.CONTESTED_MULTI
            || point.getDisplayState() == DisplayState.CAPTURING_DOWN;
    }

    private String getStatusText(CapturePoint point) {
        switch (point.getDisplayState()) {
            case CAPTURED:
                return "已占领";
            case CAPTURING_FLAG_SINGLE:
                return "升旗中";
            case CAPTURING_CONTESTED_MULTI:
            case CONTESTED_MULTI:
                return "争夺中";
            case CAPTURING_DOWN:
                return "降旗中";
            case NEUTRAL:
            default:
                return "中立";
        }
    }

    private int getStateColor(CapturePoint point) {
        if (point.getDisplayState() == DisplayState.CAPTURED) {
            return getCaptorColor(point);
        }
        if (isContested(point)) {
            return HcrMutilWidgets.WARNING;
        }
        return HcrMutilWidgets.MUTED;
    }

    private String getCaptorText(CapturePoint point) {
        String captor = point.getCaptorName();
        if (captor == null || captor.isEmpty()) {
            return "-";
        }

        String displayName = EspetroTeamBridge.displayName(captor);
        return displayName == null || displayName.isEmpty() ? "-" : displayName;
    }

    private String getOccupancyText(CapturePoint point) {
        String captor = getCaptorText(point);
        if ("-".equals(captor)) {
            return getStatusText(point);
        }
        return getStatusText(point) + " - " + captor;
    }

    private int getCaptorColor(CapturePoint point) {
        String canonicalTeam = EspetroTeamBridge.canonicalizeTeamName(point.getCaptorName());
        if (EspetroTeamBridge.ATTACK.equals(canonicalTeam)) {
            return HcrMutilWidgets.ATTACK;
        }
        if (EspetroTeamBridge.DEFEND.equals(canonicalTeam)) {
            return HcrMutilWidgets.DEFEND;
        }
        return HcrMutilWidgets.TEXT;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_R) {
            requestOverview();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
