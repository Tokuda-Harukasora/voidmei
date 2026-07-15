package ui.component;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;

import prog.Application;
import ui.overlay.model.HUDData;

/**
 * 现代战斗机风格 顶部水平航向带 (F-16 / F-35 style heading tape).
 * 
 * 设计特点:
 * - 水平刻度带横跨 HUD 顶部
 * - 显示 ±40° 范围内的航向刻度
 * - 主刻度每 10°，卡片方向 (N/E/S/W) 用特殊标记
 * - 中央固定指针指向当前航向
 * - 底部中央显示精确数值航向
 */
public class HeadingTape extends AbstractHUDComponent {

    // --- 尺寸 ---
    private int width;
    private int height;

    // --- 字体 ---
    private Font numFont;       // 刻度标签字体
    private Font cardFont;      // 卡片方向字体 (N/E/S/W, 略大)

    // --- 数据 ---
    private double heading;     // 航向 0-360°

    // --- 缓存的笔触 ---
    private BasicStroke tickMajorStroke;
    private BasicStroke tickMinorStroke;
    private BasicStroke tickCardStroke;
    private BasicStroke caretStroke;
    private int cachedLineWidth = -1;

    // 刻度常量
    private static final int MAJOR_TICK_INTERVAL = 10;   // 主刻度每 10°
    private static final int MINOR_TICK_INTERVAL = 5;    // 副刻度每 5°
    private static final double VISIBLE_RANGE = 80.0;     // 可见范围 ±40°
    private static final int[] CARDINAL_ANGLES = { 0, 90, 180, 270, 360 }; // N, E, S, W, N
    private static final String[] CARDINAL_LABELS = { "N", "E", "S", "W", "N" };

    public HeadingTape() {
    }

    @Override
    public String getId() {
        return "tape.heading";
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(width, height);
    }

    public void setStyleContext(int height, int width, Font numFont, Font cardFont) {
        this.height = height;
        this.width = width;
        this.numFont = numFont;
        this.cardFont = cardFont;
    }

    @Override
    public void onDataUpdate(HUDData data) {
        if (data == null)
            return;
        this.heading = data.heading;
    }

