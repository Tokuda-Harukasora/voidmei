package ui.component;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;

import prog.Application;
import ui.UIBaseElements;
import ui.overlay.model.HUDData;

/**
 * 现代战斗机风格 底部发动机信息条。
 * 
 * 显示: 油门百分比 | 发动机参数
 * 设计为紧贴在 HUD 底部，类似 F-16 HUD 底部数据条。
 * 
 * 带油门进度条 + 数字显示。
 */
public class EngineInfoStrip extends AbstractHUDComponent {

    // --- 尺寸 ---
    private int width;
    private int height;

    // --- 字体 ---
    private Font valueFont;
    private Font labelFont;

    // --- 数据 ---
    private int throttle;      // 0-110%
    private double ias;         // 空速 (用于后续扩展)
    private double fuelLeft;    // 油量显示

    // --- 缓存的笔触 ---
    private BasicStroke barStroke;
    private int cachedLineWidth = -1;

    public EngineInfoStrip() {
    }

    @Override
    public String getId() {
        return "strip.engine";
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(width, height);
    }

    public void setStyleContext(int height, int width, Font valueFont, Font labelFont) {
        this.height = height;
        this.width = width;
        this.valueFont = valueFont;
        this.labelFont = labelFont;
    }

    @Override
    public void onDataUpdate(HUDData data) {
        if (data == null)
            return;
        this.throttle = data.throttle;
        this.ias = data.ias;
    }

    @Override
    public void draw(Graphics2D g2d, int x, int y) {
        if (height <= 0 || width <= 0)
            return;

        int lineWidth = (valueFont != null) ? Math.max(1, valueFont.getSize() / 10) : 1;
        if (lineWidth != cachedLineWidth) {
            barStroke = new BasicStroke(lineWidth, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER);
            cachedLineWidth = lineWidth;
        }

        // --- 布局: 左侧油门条 + 右侧数字 ---
        int barX = x + 2;
        int barW = width / 4;
        int barH = height - 4;
        int barY = y + 2;

        // 油门背景
        g2d.setStroke(barStroke);
        g2d.setColor(Application.colorShadeShape);
        g2d.drawRect(barX, barY, barW, barH);

        // 油门填充 (0-110%, clamp到100%显示)
        double ratio = Math.min(throttle, 100) / 100.0;
        int fillH = (int) (barH * ratio);
        int fillY = barY + barH - fillH;

        // 加力燃烧室 (>100%) 用红色
        if (throttle > 100) {
            // WEP/加力: 顶部全满 + 红色覆盖
            g2d.setColor(Application.colorNum);
            g2d.fillRect(barX + 1, barY + 1, barW - 1, barH - 1);
            g2d.setColor(Application.colorWarning);
            g2d.fillRect(barX + 1, barY + 1, barW - 1, barH / 3);
        } else if (fillH > 0) {
            g2d.setColor(Application.colorNum);
            g2d.fillRect(barX + 1, fillY, barW - 1, fillH);
        }

        // 油门数字百分比
        if (valueFont != null) {
            int textX = barX + barW + 6;
            int textY = y + height / 2 + valueFont.getSize() / 2 - 2;

            String throttleText = throttle + "%";
            if (throttle > 100) {
                throttleText = "WEP";
            }

            g2d.setFont(valueFont);
            g2d.setColor(Application.colorShadeShape);
            g2d.drawString(throttleText, textX + 1, textY + 1);
            g2d.setColor(throttle > 100 ? Application.colorWarning : Application.colorNum);
            g2d.drawString(throttleText, textX, textY);
        }

        // 标签 "THR"
        if (labelFont != null) {
            String label = "THR";
            int labelW = g2d.getFontMetrics(labelFont).stringWidth(label);
            int labelX = barX + barW / 2 - labelW / 2;
            int labelY = barY + barH + labelFont.getSize();

            g2d.setFont(labelFont);
            g2d.setColor(Application.colorShadeShape);
            g2d.drawString(label, labelX + 1, labelY + 1);
            g2d.setColor(Application.colorNum);
            g2d.drawString(label, labelX, labelY);
        }

        // --- 右侧: 空速 (数字) ---
        if (valueFont != null) {
            String iasText = String.format("%.0f", ias);
            int textW = g2d.getFontMetrics(valueFont).stringWidth(iasText);
            int textX = x + width - textW - 4;
            int textY = y + height / 2 + valueFont.getSize() / 2 - 2;

            g2d.setFont(valueFont);
            g2d.setColor(Application.colorShadeShape);
            g2d.drawString(iasText, textX + 1, textY + 1);
            g2d.setColor(Application.colorNum);
            g2d.drawString(iasText, textX, textY);
        }
    }
}
