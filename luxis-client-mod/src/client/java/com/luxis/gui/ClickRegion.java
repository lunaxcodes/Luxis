package com.luxis.gui;

public record ClickRegion(int x, int y, int w, int h, String action, String data) {
    public boolean contains(double mx, double my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
