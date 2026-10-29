flowchart TD
    subgraph Einstellungen
        PROF["RAW-Profile pro Kamera<br/>rawdecodersettings.json<br/>(Default + 'Hersteller Modell')"]
        XMP["XMP-Sidecar pro Foto<br/>Tools, Zuschnitt, Drehung,<br/>Spiegeln, Geradestellen"]
        HIST["EditHistory<br/>(Undo/Redo, nur im Speicher)"]
    end

    subgraph Quelle["1 Quelle laden (EditPipeline.loadSource)"]
        RAW["RAW-Datei<br/>(NEF, CR2, RAF, DNG, X3F ...)"]
        LIBRAW["LibRaw via LibRawFX<br/>16 Bit, linear, output_color = 4 (ProPhoto)<br/>Weissabgleich, Demosaicing aus dem Profil"]
        OTHER["JPEG / PNG / TIFF / HEIF"]
        SRGB["JavaFX bzw. ImageIO (16-Bit-TIFF/PNG)<br/>sRGB dekodieren, linear machen,<br/>Matrix sRGB -> ProPhoto"]
        RAW --> LIBRAW
        OTHER --> SRGB
    end

    subgraph Look["2 RAW-Grundlook (RawLook, nur RAW)"]
        L1["Farbmatrix aus ColorChecker<br/>(ColorCalibration)"]
        L2["Farbverlaufs-Korrektur<br/>(ColorShading, Stärke-Regler)"]
        L3["Belichtung: Base exposure<br/>+ DNG BaselineExposure<br/>+ ISO-Verstärkung (nur X3F)"]
        L4["Lichter weich auslaufen lassen<br/>+ Tonkurve (Linear / Medium / Strong)"]
        L1 --> L2 --> L3 --> L4
    end

    CACHE[("Quellen-Cache<br/>2 Vollbilder, Image16")]

    subgraph Bearbeitung["3 Bearbeitung (FilterChain16)"]
        TOOLS["Tools in Stapel-Reihenfolge<br/>Relight, Zones, Sharpen, Styles ...<br/>je mit Bereich/Farbauswahl,<br/>Deckkraft, Blend-Modus"]
        GEO["Geometrie (Geometry)<br/>Spiegeln + Geradestellen<br/>(gleiche Bildgrösse)"]
        TOOLS --> GEO
    end

    subgraph Ausgabe["4 Ausgabe"]
        EDIT["Editor + Lighttable-Vorschau<br/>ImagePreview16: Proxy in Bildschirmgrösse,<br/>Prefix-Cache beim Slider-Ziehen,<br/>danach volle Auflösung"]
        VIEW["Zuschnitt + Zoom = Viewport<br/>Vierteldrehung = ImageView-Rotation"]
        THUMB["Vorschaubilder in allen Rastern<br/>EditPipeline.loadRawThumbnail (halbe Grösse)<br/>+ MediaFile.setFilters (Tools)"]
        PRINT["Drucken<br/>EditPipeline.renderFinal (3000 px)<br/>inkl. Zuschnitt + Drehung"]
        EXPORT["Export<br/>EditPipeline.renderForExport (Vollbild)<br/>danach Zuschnitt + Drehung"]
        DISP["Display16<br/>ProPhoto -> sRGB, sRGB-Kurve, 8 Bit"]
    end

    PROF --> LIBRAW
    PROF --> Look
    LIBRAW --> Look
    L4 --> CACHE
    SRGB --> CACHE
    CACHE --> TOOLS
    XMP --> TOOLS
    XMP --> GEO
    HIST <--> XMP
    GEO --> EDIT --> VIEW
    GEO --> PRINT
    GEO --> EXPORT
    LIBRAW -.->|halbe Grösse| THUMB
    VIEW --> DISP
    THUMB --> DISP
    PRINT --> DISP
    EXPORT --> DISP
rendering...
