import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Generates the PhotoSlide app icons for macOS, Windows and Linux, the DMG
 * volume icon and the DMG window background (Java2D, no dependencies). The
 * motif is the original PhotoSlide artwork (Icon-source.png: tilted photo with
 * the zone scale), placed on a dark glass plate in the shape each platform
 * expects. The DMG volume icon is a silver drive with the motif as label, so
 * the mounted image looks different from the app.
 *
 * Usage (from the PhotoSlide module folder, on macOS for the .icns/.tiff):
 *
 * <pre>
 * java tools/AppIcons.java src/main/resources/org/photoslide/img
 * </pre>
 *
 * Reads Icon-source.png and writes osx/pkgresource/Photoslide.icns,
 * osx/pkgresource/Photoslide-volume.icns,
 * osx/pkgresource/Photoslide-background.tiff, win/PhotoSlide.ico,
 * linux/PhotoSlide.png and Installericon.png below the given folder.
 */
public final class AppIcons {

    /** Body of the icon on the canvas, by platform. */
    enum Platform {
        /** Apple grid: 824 of 1024, corner radius 185.4 (22.5 %), with a soft shadow. */
        MAC(824.0 / 1024, 185.4 / 824, true),
        /** Windows 11: rounded square filling most of the canvas, no shadow. */
        WINDOWS(0.94, 0.18, false),
        /** GNOME / freedesktop: rounded square, soft shadow. */
        LINUX(0.88, 0.22, true);

        final double fill;
        final double radius;
        final boolean shadow;

        Platform(double fill, double radius, boolean shadow) {
            this.fill = fill;
            this.radius = radius;
            this.shadow = shadow;
        }
    }

    public static void main(String[] args) throws Exception {
        Path img = Path.of(args.length > 0 ? args[0] : "src/main/resources/org/photoslide/img");
        Path work = Files.createTempDirectory("appicons");
        art = trim(ImageIO.read(img.resolve("Icon-source.png").toFile()));

        // macOS: iconset -> icns (app and DMG volume)
        Path iconset = work.resolve("PhotoSlide.iconset");
        Files.createDirectories(iconset);
        for (int s : new int[]{16, 32, 128, 256, 512}) {
            write(icon(Platform.MAC, s), iconset.resolve("icon_" + s + "x" + s + ".png"));
            write(icon(Platform.MAC, s * 2), iconset.resolve("icon_" + s + "x" + s + "@2x.png"));
        }
        // in the jpackage resource dir, not via --icon: jpackage would use --icon for the volume too
        Path icns = img.resolve("osx/pkgresource/Photoslide.icns");
        run("iconutil", "-c", "icns", iconset.toString(), "-o", icns.toString());

        // DMG volume icon: a drive with the motif, different from the app icon
        Path volumeset = work.resolve("Volume.iconset");
        Files.createDirectories(volumeset);
        for (int s : new int[]{16, 32, 128, 256, 512}) {
            write(volumeIcon(s), volumeset.resolve("icon_" + s + "x" + s + ".png"));
            write(volumeIcon(s * 2), volumeset.resolve("icon_" + s + "x" + s + "@2x.png"));
        }
        run("iconutil", "-c", "icns", volumeset.toString(), "-o",
                img.resolve("osx/pkgresource/Photoslide-volume.icns").toString());

        // DMG background: 1x + 2x in one TIFF
        Path bg1 = work.resolve("bg.png");
        Path bg2 = work.resolve("bg@2x.png");
        write(dmgBackground(1), bg1);
        write(dmgBackground(2), bg2);
        Path t1 = work.resolve("bg.tiff");
        Path t2 = work.resolve("bg@2x.tiff");
        run("sips", "-s", "format", "tiff", "-s", "dpiWidth", "72", "-s", "dpiHeight", "72", bg1.toString(), "--out", t1.toString());
        run("sips", "-s", "format", "tiff", "-s", "dpiWidth", "144", "-s", "dpiHeight", "144", bg2.toString(), "--out", t2.toString());
        run("tiffutil", "-cathidpicheck", t1.toString(), t2.toString(), "-out",
                img.resolve("osx/pkgresource/Photoslide-background.tiff").toString());

        // Windows: ICO with PNG entries
        int[] winSizes = {16, 20, 24, 32, 40, 48, 64, 96, 128, 256};
        List<BufferedImage> win = new ArrayList<>();
        for (int s : winSizes) {
            win.add(icon(Platform.WINDOWS, s));
        }
        writeIco(win, img.resolve("win/PhotoSlide.ico"));

        // Linux and the window icon of the running app
        BufferedImage linux = icon(Platform.LINUX, 1024);
        write(linux, img.resolve("linux/PhotoSlide.png"));
        write(linux, img.resolve("Installericon.png"));

        // previews for checking
        Path preview = Path.of(args.length > 1 ? args[1] : work.resolve("preview").toString());
        Files.createDirectories(preview);
        for (Platform p : Platform.values()) {
            for (int s : new int[]{1024, 128, 32, 16}) {
                write(icon(p, s), preview.resolve(p.name().toLowerCase() + "_" + s + ".png"));
            }
        }
        for (int s : new int[]{1024, 128, 32}) {
            write(volumeIcon(s), preview.resolve("volume_" + s + ".png"));
        }
        write(dmgBackground(2), preview.resolve("dmg_background@2x.png"));
        System.out.println("Icons written below " + img.toAbsolutePath() + ", previews in " + preview);
    }

