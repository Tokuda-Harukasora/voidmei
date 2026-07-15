package ui.overlay;

import java.awt.Color;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import java.util.ArrayList;

import com.alee.laf.panel.WebPanel;

import prog.Application;
import prog.util.Logger;
import prog.Controller;
import prog.Service;
import prog.event.FlightDataBus;
import prog.event.FlightDataEvent;
import prog.event.FlightDataListener;
import prog.config.HUDSettings;
import ui.WebLafSettings;
import ui.base.DraggableOverlay;
import ui.util.OverlayStyleHelper;

/**
 * MinimalHUD overlay for displaying compact flight information.
 * Being migrated to event-driven architecture.
 */
import ui.overlay.model.HUDData;
import ui.overlay.logic.HUDCalculator;
import ui.component.HUDComponent;

public class MiniHUDOverlay extends DraggableOverlay implements FlightDataListener {

    private static final long serialVersionUID = 1L;

    public MiniHUDOverlay() {
        super();
        setTitle("MiniHUD");
    }

    private MinimalHUDContext ctx;

    // Reactive Components List
    private List<HUDComponent> components = new ArrayList<>();

    int blinkTicks = 1;
    int blinkCheckTicks = 0;
    public boolean warnRH;
    public boolean warnVne;

    private ui.component.CrosshairGauge crosshairGauge;
    private ui.component.FlapAngleBar flapAngleBar;
    private ui.component.WarningOverlay warningOverlay;
    private ui.component.CompassGauge compassGauge;
    private ui.component.AttitudeIndicatorGauge attitudeIndicatorGauge;
    private java.util.List<ui.component.row.HUDRow> hudRows;
    private boolean firstDraw = true;

    // 现代战斗机HUD组件 (F-16风格磁带式仪表)
    private ui.component.AirspeedTape airspeedTape;
    private ui.component.AltitudeTape altitudeTape;
    private ui.component.HeadingTape headingTape;
    private ui.component.VertSpeedIndicator vertSpeedIndicator;
    private ui.component.GLoadIndicator gLoadIndicator;
    private ui.component.EngineInfoStrip engineInfoStrip;

    // HUDSettings 通过 init() 参数传入，不应从 Controller 获取 configService
    private HUDSettings hudSettings;

    public void setFrameOpaque() {
        OverlayStyleHelper.applyTransparentStyle(this);
    }

    public void initpanel() {
        panel.setWebColoredBackground(false);
        panel.setBackground(new Color(0, 0, 0, 0));
    }

    public Boolean blinkX = false;
    public Boolean blinkActing = false;

    public void drawBlinkX(Graphics2D g) {
        // 高度警告标记 - now using reusable component
        if (blinkX && ctx != null) {
            if (warningOverlay != null) {
                warningOverlay.draw(g, 0, 0, ctx.width, ctx.height, blinkActing);
            }
            blinkCheckTicks += 1;
            if (blinkCheckTicks % blinkTicks == 0) {
                blinkActing = !blinkActing;
            }
        }
    }

    public int throttley = 0;
    private ui.component.LinearGauge throttleBar;
    public int OilX = 0;
    public int aoaY = 0;
    public boolean inAction = false;
    public Color throttleColor;
    public Color aoaColor;
    public Color aoaBarColor;
    public int throttleLineWidth = 1;

    // Core state and geometry
    private Controller controller;
    private ui.component.SpeedRatioBar speedRatioBar;
    private WebPanel panel;

    private Service service;
    private String lines[];
    private Container root;

    private String relEnergy;

    public void initPreview(Controller c, HUDSettings settings) {
        Logger.info("MinimalHUD", "initPreview called");
        init(c, null, settings);

        this.getWebRootPaneUI().setTopBg(Application.previewColor);
        this.getWebRootPaneUI().setMiddleBg(Application.previewColor);
        setupDragListeners();
        applyPreviewStyle();
        this.setCursor(null);
        setVisible(true);
    }

    public void saveCurrentPosition() {
        if (hudSettings != null) {
            hudSettings.saveWindowPosition(getLocation().x, getLocation().y);
        }
    }

    public void reinitConfig() {
        Logger.info("MinimalHUD", "reinitConfig called");

        if (hudSettings == null) {
            return;
        }

        // Create Immutable Context
        ctx = MinimalHUDContext.create(hudSettings);

        // 1. Refresh mock data and templates (WYSIWYG support)
        refreshTemplates();

        // 1.5. 重建组件 (支持运行时切换 modernHudStyle)
        initComponentsLayout();

        // Apply dimensions (Initial guess, will be refined by dynamic layout)
        if (hudSettings.isDisplayCrosshair())
            this.setBounds(ctx.windowX, ctx.windowY, ctx.width * 2, ctx.height);
        else
            this.setBounds(ctx.windowX, ctx.windowY, ctx.width, ctx.height);

        // 2. Sync Component State (Style & Visibility) BEFORE Layout
        // (initComponentsLayout 内部已调用, 此处保留用于直接的 reinitConfig 调用路径)
        applyStyleToComponents();
        updateComponents();

        // 3. Setup Layout Engine & Dynamic Sizing
        initModernLayout();

        firstDraw = true;

        repaint();
    }

