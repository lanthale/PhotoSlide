package org.photoslide.imageops.lz;

import java.util.stream.IntStream;
import org.photoslide.datamodel.Image16;

/**
 * Image16 <-> planar Y, S, T (luminance and two chroma axes, see
 * ColorScience.toYST), for tools that treat luminance and colour
 * separately (sharpening, noise reduction).
 */
final class YstPlanes {

    final int w;
    final int h;
    final float[] y;
    final float[] s;
    final float[] t;

    private YstPlanes(int w, int h) {
        this.w = w;
        this.h = h;
        y = new float[w * h];
        s = new float[w * h];
        t = new float[w * h];
    }

    static YstPlanes of(Image16 img) {
        final YstPlanes p = new YstPlanes(img.width(), img.height());
        final short[] d = img.data();
        IntStream.range(0, p.h).parallel().forEach(row -> {
            float[] out = new float[3];
            int o = row * p.w;
            for (int x = 0, i = o * 3; x < p.w; x++, i += 3, o++) {
                ColorScience.toYST((d[i] & 0xFFFF) / 65535f, (d[i + 1] & 0xFFFF) / 65535f,
                        (d[i + 2] & 0xFFFF) / 65535f, out);
                p.y[o] = out[0];
                p.s[o] = out[1];
                p.t[o] = out[2];
            }
        });
        return p;
    }

    Image16 toImage() {
        Image16 img = Image16.blank(w, h, true);
        final short[] d = img.data();
        IntStream.range(0, h).parallel().forEach(row -> {
            float[] rgb = new float[3];
            int o = row * w;
            for (int x = 0, i = o * 3; x < w; x++, i += 3, o++) {
                ColorScience.fromYST(y[o], s[o], t[o], rgb);
                d[i] = Planes.to16(rgb[0]);
                d[i + 1] = Planes.to16(rgb[1]);
                d[i + 2] = Planes.to16(rgb[2]);
            }
        });
        return img;
    }
}
