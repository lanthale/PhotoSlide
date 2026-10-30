package org.photoslide.editpipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Rectangle2D;
import org.photoslide.imageops.AbstractImageFilter;
import org.photoslide.imageops.Geometry;
import org.photoslide.imageops.ImageFilter;
import org.photoslide.imageops.ParamSpec;
import org.photoslide.datamodel.MediaFile;

/**
 * Undo history of one media file, as in LightZone: every change of the
 * edits is a step, the first step is the original (no edits). Rating and
 * delete mark (set in the lighttable) are steps as well; the original keeps
 * the ones the file had when the history started. Undo, redo or
 * a click on any step restores that state. Changes in quick succession with
 * the same label (dragging a slider) form one step.
 *
 * The history lives as long as the media file is in memory; the sidecar
 * only holds the current state. JavaFX thread only.
 */
public final class EditHistory {

    /** Everything an edit step can change. */
    public record State(List<ImageFilter> filters, Rectangle2D crop, double rotation, Geometry geometry,
            int rating, boolean deleted) {

        static State of(MediaFile f) {
            List<ImageFilter> copy = new ArrayList<>();
            for (ImageFilter filter : f.getFilterList()) {
                copy.add(copyOf(filter));
            }
            copy.sort(Comparator.comparingInt(ImageFilter::getPosition));
            return new State(Collections.unmodifiableList(copy), f.getCropView(),
                    f.getRotationAngleProperty().get(), f.getGeometry(),
                    f.getRatingProperty().get(), f.isDeleted());
        }

        /** No edits, with the rating and delete mark of the opened state. */
        static State original(State opened) {
            return new State(List.of(), null, 0, Geometry.NONE, opened.rating(), opened.deleted());
        }
    }

    public record Entry(String label, State state, long time) {

        @Override
        public String toString() {
            return label;
        }
    }

    /** Changes with the same label closer than this are one step. */
    private static final long MERGE_MS = 1500;
    /** Steps kept per file (the original always stays). */
    private static final int MAX_ENTRIES = 200;

    private static final Map<MediaFile, EditHistory> HISTORIES = new WeakHashMap<>();

    private final ObservableList<Entry> entries = FXCollections.observableArrayList();
    private final ReadOnlyIntegerWrapper current = new ReadOnlyIntegerWrapper(0);
    /** Number of entries that are never merged or dropped (original, opened state). */
    private int fixed;
    private String nextLabel;

    private EditHistory(MediaFile f) {
        State opened = State.of(f);
        State original = State.original(opened);
        entries.add(new Entry("Original", original, 0));
        if (!opened.equals(original)) {
            entries.add(new Entry("Opened with edits", opened, 0));
        }
        fixed = entries.size();
        current.set(entries.size() - 1);
    }

    /**
     * The history of the file; started with its current edits on the first
     * call (the editor and the lighttable call this when they show a file,
     * before it is changed).
     */
    public static EditHistory of(MediaFile f) {
        return HISTORIES.computeIfAbsent(f, EditHistory::new);
    }

    /** @return the history, or null if the file was not edited in the editor yet */
    public static EditHistory peek(MediaFile f) {
        return HISTORIES.get(f);
    }

    /** All steps, oldest (the original) first. */
    public ObservableList<Entry> entries() {
        return entries;
    }

    /** Index of the step the file is in now. */
    public ReadOnlyIntegerProperty currentProperty() {
        return current.getReadOnlyProperty();
    }

    public int current() {
        return current.get();
    }

    public boolean canUndo() {
        return current.get() > 0;
    }

    public boolean canRedo() {
        return current.get() < entries.size() - 1;
    }

    /** Label of the next recorded step (instead of the one derived from the change). */
    void labelNext(String label) {
        nextLabel = label;
    }

    /** Records the current edits of the file as a step, if they changed. */
    void record(MediaFile f) {
        State now = State.of(f);
        Entry top = entries.get(current.get());
        String label = nextLabel != null ? nextLabel : describe(top.state(), now);
        nextLabel = null;
        if (now.equals(top.state())) {
            return;
        }
        // a new change drops the steps that were undone
        if (current.get() < entries.size() - 1) {
            entries.remove(current.get() + 1, entries.size());
        }
        long time = System.currentTimeMillis();
        int last = entries.size() - 1;
        if (last >= fixed && top.label().equals(label) && time - top.time() < MERGE_MS) {
            entries.set(last, new Entry(label, now, time));
        } else {
            entries.add(new Entry(label, now, time));
            if (entries.size() > MAX_ENTRIES) {
                entries.remove(fixed);
            }
        }
        current.set(entries.size() - 1);
    }

