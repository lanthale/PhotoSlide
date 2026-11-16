package org.photoslide;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Opens a new email to the developer in the email app with the diagnostic
 * logs of the app data folder attached (program log, UI hangs, slow
 * folders). The user checks and sends it.
 */
public final class DiagnosticsMail {

    private static final Logger LOG = Logger.getLogger(DiagnosticsMail.class.getName());
    private static final String RECIPIENT = "clemens.lanthaler@itarchitects.at";
    private static final String[] LOG_FILES = {"photoslide.log", "photoslide.log.1", "fx-stalls.log", "slow-load.log"};

    private DiagnosticsMail() {
    }

    /** The log files that exist, in the app data folder. */
    public static List<File> logFiles() {
        List<File> files = new ArrayList<>();
        for (String name : LOG_FILES) {
            File f = new File(Utility.getAppData(), name);
            if (f.isFile() && f.length() > 0) {
                files.add(f);
            }
        }
        return files;
    }

    /**
     * Opens the email in the background (preferences, help menu); tells the
     * user if the logs have to be attached by hand. JavaFX thread.
     */
    public static void sendLogs(javafx.stage.Window owner, Runnable done) {
        String subject = I18n.t("preferences.mailLogsSubject");
        String body = I18n.t("preferences.mailLogsBody") + "\n\n"
                + "PhotoSlide " + new Utility().getAppVersion() + ", "
                + System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + "), Java "
                + System.getProperty("java.version");
        Thread.ofVirtual().start(() -> {
            boolean attached = open(subject, body);
            javafx.application.Platform.runLater(() -> {
                if (done != null) {
                    done.run();
                }
                if (!attached) {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION,
                            I18n.t("preferences.mailLogsManual", RECIPIENT), javafx.scene.control.ButtonType.OK);
                    alert.setHeaderText(null);
                    if (owner != null) {
                        alert.initOwner(owner);
                    }
                    alert.show();
                }
            });
        });
    }

    /**
     * Opens the email; not on the JavaFX thread (starts a process).
     *
     * @return false if the logs could not be attached: then the email is
     * opened without them and the folder of the logs is shown
     */
    public static boolean open(String subject, String body) {
        List<File> files = logFiles();
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("mac") && openMacMail(subject, body, files)) {
                return true;
            }
            if (os.contains("nux") && !files.isEmpty() && run(xdgEmail(subject, body, files))) {
                return true;
            }
        } catch (IOException ex) {
            LOG.log(Level.FINE, "Cannot open the email app with attachments", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
        // Windows and fallback: mailto knows no attachments
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
                Desktop.getDesktop().mail(URI.create("mailto:" + RECIPIENT + "?subject=" + encode(subject) + "&body=" + encode(body)));
            }
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(new File(Utility.getAppData()));
            }
        } catch (IOException | UnsupportedOperationException ex) {
            LOG.log(Level.WARNING, "Cannot open the email app", ex);
        }
        return false;
    }

    /**
     * The default email app of macOS (as set in Mail's settings): Mail,
     * Outlook and Thunderbird get the attachments; for others false (mailto
     * without attachments).
     */
    private static boolean openMacMail(String subject, String body, List<File> files) throws IOException, InterruptedException {
        String[] app = macDefaultMailApp();
        String bundleId = app[0].toLowerCase();
        LOG.log(Level.FINE, "Default email app: {0}", app[0]);
        return switch (bundleId) {
            case "", "com.apple.mail" -> appleScript(MAIL_SCRIPT, subject, body, files);
            case "com.microsoft.outlook" -> appleScript(OUTLOOK_SCRIPT, subject, body, files);
            case "org.mozilla.thunderbird", "eu.betterbird.betterbird" -> thunderbird(app[1], subject, body, files);
            default -> false;
        };
    }

    /** Bundle id and executable of the app that opens mailto links ("" if unknown). */
    private static String[] macDefaultMailApp() throws IOException, InterruptedException {
        Process p = new ProcessBuilder("osascript", "-l", "JavaScript", "-e",
                "ObjC.import('AppKit');"
                + "var u = $.NSWorkspace.sharedWorkspace.URLForApplicationToOpenURL($.NSURL.URLWithString('mailto:" + RECIPIENT + "'));"
                + "if (u.isNil()) { '' } else { var b = $.NSBundle.bundleWithURL(u); b.bundleIdentifier.js + '\\n' + b.executablePath.js }")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (p.waitFor() != 0 || out.isEmpty()) {
            return new String[]{"", ""};
        }
        String[] lines = out.split("\n", 2);
        return new String[]{lines[0].trim(), lines.length > 1 ? lines[1].trim() : ""};
    }

    /** argv: subject, body, attachments; the recipient is in the script. */
    private static final String MAIL_SCRIPT = """
            on run argv
                set atts to {}
                repeat with i from 3 to count of argv
                    set end of atts to (POSIX file (item i of argv)) as alias
                end repeat
                tell application id "com.apple.mail"
                    set m to make new outgoing message with properties {subject:(item 1 of argv), content:(item 2 of argv) & return & return, visible:true}
                    tell m to make new to recipient at end of to recipients with properties {address:"%s"}
                    tell content of m
                        repeat with a in atts
                            make new attachment with properties {file name:a} at after last paragraph
                        end repeat
                    end tell
                    activate
                end tell
            end run
            """.formatted(RECIPIENT);

    /** Outlook (classic; the new Outlook has no AppleScript: fallback). */
    private static final String OUTLOOK_SCRIPT = """
            on run argv
                set atts to {}
                repeat with i from 3 to count of argv
                    set end of atts to (POSIX file (item i of argv)) as alias
                end repeat
                tell application id "com.microsoft.Outlook"
                    set m to make new outgoing message with properties {subject:(item 1 of argv), plain text content:(item 2 of argv)}
                    make new recipient at m with properties {email address:{address:"%s"}}
                    repeat with a in atts
                        make new attachment at m with properties {file:a}
                    end repeat
                    open m
                    activate
                end tell
            end run
            """.formatted(RECIPIENT);

    /** Asks the user once for the permission to control the app (automation); denied: false. */
    private static boolean appleScript(String script, String subject, String body, List<File> files) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(List.of("osascript", "-e", script, subject, body));
        files.forEach(f -> cmd.add(f.getAbsolutePath()));
        return run(cmd);
    }

    /**
     * Thunderbird's compose window from the command line; a running
     * Thunderbird takes it over. Not waited for: if Thunderbird was not
     * running, the process is Thunderbird itself.
     */
    private static boolean thunderbird(String executable, String subject, String body, List<File> files) throws IOException {
        if (executable.isEmpty() || !new File(executable).canExecute()) {
            return false;
        }
        StringBuilder attachments = new StringBuilder();
        for (File f : files) {
            if (attachments.length() > 0) {
                attachments.append(',');
            }
            attachments.append(f.toURI());
        }
        String compose = "to='" + RECIPIENT + "',subject='" + composeValue(subject) + "',body='" + composeValue(body) + "'"
                + (files.isEmpty() ? "" : ",attachment='" + attachments + "'");
        new ProcessBuilder(executable, "-compose", compose).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        return true;
    }

    /** A value of -compose: a quote would end it, line breaks are not kept. */
    private static String composeValue(String s) {
        return s.replace("'", "").replaceAll("\\s*\\n\\s*", " ");
    }

    private static List<String> xdgEmail(String subject, String body, List<File> files) {
        List<String> cmd = new ArrayList<>(List.of("xdg-email", "--subject", subject, "--body", body));
        for (File f : files) {
            cmd.add("--attach");
            cmd.add(f.getAbsolutePath());
        }
        cmd.add(RECIPIENT);
        return cmd;
    }

    private static boolean run(List<String> cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = p.waitFor();
        if (exit != 0) {
            LOG.log(Level.FINE, "{0} failed ({1}): {2}", new Object[]{cmd.get(0), exit, out});
        }
        return exit == 0;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
