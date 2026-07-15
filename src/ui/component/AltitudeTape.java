package ui.component;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;

import prog.Application;
import ui.overlay.model.HUDData;

/**
 * 现代战斗机风格 右侧垂直高度带 (F-16 / F-35 style altitude tape).
 * 
 * 设计特点:
 * - 垂直刻度带，当前高度指针固定在 40% 高度处
 * - 主刻度每 500m，副刻度每 100m
 * - 底部显示精确数字高度（气压高度）
 * - 低空时显示雷达高度 (RALT)
 */
public class AltitudeTape extends AbstractHUDComponent {

    // --- 尺寸 ---
    private int width;
    private int height;

    // --- 字体 ---
    private Font numFont;       // 刻度数字字体
    private Font valueFont;     // 精确数值字体
    private Font unitFont;      // 单位/标签字体

    // --- 数据 ---
    private double altitude;     // 气压高度 m
    private double radioAltitude; // 雷达高度 m
    private boolean warnAltitude; // 低空警告

    // --- 缓存的笔触 ---
    private BasicStroke tickMajorStroke;
    private BasicStroke tickMinorStroke;
    private BasicStroke caretStroke;
    private BasicStroke frameStroke;
    private int cachedLineWidth = -1;

    // 刻度常量
    private static final int MAJOR_TICK_INTERVAL = 500;  // 主刻度每 500m
    private static final int MINOR_TICK_INTERVAL = 100;  // 副刻度每 100m
    private static final double CARET_POS_RATIO = 0.40;  // 指针位置在 40% 高度处
    private static final double VISIBLE_RANGE = 2000.0;  // 可见范围 ±1000m

    public AltitudeTape() {
    }

    @Override
    public String getId() {
        return "tape.altitude";
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(width, height);
    }

    public void setStyleContext(int height, int width, Font numFont, Font valueFont, Font unitFont) {
        this.height = height;
        this.width = width;
        this.numFont = numFont;
        this.valueFont = valueFont;
        this.unitFont = unitFont;
    }

    @Override
    public void onDataUpdate(HUDData data) {
        if (data == null)
            return;
        this.altitude = data.altitude;
        this.radioAltitude = data.radioAltitude;
        this.warnAltitude = data.warnAltitude;
    }

