package org.photoslide.browsermetadata;

import com.icafe4j.image.meta.Metadata;
import com.icafe4j.image.meta.MetadataEntry;
import com.icafe4j.image.meta.exif.Exif;
import com.icafe4j.image.meta.iptc.IPTC;
import com.icafe4j.image.meta.xmp.XMP;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The metadata of one file as read by MetadataController.readMetadata. Each
 * read gets its own result: parallel reads (folder loading, search index,
 * editor) no longer overwrite the metadata of the selected file.
 *
 * @author selfemp
 */
public class MetadataResult {

    private static final int MAX_SEARCH_STRING = 8000;
    // longer values are binary dumps (maker notes, thumbnails)
    private static final int MAX_SEARCH_VALUE = 200;
    // numbers, also as icafe4j writes them ("[206]", "[1/60]", "[0x03, 0x02]"), and "null"
    private static final Pattern NUMERIC = Pattern.compile("(?i)[-+0-9.,:/ ()x\\[\\]]*|[\\[\\]0-9a-fx, ]*0x[\\[\\]0-9a-fx, ]*|null");

    private Exif exif;
    private IPTC iptc;
    private XMP xmp;
    private List<String> comments;
    // libraw, libheif and video metadata as key/value pairs
    private Map<String, String> raw;

    public Exif getExif() {
        return exif;
    }

    void setExif(Exif exif) {
        this.exif = exif;
    }

    public IPTC getIptc() {
        return iptc;
    }

    void setIptc(IPTC iptc) {
        this.iptc = iptc;
    }

    public XMP getXmp() {
        return xmp;
    }

    void setXmp(XMP xmp) {
        this.xmp = xmp;
    }

    public List<String> getComments() {
        return comments;
    }

    void setComments(List<String> comments) {
        this.comments = comments;
    }

    public Map<String, String> getRaw() {
        return raw;
    }

    void setRaw(Map<String, String> raw) {
        this.raw = raw;
    }

    /**
     * Calls the consumer for each key/value pair: raw metadata sorted by key,
     * then EXIF, IPTC and XMP.
     */
    public void forEachEntry(EntryConsumer consumer) {
        if (raw != null) {
            raw.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(e -> consumer.accept(e.getKey(), e.getValue()));
        }
        forEachEntry(exif, consumer);
        forEachEntry(iptc, consumer);
        forEachEntry(xmp, consumer);
    }

    private static void forEachEntry(Metadata meta, EntryConsumer consumer) {
        if (meta == null) {
            return;
        }
        for (MetadataEntry item : meta) {
            for (MetadataEntry e : item.getMetadataEntries()) {
                consumer.accept(e.getKey(), e.getValue());
            }
        }
    }

    /**
     * The words of the EXIF and IPTC values for the full text index of the
     * search database: no keys (they are the same for every file), no pure
     * numbers, each value once.
     *
     * @return the values separated by ";", at most MAX_SEARCH_STRING chars
     */
    public String asSearchString() {
        Set<String> values = new LinkedHashSet<>();
        EntryConsumer append = (key, value) -> {
            if (value != null) {
                String v = value.trim();
                if (!v.isEmpty() && v.length() <= MAX_SEARCH_VALUE && !NUMERIC.matcher(v).matches()) {
                    values.add(v);
                }
            }
        };
        forEachEntry(exif, append);
        forEachEntry(iptc, append);
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            if (sb.length() + v.length() + 1 > MAX_SEARCH_STRING) {
                break;
            }
            sb.append(v).append(';');
        }
        return sb.toString();
    }

    @FunctionalInterface
    public interface EntryConsumer {

        void accept(String key, String value);
    }
}