    @Override
    public void draw(Graphics2D g2d, int x, int y) {
        if (height <= 0 || width <= 0)
            return;

        int lineWidth = (numFont != null) ? Math.max(1, numFont.getSize() / 10) : 1;
        if (lineWidth != cachedLineWidth) {
            tickMajorStroke = new BasicStroke(lineWidth + 1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
            tickMinorStroke = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
            tickCardStroke = new BasicStroke(lineWidth + 2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
            caretStroke = new BasicStroke(lineWidth + 2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            cachedLineWidth = lineWidth;
        }

        // 水平刻度: 中心 = 当前航向, 刻度向两侧展开
        int centerX = x + width / 2;
        double scale = width / VISIBLE_RANGE;  // 像素/度
        double rangeHalf = VISIBLE_RANGE / 2.0; // ±40°

        // 航向角度标准化辅助函数 (内联避免 GC)
        // normalized = ((angle % 360) + 360) % 360
        // 但从 heading 展开时，我们用 heading 作为中心

        // --- 绘制底部边框线 ---
        int tapeBottom = y + height;
        g2d.setStroke(tickMinorStroke);
        g2d.setColor(Application.colorNum);
        g2d.drawLine(x, tapeBottom, x + width, tapeBottom);

        // --- 绘制刻度 ---
        // 计算从中心出发的刻度: 刻度角度 - 当前航向 = 偏移量
        // 需要处理角度环绕
        double hdg = heading;

        // 主刻度: 遍历航向 ±40° 范围内的所有 10° 刻度
        // 找到起始刻度 (最靠近 hdg - 42° 的 10° 倍数)
        double startAngle = Math.ceil((hdg - rangeHalf - 2) / MAJOR_TICK_INTERVAL) * MAJOR_TICK_INTERVAL;
        double endAngle = Math.floor((hdg + rangeHalf + 2) / MAJOR_TICK_INTERVAL) * MAJOR_TICK_INTERVAL;

        for (double angle = startAngle; angle <= endAngle; angle += MAJOR_TICK_INTERVAL) {
            double delta = angle - hdg;
            // 标准化 delta 到 [-180, 180]
            if (delta > 180)
                delta -= 360;
            if (delta < -180)
                delta += 360;

            if (Math.abs(delta) > rangeHalf + 2)
                continue;

            int pixelX = (int) Math.round(centerX + delta * scale);
            if (pixelX < x || pixelX > x + width)
                continue;

            // 检查是否为卡片方向 (N/E/S/W)
            int normAngle = ((int) Math.round(angle) % 360 + 360) % 360;
            boolean isCardinal = (normAngle == 0 || normAngle == 90 || normAngle == 180 || normAngle == 270);

            if (isCardinal) {
                // 卡片方向: 粗长线 + 字母标签
                g2d.setStroke(tickCardStroke);
                g2d.setColor(Application.colorNum);
                g2d.drawLine(pixelX, tapeBottom - (int) (height * 0.75), pixelX, tapeBottom);

                // 字母标签 (用卡片方向字体)
                if (cardFont != null) {
                    String cardLabel = normAngle == 0 ? "N" : normAngle == 90 ? "E" : normAngle == 180 ? "S" : "W";
                    int textW = g2d.getFontMetrics(cardFont).stringWidth(cardLabel);
                    int textX = pixelX - textW / 2;
                    int textY = y + cardFont.getSize();

                    g2d.setFont(cardFont);
                    // 卡片方向用醒目的 unit 色
                    g2d.setColor(Application.colorShadeShape);
                    g2d.drawString(cardLabel, textX + 1, textY + 1);
                    g2d.setColor(Application.colorNum);
                    g2d.drawString(cardLabel, textX, textY);
                }
            } else if (normAngle % 30 == 0) {
                // 每 30° 中长线 + 数字
                g2d.setStroke(tickMajorStroke);
                g2d.setColor(Application.colorNum);
                g2d.drawLine(pixelX, tapeBottom - (int) (height * 0.5), pixelX, tapeBottom);

                if (numFont != null) {
                    // 显示 3 位航向数字
                    String label = String.valueOf(normAngle);
                    // 简化: 30, 60, 120, 150, 210, 240, 300, 330
                    int textW = g2d.getFontMetrics(numFont).stringWidth(label);
                    int textX = pixelX - textW / 2;
                    int textY = y + numFont.getSize();

                    g2d.setFont(numFont);
                    g2d.setColor(Application.colorShadeShape);
                    g2d.drawString(label, textX + 1, textY + 1);
                    g2d.setColor(Application.colorNum);
                    g2d.drawString(label, textX, textY);
                }
            } else {
                // 普通 10° 刻度: 短竖线
                g2d.setStroke(tickMinorStroke);
                g2d.setColor(Application.colorNum);
                g2d.drawLine(pixelX, tapeBottom - (int) (height * 0.3), pixelX, tapeBottom);
            }
        }

        // 副刻度 (5°): 更短的线
        double startMinor = Math.ceil((hdg - rangeHalf - 2) / MINOR_TICK_INTERVAL) * MINOR_TICK_INTERVAL;
        double endMinor = Math.floor((hdg + rangeHalf + 2) / MINOR_TICK_INTERVAL) * MINOR_TICK_INTERVAL;

        for (double angle = startMinor; angle <= endMinor; angle += MINOR_TICK_INTERVAL) {
            if (((int) Math.round(angle)) % MAJOR_TICK_INTERVAL == 0)
                continue; // 跳过已画的主刻度

            double delta = angle - hdg;
            if (delta > 180)
                delta -= 360;
            if (delta < -180)
                delta += 360;

            if (Math.abs(delta) > rangeHalf + 2)
                continue;

            int pixelX = (int) Math.round(centerX + delta * scale);
            if (pixelX < x || pixelX > x + width)
                continue;

            g2d.setStroke(tickMinorStroke);
            g2d.setColor(Application.colorNum);
            g2d.drawLine(pixelX, tapeBottom - (int) (height * 0.15), pixelX, tapeBottom);
        }

        // --- 中央航向指针 (向下三角形) ---
        int caretH = height / 3;
        int[] cx = { centerX - caretH / 2, centerX, centerX + caretH / 2 };
        int[] cy = { tapeBottom, tapeBottom + caretH, tapeBottom };

        g2d.setStroke(caretStroke);
        g2d.setColor(Application.colorShadeShape);
        g2d.fillPolygon(cx, cy, 3);
        g2d.setColor(Application.colorNum);
        g2d.drawPolygon(cx, cy, 3);

        // --- 中央精确航向数值 ---
        if (numFont != null) {
            String hdgText = String.format("%03.0f", heading);
            int textW = g2d.getFontMetrics(numFont).stringWidth(hdgText);
            int textX = centerX - textW / 2;
            // 显示在指针下方
            int textY = tapeBottom + caretH + numFont.getSize() + 2;

            g2d.setFont(numFont);
            g2d.setColor(Application.colorShadeShape);
            g2d.drawString(hdgText, textX + 1, textY + 1);
            g2d.setColor(Application.colorNum);
            g2d.drawString(hdgText, textX, textY);
        }
    }
}
