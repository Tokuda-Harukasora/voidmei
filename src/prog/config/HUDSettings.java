package prog.config;

/**
 * Interface for reading HUD-specific configurations.
 * Decouples the UI layer from underlying key names and parsing logic.
 */
public interface HUDSettings extends OverlaySettings {
    String getNumFont();

    @Override
    int getWindowX(int canvasWidth);

    @Override
    int getWindowY(int canvasHeight);

    @Override
    void saveWindowPosition(double x, double y);

    int getCrosshairScale();

    String getCrosshairName();

    boolean isDisplayCrosshair();

    boolean useTextureCrosshair();

    boolean drawHUDText();

    boolean showAttitudeGauge();

    double getAoAWarningRatio();

    double getAoABarWarningRatio();

    boolean enableFlapAngleBar();

    boolean showSpeedBar();

    boolean drawHudMach();

    boolean isSpeedLabelDisabled();

    boolean isAltitudeLabelDisabled();

    boolean isSEPLabelDisabled();

    boolean isAoADisabled();

    boolean isAttitudeIndicatorInertialMode();

    boolean isGPUCompatibilityMode();

    boolean alwaysShowRadarAltitude();

    /**
     * 现代战斗机 HUD 风格开关。
     * 启用后使用磁带式空速/高度/航向显示，替代经典行布局。
     * @return true = 现代战机HUD风格（默认）
     */
    boolean isModernHudStyle();
}
