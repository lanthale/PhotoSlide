package org.photoslide.imageops;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.Rational;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import org.librawjava.LibrawImage;
import org.photoslide.datamodel.Display16;
import org.photoslide.datamodel.Image16;

/**
 * The base look of RAW files, applied once after decoding (before all tools),
 * like the camera profile and baseline exposure of Lightroom: RAW data is
 * linear and usually exposed short to protect the highlights, so without it
 * photos look darker and flatter than the camera JPEG.
 *
 * The look belongs to the RAW profile of the camera ("Maker Model", see the
 * RAW settings); cameras without own profile use the "Default" profile.
 *
 * @param exposure base exposure in stops, added to the DNG BaselineExposure
 * @param useDngBaseline add the BaselineExposure written into DNG files
 * @param curve tone curve; highlights roll off softly instead of clipping
 * @param matrix colour matrix of the camera (3 x 3, row major, working
 * space), from a ColorChecker calibration; null = none. Applied first.
 * @param isoGain brighten Sigma X3F files by their ISO: LibRaw does not
 * apply the ISO gain of the Foveon sensors (Sigma's software does)
 */
public record RawLook(double exposure, boolean useDngBaseline, ToneCurve curve, double[] matrix, boolean isoGain) {

    private static final Logger LOG = Logger.getLogger(RawLook.class.getName());

    /** Contrast curve on the sRGB encoded values (S-curve), mixed with the identity. */
    public enum ToneCurve {
        LINEAR("Linear (no curve)", 0),
        MEDIUM("Medium contrast", 0.35),
        STRONG("Strong contrast", 0.7);

        private final String label;
        private final double strength;

        ToneCurve(String label, double strength) {
            this.label = label;
            this.strength = strength;
        }

        public String label() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Used when no profile sets a look (comparable to the camera JPEG). */
    public static final RawLook DEFAULT = new RawLook(0.8, true, ToneCurve.MEDIUM);
    /** The decoded RAW data unchanged. */
    public static final RawLook NONE = new RawLook(0, false, ToneCurve.LINEAR);

    /** Linear values above this start to roll off (after the exposure). */
    private static final double KNEE = 0.8;
    /** Tag BaselineExposure of DNG files (signed rational, stops). */
    private static final int TAG_BASELINE_EXPOSURE = 0xC62A;
    private static final String DEFAULT_KEY = "Default";

    private static final Map<String, RawLook> PROFILES = new ConcurrentHashMap<>();

    public RawLook {
        curve = curve == null ? ToneCurve.MEDIUM : curve;
        matrix = matrix == null || matrix.length != 9 ? null : matrix.clone();
    }

    /** Without colour matrix, with ISO gain for X3F. */
    public RawLook(double exposure, boolean useDngBaseline, ToneCurve curve) {
        this(exposure, useDngBaseline, curve, null, true);
    }

    /** ISO of the Foveon sensors that needs no gain. */
    private static final double FOVEON_BASE_ISO = 100;

    /**
     * Exposure in stops the file itself asks for: DNG BaselineExposure and,
     * for Sigma X3F files, the ISO gain (as enabled in this look).
     *
     * @param libraw the decoder of the file (for the ISO)
     */
    public double fileStops(Path file, LibrawImage libraw) {
        double stops = useDngBaseline ? dngBaselineExposure(file) : 0;
        if (isoGain && file.getFileName().toString().toLowerCase().endsWith(".x3f")) {
            try {
                String iso = libraw.getMetaData().get("ISO speed");
                double v = iso == null ? 0 : Double.parseDouble(iso.trim());
                if (v > FOVEON_BASE_ISO) {
                    stops += Math.log(v / FOVEON_BASE_ISO) / Math.log(2);
                }
            } catch (IOException | RuntimeException ex) {
                LOG.log(Level.FINE, "No ISO for " + file, ex);
            }
        }
        return stops;
    }

    /** Sets the look of a RAW profile ("Maker Model" or "Default"); null removes it. */
    public static void register(String profile, RawLook look) {
        if (look == null) {
            PROFILES.remove(profile);
        } else {
            PROFILES.put(profile, look);
        }
    }

