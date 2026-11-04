package org.photoslide;

/**
 * A full-width view of the main window (map, compositions, slideshow, ...).
 * Its FXML is loaded by MainViewController on first use.
 */
public interface ViewModule {

    void injectMainController(MainViewController mainController);

    /** The module is shown (again). */
    default void activate() {
    }

    void shutdown();
}
