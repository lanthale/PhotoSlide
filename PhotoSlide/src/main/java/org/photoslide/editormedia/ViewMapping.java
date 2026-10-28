package org.photoslide.editormedia;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.transform.NonInvertibleTransformException;
import javafx.scene.transform.Transform;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.imageops.Geometry;
import org.photoslide.imageops.ImagePreview16;

/**
 * Maps between pixels of the original and a layer over the editor image.
 * The way from the original: flip and straighten (Geometry, in the rendered
 * image), the viewport (zoom and crop), the size of the ImageView and its
 * quarter turn. "Shown" coordinates are pixels of the rendered image (after
 * Geometry) in the scale of the original, as the crop is stored.
 */
final class ViewMapping {

    /** original -> rendered (same size) */
    private final AffineTransform geometry;
    /** rendered -> layer */
    private final AffineTransform shownToLayer;
    private final AffineTransform layerToShown;
    private final AffineTransform geometryInverse;
    /** Part of the rendered image in the view, scale of the original. */
    private final Rectangle2D visible;
    private final double width;
    private final double height;
    private final Bounds viewInLayer;

    private ViewMapping(AffineTransform geometry, AffineTransform shownToLayer, Rectangle2D visible,
            double width, double height, Bounds viewInLayer) throws NoninvertibleTransformException {
        this.geometry = geometry;
        this.shownToLayer = shownToLayer;
        this.layerToShown = shownToLayer.createInverse();
        this.geometryInverse = geometry.createInverse();
        this.visible = visible;
        this.width = width;
        this.height = height;
        this.viewInLayer = viewInLayer;
    }

    /** @return the mapping, or null while no image is shown */
    static ViewMapping of(ImageView view, ImagePreview16 preview, Node layer) {
        Image img = view.getImage();
        if (img == null || view.getScene() == null || layer.getScene() == null) {
            return null;
        }
        double ds = preview.getDisplayScale();
        Image16 source = preview.getSource();
        double w = source != null ? source.width() : img.getWidth() / ds;
        double h = source != null ? source.height() : img.getHeight() / ds;
        Rectangle2D vp = view.getViewport();
        if (vp == null) {
            vp = new Rectangle2D(0, 0, img.getWidth(), img.getHeight());
        }
        Bounds lb = view.getLayoutBounds();
        if (vp.getWidth() <= 0 || lb.getWidth() <= 0) {
            return null;
        }
        double k = lb.getWidth() / vp.getWidth();
        // rendered (original scale) -> local coordinates of the ImageView
        AffineTransform toLocal = new AffineTransform();
        toLocal.translate(lb.getMinX(), lb.getMinY());
        toLocal.scale(k * ds, k * ds);
        toLocal.translate(-vp.getMinX() / ds, -vp.getMinY() / ds);
        try {
            Transform t = layer.getLocalToSceneTransform().createInverse()
                    .createConcatenation(view.getLocalToSceneTransform());
            AffineTransform shownToLayer = new AffineTransform(t.getMxx(), t.getMyx(), t.getMxy(), t.getMyy(),
                    t.getTx(), t.getTy());
            shownToLayer.concatenate(toLocal);
            MediaFile f = preview.getFile();
            Geometry g = f == null ? Geometry.NONE : f.getGeometry();
            Rectangle2D visible = new Rectangle2D(vp.getMinX() / ds, vp.getMinY() / ds,
                    vp.getWidth() / ds, vp.getHeight() / ds);
            Bounds inLayer = layer.sceneToLocal(view.localToScene(view.getBoundsInLocal()));
            return new ViewMapping(g.transform(w, h), shownToLayer, visible, w, h, inLayer);
        } catch (NonInvertibleTransformException | NoninvertibleTransformException ex) {
            return null;
        }
    }

    /** Size of the original. */
    double width() {
        return width;
    }

    double height() {
        return height;
    }

    /** Part of the rendered image that is shown (scale of the original). */
    Rectangle2D visible() {
        return visible;
    }

    /** Bounds of the ImageView in the layer. */
    Bounds viewInLayer() {
        return viewInLayer;
    }

    /** Pixel of the original -> layer. */
    javafx.geometry.Point2D toLayer(double ox, double oy) {
        Point2D p = geometry.transform(new Point2D.Double(ox, oy), null);
        return shownToLayer(p.getX(), p.getY());
    }

    /** Pixel of the rendered image (scale of the original) -> layer. */
    javafx.geometry.Point2D shownToLayer(double sx, double sy) {
        Point2D p = shownToLayer.transform(new Point2D.Double(sx, sy), null);
        return new javafx.geometry.Point2D(p.getX(), p.getY());
    }

    /** Outline in pixels of the original -> layer. */
    Shape toLayer(Shape original) {
        AffineTransform t = new AffineTransform(shownToLayer);
        t.concatenate(geometry);
        return t.createTransformedShape(original);
    }

    /**
     * Layer -> pixel of the rendered image.
     *
     * @param clamp true = points outside the shown part are moved to its
     * edge, false = they give null
     */
    double[] shownFromLayer(double lx, double ly, boolean clamp) {
        Point2D p = layerToShown.transform(new Point2D.Double(lx, ly), null);
        double sx = p.getX();
        double sy = p.getY();
        if (sx < visible.getMinX() || sy < visible.getMinY() || sx > visible.getMaxX() || sy > visible.getMaxY()) {
            if (!clamp) {
                return null;
            }
            sx = Math.max(visible.getMinX(), Math.min(visible.getMaxX(), sx));
            sy = Math.max(visible.getMinY(), Math.min(visible.getMaxY(), sy));
        }
        return new double[]{sx, sy};
    }

    /** Layer -> pixel of the original (see shownFromLayer for clamp). */
    double[] fromLayer(double lx, double ly, boolean clamp) {
        double[] s = shownFromLayer(lx, ly, clamp);
        if (s == null) {
            return null;
        }
        Point2D p = geometryInverse.transform(new Point2D.Double(s[0], s[1]), null);
        return new double[]{p.getX(), p.getY()};
    }
}
