package org.photoslide.editpipeline;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import org.librawdecodersettings.RawDecoderSettings;
import org.librawjava.LibrawImage;
import org.librawjava.OutputColor;
import org.photoslide.datamodel.Display16;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.WorkingSpace;

/**
 * Colour shading correction of a camera: the colour of a photo drifts
 * towards its edges (with Foveon sensors light at a slant reaches the colour
 * layers differently; Sigma corrects this with its "spatial gain", LibRaw
 * does not). A grid of per channel gains, bilinear between the cell centres,
 * changes only the colour, not the brightness, and is 1 in the centre.
 *
 * The grid is estimated from photos with an embedded camera JPEG (the
 * camera's corrected rendering): per cell the colour of the decoded photo is
 * compared with the JPEG, relative to the centre, and the median over all
 * photos is taken, so the motifs cancel out.
 *
 * @param gx cells across
 * @param gy cells down
 * @param gains gx * gy * 3 gains (working space), row by row
 */
public record ColorShading(int gx, int gy, float[] gains) {

    private static final Logger LOG = Logger.getLogger(ColorShading.class.getName());
    public static final int GRID_X = 24;
    public static final int GRID_Y = 16;
    /** Cells with fewer usable photos keep the gain of their neighbours. */
    private static final int MIN_SAMPLES = 3;

    public ColorShading {
        if (gx < 2 || gy < 2 || gains.length != gx * gy * 3) {
            throw new IllegalArgumentException("Invalid colour shading grid");
        }
        gains = gains.clone();
    }

    /**
     * Multiplies the linear image in place by the gains of its position.
     * Any resolution: the grid covers the whole image.
     */
    public void apply(Image16 img) {
        final int w = img.width();
        final int h = img.height();
        final short[] d = img.data();
        java.util.stream.IntStream.range(0, h).parallel().forEach(y -> {
            float fy = (y + 0.5f) / h * gy - 0.5f;
            int j0 = Math.max(0, Math.min(gy - 2, (int) Math.floor(fy)));
            float ty = Math.max(0, Math.min(1, fy - j0));
            float[] rowGain = new float[gx * 3];
            for (int i = 0; i < gx * 3; i++) {
                rowGain[i] = gains[j0 * gx * 3 + i] * (1 - ty) + gains[(j0 + 1) * gx * 3 + i] * ty;
            }
            for (int x = 0, o = y * w * 3; x < w; x++, o += 3) {
                float fx = (x + 0.5f) / w * gx - 0.5f;
                int i0 = Math.max(0, Math.min(gx - 2, (int) Math.floor(fx)));
                float tx = Math.max(0, Math.min(1, fx - i0));
                for (int c = 0; c < 3; c++) {
                    float g = rowGain[i0 * 3 + c] * (1 - tx) + rowGain[(i0 + 1) * 3 + c] * tx;
                    int v = (int) ((d[o + c] & 0xFFFF) * g + 0.5f);
                    d[o + c] = (short) (v > 65535 ? 65535 : v);
                }
            }
        });
    }

    /**
     * The correction scaled in strength: 0 = none, 1 = as calibrated, more
     * = stronger (on the logarithm of the gains, brightness stays neutral).
     */
    public ColorShading withStrength(double strength) {
        if (strength == 1) {
            return this;
        }
        float[] g = new float[gains.length];
        for (int i = 0; i < g.length; i += 3) {
            double[] v = new double[3];
            for (int k = 0; k < 3; k++) {
                v[k] = Math.pow(gains[i + k], strength);
            }
            double lum = luminance(v);
            for (int k = 0; k < 3; k++) {
                g[i + k] = (float) (v[k] / lum);
            }
        }
        return new ColorShading(gx, gy, g);
    }

    /** Largest colour change of the grid in percent (for the user). */
    public double strength() {
        double max = 0;
        for (float g : gains) {
            max = Math.max(max, Math.abs(g - 1));
        }
        return max * 100;
    }

    /** "gx;gy;g0;g1;..." as stored in the RAW profile. */
    public String format() {
        StringBuilder b = new StringBuilder().append(gx).append(';').append(gy);
        for (float g : gains) {
            b.append(';').append(String.format(Locale.ROOT, "%.4f", g));
        }
        return b.toString();
    }

