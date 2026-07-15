package ui.component;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;

import prog.Application;
import ui.UIBaseElements;
import ui.overlay.model.HUDData;

/**
 * 现代战斗机风格 左侧垂直空速带 (F-16 / F-35 style airspeed tape).
 * 
 * 设计特点:
 * - 垂直刻度带，当前空速指针固定在 40% 高度处
 * - 刻度随速度变化上下滑动
 * - 主刻度每 100 km/h，副刻度每 20 km/h
 * - 底部显示精确数字空速和马赫数
 * - 战斗机HUD绿色单色渲染
 */
public class AirspeedTape extends AbstractHUDComponent {

    // --- 尺寸 ---
    private int width;
    private int height;

    // --- 字体 ---
    private Font numFont;      // 刻度数字字体
    private Font valueFont;    // 精确数值字体（略大）
    private Font unitFont;     // 单位/标签字体

    // --- 数据 ---
    private double ias;        // 指示空速 km/h
    private double mach;       // 马赫数
    private boolean warnVne;   // 超速警告

    // --- 缓存的笔触 ---
    private BasicStroke tickMajorStroke;
    private BasicStroke tickMinorStroke;
    private BasicStroke caretStroke;
    private BasicStroke frameStroke;
    private int cachedLineWidth = -1;
    private int cachedFontSize = -1;

    // 刻度常量
    private static final int MAJOR_TICK_INTERVAL = 100;  // 主刻度每 100 km/h
    private static final int MINOR_TICK_INTERVAL = 20;   // 副刻度每 20 km/h
    private static final double CARET_POS_RATIO = 0.40;  // 指针位置在 40% 高度处
    private static final double VISIBLE_RANGE = 400.0;   // 可见速度范围 ±200 km/h

    public AirspeedTape() {
    }

    @Override
    public String getId() {
        return "tape.airspeed";
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(width, height);
    }

