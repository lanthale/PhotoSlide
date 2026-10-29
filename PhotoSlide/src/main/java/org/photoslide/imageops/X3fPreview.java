package org.photoslide.imageops;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

/**
 * Reads the JPEG that Sigma cameras embed in their X3F files (the camera's
 * own rendering, full size). Only the directory at the end of the file and
 * the image headers are read, then the largest JPEG.
 *
 * X3F layout: "FOVb" header; the last 4 bytes point to the directory "SECd"
 * (version, number of entries, then offset, length and type per entry).
 * Image entries ("IMAG", "IMA2") start with "SECi", version, image type,
 * data format (18 = JPEG), columns, rows, row size, then the data.
 */
public final class X3fPreview {

    private static final int FORMAT_JPEG = 18;
    private static final int IMAGE_HEADER = 28;

    private X3fPreview() {
    }

    /** @return the bytes of the largest embedded JPEG, or null if there is none */
    public static byte[] largestJpeg(Path file) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(file.toFile(), "r")) {
            if (in.length() < 64 || !"FOVb".equals(chars(read(in, 0, 4), 0))) {
                return null;
            }
            long dir = read(in, in.length() - 4, 4).getInt() & 0xFFFFFFFFL;
            ByteBuffer head = read(in, dir, 12);
            if (!"SECd".equals(chars(head, 0))) {
                return null;
            }
            int entries = head.getInt(8);
            if (entries <= 0 || entries > 1000) {
                return null;
            }
            ByteBuffer list = read(in, dir + 12, entries * 12);
            long bestOffset = -1;
            int bestLength = 0;
            long bestPixels = 0;
            for (int i = 0; i < entries; i++) {
                long offset = list.getInt(i * 12) & 0xFFFFFFFFL;
                int length = list.getInt(i * 12 + 4);
                String type = chars(list, i * 12 + 8);
                if (!type.equals("IMAG") && !type.equals("IMA2") || length <= IMAGE_HEADER) {
                    continue;
                }
                ByteBuffer img = read(in, offset, IMAGE_HEADER);
                if (!"SECi".equals(chars(img, 0)) || img.getInt(12) != FORMAT_JPEG) {
                    continue;
                }
                long pixels = (long) img.getInt(16) * img.getInt(20);
                if (pixels > bestPixels) {
                    bestPixels = pixels;
                    bestOffset = offset + IMAGE_HEADER;
                    bestLength = length - IMAGE_HEADER;
                }
            }
            if (bestOffset < 0) {
                return null;
            }
            byte[] jpeg = new byte[bestLength];
            in.seek(bestOffset);
            in.readFully(jpeg);
            return jpeg;
        }
    }

    private static ByteBuffer read(RandomAccessFile in, long offset, int length) throws IOException {
        byte[] b = new byte[length];
        in.seek(offset);
        in.readFully(b);
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static String chars(ByteBuffer b, int at) {
        char[] c = new char[4];
        for (int i = 0; i < 4; i++) {
            c[i] = (char) (b.get(at + i) & 0xFF);
        }
        return new String(c);
    }
}
