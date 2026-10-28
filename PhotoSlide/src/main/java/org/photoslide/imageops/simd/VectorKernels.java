package org.photoslide.imageops.simd;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/**
 * Simd.Kernels with the Vector API. Only loaded by Simd when the module
 * jdk.incubator.vector is present. The rest of each row (fewer values than
 * one vector) is computed scalar.
 */
final class VectorKernels implements Simd.Kernels {

    private static final VectorSpecies<Float> S = FloatVector.SPECIES_PREFERRED;

    @Override
    public void pow(float[] a, float p, float[] out, int n) {
        FloatVector vp = FloatVector.broadcast(S, p);
        int i = 0;
        for (int end = S.loopBound(n); i < end; i += S.length()) {
            FloatVector.fromArray(S, a, i).lanewise(VectorOperators.POW, vp).intoArray(out, i);
        }
        for (; i < n; i++) {
            out[i] = (float) Math.pow(a[i], p);
        }
    }

    @Override
    public void powRatio(float[] a, float[] b, float p, float q, float[] out, int n) {
        FloatVector vp = FloatVector.broadcast(S, p);
        FloatVector vq = FloatVector.broadcast(S, q);
        int i = 0;
        for (int end = S.loopBound(n); i < end; i += S.length()) {
            FloatVector va = FloatVector.fromArray(S, a, i);
            FloatVector vb = FloatVector.fromArray(S, b, i);
            va.lanewise(VectorOperators.POW, vp)
                    .mul(vb.div(va).lanewise(VectorOperators.POW, vq))
                    .intoArray(out, i);
        }
        for (; i < n; i++) {
            out[i] = (float) (Math.pow(a[i], p) * Math.pow(b[i] / a[i], q));
        }
    }

    @Override
    public void exp(float[] a, float[] out, int n) {
        int i = 0;
        for (int end = S.loopBound(n); i < end; i += S.length()) {
            FloatVector.fromArray(S, a, i).lanewise(VectorOperators.EXP).intoArray(out, i);
        }
        for (; i < n; i++) {
            out[i] = (float) Math.exp(a[i]);
        }
    }
}