    /** Puts the file into the state of the step and lets all views follow. */
    public void goTo(MediaFile f, int index) {
        if (index < 0 || index >= entries.size() || index == current.get()) {
            return;
        }
        current.set(index);
        apply(entries.get(index).state(), f);
        EditPipeline.editsRestored(f);
    }

    public void undo(MediaFile f) {
        goTo(f, current.get() - 1);
    }

    public void redo(MediaFile f) {
        goTo(f, current.get() + 1);
    }

    /** Label of the step undo goes back from, null if there is none. */
    public String undoLabel() {
        return canUndo() ? entries.get(current.get()).label() : null;
    }

    /** Label of the step redo restores, null if there is none. */
    public String redoLabel() {
        return canRedo() ? entries.get(current.get() + 1).label() : null;
    }

    private static void apply(State s, MediaFile f) {
        List<ImageFilter> copy = new ArrayList<>();
        for (ImageFilter filter : s.filters()) {
            copy.add(copyOf(filter));
        }
        f.getFilterList().setAll(copy);
        f.setCropView(s.crop());
        f.setRotationAngle(s.rotation());
        f.setGeometry(s.geometry());
        f.setRating(s.rating());
        f.setDeleted(s.deleted());
    }

    private static ImageFilter copyOf(ImageFilter f) {
        try {
            return (ImageFilter) f.clone();
        } catch (CloneNotSupportedException ex) {
            throw new IllegalStateException(ex);
        }
    }

    // ------------------------------------------------------------------
    // labels
    // ------------------------------------------------------------------
    /** Short text for the change from a to b, as LightZone shows it in the history. */
    static String describe(State a, State b) {
        if (a.deleted() != b.deleted()) {
            return b.deleted() ? "Delete" : "Restore";
        }
        if (a.rating() != b.rating()) {
            return b.rating() == 0 ? "Remove rating" : "Rating " + b.rating() + (b.rating() == 1 ? " star" : " stars");
        }
        if (!Objects.equals(a.crop(), b.crop())) {
            return b.crop() == null ? "Reset crop" : "Crop";
        }
        if (a.rotation() != b.rotation()) {
            return "Rotate";
        }
        if (!a.geometry().equals(b.geometry())) {
            if (a.geometry().flipH() != b.geometry().flipH() || a.geometry().flipV() != b.geometry().flipV()) {
                return "Flip";
            }
            return "Straighten";
        }
        List<ImageFilter> fa = a.filters();
        List<ImageFilter> fb = b.filters();
        if (fb.size() == fa.size() + 1) {
            return "Add " + displayName(missing(fb, fa));
        }
        if (fa.size() == fb.size() + 1) {
            return "Remove " + displayName(missing(fa, fb));
        }
        if (fa.size() != fb.size()) {
            return "Change tools";
        }
        ImageFilter changedA = null;
        ImageFilter changedB = null;
        int changes = 0;
        for (int i = 0; i < fa.size(); i++) {
            if (!fa.get(i).equals(fb.get(i))) {
                changes++;
                changedA = fa.get(i);
                changedB = fb.get(i);
            }
        }
        if (changes != 1) {
            return "Reorder tools";
        }
        return describe(changedA, changedB);
    }

    private static String describe(ImageFilter a, ImageFilter b) {
        String name = displayName(b);
        if (!a.getName().equals(b.getName())) {
            return "Reorder tools";
        }
        if (a.isActive() != b.isActive()) {
            return name + (b.isActive() ? " on" : " off");
        }
        if (a.getOpacity() != b.getOpacity()) {
            return name + ": Opacity";
        }
        if (a.getBlendMode() != b.getBlendMode()) {
            return name + ": Mode";
        }
        if (a instanceof AbstractImageFilter x && b instanceof AbstractImageFilter y) {
            if (!x.getRegion().equals(y.getRegion())) {
                return name + ": Area";
            }
            if (!x.getColorSelection().equals(y.getColorSelection())) {
                return name + ": Color selection";
            }
            float[] va = x.getValues();
            float[] vb = y.getValues();
            for (ParamSpec p : y.params()) {
                for (int i = p.index(); i < p.index() + p.count() && i < va.length && i < vb.length; i++) {
                    if (va[i] != vb[i]) {
                        return name + ": " + p.label();
                    }
                }
            }
        }
        return name;
    }

    /** First filter of list that has no equally named partner in other. */
    private static ImageFilter missing(List<ImageFilter> list, List<ImageFilter> other) {
        List<String> names = new ArrayList<>();
        for (ImageFilter f : other) {
            names.add(f.getName());
        }
        for (ImageFilter f : list) {
            if (!names.remove(f.getName())) {
                return f;
            }
        }
        return list.get(list.size() - 1);
    }

    private static String displayName(ImageFilter f) {
        return f instanceof AbstractImageFilter a ? a.displayName() : f.getName();
    }
}
