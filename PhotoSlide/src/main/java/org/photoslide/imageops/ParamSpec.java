package org.photoslide.imageops;

import java.util.List;

/**
 * Describes one setting of a tool, so the editor can build its controls
 * generically. The values live in the float[] of the filter at
 * [index, index + count).
 *
 * @param key stable identifier (not shown)
 * @param label text shown next to the control
 * @param kind type of control
 * @param index first position in the values array
 * @param count number of values (1, 3 for a colour, 16 for zones)
 * @param min smallest value (sliders)
 * @param max largest value (sliders)
 * @param step step of the slider / keyboard increment
 * @param defaults default values (length = count)
 * @param choices texts of a CHOICE (value = index), otherwise empty
 */
public record ParamSpec(String key, String label, Kind kind, int index, int count,
        float min, float max, float step, float[] defaults, List<String> choices) {

    public enum Kind {
        /** number with slider */
        SLIDER,
        /** on/off, 0 or 1 */
        CHECK,
        /** one of several texts, value = index */
        CHOICE,
        /** colour chosen with a colour picker: sRGB encoded 0..1, 3 values */
        COLOR,
        /** colour picked from the image with the dropper: linear working space, 3 values, -1 = none */
        PICK,
        /** zone mapper: count points, -1 = not set, otherwise 0..1 */
        ZONES
    }

    public static ParamSpec slider(String key, String label, int index, float min, float max,
            float def, float step) {
        return new ParamSpec(key, label, Kind.SLIDER, index, 1, min, max, step, new float[]{def}, List.of());
    }

    public static ParamSpec check(String key, String label, int index, boolean def) {
        return new ParamSpec(key, label, Kind.CHECK, index, 1, 0, 1, 1, new float[]{def ? 1 : 0}, List.of());
    }

    public static ParamSpec choice(String key, String label, int index, int def, List<String> choices) {
        return new ParamSpec(key, label, Kind.CHOICE, index, 1, 0, choices.size() - 1, 1,
                new float[]{def}, List.copyOf(choices));
    }

    public static ParamSpec color(String key, String label, int index, float r, float g, float b) {
        return new ParamSpec(key, label, Kind.COLOR, index, 3, 0, 1, 0.01f, new float[]{r, g, b}, List.of());
    }

    public static ParamSpec pick(String key, String label, int index) {
        return new ParamSpec(key, label, Kind.PICK, index, 3, 0, 1, 0.01f, new float[]{-1, -1, -1}, List.of());
    }

    public static ParamSpec zones(String key, String label, int index, int count) {
        float[] def = new float[count];
        java.util.Arrays.fill(def, -1f);
        return new ParamSpec(key, label, Kind.ZONES, index, count, 0, 1, 0.005f, def, List.of());
    }
}