    /**
     * 设置样式上下文。
     * @param height  磁带总高度 (像素)
     * @param width   磁带宽度 (像素)
     * @param numFont 刻度标签字体
     * @param valueFont 精确数值字体
     * @param unitFont 单位标签字体
     */
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
        this.ias = data.ias;
        this.mach = data.mach;
        this.warnVne = data.warnVne;
    }

    @Override
    public void draw(Graphics2D g2d, int x, int y) {
        if (height <= 0 || width <= 0)
            return;

        // 缓存笔触 — 基于 lineWidth (取 numFont 大小的 1/12)
        int lineWidth = (numFont != null) ? Math.max(1, numFont.getSize() / 12) : 1;
        if (lineWidth != cachedLineWidth) {
            tickMajorStroke = new BasicStroke(lineWidth + 1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
            tickMinorStroke = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
            caretStroke = new BasicStroke(lineWidth + 2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            frameStroke = new BasicStroke(lineWidth, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER);
            cachedLineWidth = lineWidth;
        }

        // 当前速度指示器位置 (Y坐标)
        int caretY = y + (int) (height * CARET_POS_RATIO);
        // 刻度比例: 像素 / (km/h)
        double scale = height / VISIBLE_RANGE;
        // 可见速度范围
        double rangeHalf = VISIBLE_RANGE / 2.0;
        double speedMin = Math.max(0, ias - rangeHalf);
        double speedMax = ias + rangeHalf;

        // --- 绘制刻度带背景框 ---
        // 右侧边框线（数字在右侧）
        g2d.setStroke(frameStroke);
        g2d.setColor(Application.colorNum);
        int frameRight = x + width;
        g2d.drawLine(frameRight, y, frameRight, y + height);
        // 左侧细线
        g2d.setColor(Application.colorShadeShape);
        g2d.drawLine(x, y, x, y + height);

        // --- 绘制刻度线 ---
        // 主刻度 (每 100 km/h)
        int firstMajor = (int) (Math.ceil(speedMin / MAJOR_TICK_INTERVAL) * MAJOR_TICK_INTERVAL);
        for (int speed = firstMajor; speed <= speedMax; speed += MAJOR_TICK_INTERVAL) {
            double pixelY = caretY - (speed - ias) * scale;
            int tickY = (int) Math.round(pixelY);

            // 只在可见范围内绘制
            if (tickY < y || tickY > y + height)
                continue;

            // 跳过负数速度刻度
            if (speed < 0)
                continue;

            // 主刻度线 (横跨右侧到中部)
            g2d.setStroke(tickMajorStroke);
            g2d.setColor(Application.colorNum);
            g2d.drawLine(frameRight - (int) (width * 0.45), tickY, frameRight, tickY);

            // 刻度标签
            if (numFont != null) {
                String label = String.valueOf(speed);
                int textW = g2d.getFontMetrics(numFont).stringWidth(label);
                int textX = frameRight - (int) (width * 0.45) - textW - 4;
                int textY = tickY + g2d.getFontMetrics(numFont).getAscent() / 2 - 1;

                // 两遍渲染: 阴影 + 主体
                g2d.setFont(numFont);
                g2d.setColor(Application.colorShadeShape);
                g2d.drawString(label, textX + 1, textY + 1);
                g2d.setColor(Application.colorNum);
                g2d.drawString(label, textX, textY);
            }
        }

        // 副刻度 (每 20 km/h) — 只在主刻度之间
        int firstMinor = (int) (Math.ceil(speedMin / MINOR_TICK_INTERVAL) * MINOR_TICK_INTERVAL);
        for (int speed = firstMinor; speed <= speedMax; speed += MINOR_TICK_INTERVAL) {
            // 跳过主刻度位置
            if (speed % MAJOR_TICK_INTERVAL == 0)
                continue;

            double pixelY = caretY - (speed - ias) * scale;
            int tickY = (int) Math.round(pixelY);

            if (tickY < y || tickY > y + height)
                continue;

            // 跳过负数速度刻度
            if (speed < 0)
                continue;
            g2d.setStroke(tickMinorStroke);
            g2d.setColor(Application.colorNum);
            g2d.drawLine(frameRight - (int) (width * 0.25), tickY, frameRight, tickY);
        }

        // --- 绘制当前速度指示器 (固定三角形指针) ---
        // 指向左侧的三角箭头 (指向刻度带内部)
        int caretSize = (numFont != null) ? numFont.getSize() / 2 : 6;
        int[] cx = { frameRight - 1, frameRight - 1 - caretSize, frameRight - 1 };
        int[] cy = { caretY - caretSize, caretY, caretY + caretSize };

        g2d.setStroke(caretStroke);
        g2d.setColor(Application.colorShadeShape);
        g2d.fillPolygon(cx, cy, 3);
        g2d.setColor(Application.colorNum);
        g2d.drawPolygon(cx, cy, 3);

        // --- 绘制精确数字空速 (底部) ---
        int digitalY = y + height + (numFont != null ? numFont.getSize() + 4 : 14);
        String speedText = String.format("%.0f", ias);

        if (valueFont != null) {
            g2d.setFont(valueFont);
            int textW = g2d.getFontMetrics(valueFont).stringWidth(speedText);
            int textX = frameRight - textW;  // 右对齐

            // 警告色
            Color speedColor = warnVne ? Application.colorWarning : Application.colorNum;
            g2d.setColor(Application.colorShadeShape);
            g2d.drawString(speedText, textX + 1, digitalY + 1);
            g2d.setColor(speedColor);
            g2d.drawString(speedText, textX, digitalY);
        }

        // --- 绘制马赫数 ---
        if (mach > 0.01 && unitFont != null) {
            int machY = digitalY + unitFont.getSize() + 2;
            String machText = String.format("M%.2f", mach);
            g2d.setFont(unitFont);
            int textW = g2d.getFontMetrics(unitFont).stringWidth(machText);
            int textX = frameRight - textW;

            g2d.setColor(Application.colorShadeShape);
            g2d.drawString(machText, textX + 1, machY + 1);
            g2d.setColor(Application.colorNum);
            g2d.drawString(machText, textX, machY);
        }
    }
}