    private void refreshTemplates() {
        if (hudSettings == null)
            return;

        if (lines == null)
            lines = new String[6];

        String spdPre = hudSettings.isSpeedLabelDisabled() ? "" : "SPD";
        String altPre = hudSettings.isAltitudeLabelDisabled() ? "" : "ALT";
        String sepPre = hudSettings.isSEPLabelDisabled() ? "" : "SEP";

        if (hudSettings.drawHudMach()) {
            lines[0] = String.format("M%5.2f", 0.85);
        } else {
            lines[0] = spdPre + String.format("%5s", "360");
        }
        // Format must match HUDCalculator: radar = "R%5.0f" (R + 5 digits), barometric = "%6.0f" (6 digits)
        if (hudSettings.alwaysShowRadarAltitude()) {
            lines[1] = altPre + String.format("R%5s", "1024");
        } else {
            lines[1] = altPre + String.format("%6s", "1024");
        }
        lines[3] = sepPre + String.format("↑%-4s", "30");
        lines[4] = "G" + String.format("%5s", "2.0");
        if (hudSettings.enableFlapAngleBar()) {
            lines[2] = String.format("%4s", "");
        } else {
            lines[2] = "F" + String.format("%3s", "100");
        }
        lines[2] += "BRK";
        lines[2] += "GEAR";
        throttley = 100;
        aoaY = 10;
        throttleColor = Application.colorShadeShape;
        aoaColor = Application.colorNum;
        aoaBarColor = Application.colorNum;
        lineAoA = String.format("α%3.0f", 20.0);
        relEnergy = "E114514";

        if (hudSettings.isAoADisabled()) {
            lineAoA = "";
            relEnergy = "";
        }

        // Push new templates to existing components immediately
        if (hudRows != null && hudRows.size() >= 5) {
            ((ui.component.row.HUDAkbRow) hudRows.get(0)).setTemplate(lines[0], lineAoA);
            ((ui.component.row.HUDEnergyRow) hudRows.get(1)).setTemplate(lines[1], relEnergy);
            ((ui.component.row.HUDTextRow) hudRows.get(2)).setTemplate(lines[2]);
            ((ui.component.row.HUDTextRow) hudRows.get(3)).setTemplate(lines[3]);
            ((ui.component.row.HUDManeuverRow) hudRows.get(4)).setTemplate(lines[4]);
        }
    }

