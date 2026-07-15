package ui.component;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;

import prog.Application;
import ui.overlay.model.HUDData;

/**
 * 现代战斗机风格 G 值指示器。
 * 
 * 显示当前过载 G 值，大号数字，带过载方向指示。
 * 持续显示当前 G 值，正 G 用常规色，负 G 用警告色。
 */
public class GLoadIndicator extends AbstractHUDComponent {

    // --- 尺寸 ---
    private int width;
    private int height;

    // --- 字体 ---
    private Font valueFont;
    private Font labelFont;

    // --- 数据 ---
    private double gLoad;

    // --- 预分配缓冲区 ---
    private final StringBuilder valueBuffer = new StringBuilder(6);

    public GLoadIndicator() {
    }

    @Override
    public String getId() {
        return "gauge.gload";
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
        this.gLoad = data.gLoad;
    }

    @Override
    public void draw(Graphics2D g2d, int x, int y) {
        if (height <= 0 || width <= 0)
            return;

        int centerX = x + width / 2;

        // --- 标签 "G" ---
        if (labelFont != null) {
            String label = "G";
            int labelW = g2d.getFontMetrics(labelFont).stringWidth(label);
            int labelX = centerX - labelW / 2;
            int labelY = y + labelFont.getSize();

            g2d.setFont(labelFont);
            g2d.setColor(Application.colorShadeShape);
            g2d.drawString(label, labelX + 1, labelY + 1);
            g2d.setColor(Application.colorLabel);
            g2d.drawString(label, labelX, labelY);
        }

        // --- G 值 ---
        if (valueFont != null) {
            valueBuffer.setLength(0);
            valueBuffer.append(String.format("%.1f", gLoad));

            String valStr = valueBuffer.toString();
            int valW = g2d.getFontMetrics(valueFont).stringWidth(valStr);
            int valX = centerX - valW / 2;
            int valY = y + height - 4;  // 底部对齐

            // 正G=绿色, 负G=警告色
            Color gColor = (gLoad >= 0) ? Application.colorNum : Application.colorWarning;

            g2d.setFont(valueFont);
            g2d.setColor(Application.colorShadeShape);
            g2d.drawString(valStr, valX + 1, valY + 1);
            g2d.setColor(gColor);
            g2d.drawString(valStr, valX, valY);
        }
    }
}
