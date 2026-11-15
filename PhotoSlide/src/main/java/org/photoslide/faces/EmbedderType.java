package org.photoslide.faces;

/** The face recognizers to choose from (preferences). */
public enum EmbedderType {

    /** AuraFace (ResNet100 ArcFace, ONNX Runtime): accurate, the default. */
    AURAFACE("auraface", 512, 0.30),
    /** SFace (OpenCV): small and fast, less accurate. */
    SFACE("sface", 128, 0.363);

    private final String id;
    private final int dimension;
    private final double matchThreshold;

    EmbedderType(String id, int dimension, double matchThreshold) {
        this.id = id;
        this.dimension = dimension;
        this.matchThreshold = matchThreshold;
    }

    /** Stored with the feature vectors in the search database. */
    public String id() {
        return id;
    }

    /** Length of the feature vector. */
    public int dimension() {
        return dimension;
    }

    /** Minimum cosine similarity for a name suggestion. */
    public double matchThreshold() {
        return matchThreshold;
    }
}
