/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.editortools;

/**
 *
 * @author selfemp
 */
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Calculate the histogram of an image.
 */
public class Histogram {

    private static final short BITS = 256;

    private final List<Integer> r;
    private final List<Integer> g;
    private final List<Integer> b;

    /**
     * @param argb opaque 8 bit pixels as displayed (see Display16.toArgb)
     */
    public Histogram(int[] argb) {
        r = new ArrayList<>(Collections.nCopies(BITS, 0));
        g = new ArrayList<>(Collections.nCopies(BITS, 0));
        b = new ArrayList<>(Collections.nCopies(BITS, 0));
        // Calculate frequency of each level
        int[] countR = new int[BITS];
        int[] countG = new int[BITS];
        int[] countB = new int[BITS];
        for (int p : argb) {
            countR[(p >> 16) & 0xFF]++;
            countG[(p >> 8) & 0xFF]++;
            countB[p & 0xFF]++;
        }
        for (int i = 0; i < BITS; i++) {
            r.set(i, countR[i]);
            g.set(i, countG[i]);
            b.set(i, countB[i]);
        }
        // Calculare max value
        int maxR = Collections.max(r);
        int maxG = Collections.max(g);
        int maxB = Collections.max(b);
        int max = Math.max(Math.max(maxR, maxG), maxB);
        // Scale vaues: maxnumber -> 100%
        for (int i = 0; i < BITS; i++) {
            r.set(i, (int) ((r.get(i) / (double) max) * 100));
            g.set(i, (int) ((g.get(i) / (double) max) * 100));
            b.set(i, (int) ((b.get(i) / (double) max) * 100));
        }
    }

    /**
     * Get the percentage of red values.(0->0%; maxValue->100%)
     *
     * @return
     */
    public List<Integer> getRed() {
        return r;
    }

    /**
     * Get the percentage of green values.(0->0%; maxValue->100%)
     *
     * @return
     */
    public List<Integer> getGreen() {
        return g;
    }

    /**
     * Get the percentage of blue values.(0->0%; maxValue->100%)
     *
     * @return
     */
    public List<Integer> getBlue() {
        return b;
    }
}
