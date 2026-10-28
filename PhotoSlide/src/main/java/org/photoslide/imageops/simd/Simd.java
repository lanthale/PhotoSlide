package org.photoslide.imageops.simd;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Per element math on float rows with the SIMD units of the CPU (Vector API,
 * module jdk.incubator.vector). pow and exp in float vectors are several
 * times faster than Math.pow / Math.exp in double, with an error below one
 * 16 bit step.
 *
 * Only enabled if the module is in the boot layer (java --add-modules
 * jdk.incubator.vector) and a short measurement shows that it is faster on
 * this CPU (without vector math intrinsics it is slower). Filters check
 * {@link #ENABLED} and otherwise keep their scalar code. Switch off with
 * -Dphotoslide.simd=false.
 */
public final class Simd {

    private static final Logger LOG = Logger.getLogger(Simd.class.getName());

    /** The math of one implementation. */
    interface Kernels {

        /** out[i] = a[i]^p (a[i] >= 0) */
        void pow(float[] a, float p, float[] out, int n);

        /** out[i] = a[i]^p * (b[i] / a[i])^q (a[i] > 0, b[i] >= 0) */
        void powRatio(float[] a, float[] b, float p, float q, float[] out, int n);

        /** out[i] = e^a[i] */
        void exp(float[] a, float[] out, int n);
    }

    private static final Kernels KERNELS = load();

    /** true = the methods of this class use the Vector API. */
    public static final boolean ENABLED = KERNELS != null;

    private Simd() {
    }

    public static void pow(float[] a, float p, float[] out, int n) {
        KERNELS.pow(a, p, out, n);
    }

    public static void powRatio(float[] a, float[] b, float p, float q, float[] out, int n) {
        KERNELS.powRatio(a, b, p, q, out, n);
    }

    public static void exp(float[] a, float[] out, int n) {
        KERNELS.exp(a, out, n);
    }

    private static Kernels load() {
        if (!Boolean.parseBoolean(System.getProperty("photoslide.simd", "true"))
                || ModuleLayer.boot().findModule("jdk.incubator.vector").isEmpty()) {
            LOG.log(Level.INFO, "Vector API not used (start with --add-modules jdk.incubator.vector)");
            return null;
        }
        try {
            // loaded by name: the class needs the module, which may be missing
            Kernels vector = (Kernels) Class.forName("org.photoslide.imageops.simd.VectorKernels")
                    .getDeclaredConstructor().newInstance();
            long tv = measure(vector);
            long ts = measure(new ScalarKernels());
            boolean faster = tv < ts;
            LOG.log(Level.INFO, "Vector API {0}: pow {1} µs vector, {2} µs scalar",
                    new Object[]{faster ? "used" : "not used (slower on this CPU)", tv / 1000, ts / 1000});
            return faster ? vector : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            LOG.log(Level.WARNING, "Vector API not available", ex);
            return null;
        }
    }

    /** Best time of a few runs of powRatio on one row of 16k values (includes warm up). */
    private static long measure(Kernels k) {
        int n = 16384;
        float[] a = new float[n];
        float[] b = new float[n];
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            a[i] = 0.001f + (i % 1000) / 1000f;
            b[i] = 0.002f + (i % 777) / 777f;
        }
        long best = Long.MAX_VALUE;
        for (int r = 0; r < 40; r++) {
            long t0 = System.nanoTime();
            k.powRatio(a, b, 0.33f, 1.5f, out, n);
            best = Math.min(best, System.nanoTime() - t0);
        }
        return best;
    }

    /** Scalar reference, only used to measure (the filters keep their own scalar code). */
    static final class ScalarKernels implements Kernels {

        @Override
        public void pow(float[] a, float p, float[] out, int n) {
            for (int i = 0; i < n; i++) {
                out[i] = (float) Math.pow(a[i], p);
            }
        }

        @Override
        public void powRatio(float[] a, float[] b, float p, float q, float[] out, int n) {
            for (int i = 0; i < n; i++) {
                out[i] = (float) (Math.pow(a[i], p) * Math.pow(b[i] / a[i], q));
            }
        }

        @Override
        public void exp(float[] a, float[] out, int n) {
            for (int i = 0; i < n; i++) {
                out[i] = (float) Math.exp(a[i]);
            }
        }
    }
}