    /**
     * 初始化 MiniHUD overlay。
     *
     * @param c Controller 引用，用于 FM 数据访问 (getBlkx())
     * @param s Service 引用，用于遥测数据
     * @param settings HUDSettings 配置接口，直接传入而不是从 Controller 获取
     */
    public void init(Controller c, Service s, HUDSettings settings) {
        Logger.info("MinimalHUD", "init called");
        service = s;
        controller = c;
        // HUDSettings 通过参数直接传入，遵循解耦原则
        this.hudSettings = settings;
        this.setLayout(new java.awt.BorderLayout());
        setFrameOpaque();

        reinitConfig();

        // refreshTemplates() is now called inside reinitConfig()
        // No need to repeat the lines[] initialization here.

        if (ctx != null && aoaY > ctx.rightDraw)
            aoaY = ctx.rightDraw;
        aoaColor = Application.colorNum;
        aoaBarColor = Application.colorNum;

        initComponentsLayout();

        panel = new WebPanel() {
            private static final long serialVersionUID = -9061280572815010060L;

            public void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g;
                g2d.setPaintMode();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, Application.graphAASetting);
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, Application.textAASetting);
                g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                        RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);
                g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_SPEED);

                if (modernLayout != null) {
                    modernLayout.doLayout();
                    modernLayout.render(g2d);
                }

                drawBlinkX(g2d);
            }
        };
        panel.setOpaque(false);
        panel.setWebColoredBackground(false);

        this.setContentPane(panel);

        blinkTicks = (int) ((1000 / controller.serviceLoopIntervalMs) >> 3);
        if (blinkTicks == 0)
            blinkTicks = 1;

        refreshInterval = controller.serviceLoopIntervalMs; // Sync with service loop interval

        setTitle("miniHUD");
        WebLafSettings.setWindowOpaque(this);
        root = this.getContentPane();
        // this.createBufferStrategy(2);

        // Subscribe to events for game mode
        if (service != null) {
            subscribeToEvents();
            setVisible(true);
        }

        updateComponents();
    }

    public long hudCheckMili;
    private String lineAoA;

    double realSpdPitch;

    private double maneuverIndex;
    private int maneuverIndexLen;

    private int maneuverIndexLen30;
    private int maneuverIndexLen10;
    private int maneuverIndexLen20;
    private int maneuverIndexLen40;
    private int maneuverIndexLen50;
    private boolean disableAttitude;

    /**
     * Legacy update logic.
     * 
     * @deprecated Replaced by event-driven implementation in updateFromEvent().
     */
    public void updateString() {
        // No-op. Logic moved to updateFromEvent().
        if (root != null)
            root.repaint();
    }

    private void updateComponents() {
        boolean textVisible = hudSettings.drawHUDText();

        boolean enableFlapBar = hudSettings.enableFlapAngleBar();
        if (flapAngleBar != null) {
            flapAngleBar.setVisible(textVisible && enableFlapBar);
        }
        boolean showAttitude = hudSettings.showAttitudeGauge();
        if (compassGauge != null) {
            compassGauge.setVisible(textVisible && !showAttitude);
        }
        if (attitudeIndicatorGauge != null) {
            attitudeIndicatorGauge.setVisible(textVisible && showAttitude && !disableAttitude);
        }
        if (crosshairGauge != null) {
            crosshairGauge.setVisible(hudSettings.isDisplayCrosshair());
            // Dynamic position based on current Width/CrossX
            if (ctx != null) {
                // Position handled by ModernHUDLayoutEngine
            }
        }
        boolean showSpeed = hudSettings.showSpeedBar();
        boolean modernStyle = (hudSettings != null) && hudSettings.isModernHudStyle();
        if (throttleBar != null) {
            // 现代模式下始终显示油门条, 经典模式按 showSpeed 切换
            throttleBar.setVisible(textVisible && (modernStyle || !showSpeed));
        }
        if (speedRatioBar != null) {
            speedRatioBar.setVisible(textVisible && (modernStyle || showSpeed));
        }
        if (hudRows != null) {
            for (ui.component.HUDComponent row : hudRows) {
                row.setVisible(textVisible);
            }
        }

        if (hudRows != null && hudRows.size() >= 5) {
            // Row 0, 1: Only update in preview mode (service == null)
            // In game mode, they are updated via onDataUpdate() from FlightDataEvent
            if (service == null) {
                // HUDAkbRow.update(text, isWarning, aoaText, aoaY, aoaColor, aoaBarColor)
                ((ui.component.row.HUDAkbRow) hudRows.get(0)).update(lines[0], false, lineAoA, aoaY, aoaColor, aoaBarColor);
                // 能量颜色已统一使用 Application.colorNum，不再需要传入颜色参数
                ((ui.component.row.HUDEnergyRow) hudRows.get(1)).update(lines[1], false, relEnergy);
            }

            // Row 2: Standard (Flaps/Gear)
            ((ui.component.row.HUDTextRow) hudRows.get(2)).update(lines[2], inAction);
            // Row 3: Standard (SEP)
            ((ui.component.row.HUDTextRow) hudRows.get(3)).update(lines[3], false);
            // Row 4: Maneuver (G)
            ((ui.component.row.HUDManeuverRow) hudRows.get(4)).update(lines[4], false, maneuverIndex,
                    maneuverIndexLen, maneuverIndexLen10, maneuverIndexLen20, maneuverIndexLen30,
                    maneuverIndexLen40, maneuverIndexLen50);

            for (ui.component.row.HUDRow row : hudRows) {
                row.setVisible(textVisible);
            }
        }

        if (throttleBar != null) {
            int throttleValue = 0;
            if (service != null && service.sState != null) {
                throttleValue = service.sState.throttle;
            }
            throttleBar.update(throttleValue, String.format("%3d", throttleValue));
        }
    }

    public void drawTick() {

        // updateString();
        if (modernLayout != null) {
            root.repaint();
        }
    }

    // --- Event-Driven Update ---

    // Throttling for refresh rate
    private static final long DEFAULT_REFRESH_INTERVAL = 100; // ms
    private long refreshInterval = DEFAULT_REFRESH_INTERVAL;
    private long lastRefreshTime = 0;

    @Override
    public void onFlightData(FlightDataEvent event) {
        // Throttling prevents EDT task accumulation when events arrive faster than processing
        long now = System.currentTimeMillis();
        if (now - lastRefreshTime < refreshInterval) {
            return; // Skip this update, too soon
        }
        lastRefreshTime = now;

        javax.swing.SwingUtilities.invokeLater(() -> {
            updateFromEvent(event);
            if (root != null)
                root.repaint();
        });
    }

    private void updateFromEvent(FlightDataEvent event) {
        if (ctx == null)
            return;

        // 1. Get pre-computed HUDData from Service thread (reduces EDT latency)
        HUDData data = event.getHudData();

        // Fallback: calculate on EDT if pre-computed data is not available
        // This handles preview mode and edge cases where Service hasn't computed yet
        if (data == null) {
            data = HUDCalculator.calculate(event, service, controller.getBlkx(), hudSettings, ctx);
        }

        // 2. Dispatch to Reactive Components
        for (HUDComponent comp : components) {
            comp.onDataUpdate(data);
        }

        // 3. Update Legacy Components (Bridge) & Global State
        warnVne = data.warnVne;
        warnRH = data.warnAltitude;
        blinkX = event.getPayload().fatalWarn;

        if (hudRows != null && hudRows.size() >= 5) {
            // Let's call a legacy bridge method explicitly
            updateLegacyComponents(data);
        }

        if (throttleBar != null) {
            throttleBar.update(data.throttle, String.format("%3d", data.throttle));
        }
    }

    private void updateLegacyComponents(HUDData data) {
        if (hudRows == null || hudRows.size() < 5)
            return;

        // Row 0, 1 are refactored (Akb, Energy). They use onDataUpdate.
        // Row 2: Flaps/Gear
        ((ui.component.row.HUDTextRow) hudRows.get(2)).update(data.mechanizationStr, data.warnConfiguration);
        // Row 3: SEP
        ((ui.component.row.HUDTextRow) hudRows.get(3)).update(data.sepStr, false);
        // Row 4: Maneuver
        // ManeuverRow update signature is complex.
        ((ui.component.row.HUDManeuverRow) hudRows.get(4)).update(data.maneuverStateStr, false, data.maneuverIndex,
                maneuverIndexLen, maneuverIndexLen10, maneuverIndexLen20, maneuverIndexLen30,
                maneuverIndexLen40, maneuverIndexLen50);
        // Note: maneuverIndexLen variables are member fields of MinimalHUD calculated
        // in legacy loop.
        // We need to recalculate them or move calculation to Calculator.
        // Ideally Calculator provides "maneuverBarLength" or similar?
        // Or we calculate here based on data.maneuverIndex?
        if (ctx != null) {
            int rightDraw = ctx.rightDraw;
            maneuverIndexLen = (int) Math.round(data.maneuverIndex / 0.5 * rightDraw);
            maneuverIndexLen10 = (int) Math.round(0.1 / 0.5 * rightDraw);
            maneuverIndexLen20 = (int) Math.round(0.2 / 0.5 * rightDraw);
            maneuverIndexLen30 = (int) Math.round(0.3 / 0.5 * rightDraw);
            maneuverIndexLen40 = (int) Math.round(0.4 / 0.5 * rightDraw);
            maneuverIndexLen50 = (int) Math.round(0.5 / 0.5 * rightDraw);
        }
    }

    /**
     * Subscribe to flight data events.
     */
    public void subscribeToEvents() {
        FlightDataBus.getInstance().register(this);
    }

    /**
     * Unsubscribe from flight data events.
     */
    public void unsubscribeFromEvents() {
        FlightDataBus.getInstance().unregister(this);
    }

    @Override
    public void run() {
        // Event-driven - no polling needed
        // Kept for compatibility with DraggableOverlay interface
    }

    @Override
    public void dispose() {
        unsubscribeFromEvents();
        super.dispose();
    }

    protected void initComponentsLayout() {
        components.clear(); // Ensure list is clean on re-init

        // 检查是否启用现代战斗机HUD风格
        boolean modernStyle = (hudSettings != null) && hudSettings.isModernHudStyle();

        // 0. Aux Overlays (通用)
        warningOverlay = new ui.component.WarningOverlay();
        flapAngleBar = new ui.component.FlapAngleBar();
        components.add(flapAngleBar);

        if (modernStyle) {
            // ========================================
            // 现代战斗机 HUD 风格 (F-16 / F-35 磁带式)
            // ========================================

            // 左侧空速带
            airspeedTape = new ui.component.AirspeedTape();
            components.add(airspeedTape);

            // 右侧高度带
            altitudeTape = new ui.component.AltitudeTape();
            components.add(altitudeTape);

            // 顶部航向带
            headingTape = new ui.component.HeadingTape();
            components.add(headingTape);

            // 右侧升降率
            vertSpeedIndicator = new ui.component.VertSpeedIndicator();
            components.add(vertSpeedIndicator);

            // G值指示器
            gLoadIndicator = new ui.component.GLoadIndicator();
            components.add(gLoadIndicator);

            // 底部发动机信息条
            engineInfoStrip = new ui.component.EngineInfoStrip();
            components.add(engineInfoStrip);

            // 中央姿态仪 (保留作为俯仰参考)
            attitudeIndicatorGauge = new ui.component.AttitudeIndicatorGauge();
            components.add(attitudeIndicatorGauge);

            // 准星
            crosshairGauge = new ui.component.CrosshairGauge();
            components.add(crosshairGauge);

            // 速度比条和油门条 (辅助信息，放左下)
            speedRatioBar = new ui.component.SpeedRatioBar();
            components.add(speedRatioBar);
            throttleBar = new ui.component.LinearGauge("ThrottleBar", 110, true, false);
            components.add(throttleBar);

            // 现代模式下不使用圆形罗盘和行布局
            compassGauge = null;
            hudRows = null;

        } else {
            // ========================================
            // 经典 HUD 风格 (原有行布局)
            // ========================================

            speedRatioBar = new ui.component.SpeedRatioBar();
            components.add(speedRatioBar);

            compassGauge = new ui.component.CompassGauge(ctx.roundCompass);
            components.add(compassGauge);

            attitudeIndicatorGauge = new ui.component.AttitudeIndicatorGauge();
            components.add(attitudeIndicatorGauge);

            crosshairGauge = new ui.component.CrosshairGauge();
            components.add(crosshairGauge);

            hudRows = new java.util.ArrayList<>();

            ui.component.row.HUDAkbRow row0 = new ui.component.row.HUDAkbRow(0, ctx.drawFont, ctx.hudFontSize,
                    ctx.drawFontSmall, ctx.rightDraw, ctx.lineWidth);
            row0.setTemplate(lines[0], lineAoA);
            hudRows.add(row0);

            ui.component.row.HUDEnergyRow row1 = new ui.component.row.HUDEnergyRow(1, ctx.drawFont, ctx.hudFontSize,
                    ctx.drawFontSmall, ctx.rightDraw);
            row1.setTemplate(lines[1], relEnergy);
            hudRows.add(row1);

            ui.component.row.HUDTextRow row2 = new ui.component.row.HUDTextRow(2, ctx.drawFont, ctx.hudFontSize);
            row2.setTemplate(lines[2]);
            hudRows.add(row2);

            ui.component.row.HUDTextRow row3 = new ui.component.row.HUDTextRow(3, ctx.drawFont, ctx.hudFontSize);
            row3.setTemplate(lines[3]);
            hudRows.add(row3);

            ui.component.row.HUDManeuverRow row4 = new ui.component.row.HUDManeuverRow(4, ctx.drawFont, ctx.hudFontSize,
                    ctx.rightDraw, ctx.halfLine, ctx.lineWidth,
                    ctx.strokeThick, ctx.strokeThin);
            row4.setTemplate(lines[4]);
            hudRows.add(row4);

            for (ui.component.row.HUDRow row : hudRows) {
                components.add(row);
            }

            throttleBar = new ui.component.LinearGauge("ThrottleBar", 110, true, false);
            components.add(throttleBar);

            // 清空现代模式组件引用
            airspeedTape = null;
            altitudeTape = null;
            headingTape = null;
            vertSpeedIndicator = null;
            gLoadIndicator = null;
            engineInfoStrip = null;
        }

        // Ensure everything is styled and updated before layout & sizing
        applyStyleToComponents();
        updateComponents();

        initModernLayout();
    }

    private void applyStyleToComponents() {
        if (ctx == null)
            return;

        if (speedRatioBar != null) {
            // Width: similar to throttle bar or slightly thinner?
            int w = (int) (ctx.hudFontSize * 0.25);
            int h = (int) (ctx.hudFontSize * 5.5);
            if (w < 6)
                w = 6;
            speedRatioBar.setStyleContext(w, h, ctx.drawFontSSmall);
        }

        if (crosshairGauge != null) {
            if (hudSettings.useTextureCrosshair()) {
                // Use loaded image from Context if available
                crosshairGauge.setTextureStyle(true, ctx.crosshairImageScaled, ctx.crossScale);
            } else {
                crosshairGauge.setStyleContext(hudSettings.getCrosshairScale());
            }
        }
        if (flapAngleBar != null) {
            // Dynamic width
            int responsiveWidth = (int) (ctx.hudFontSize * 6);
            flapAngleBar.setStyleContext(responsiveWidth, ctx.lineWidth + 2, ctx.drawFontSmall);
        }
        if (compassGauge != null) {
            compassGauge.setStyleContext(ctx.roundCompass, ctx.lineWidth, ctx.hudFontSize, ctx.hudFontSizeSmall,
                    ctx.drawFontSmall);
            compassGauge.setInertialMode(hudSettings.isAttitudeIndicatorInertialMode());
        }
        if (attitudeIndicatorGauge != null) {
            attitudeIndicatorGauge.setStyleContext(ctx.compassDiameter, ctx.compassRadius, ctx.compassInnerMarkRadius,
                    ctx.lineWidth, ctx.halfLine, ctx.drawFontSmall);
            attitudeIndicatorGauge.setInertialMode(hudSettings.isAttitudeIndicatorInertialMode());
        }
        // Synchronize styles for Rows
        if (hudRows != null && hudRows.size() >= 5) {
            ((ui.component.row.HUDAkbRow) hudRows.get(0)).setStyle(ctx.drawFont, ctx.hudFontSize, ctx.drawFontSmall,
                    ctx.rightDraw,
                    ctx.lineWidth, (int) ctx.aoaLength);
            ((ui.component.row.HUDEnergyRow) hudRows.get(1)).setStyle(ctx.drawFont, ctx.hudFontSize, ctx.drawFontSmall,
                    ctx.rightDraw);
            ((ui.component.row.HUDTextRow) hudRows.get(2)).setStyle(ctx.drawFont, ctx.hudFontSize);
            ((ui.component.row.HUDTextRow) hudRows.get(3)).setStyle(ctx.drawFont, ctx.hudFontSize);
            ((ui.component.row.HUDManeuverRow) hudRows.get(4)).setStyle(ctx.drawFont, ctx.hudFontSize, ctx.rightDraw,
                    ctx.halfLine, ctx.lineWidth, ctx.strokeThick, ctx.strokeThin);
        }

        if (throttleBar != null) {
            // Re-calc explicit height for ThrottleBar if needed or use existing
            // throttley_max
            // Standardizing to relative size: 4.8 lines high (closer to legacy 4.75)
            int responsiveHeight = (int) (ctx.hudFontSize * 4.8);
            throttleBar.setStyleContext(responsiveHeight, ctx.barWidth, ctx.drawFontSSmall, ctx.drawFontSSmall);
        }

        // --- 现代战斗机HUD组件样式 ---
        if (airspeedTape != null) {
            int tapeH = (int) (ctx.hudFontSize * 10);
            int tapeW = (int) (ctx.hudFontSize * 3);
            airspeedTape.setStyleContext(tapeH, tapeW, ctx.drawFontSmall, ctx.drawFont, ctx.drawFontSSmall);
        }
        if (altitudeTape != null) {
            int tapeH = (int) (ctx.hudFontSize * 10);
            int tapeW = (int) (ctx.hudFontSize * 3);
            altitudeTape.setStyleContext(tapeH, tapeW, ctx.drawFontSmall, ctx.drawFont, ctx.drawFontSSmall);
        }
        if (headingTape != null) {
            int tapeH = (int) (ctx.hudFontSize * 2);
            int tapeW = (int) (ctx.hudFontSize * 18);
            headingTape.setStyleContext(tapeH, tapeW, ctx.drawFontSmall, ctx.drawFont);
        }
        if (vertSpeedIndicator != null) {
            int vsiH = (int) (ctx.hudFontSize * 4);
            int vsiW = (int) (ctx.hudFontSize * 1.5);
            vertSpeedIndicator.setStyleContext(vsiH, vsiW, ctx.drawFontSSmall);
        }
        if (gLoadIndicator != null) {
            int gH = (int) (ctx.hudFontSize * 2.5);
            int gW = (int) (ctx.hudFontSize * 3);
            gLoadIndicator.setStyleContext(gH, gW, ctx.drawFont, ctx.drawFontSmall);
        }
        if (engineInfoStrip != null) {
            int stripH = (int) (ctx.hudFontSize * 2.5);
            int stripW = (int) (ctx.hudFontSize * 12);
            engineInfoStrip.setStyleContext(stripH, stripW, ctx.drawFontSmall, ctx.drawFontSSmall);
        }
    }

    // --- Modern Layout Engine Integration ---
    private ui.layout.ModernHUDLayoutEngine modernLayout;

    private void initModernLayout() {
        boolean modernStyle = (hudSettings != null) && hudSettings.isModernHudStyle();

        // 现代模式下如果组件尚未创建，暂时跳过布局 (等待 initComponentsLayout 完成)
        if (modernStyle && airspeedTape == null) {
            Logger.info("MinimalHUD", "initModernLayout: modern components not ready yet, deferring");
            modernLayout = null;
            return;
        }

        // 经典模式下如果行组件尚未创建，暂时跳过
        if (!modernStyle && (hudRows == null || hudRows.isEmpty())) {
            Logger.info("MinimalHUD", "initModernLayout: classic rows not ready yet, deferring");
            modernLayout = null;
            return;
        }

        boolean showCrosshair = hudSettings.isDisplayCrosshair();
        int layoutWidth = modernStyle ? ctx.width : (showCrosshair ? ctx.width * 2 : ctx.width);
        // 现代模式需要更大的画布来容纳磁带式仪表
        int layoutHeight = modernStyle ? (int)(ctx.hudFontSize * 16) : ctx.height;
        Logger.info("MinimalHUD", "initModernLayout: modernStyle=" + modernStyle + ", layoutWidth=" + layoutWidth);

        modernLayout = new ui.layout.ModernHUDLayoutEngine(layoutWidth, layoutHeight);

        if (hudSettings != null) {
            modernLayout.setDebug(hudSettings.getBool("enableLayoutDebug", false));
        }
        modernLayout.setLineHeight(ctx.hudFontSize);

        if (components.isEmpty())
            return;

        Logger.info("MinimalHUD", "initModernLayout: Adding nodes. Components: " + components.size());

        if (modernStyle) {
            initModernFighterLayout();
        } else {
            initClassicLayout();
        }

        modernLayout.doLayout();
        modernLayout.applyAutoSizing(this, LAYOUT_PADDING);
        modernLayout.logTopology();
    }

    /**
     * 现代战斗机 HUD 布局 (F-16 风格).
     * 
     * 布局结构:
     * ┌─────────────────────────────────────┐
     * │         HEADING TAPE (top)          │
     * ├──────┬──────────────────┬───────────┤
     * │      │                  │  ALT TAPE │
     * │ IAS  │    ATTITUDE      │  + VSI   │
     * │ TAPE │    (center)      │           │
     * │      │                  │           │
     * │ G+THR│    CROSSHAIR     │           │
     * ├──────┴──────────────────┴───────────┤
     * │        ENGINE STRIP (bottom)        │
     * └─────────────────────────────────────┘
     */
    private void initModernFighterLayout() {
        // 内缩边距: 0 = 贴边 (可通过调整此值控制左右仪表内缩距离)
        double marginUnits = 0;
        // 50px 换算为布局单位 (1单位 = hudFontSize px)
        // 微调偏移量 (单位: px), 1单位 = hudFontSize px
        double pxOffset = 200.0 / ctx.hudFontSize;

        // --- 1. 顶部航向带 ---
        ui.layout.HUDLayoutNode headingNode = new ui.layout.HUDLayoutNode("heading", headingTape);
        headingNode.setRelativePosition(0.5, 0.3)
                .setAnchors(ui.layout.Anchor.TOP_LEFT, ui.layout.Anchor.TOP_LEFT);
        modernLayout.addNode(headingNode);

        // --- 2. 左侧空速带 (向内缩 marginUnits) ---
        ui.layout.HUDLayoutNode airspeedNode = new ui.layout.HUDLayoutNode("airspeed", airspeedTape);
        airspeedNode.setParent(headingNode)
                .setRelativePosition(marginUnits, 1.5)
                .setAnchors(ui.layout.Anchor.BOTTOM_LEFT, ui.layout.Anchor.TOP_LEFT);
        modernLayout.addNode(airspeedNode);

        // --- 3. 右侧高度带 (向内缩 marginUnits) ---
        ui.layout.HUDLayoutNode altitudeNode = new ui.layout.HUDLayoutNode("altitude", altitudeTape);
        altitudeNode.setParent(headingNode)
                .setRelativePosition(-marginUnits, 1.5)
                .setAnchors(ui.layout.Anchor.BOTTOM_RIGHT, ui.layout.Anchor.TOP_RIGHT);
        modernLayout.addNode(altitudeNode);

        // --- 4. 右侧升降率 (紧邻高度带右侧) ---
        ui.layout.HUDLayoutNode vsiNode = new ui.layout.HUDLayoutNode("vsi", vertSpeedIndicator);
        vsiNode.setParent(altitudeNode)
                .setRelativePosition(0.3, 0)
                .setAnchors(ui.layout.Anchor.TOP_RIGHT, ui.layout.Anchor.TOP_LEFT);
        modernLayout.addNode(vsiNode);

        // --- 5. 中央姿态仪 (居中, 偏左50px) ---
        ui.layout.HUDLayoutNode attitudeNode = new ui.layout.HUDLayoutNode("attitude", attitudeIndicatorGauge);
        attitudeNode.setRelativePosition(-pxOffset, 0.5)
                .setAnchors(ui.layout.Anchor.CENTER, ui.layout.Anchor.CENTER);
        modernLayout.addNode(attitudeNode);

        // --- 6. 准星 (居中) ---
        if (hudSettings.isDisplayCrosshair()) {
            ui.layout.HUDLayoutNode crosshairNode = new ui.layout.HUDLayoutNode("crosshair", crosshairGauge);
            crosshairNode.setRelativePosition(0, 0)
                    .setAnchors(ui.layout.Anchor.CENTER, ui.layout.Anchor.CENTER);
            modernLayout.addNode(crosshairNode);
        }

        // --- 7. 襟翼角条 (与空速带对齐) ---
        ui.layout.HUDLayoutNode flapNode = new ui.layout.HUDLayoutNode("flap", flapAngleBar);
        flapNode.setParent(headingNode)
                .setRelativePosition(marginUnits, 1.0)
                .setAnchors(ui.layout.Anchor.BOTTOM_LEFT, ui.layout.Anchor.TOP_LEFT);
        modernLayout.addNode(flapNode);

        // --- 8. G值指示器 (空速带下方) ---
        ui.layout.HUDLayoutNode gNode = new ui.layout.HUDLayoutNode("gload", gLoadIndicator);
        gNode.setParent(airspeedNode)
                .setRelativePosition(-100.0 / ctx.hudFontSize, 0.3)
                .setAnchors(ui.layout.Anchor.BOTTOM_LEFT, ui.layout.Anchor.TOP_LEFT);
        modernLayout.addNode(gNode);

        // --- 9. 底部发动机信息条 (下移) ---
        ui.layout.HUDLayoutNode engineNode = new ui.layout.HUDLayoutNode("engine", engineInfoStrip);
        engineNode.setRelativePosition(marginUnits, pxOffset)
                .setAnchors(ui.layout.Anchor.BOTTOM_LEFT, ui.layout.Anchor.BOTTOM_LEFT);
        modernLayout.addNode(engineNode);

        // --- 10. 油门条 (空速带右侧) ---
        ui.layout.HUDLayoutNode throttleNode = new ui.layout.HUDLayoutNode("throttle", throttleBar);
        throttleNode.setParent(airspeedNode)
                .setRelativePosition(0.5, 0)
                .setAnchors(ui.layout.Anchor.BOTTOM_RIGHT, ui.layout.Anchor.BOTTOM_LEFT);
        modernLayout.addNode(throttleNode);
    }

    /**
     * 经典 HUD 布局 (原有行布局).
     */
    private void initClassicLayout() {
        if (hudRows == null || hudRows.isEmpty())
            return;

        // Row 0 (New Anchor for Left Block)
        ui.layout.HUDLayoutNode row0 = new ui.layout.HUDLayoutNode("row0", hudRows.get(0));
        row0.setRelativePosition(2.1, 3.5)
                .setAnchors(ui.layout.Anchor.TOP_LEFT, ui.layout.Anchor.TOP_LEFT);
        modernLayout.addNode(row0);

        // Flap Bar (Child of Row 0)
        ui.layout.HUDLayoutNode flapNode = new ui.layout.HUDLayoutNode("flap", flapAngleBar);
        flapNode.setParent(row0)
                .setRelativePosition(0, -0.1)
                .setAnchors(ui.layout.Anchor.TOP_LEFT, ui.layout.Anchor.BOTTOM_LEFT);
        modernLayout.addNode(flapNode);

        // Rows Chain & Right-Side Attachments
        ui.layout.HUDLayoutNode prevRow = row0;
        ui.layout.HUDLayoutNode row2 = null;
        ui.layout.HUDLayoutNode row4 = null;

        for (int i = 1; i < hudRows.size(); i++) {
            ui.layout.HUDLayoutNode rowNode = new ui.layout.HUDLayoutNode("row" + i, hudRows.get(i));
            rowNode.setParent(prevRow)
                    .setRelativePosition(0, 0.1)
                    .setAnchors(ui.layout.Anchor.BOTTOM_LEFT, ui.layout.Anchor.TOP_LEFT);
            modernLayout.addNode(rowNode);
            prevRow = rowNode;

            if (i == 2) {
                row2 = rowNode;
            } else if (i == 4) {
                row4 = rowNode;
            }
        }

        // Right Side Instruments
        if (row2 != null) {
            ui.layout.HUDLayoutNode attitudeNode = new ui.layout.HUDLayoutNode("attitude", attitudeIndicatorGauge);
            attitudeNode.setParent(row2)
                    .setRelativePosition(0, 0.5)
                    .setAnchors(ui.layout.Anchor.BOTTOM_RIGHT, ui.layout.Anchor.TOP_RIGHT);
            modernLayout.addNode(attitudeNode);

            if (compassGauge != null) {
                ui.layout.HUDLayoutNode compassNode = new ui.layout.HUDLayoutNode("compass", compassGauge);
                compassNode.setParent(row2)
                        .setRelativePosition(0, 0.1)
                        .setAnchors(ui.layout.Anchor.BOTTOM_RIGHT, ui.layout.Anchor.TOP_RIGHT);
                modernLayout.addNode(compassNode);
            }
        }

        // SpeedRatioBar
        if (row4 != null && speedRatioBar != null) {
            ui.layout.HUDLayoutNode speedBarNode = new ui.layout.HUDLayoutNode("speedBar", speedRatioBar);
            speedBarNode.setParent(row4)
                    .setRelativePosition(-0.3, 0)
                    .setAnchors(ui.layout.Anchor.BOTTOM_LEFT, ui.layout.Anchor.BOTTOM_RIGHT);
            modernLayout.addNode(speedBarNode);
        }

        // Throttle Bar
        if (row4 != null && throttleBar != null) {
            ui.layout.HUDLayoutNode throttleNode = new ui.layout.HUDLayoutNode("throttle", throttleBar);
            throttleNode.setParent(row4)
                    .setRelativePosition(-0.3, 0)
                    .setAnchors(ui.layout.Anchor.BOTTOM_LEFT, ui.layout.Anchor.BOTTOM_RIGHT);
            modernLayout.addNode(throttleNode);
        }

        // Crosshair
        if (hudSettings.isDisplayCrosshair() && crosshairGauge != null) {
            ui.layout.HUDLayoutNode crosshairNode = new ui.layout.HUDLayoutNode("crosshair", crosshairGauge);
            crosshairNode.setRelativePosition(0, 0)
                    .setAnchors(ui.layout.Anchor.MIDDLE_RIGHT, ui.layout.Anchor.MIDDLE_RIGHT);
            modernLayout.addNode(crosshairNode);
        }
    }

    private static final int LAYOUT_PADDING = 45;
}
