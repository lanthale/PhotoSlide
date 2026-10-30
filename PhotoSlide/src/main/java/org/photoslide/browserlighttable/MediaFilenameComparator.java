/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.photoslide.browserlighttable;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.regex.Pattern;
import org.photoslide.datamodel.MediaFile;

/**
 *
 * @author cleme
 */
public final class MediaFilenameComparator implements Comparator<MediaFile> {
    private static final Pattern NUMBERS = 
        Pattern.compile("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");

    // the split of a filename is computed once and kept in the media file: the
    // comparator is called n*log(n) times on the FX thread when a large folder is sorted

    private static final class Key {
        final String[] parts;
        final BigInteger[] numbers;

        Key(String filename) {
            parts = NUMBERS.split(filename);
            numbers = new BigInteger[parts.length];
            for (int i = 0; i < parts.length; i++) {
                char c = parts[i].isEmpty() ? 0 : parts[i].charAt(0);
                if (c >= '0' && c <= '9') {
                    numbers[i] = new BigInteger(parts[i]);
                }
            }
        }
    }

    private static Key keyOf(MediaFile m) {
        Key k = m.getSortKey() instanceof Key key ? key : null;
        if (k == null) {
            k = new Key(m.getPathStorage().getFileName().toString());
            m.setSortKey(k);
        }
        return k;
    }

    /** Computes the sort key in advance, e.g. in the loading thread instead of the FX thread. */
    public void prepare(MediaFile m) {
        keyOf(m);
    }

    @Override public final int compare(MediaFile o1, MediaFile o2) {
        // Optional "NULLS LAST" semantics:
        if (o1 == null || o2 == null)
            return o1 == null ? o2 == null ? 0 : -1 : 1;

        Key k1 = keyOf(o1);
        Key k2 = keyOf(o2);
        for (int i = 0; i < Math.min(k1.parts.length, k2.parts.length); i++) {
            int cmp = 0;

            // If both segments start with a digit, sort them numerically
            if (k1.numbers[i] != null && k2.numbers[i] != null)
                cmp = k1.numbers[i].compareTo(k2.numbers[i]);

            // If we haven't sorted numerically before, or if numeric sorting yielded 
            // equality (e.g 007 and 7) then sort lexicographically
            if (cmp == 0)
                cmp = k1.parts[i].compareTo(k2.parts[i]);

            // Abort once some prefix has unequal ordering
            if (cmp != 0)
                return cmp;
        }

        // If we reach this, then both strings have equally ordered prefixes, but 
        // maybe one string is longer than the other (i.e. has more segments)
        return k1.parts.length - k2.parts.length;
    }
}
