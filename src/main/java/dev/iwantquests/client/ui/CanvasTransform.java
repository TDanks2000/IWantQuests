package dev.iwantquests.client.ui;

public final class CanvasTransform {
    public double panX, panY;
    public double zoom = 1;

    public double screenX(double x) {
        return panX + x * zoom;
    }

    public double screenY(double y) {
        return panY + y * zoom;
    }

    public void zoomAt(double x, double y, double factor) {
        double worldX = (x - panX) / zoom, worldY = (y - panY) / zoom;
        zoom = Math.max(0.25, Math.min(2.5, zoom * factor));
        panX = x - worldX * zoom;
        panY = y - worldY * zoom;
    }
}
