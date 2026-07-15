package ui.component;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;

import prog.Application;
import ui.overlay.model.HUDData;

/**
 * 现代战斗机风格 右侧升降率指示器 (Vertical Speed Indicator / VSI).
 * 
 * 设计特点:
 * - 紧邻高度带右侧的小型垂直指示条
 * - 显示爬升/下降率范围 ±50 m/s
 * - 指针随升降率移动
 * - 上升用绿色箭头，下降用警告色
 */
public class VertSpeedIndicator extends AbstractHUDComponent {

    // --- 尺寸 ---
    private int width;
    private int height;

    // --- 字体 ---
    private Font unitFont;

    // --- 数据 ---
    private double verticalSpeed;  // m/s (正值=爬升)

    // --- 缓存的笔触 ---
    private BasicStroke frameStroke;
    private BasicStroke needleStroke;
    private int cachedLineWidth = -1;

    private static final double MAX_RANGE = 50.0;  // ±50 m/s
    private static final double CARET_POS_RATIO = 0.40;  // 零点在 40% 高度处

    public VertSpeedIndicator() {
    }

    @Override
    public String getId() {
        return "tape.vertspeed";
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(width, height);
    }

    public void setStyleContext(int height, int width, Font unitFont) {
        this.height = height;
        this.width = width;
        this.unitFont = unitFont;
    }

    @Override
    public void onDataUpdate(HUDData data) {
        if (data == null)
            return;
        this.verticalSpeed = data.verticalSpeed;
    }

    @Override
    public void draw(Graphics2D g2d, int x, int y) {
        if (height <= 0 || width <= 0)
            return;

        int lineWidth = (unitFont != null) ? Math.max(1, unitFont.getSize() / 12) : 1;
        if (lineWidth != cachedLineWidth) {
            frameStroke = new BasicStroke(lineWidth, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER);
            needleStroke = new BasicStroke(lineWidth + 2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            cachedLineWidth = lineWidth;
        }

        // 比例和零点位置
        double scale = (height * 0.35) / MAX_RANGE;  // 上半部 35% 用于正范围，下半部用于负范围
        int zeroY = y + (int) (height * CARET_POS_RATIO);  // 零点

        // --- 背景框 ---
        g2d.setStroke(frameStroke);
        g2d.setColor(Application.colorShadeShape);
        g2d.drawRect(x, y, width, height);

        // --- 零线 ---
        g2d.setColor(Application.colorNum);
        g2d.drawLine(x + 2, zeroY, x + width - 2, zeroY);

        // --- 刻度标记 (每 10 m/s) ---
        for (int vs = -50; vs <= 50; vs += 10) {
            int pixelY = zeroY - (int) Math.round(vs * scale);
            if (pixelY < y + 2 || pixelY > y + height - 2)
                continue;

            int tickLen = (vs % 20 == 0) ? width / 2 : width / 4;

            if (vs >= 0) {
                // 右侧刻度
                g2d.setStroke(frameStroke);
                g2d.setColor(Application.colorNum);
                g2d.drawLine(x + width - tickLen, pixelY, x + width, pixelY);

                // 标签 (每 20 m/s)
                if (vs % 20 == 0 && vs != 0 && unitFont != null) {
                    String label = String.valueOf(vs);
                    g2d.setFont(unitFont);
                    int textW = g2d.getFontMetrics(unitFont).stringWidth(label);
                    g2d.setColor(Application.colorShadeShape);
                    g2d.drawString(label, x + width - tickLen - textW - 2, pixelY + 4);
                    g2d.setColor(Application.colorNum);
                    g2d.drawString(label, x + width - tickLen - textW - 3, pixelY + 3);
                }
            } else {
                // 负值也用相同方式
                g2d.setStroke(frameStroke);
                g2d.setColor(Application.colorNum);
                g2d.drawLine(x, pixelY, x + tickLen, pixelY);
            }
        }

        // --- 指针 (水平移动条) ---
        double clampedVs = Math.max(-MAX_RANGE, Math.min(MAX_RANGE, verticalSpeed));
        int pointerY = zeroY - (int) Math.round(clampedVs * scale);
        // 限制在框内
        if (pointerY < y + 2)
            pointerY = y + 2;
        if (pointerY > y + height - 2)
            pointerY = y + height - 2;

        // 指针颜色: 爬升=绿, 下降=黄/警告
        Color ptrColor = (clampedVs >= 0) ? Application.colorNum : Application.colorWarning;

        g2d.setStroke(needleStroke);
        g2d.setColor(Application.colorShadeShape);
        g2d.drawLine(x + 2, pointerY, x + width - 2, pointerY);
        // 指针尖端三角
        int[] tx = { x + width - 2, x + width + 3, x + width - 2 };
        int[] ty = { pointerY - 3, pointerY, pointerY + 3 };
        g2d.fillPolygon(tx, ty, 3);

        g2d.setColor(ptrColor);
        g2d.drawLine(x + 2, pointerY, x + width - 2, pointerY);
        g2d.drawPolygon(tx, ty, 3);
    }
}
