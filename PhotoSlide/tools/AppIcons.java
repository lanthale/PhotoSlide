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
 * Generates the PhotoSlide app icons for macOS, Windows and Linux and the
 * DMG window background from one drawn artwork (Java2D, no dependencies):
 * a stylised landscape at sunrise with a frosted-glass zone scale in front.
 *
 * Usage (from the PhotoSlide module folder, on macOS for the .icns/.tiff):
 *
 * <pre>
 * java tools/AppIcons.java src/main/resources/org/photoslide/img
 * </pre>
 *
 * Writes osx/PhotoSlide.icns, osx/pkgresource/Photoslide-volume.icns,
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

        // macOS: iconset -> icns (app and DMG volume)
        Path iconset = work.resolve("PhotoSlide.iconset");
        Files.createDirectories(iconset);
        for (int s : new int[]{16, 32, 128, 256, 512}) {
            write(icon(Platform.MAC, s), iconset.resolve("icon_" + s + "x" + s + ".png"));
            write(icon(Platform.MAC, s * 2), iconset.resolve("icon_" + s + "x" + s + "@2x.png"));
        }
        Path icns = img.resolve("osx/PhotoSlide.icns");
        run("iconutil", "-c", "icns", iconset.toString(), "-o", icns.toString());
        Files.copy(icns, img.resolve("osx/pkgresource/Photoslide-volume.icns"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

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
        drawArt(g, shape, new Rectangle2D.Double(x0, y0, body, body), size);
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

    /**
     * The artwork inside the body, in coordinates relative to its bounds
     * (0..1): sky, sun, mountains, lake, ground, the glass zone card and the
     * glass finish of the body.
     */
    static void drawArt(Graphics2D g0, Shape body, Rectangle2D b, int size) {
        Graphics2D g = (Graphics2D) g0.create();
        g.clip(body);
        g.translate(b.getX(), b.getY());
        double w = b.getWidth();
        boolean detail = size >= 48;
        boolean tiny = size <= 24;          // 16-24 px: fewer, larger shapes

        // sky
        g.setPaint(new LinearGradientPaint(0, 0, 0, (float) (w * 0.66),
                new float[]{0f, 0.45f, 0.82f, 1f},
                new Color[]{new Color(14, 38, 92), new Color(42, 96, 170), new Color(238, 150, 98), new Color(255, 196, 120)}));
        g.fill(new Rectangle2D.Double(0, 0, w, w));

        // sun with glow
        double sx = w * 0.32;
        double sy = w * 0.47;
        g.setPaint(new RadialGradientPaint(new Point2D.Double(sx, sy), (float) (w * 0.34),
                new float[]{0f, 0.25f, 1f},
                new Color[]{new Color(255, 248, 220, 230), new Color(255, 214, 150, 120), new Color(255, 190, 120, 0)}));
        g.fill(new Rectangle2D.Double(0, 0, w, w));
        g.setColor(new Color(255, 250, 232));
        g.fill(new Ellipse2D.Double(sx - w * 0.075, sy - w * 0.075, w * 0.15, w * 0.15));

        // far mountains
        if (!tiny) {
            g.setPaint(new GradientPaint(0, (float) (w * 0.40), new Color(74, 82, 132), 0, (float) (w * 0.64), new Color(52, 56, 96)));
            g.fill(ridge(w, 0.64, new double[][]{{0, 0.56}, {0.16, 0.50}, {0.30, 0.55}, {0.52, 0.36}, {0.66, 0.46}, {0.80, 0.40}, {1, 0.50}}));
        }
        // near hills
        g.setPaint(new GradientPaint(0, (float) (w * 0.52), new Color(34, 38, 66), 0, (float) (w * 0.68), new Color(22, 24, 44)));
        g.fill(ridge(w, 0.68, new double[][]{{0, 0.60}, {0.22, 0.66}, {0.46, 0.58}, {0.70, 0.62}, {0.88, 0.55}, {1, 0.58}}));

        // lake reflecting the sky, fading into the ground
        g.setPaint(new LinearGradientPaint(0, (float) (w * 0.655), 0, (float) (w * 0.77),
                new float[]{0f, 0.35f, 1f},
                new Color[]{new Color(255, 196, 132), new Color(150, 120, 140), new Color(26, 32, 60)}));
        g.fill(new Rectangle2D.Double(0, w * 0.655, w, w * 0.12));
        if (detail) {
            g.setColor(new Color(255, 245, 225, 150));
            for (int i = 0; i < 4; i++) {
                double ly = w * (0.675 + i * 0.02);
                double lw = w * (0.16 - i * 0.03);
                g.fill(new RoundRectangle2D.Double(sx - lw / 2, ly, lw, w * 0.006, w * 0.006, w * 0.006));
            }
        }

        // ground
        g.setPaint(new GradientPaint(0, (float) (w * 0.77), new Color(26, 32, 60), 0, (float) w, new Color(8, 10, 18)));
        g.fill(new Rectangle2D.Double(0, w * 0.77, w, w * 0.23));
        if (size >= 128) {
            cottonGrass(g, w);
        }

        // zone scale on frosted glass
        double cx = tiny ? w * 0.48 : w * 0.53;
        double cy = tiny ? w * 0.30 : w * 0.41;
        double cw = tiny ? w * 0.42 : w * 0.34;
        double ch = tiny ? w * 0.60 : w * 0.47;
        double cr = w * 0.07;
        RoundRectangle2D card = new RoundRectangle2D.Double(cx, cy, cw, ch, cr, cr);
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new RoundRectangle2D.Double(cx + w * 0.008, cy + w * 0.016, cw, ch, cr, cr));
        g.setPaint(new GradientPaint(0, (float) cy, new Color(255, 255, 255, 120), 0, (float) (cy + ch), new Color(255, 255, 255, 55)));
        g.fill(card);
        int steps = tiny ? 4 : 6;
        double pad = cw * (tiny ? 0.12 : 0.16);
        double sw = cw - 2 * pad;
        double sh = (ch - 2 * pad) / steps;
        Area steps0 = new Area(new RoundRectangle2D.Double(cx + pad, cy + pad, sw, sh * steps, cr * 0.55, cr * 0.55));
        Graphics2D gs = (Graphics2D) g.create();
        gs.clip(steps0);
        for (int i = 0; i < steps; i++) {
            int v = (int) Math.round(255 * Math.pow(1 - i / (double) (steps - 1), 1.1));
            gs.setColor(new Color(v, v, v));
            gs.fill(new Rectangle2D.Double(cx + pad, cy + pad + i * sh, sw, sh + 1));
        }
        gs.dispose();
        g.setStroke(new BasicStroke((float) Math.max(1, w * 0.008)));
        g.setPaint(new GradientPaint(0, (float) cy, new Color(255, 255, 255, 230), 0, (float) (cy + ch), new Color(255, 255, 255, 90)));
        g.draw(card);

        // glass finish of the body: highlight at the top, light rim, darker bottom
        g.translate(-b.getX(), -b.getY());
        if (size >= 32) {
            g.setPaint(new GradientPaint(0, (float) b.getY(), new Color(255, 255, 255, 48),
                    0, (float) (b.getY() + w * 0.42), new Color(255, 255, 255, 0)));
            g.fill(body);
            g.setPaint(new GradientPaint(0, (float) (b.getY() + w * 0.7), new Color(0, 0, 0, 0),
                    0, (float) (b.getY() + w), new Color(0, 0, 0, 60)));
            g.fill(body);
            g.setStroke(new BasicStroke((float) (w * 0.012)));
            g.setPaint(new GradientPaint(0, (float) b.getY(), new Color(255, 255, 255, 150),
                    0, (float) (b.getY() + w), new Color(255, 255, 255, 25)));
            g.draw(body);
        }
        g.dispose();
    }

    /** Wollgras (cotton grass) in the foreground, as in the old PhotoSlide photo. */
    static void cottonGrass(Graphics2D g, double w) {
        double[][] tufts = {{0.09, 0.90, 0.026}, {0.16, 0.865, 0.021}, {0.24, 0.915, 0.029}, {0.32, 0.885, 0.018}, {0.40, 0.925, 0.023}};
        java.util.Random rnd = new java.util.Random(7);      // fixed: same icon on every run
        for (double[] t : tufts) {
            double x = w * t[0];
            double y = w * t[1];
            double r = w * t[2];
            // slightly bent stem
            g.setColor(new Color(64, 74, 66));
            g.setStroke(new BasicStroke((float) Math.max(1, w * 0.0035), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D.Double stem = new Path2D.Double();
            stem.moveTo(x, y);
            stem.quadTo(x + r * 0.9, y + (w - y) * 0.5, x + r * 0.4, w);
            g.draw(stem);
            // fluffy head: many soft, half transparent dots
            for (int i = 0; i < 26; i++) {
                double a = rnd.nextDouble() * 2 * Math.PI;
                double d = Math.sqrt(rnd.nextDouble()) * r * 0.75;
                double px = x + Math.cos(a) * d;
                double py = y + Math.sin(a) * d * 0.85;
                double pr = r * (0.28 + rnd.nextDouble() * 0.22);
                g.setPaint(new RadialGradientPaint(new Point2D.Double(px, py), (float) pr,
                        new float[]{0f, 1f},
                        new Color[]{new Color(255, 251, 242, 150), new Color(238, 230, 222, 0)}));
                g.fill(new Ellipse2D.Double(px - pr, py - pr, 2 * pr, 2 * pr));
            }
        }
    }

    /** Filled ridge line through the points (x, y relative), smoothed, down to the base. */
    static Shape ridge(double w, double base, double[][] pts) {
        GeneralPath p = new GeneralPath();
        p.moveTo(0, w * base + w);
        p.lineTo(0, w * pts[0][1]);
        for (int i = 1; i < pts.length; i++) {
            double mx = (pts[i - 1][0] + pts[i][0]) / 2;
            p.curveTo(w * mx, w * pts[i - 1][1], w * mx, w * pts[i][1], w * pts[i][0], w * pts[i][1]);
        }
        p.lineTo(w, w * base + w);
        p.closePath();
        return p;
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