    @Override
    public void draw(Graphics2D g2d, int x, int y) {
        if (height <= 0 || width <= 0)
            return;

        int lineWidth = (numFont != null) ? Math.max(1, numFont.getSize() / 12) : 1;
        if (lineWidth != cachedLineWidth) {
            tickMajorStroke = new BasicStroke(lineWidth + 1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
            tickMinorStroke = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
            caretStroke = new BasicStroke(lineWidth + 2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            frameStroke = new BasicStroke(lineWidth, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER);
            cachedLineWidth = lineWidth;
        }

        int caretY = y + (int) (height * CARET_POS_RATIO);
        double scale = height / VISIBLE_RANGE;
        double rangeHalf = VISIBLE_RANGE / 2.0;
        double altMin = Math.max(0, altitude - rangeHalf);
        double altMax = altitude + rangeHalf;

        // --- 绘制刻度带背景框 ---
        // 左侧边框线（数字在左侧，和空速带对称）
        g2d.setStroke(frameStroke);
        g2d.setColor(Application.colorNum);
        g2d.drawLine(x, y, x, y + height);
        // 右侧细线
        int frameRight = x + width;
        g2d.setColor(Application.colorShadeShape);
        g2d.drawLine(frameRight, y, frameRight, y + height);

        // --- 主刻度 (每 500m) ---
        int firstMajor = (int) (Math.ceil(altMin / MAJOR_TICK_INTERVAL) * MAJOR_TICK_INTERVAL);
        for (int alt = firstMajor; alt <= altMax; alt += MAJOR_TICK_INTERVAL) {
            double pixelY = caretY - (alt - altitude) * scale;
            int tickY = (int) Math.round(pixelY);

            if (tickY < y || tickY > y + height)
                continue;

            // 跳过负数高度刻度
            if (alt < 0)
                continue;

            g2d.setStroke(tickMajorStroke);
            g2d.setColor(Application.colorNum);
            g2d.drawLine(x, tickY, x + (int) (width * 0.45), tickY);

            // 刻度标签 (左侧对齐)
            if (numFont != null) {
                String label = String.valueOf(alt);
                int textX = x + (int) (width * 0.45) + 4;
                int textY = tickY + g2d.getFontMetrics(numFont).getAscent() / 2 - 1;

                g2d.setFont(numFont);
                g2d.setColor(Application.colorShadeShape);
                g2d.drawString(label, textX + 1, textY + 1);
                g2d.setColor(Application.colorNum);
                g2d.drawString(label, textX, textY);
            }
        }

        // --- 副刻度 (每 100m) ---
        int firstMinor = (int) (Math.ceil(altMin / MINOR_TICK_INTERVAL) * MINOR_TICK_INTERVAL);
        for (int alt = firstMinor; alt <= altMax; alt += MINOR_TICK_INTERVAL) {
            if (alt % MAJOR_TICK_INTERVAL == 0)
                continue;

            double pixelY = caretY - (alt - altitude) * scale;
            int tickY = (int) Math.round(pixelY);

            if (tickY < y || tickY > y + height)
                continue;

            // 跳过负数高度刻度
            if (alt < 0)
                continue;

            g2d.setStroke(tickMinorStroke);
            g2d.setColor(Application.colorNum);
            g2d.drawLine(x, tickY, x + (int) (width * 0.25), tickY);
        }

        // --- 当前高度指示器 (指向右侧的三角箭头, 指向刻度带内部) ---
        int caretSize = (numFont != null) ? numFont.getSize() / 2 : 6;
        int[] cx = { x + 1, x + 1 + caretSize, x + 1 };
        int[] cy = { caretY - caretSize, caretY, caretY + caretSize };

        g2d.setStroke(caretStroke);
        g2d.setColor(Application.colorShadeShape);
        g2d.fillPolygon(cx, cy, 3);
        g2d.setColor(Application.colorNum);
        g2d.drawPolygon(cx, cy, 3);

        // --- 精确数字高度 (底部) ---
        int digitalY = y + height + (numFont != null ? numFont.getSize() + 4 : 14);

        if (warnAltitude) {
            // 低空警告: 显示雷达高度
            if (valueFont != null && radioAltitude > 0) {
                String raltText = String.format("R%.0f", radioAltitude);
                g2d.setFont(valueFont);
                int textW = g2d.getFontMetrics(valueFont).stringWidth(raltText);
                int textX = x;  // 左对齐

                // 闪烁效果用警告色
                g2d.setColor(Application.colorShadeShape);
                g2d.drawString(raltText, textX + 1, digitalY + 1);
                g2d.setColor(Application.colorWarning);
                g2d.drawString(raltText, textX, digitalY);
            }
        } else {
            // 正常: 显示气压高度
            if (valueFont != null) {
                String altText = String.format("%.0f", altitude);
                g2d.setFont(valueFont);
                int textX = x;  // 左对齐

                g2d.setColor(Application.colorShadeShape);
                g2d.drawString(altText, textX + 1, digitalY + 1);
                g2d.setColor(Application.colorNum);
                g2d.drawString(altText, textX, digitalY);
            }
        }

        // --- 单位标签 ---
        if (unitFont != null) {
            int labelY = digitalY + unitFont.getSize() + 2;
            g2d.setFont(unitFont);
            g2d.setColor(Application.colorShadeShape);
            g2d.drawString("ALT", x + 1, labelY + 1);
            g2d.setColor(Application.colorNum);
            g2d.drawString("ALT", x, labelY);
        }
    }
}