    /** @return the grid, or null if the text is empty or invalid */
    public static ColorShading parse(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            String[] p = s.split(";");
            int gx = Integer.parseInt(p[0].trim());
            int gy = Integer.parseInt(p[1].trim());
            if (p.length != 2 + gx * gy * 3) {
                return null;
            }
            float[] g = new float[gx * gy * 3];
            for (int i = 0; i < g.length; i++) {
                g[i] = Float.parseFloat(p[i + 2].trim());
            }
            return new ColorShading(gx, gy, g);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // calibration from embedded camera JPEGs
    // ------------------------------------------------------------------
    /** Result of a calibration. */
    public record Calibration(ColorShading shading, int photos) {
    }

    /**
     * Estimates the grid from RAW files with embedded camera JPEG (Sigma X3F).
     *
     * @param settings decoder settings of the profile (half size is used)
     * @param matrix colour matrix of the profile, null = none (the grid
     * corrects the colours after it)
     * @param progress (done, total), any thread
     * @return null if fewer than 5 photos could be used
     */
    public static Calibration calibrate(List<Path> files, RawDecoderSettings settings, double[] matrix,
            BiConsumer<Integer, Integer> progress) {
        RawDecoderSettings half = settings.copy();
        half.setHalfSizeOutput(true);
        List<double[]> logRatios = new ArrayList<>();          // per photo: gx*gy*3 (NaN = unusable)
        int done = 0;
        for (Path f : files) {
            progress.accept(done++, files.size());
            try {
                byte[] jpeg = X3fPreview.largestJpeg(f);
                if (jpeg == null) {
                    continue;
                }
                BufferedImage camera = ImageIO.read(new ByteArrayInputStream(jpeg));
                LibrawImage libraw = new LibrawImage(f.toString(), new ConcurrentHashMap<>());
                libraw.setSettingsOverride(half);
                Image16 decoded = Image16.fromRaw(libraw.readPixelData16(Files.readAllBytes(f), true, OutputColor.PROPHOTO));
                if (camera != null) {
                    logRatios.add(compare(cells(decoded, matrix), cells(camera)));
                }
            } catch (IOException | RuntimeException ex) {
                LOG.log(Level.WARNING, "Colour shading: cannot use " + f, ex);
            }
        }
        progress.accept(files.size(), files.size());
        if (logRatios.size() < 5) {
            return null;
        }
        return new Calibration(fromLogRatios(logRatios), logRatios.size());
    }

    /** Mean linear working space colour per cell of the decoded photo (matrix applied). */
    private static double[][] cells(Image16 img, double[] matrix) {
        double[][] c = new double[GRID_X * GRID_Y][3];
        int[] n = new int[GRID_X * GRID_Y];
        short[] d = img.data();
        for (int y = 0; y < img.height(); y += 2) {
            int cy = y * GRID_Y / img.height();
            for (int x = 0; x < img.width(); x += 2) {
                int cell = cy * GRID_X + x * GRID_X / img.width();
                int i = (y * img.width() + x) * 3;
                for (int k = 0; k < 3; k++) {
                    c[cell][k] += (d[i + k] & 0xFFFF) / 65535.0;
                }
                n[cell]++;
            }
        }
        for (int i = 0; i < c.length; i++) {
            for (int k = 0; k < 3; k++) {
                c[i][k] /= Math.max(1, n[i]);
            }
            if (matrix != null) {
                c[i] = ColorCalibration.multiply(matrix, c[i]);    // linear: the mean of the matrix is the matrix of the mean
            }
        }
        return c;
    }

    /** Mean linear working space colour per cell of the camera JPEG (sRGB). */
    private static double[][] cells(BufferedImage img) {
        double[][] c = new double[GRID_X * GRID_Y][3];
        int[] n = new int[GRID_X * GRID_Y];
        int w = img.getWidth();
        int h = img.getHeight();
        int[] row = new int[w];
        for (int y = 0; y < h; y += 2) {
            img.getRGB(0, y, w, 1, row, 0, w);
            int cy = y * GRID_Y / h;
            for (int x = 0; x < w; x += 2) {
                int cell = cy * GRID_X + x * GRID_X / w;
                int p = row[x];
                c[cell][0] += Display16.srgbDecode(((p >> 16) & 255) / 255.0);
                c[cell][1] += Display16.srgbDecode(((p >> 8) & 255) / 255.0);
                c[cell][2] += Display16.srgbDecode((p & 255) / 255.0);
                n[cell]++;
            }
        }
        for (int i = 0; i < c.length; i++) {
            for (int k = 0; k < 3; k++) {
                c[i][k] /= Math.max(1, n[i]);
            }
            c[i] = WorkingSpace.multiply(WorkingSpace.SRGB_TO_WORKING, c[i]);
        }
        return c;
    }

    /**
     * log(camera colour / decoded colour) per cell and channel, each colour
     * divided by its luminance (only the colour counts). NaN where a cell is
     * too dark or too bright to compare.
     */
    private static double[] compare(double[][] decoded, double[][] camera) {
        double[] r = new double[decoded.length * 3];
        for (int i = 0; i < decoded.length; i++) {
            double yd = luminance(decoded[i]);
            double yc = luminance(camera[i]);
            boolean usable = yd > 0.01 && yc > 0.01 && yc < 0.8 && max(camera[i]) < 0.95 && min(decoded[i]) > 0
                    && min(camera[i]) > 0;
            for (int k = 0; k < 3; k++) {
                r[i * 3 + k] = usable ? Math.log((camera[i][k] / yc) / (decoded[i][k] / yd)) : Double.NaN;
            }
        }
        return r;
    }

    /** Median over the photos, relative to the centre, smoothed, brightness neutral. */
    private static ColorShading fromLogRatios(List<double[]> photos) {
        int cells = GRID_X * GRID_Y;
        double[] med = new double[cells * 3];
        boolean[] known = new boolean[cells];
        double[] v = new double[photos.size()];
        for (int i = 0; i < cells; i++) {
            known[i] = true;
            for (int k = 0; k < 3; k++) {
                int n = 0;
                for (double[] p : photos) {
                    if (!Double.isNaN(p[i * 3 + k])) {
                        v[n++] = p[i * 3 + k];
                    }
                }
                if (n < MIN_SAMPLES) {
                    known[i] = false;
                    continue;
                }
                Arrays.sort(v, 0, n);
                med[i * 3 + k] = v[n / 2];
            }
        }
        fillUnknown(med, known);
        double[] smooth = blur(med);
        // relative to the centre (the colour of the whole photo belongs to the matrix and white balance)
        double[] centre = new double[3];
        int[][] middle = {{GRID_X / 2 - 1, GRID_Y / 2 - 1}, {GRID_X / 2, GRID_Y / 2 - 1},
            {GRID_X / 2 - 1, GRID_Y / 2}, {GRID_X / 2, GRID_Y / 2}};
        for (int[] m : middle) {
            for (int k = 0; k < 3; k++) {
                centre[k] += smooth[(m[1] * GRID_X + m[0]) * 3 + k] / middle.length;
            }
        }
        float[] gains = new float[cells * 3];
        for (int i = 0; i < cells; i++) {
            double[] g = new double[3];
            for (int k = 0; k < 3; k++) {
                g[k] = Math.exp(smooth[i * 3 + k] - centre[k]);
            }
            double lum = luminance(g);                     // keep the brightness
            for (int k = 0; k < 3; k++) {
                gains[i * 3 + k] = (float) (g[k] / lum);
            }
        }
        return new ColorShading(GRID_X, GRID_Y, gains);
    }

    /** Cells without enough samples take the mean of their known neighbours (repeated). */
    private static void fillUnknown(double[] v, boolean[] known) {
        for (int pass = 0; pass < GRID_X + GRID_Y; pass++) {
            boolean missing = false;
            boolean[] next = known.clone();
            for (int y = 0; y < GRID_Y; y++) {
                for (int x = 0; x < GRID_X; x++) {
                    int i = y * GRID_X + x;
                    if (known[i]) {
                        continue;
                    }
                    double[] sum = new double[3];
                    int n = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int xx = x + dx, yy = y + dy;
                            if (xx >= 0 && yy >= 0 && xx < GRID_X && yy < GRID_Y && known[yy * GRID_X + xx]) {
                                for (int k = 0; k < 3; k++) {
                                    sum[k] += v[(yy * GRID_X + xx) * 3 + k];
                                }
                                n++;
                            }
                        }
                    }
                    if (n > 0) {
                        for (int k = 0; k < 3; k++) {
                            v[i * 3 + k] = sum[k] / n;
                        }
                        next[i] = true;
                    } else {
                        missing = true;
                    }
                }
            }
            System.arraycopy(next, 0, known, 0, known.length);
            if (!missing) {
                return;
            }
        }
    }

    /** 3 x 3 box blur of the grid (edges repeated), against the noise of single cells. */
    private static double[] blur(double[] v) {
        double[] out = new double[v.length];
        for (int y = 0; y < GRID_Y; y++) {
            for (int x = 0; x < GRID_X; x++) {
                for (int k = 0; k < 3; k++) {
                    double s = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int xx = Math.max(0, Math.min(GRID_X - 1, x + dx));
                            int yy = Math.max(0, Math.min(GRID_Y - 1, y + dy));
                            s += v[(yy * GRID_X + xx) * 3 + k];
                        }
                    }
                    out[(y * GRID_X + x) * 3 + k] = s / 9;
                }
            }
        }
        return out;
    }

    private static double luminance(double[] v) {
        return WorkingSpace.luminance(v[0], v[1], v[2]);
    }

    private static double max(double[] v) {
        return Math.max(v[0], Math.max(v[1], v[2]));
    }

    private static double min(double[] v) {
        return Math.min(v[0], Math.min(v[1], v[2]));
    }
}