    // ------------------------------------------------------------------
    // icon
    // ------------------------------------------------------------------
    static BufferedImage icon(Platform p, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(out);
        double body = size * p.fill;
        double x0 = (size - body) / 2;
        // the Apple grid leaves more room below the icon for the shadow
        double y0 = p == Platform.MAC ? size * 100.0 / 1024 - size * 0.006 : (size - body) / 2;
        Shape shape = new RoundRectangle2D.Double(x0, y0, body, body, body * p.radius * 2, body * p.radius * 2);
        if (p.shadow && size >= 32) {
            shadow(out, shape, size);
        }
        drawPlate(g, shape, new Rectangle2D.Double(x0, y0, body, body), size);
        g.dispose();
        return out;
    }

    /** Soft drop shadow of the body, drawn into the image below the art. */
    static void shadow(BufferedImage out, Shape shape, int size) {
        BufferedImage s = new BufferedImage(out.getWidth(), out.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(s);
        g.translate(0, size * 0.012);
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(shape);
        g.dispose();
        int radius = Math.max(1, (int) Math.round(size * 0.02));
        BufferedImage blurred = blur(blur(s, radius), radius);
        Graphics2D o = out.createGraphics();
        o.drawImage(blurred, 0, 0, null);
        o.dispose();
    }

    static BufferedImage blur(BufferedImage src, int r) {
        int n = 2 * r + 1;
        float[] k = new float[n];
        java.util.Arrays.fill(k, 1f / n);
        BufferedImage padded = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        new ConvolveOp(new Kernel(n, 1, k), ConvolveOp.EDGE_NO_OP, null).filter(src, padded);
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        new ConvolveOp(new Kernel(1, n, k), ConvolveOp.EDGE_NO_OP, null).filter(padded, out);
        return out;
    }

    /** The original artwork, cut to its visible part. */
    static BufferedImage art;

    /** The image without its fully transparent border. */
    static BufferedImage trim(BufferedImage src) {
        int x0 = src.getWidth(), y0 = src.getHeight(), x1 = -1, y1 = -1;
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                if ((src.getRGB(x, y) >>> 24) > 8) {
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x);
                    y1 = Math.max(y1, y);
                }
            }
        }
        BufferedImage out = new BufferedImage(x1 - x0 + 1, y1 - y0 + 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, -x0, -y0, null);
        g.dispose();
        return out;
    }

    /** High quality downscale (halving steps, then bicubic), keeps the alpha. */
    static BufferedImage scaled(BufferedImage src, int w, int h) {
        BufferedImage cur = src;
        while (cur.getWidth() / 2 >= w && cur.getHeight() / 2 >= h) {
            cur = resize(cur, cur.getWidth() / 2, cur.getHeight() / 2);
        }
        return resize(cur, w, h);
    }

    static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(out);
        g.drawImage(src, 0, 0, out.getWidth(), out.getHeight(), null);
        g.dispose();
        return out;
    }

    /**
     * Dark glass plate in the shape of the platform with the original motif
     * on it: plate gradient, soft light from the top left, the motif with its
     * own shadow, highlight at the top and a light rim.
     */
    static void drawPlate(Graphics2D g0, Shape body, Rectangle2D b, int size) {
        Graphics2D g = (Graphics2D) g0.create();
        double w = b.getWidth();
        g.clip(body);
        g.setPaint(new GradientPaint(0, (float) b.getY(), new Color(74, 80, 94),
                0, (float) (b.getY() + w), new Color(22, 24, 31)));
        g.fill(body);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(b.getX() + w * 0.3, b.getY() + w * 0.2), (float) (w * 0.8),
                new float[]{0f, 1f}, new Color[]{new Color(255, 255, 255, 38), new Color(255, 255, 255, 0)}));
        g.fill(body);

        // the motif, a bit smaller than the plate, centred
        double fit = size <= 24 ? 0.90 : 0.80;
        double scale = Math.min(w * fit / art.getWidth(), w * fit / art.getHeight());
        int aw = Math.max(1, (int) Math.round(art.getWidth() * scale));
        int ah = Math.max(1, (int) Math.round(art.getHeight() * scale));
        BufferedImage motif = scaled(art, aw, ah);
        int ax = (int) Math.round(b.getX() + (w - aw) / 2);
        int ay = (int) Math.round(b.getY() + (w - ah) / 2);
        if (size >= 32) {
            // shadow of the motif on the plate
            BufferedImage sh = new BufferedImage(aw + 2 * size / 16, ah + 2 * size / 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gs = sh.createGraphics();
            gs.drawImage(motif, size / 16, size / 16, null);
            gs.setComposite(AlphaComposite.SrcIn);
            gs.setColor(new Color(0, 0, 0, 150));
            gs.fillRect(0, 0, sh.getWidth(), sh.getHeight());
            gs.dispose();
            int r = Math.max(1, size / 80);
            sh = blur(blur(sh, r), r);
            g.drawImage(sh, ax - size / 16, ay - size / 16 + (int) Math.round(w * 0.015), null);
        }
        g.drawImage(motif, ax, ay, null);

        // glass finish
        if (size >= 32) {
            g.setPaint(new GradientPaint(0, (float) b.getY(), new Color(255, 255, 255, 44),
                    0, (float) (b.getY() + w * 0.4), new Color(255, 255, 255, 0)));
            g.fill(body);
        }
        g.setClip(null);
        g.setStroke(new BasicStroke((float) Math.max(1, w * 0.012)));
        g.setPaint(new GradientPaint(0, (float) b.getY(), new Color(255, 255, 255, 130),
                0, (float) (b.getY() + w), new Color(255, 255, 255, 20)));
        g.draw(body);
        g.dispose();
    }

    /**
     * DMG volume icon: a silver external drive seen from the front (like the
     * macOS drive icons), with the PhotoSlide motif as its label.
     */
    static BufferedImage volumeIcon(int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(out);
        double s = size / 1024.0;
        RoundRectangle2D drive = new RoundRectangle2D.Double(100 * s, 330 * s, 824 * s, 420 * s, 110 * s, 110 * s);
        if (size >= 32) {
            shadow(out, drive, size);
        }
        // aluminium body
        g.setPaint(new LinearGradientPaint(0, (float) (330 * s), 0, (float) (750 * s),
                new float[]{0f, 0.12f, 0.75f, 1f},
                new Color[]{new Color(252, 252, 253), new Color(232, 234, 238), new Color(198, 201, 208), new Color(162, 166, 175)}));
        g.fill(drive);
        // front face: a slightly darker band at the bottom with a light slot and an LED
        Graphics2D gf = (Graphics2D) g.create();
        gf.clip(drive);
        gf.setPaint(new GradientPaint(0, (float) (640 * s), new Color(150, 154, 163), 0, (float) (750 * s), new Color(118, 122, 132)));
        gf.fill(new Rectangle2D.Double(100 * s, 640 * s, 824 * s, 120 * s));
        gf.setColor(new Color(255, 255, 255, 120));
        gf.fill(new Rectangle2D.Double(100 * s, 640 * s, 824 * s, Math.max(1, 3 * s)));
        gf.setColor(new Color(70, 74, 82));
        gf.fill(new RoundRectangle2D.Double(250 * s, 690 * s, 300 * s, 14 * s, 14 * s, 14 * s));
        gf.setColor(new Color(120, 230, 140));
        gf.fill(new Ellipse2D.Double(820 * s, 686 * s, 22 * s, 22 * s));
        gf.dispose();
        g.setStroke(new BasicStroke((float) Math.max(1, 4 * s)));
        g.setColor(new Color(140, 144, 152));
        g.draw(drive);
        g.setStroke(new BasicStroke((float) Math.max(1, 3 * s)));
        g.setColor(new Color(255, 255, 255, 200));
        g.draw(new RoundRectangle2D.Double(104 * s, 334 * s, 816 * s, 412 * s, 104 * s, 104 * s));
        // label: the app icon on the drive, rising above it
        BufferedImage label = icon(Platform.MAC, (int) Math.round(560 * s));
        g.drawImage(label, (int) Math.round((size - label.getWidth()) / 2.0), (int) Math.round(150 * s), null);
        g.dispose();
        return out;
    }

    // ------------------------------------------------------------------
    // DMG background (window 520 x 340, app icon at x 120, Applications at x 390, y 130)
    // ------------------------------------------------------------------
    static BufferedImage dmgBackground(int scale) {
        int w = 520 * scale;
        int h = 340 * scale;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(out);
        g.setPaint(new GradientPaint(0, 0, new Color(246, 246, 248), 0, h, new Color(226, 228, 234)));
        g.fillRect(0, 0, w, h);
        // soft light spots behind the two icons
        for (int x : new int[]{120, 390}) {
            g.setPaint(new RadialGradientPaint(new Point2D.Double(x * scale, 130 * scale), 110 * scale,
                    new float[]{0f, 1f}, new Color[]{new Color(255, 255, 255, 200), new Color(255, 255, 255, 0)}));
            g.fillRect(0, 0, w, h);
        }
        // curved arrow from the app to Applications
        g.setColor(new Color(150, 154, 166));
        g.setStroke(new BasicStroke(5f * scale, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Double arrow = new Path2D.Double();
        arrow.moveTo(196 * scale, 132 * scale);
        arrow.curveTo(240 * scale, 112 * scale, 272 * scale, 112 * scale, 312 * scale, 128 * scale);
        g.draw(arrow);
        Path2D.Double head = new Path2D.Double();
        head.moveTo(318 * scale, 131 * scale);
        head.lineTo(297 * scale, 132 * scale);
        head.lineTo(309 * scale, 115 * scale);
        head.closePath();
        g.fill(head);
        // hint
        g.setColor(new Color(92, 96, 108));
        Font font = new Font("SansSerif", Font.PLAIN, 13 * scale);
        g.setFont(font);
        String text = "Drag PhotoSlide to Applications to install";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (w - fm.stringWidth(text)) / 2, 262 * scale);
        // small app mark
        BufferedImage mark = icon(Platform.MAC, 22 * scale);
        g.setComposite(AlphaComposite.SrcOver.derive(0.85f));
        g.drawImage(mark, (w - mark.getWidth()) / 2, 278 * scale, null);
        g.dispose();
        return out;
    }

    // ------------------------------------------------------------------
    // output
    // ------------------------------------------------------------------
    static Graphics2D graphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        return g;
    }

    static void write(BufferedImage img, Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        ImageIO.write(img, "png", file.toFile());
    }

    /** ICO with one PNG per size (Windows Vista and later). */
    static void writeIco(List<BufferedImage> images, Path file) throws IOException {
        List<byte[]> pngs = new ArrayList<>();
        for (BufferedImage img : images) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            ImageIO.write(img, "png", b);
            pngs.add(b.toByteArray());
        }
        ByteBuffer head = ByteBuffer.allocate(6 + 16 * images.size()).order(ByteOrder.LITTLE_ENDIAN);
        head.putShort((short) 0).putShort((short) 1).putShort((short) images.size());
        int offset = 6 + 16 * images.size();
        for (int i = 0; i < images.size(); i++) {
            int s = images.get(i).getWidth();
            head.put((byte) (s >= 256 ? 0 : s)).put((byte) (s >= 256 ? 0 : s)).put((byte) 0).put((byte) 0);
            head.putShort((short) 1).putShort((short) 32).putInt(pngs.get(i).length).putInt(offset);
            offset += pngs.get(i).length;
        }
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
            out.write(head.array());
            for (byte[] p : pngs) {
                out.write(p);
            }
        }
    }

    static void run(String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).inheritIO().start();
        if (p.waitFor() != 0) {
            throw new IOException("Failed: " + String.join(" ", cmd));
        }
    }

    private AppIcons() {
    }
}
