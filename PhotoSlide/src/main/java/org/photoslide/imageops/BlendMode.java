package org.photoslide.imageops;

/**
 * How the result of a tool (front) is combined with the image below it
 * (back), per channel on values 0..1.
 *
 * Algorithm ported from LightZone (jnisrc/fast_jai/blendFunctions.cpp,
 * BlendOpImage.BlendingMode), Copyright (c) 2005-2011 Fabio Riccardi,
 * BSD-3-Clause, see THIRD-PARTY-NOTICES.md. Formulas after
 * http://www.pegtop.net/delphi/blendmodes/.
 */
public enum BlendMode {

    NORMAL("Normal") {
        @Override
        public float blend(float f, float b) {
            return f;
        }
    },
    AVERAGE("Average") {
        @Override
        public float blend(float f, float b) {
            return (f + b) * 0.5f;
        }
    },
    MULTIPLY("Multiply") {
        @Override
        public float blend(float f, float b) {
            return f * b;
        }
    },
    SCREEN("Screen") {
        @Override
        public float blend(float f, float b) {
            return 1 - (1 - f) * (1 - b);
        }
    },
    DARKEN("Darken") {
        @Override
        public float blend(float f, float b) {
            return Math.min(f, b);
        }
    },
    LIGHTEN("Lighten") {
        @Override
        public float blend(float f, float b) {
            return Math.max(f, b);
        }
    },
    DIFFERENCE("Difference") {
        @Override
        public float blend(float f, float b) {
            return Math.abs(f - b);
        }
    },
    NEGATION("Negation") {
        @Override
        public float blend(float f, float b) {
            return 1 - Math.abs(1 - f - b);
        }
    },
    EXCLUSION("Exclusion") {
        @Override
        public float blend(float f, float b) {
            return f + b - 2 * f * b;
        }
    },
    OVERLAY("Overlay") {
        @Override
        public float blend(float f, float b) {
            return b < 0.5f ? 2 * f * b : 1 - 2 * (1 - f) * (1 - b);
        }
    },
    HARD_LIGHT("Hard Light") {
        @Override
        public float blend(float f, float b) {
            return f < 0.5f ? 2 * f * b : 1 - 2 * (1 - f) * (1 - b);
        }
    },
    SOFT_LIGHT("Soft Light") {
        @Override
        public float blend(float f, float b) {
            float m = f * b;
            float s = 1 - (1 - f) * (1 - b);
            return (1 - b) * m + b * s;
        }
    },
    COLOR_DODGE("Color Dodge") {
        @Override
        public float blend(float f, float b) {
            return f >= 1 ? 1 : Math.min(b / (1 - f), 1);
        }
    },
    COLOR_BURN("Color Burn") {
        @Override
        public float blend(float f, float b) {
            return f <= 0 ? 0 : Math.max(1 - (1 - b) / f, 0);
        }
    },
    SOFT_DODGE("Soft Dodge") {
        @Override
        public float blend(float f, float b) {
            if (f + b < 1) {
                return f >= 1 ? 1 : Math.min(b * 0.5f / (1 - f), 1);
            }
            return Math.max(1 - (1 - f) * 0.5f / b, 0);
        }
    },
    SOFT_BURN("Soft Burn") {
        @Override
        public float blend(float f, float b) {
            if (f + b < 1) {
                return b >= 1 ? 1 : Math.min(f * 0.5f / (1 - b), 1);
            }
            return Math.max(1 - (1 - b) * 0.5f / f, 0);
        }
    },
    /** Only where the image below is dark (low pass on the back value). */
    SHADOWS("Shadows") {
        @Override
        public float blend(float f, float b) {
            return lowPass(f, b, MIDTONES_T, MIDTONES_T / 2);
        }
    },
    /** Everywhere except the deepest shadows. */
    MID_HILIGHTS("Mid+Hilights") {
        @Override
        public float blend(float f, float b) {
            return highPass(f, b, SHADOWS_T, SHADOWS_T / 2);
        }
    },
    /** Only in the midtones (band between shadows and highlights). */
    MIDTONES("Midtones") {
        @Override
        public float blend(float f, float b) {
            return lowPass(highPass(f, b, SHADOWS_T, SHADOWS_T / 2), b, HILIGHTS_T, HILIGHTS_T / 2);
        }
    };

    private static final float HILIGHTS_T = 0.59f;
    private static final float MIDTONES_T = 0.172f;
    private static final float SHADOWS_T = 0.0425f;

    private final String label;

    BlendMode(String label) {
        this.label = label;
    }

    /**
     * @param f front value 0..1 (result of the tool)
     * @param b back value 0..1 (image below)
     * @return the blended value (not yet clamped)
     */
    public abstract float blend(float f, float b);

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }

    private static float lowPass(float f, float b, float threshold, float transition) {
        if (b < threshold - transition) {
            return f;
        }
        if (b > threshold + transition) {
            return b;
        }
        float k = (b - (threshold - transition)) / (2 * transition);
        k *= k;
        return k * b + (1 - k) * f;
    }

    private static float highPass(float f, float b, float threshold, float transition) {
        if (b > threshold + transition) {
            return f;
        }
        if (b < threshold - transition) {
            return b;
        }
        float k = (float) Math.sqrt((b - (threshold - transition)) / (2 * transition));
        return k * f + (1 - k) * b;
    }
}
