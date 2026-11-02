package org.photoslide.browsermetadata;

import com.icafe4j.image.meta.Metadata;
import com.icafe4j.image.meta.MetadataEntry;
import com.icafe4j.image.meta.exif.Exif;
import com.icafe4j.image.meta.iptc.IPTC;
import com.icafe4j.image.meta.xmp.XMP;
import java.util.List;
import java.util.Map;

/**
 * The metadata of one file as read by MetadataController.readMetadata. Each
 * read gets its own result: parallel reads (folder loading, search index,
 * editor) no longer overwrite the metadata of the selected file.
 *
 * @author selfemp
 */
public class MetadataResult {

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
     * All EXIF and IPTC key/value pairs for the search database.
     *
     * @return String in the format key:value;key:value;...
     */
    public String asSearchString() {
        StringBuilder sb = new StringBuilder();
        EntryConsumer append = (key, value) -> sb.append(key).append(":").append(value).append(";");
        forEachEntry(exif, append);
        forEachEntry(iptc, append);
        return sb.toString();
    }

    @FunctionalInterface
    public interface EntryConsumer {

        void accept(String key, String value);
    }
}
