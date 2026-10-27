package org.photoslide.editortools;

import java.util.function.Consumer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import org.photoslide.datamodel.Display16;
import org.photoslide.imageops.lz.ZonesFilter;

/**
 * Editor for the 16 zones of the zone mapper: each column is a zone (dark
 * on the left, bright on the right) filled with its resulting grey. Drag a
 * column up or down to brighten or darken that zone; the curve through the
 * moved zones is shown. Double click (or right click) releases a zone.
 */
public final class ZoneMapperControl extends Pane {

    private static final double HEIGHT = 120;

    private final Canvas canvas = new Canvas();
    private final float[] values;
    private final Consumer<float[]> onChange;
    private int dragZone = -1;

    /**
     * @param values the 16 zone values (-1 = not set), changed in place
     * @param onChange called after every change with the values
     */
    public ZoneMapperControl(float[] values, Consumer<float[]> onChange) {
        this.values = values;
        this.onChange = onChange;
        setPrefHeight(HEIGHT);
        setMinHeight(HEIGHT);
        getChildren().add(canvas);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        canvas.widthProperty().addListener(o -> draw());
        canvas.heightProperty().addListener(o -> draw());
        canvas.addEventHandler(MouseEvent.MOUSE_PRESSED, this::pressed);
        canvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::dragged);
        canvas.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> dragZone = -1);
        draw();
    }

    private int zoneAt(double x) {
        int z = (int) (x / Math.max(1, canvas.getWidth()) * ZonesFilter.ZONES);
        return Math.max(0, Math.min(ZonesFilter.ZONES - 1, z));
    }

    private float valueAt(double y) {
        double h = Math.max(1, canvas.getHeight());
        return (float) Math.max(0, Math.min(1, 1 - y / h));
    }

    private void pressed(MouseEvent e) {
        int z = zoneAt(e.getX());
        if (e.getButton() == MouseButton.SECONDARY || e.getClickCount() == 2) {
            values[z] = -1;
            dragZone = -1;
        } else {
            dragZone = z;
            values[z] = valueAt(e.getY());
        }
        changed();
    }

    private void dragged(MouseEvent e) {
        if (dragZone >= 0) {
            values[dragZone] = valueAt(e.getY());
            changed();
        }
    }

    private void changed() {
        draw();
        onChange.accept(values.clone());
    }

    private void draw() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        GraphicsContext g = canvas.getGraphicsContext2D();
        double[] curve = ZonesFilter.curve(values);
        double cw = w / ZonesFilter.ZONES;
        double[] out = new double[ZonesFilter.ZONES];
        for (int i = 0; i < ZonesFilter.ZONES; i++) {
            int idx = (int) Math.round(ZonesFilter.zoneX(i) * 65535);
            double y = curve[Math.max(0, Math.min(65535, idx))];
            out[i] = ZonesFilter.zoneOf(Math.max(0, y));
            double grey = Display16.srgbEncode(Math.max(0, Math.min(1, y)));
            g.setFill(Color.gray(grey));
            g.fillRect(i * cw, 0, cw + 1, h);
        }
        g.setStroke(Color.web("#e8a33d"));
        g.setLineWidth(1.5);
        g.beginPath();
        for (int i = 0; i < ZonesFilter.ZONES; i++) {
            double x = (i + 0.5) * cw;
            double y = (1 - out[i]) * h;
            if (i == 0) {
                g.moveTo(x, y);
            } else {
                g.lineTo(x, y);
            }
        }
        g.stroke();
        for (int i = 0; i < ZonesFilter.ZONES; i++) {
            double x = (i + 0.5) * cw;
            double y = (1 - out[i]) * h;
            boolean set = values[i] >= 0;
            g.setFill(set ? Color.web("#e8a33d") : Color.web("#808080"));
            double r = set ? 4 : 2.5;
            g.fillOval(x - r, y - r, 2 * r, 2 * r);
        }
        g.setStroke(Color.web("#404040"));
        g.setLineWidth(1);
        g.strokeRect(0.5, 0.5, w - 1, h - 1);
    }
}