    /** The look of the camera profile, else of the "Default" profile, else DEFAULT. */
    public static RawLook forCamera(String makeAndModel) {
        RawLook look = makeAndModel == null ? null : PROFILES.get(makeAndModel);
        if (look == null) {
            look = PROFILES.get(DEFAULT_KEY);
        }
        return look != null ? look : DEFAULT;
    }

    /**
     * Applies the look to a linear image in place.
     *
     * @param fileStops exposure the file asks for, see fileStops()
     */
    public Image16 apply(Image16 linear, double fileStops) {
        double stops = exposure + fileStops;
        if (stops == 0 && curve == ToneCurve.LINEAR && matrix == null) {
            return linear;
        }
        final short[] lut = lut(Math.pow(2, stops), curve.strength);
        final short[] d = linear.data();
        final int rowLength = linear.width() * Image16.CHANNELS;
        final float[] m = matrix == null ? null : toFloat(matrix);
        IntStream.range(0, linear.height()).parallel().forEach(y -> {
            for (int i = y * rowLength, end = i + rowLength; i < end; i += 3) {
                int r = d[i] & 0xFFFF;
                int g = d[i + 1] & 0xFFFF;
                int b = d[i + 2] & 0xFFFF;
                if (m != null) {
                    int r2 = clamp16(m[0] * r + m[1] * g + m[2] * b);
                    int g2 = clamp16(m[3] * r + m[4] * g + m[5] * b);
                    b = clamp16(m[6] * r + m[7] * g + m[8] * b);
                    r = r2;
                    g = g2;
                }
                d[i] = lut[r];
                d[i + 1] = lut[g];
                d[i + 2] = lut[b];
            }
        });
        return linear;
    }

    private static float[] toFloat(double[] v) {
        float[] f = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            f[i] = (float) v[i];
        }
        return f;
    }

    private static int clamp16(float v) {
        int q = (int) (v + 0.5f);
        return q < 0 ? 0 : (q > 65535 ? 65535 : q);
    }

    /** "m00;m01;...;m22" as stored in the RAW profile, null if empty or invalid. */
    public static double[] parseMatrix(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String[] parts = s.split(";");
        if (parts.length != 9) {
            return null;
        }
        double[] m = new double[9];
        try {
            for (int i = 0; i < 9; i++) {
                m[i] = Double.parseDouble(parts[i].trim());
            }
        } catch (NumberFormatException ex) {
            return null;
        }
        return m;
    }

    public static String formatMatrix(double[] m) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < m.length; i++) {
            b.append(i == 0 ? "" : ";").append(String.format(java.util.Locale.ROOT, "%.6f", m[i]));
        }
        return b.toString();
    }

    /** Linear 16 bit -> linear 16 bit: exposure, highlight roll off, contrast curve. */
    static short[] lut(double gain, double contrast) {
        short[] lut = new short[65536];
        for (int i = 0; i < 65536; i++) {
            double x = i / 65535.0 * gain;
            if (x > KNEE) {
                // slope 1 at the knee, approaches white without clipping
                x = KNEE + (1 - KNEE) * (1 - Math.exp(-(x - KNEE) / (1 - KNEE)));
            }
            double e = Display16.srgbEncode(x);
            double s = e * e * (3 - 2 * e);
            double out = Display16.srgbDecode(e + contrast * (s - e));
            lut[i] = (short) (int) Math.round(Math.max(0, Math.min(1, out)) * 65535);
        }
        return lut;
    }

    /** BaselineExposure of a DNG file in stops, 0 for other files or if missing. */
    public static double dngBaselineExposure(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (!name.endsWith(".dng")) {
            return 0;
        }
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file.toFile());
            for (Directory dir : metadata.getDirectories()) {
                if (dir.containsTag(TAG_BASELINE_EXPOSURE)) {
                    Rational r = dir.getRational(TAG_BASELINE_EXPOSURE);
                    return r == null ? 0 : r.doubleValue();
                }
            }
        } catch (Exception ex) {
            LOG.log(Level.FINE, "No BaselineExposure in " + file, ex);
        }
        return 0;
    }
}
