package org.photoslide.preferences;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.librawdecodersettings.RawDecoderSettings;

/**
 * JavaFX-Property-Spiegel von {@link RawDecoderSettings} (LibRawFX 2.0.1).
 * Enthaelt genau die Felder, die RawDecoderToNativeTranslator an LibRaw
 * uebergibt. Thread-Safety: fuer jeden Decode/Save ein frisches Objekt ueber
 * {@link #toRawDecoderSettings()} erzeugen (LibRawFX-Konvention).
 */
public class RawDecoderSettingsFx {

    private final StringProperty whiteBalance = new SimpleStringProperty(this, "whiteBalance", "CAMERA");
    private final BooleanProperty autoWhitebalance = new SimpleBooleanProperty(this, "autoWhitebalance", false);
    private final BooleanProperty outputTiff = new SimpleBooleanProperty(this, "outputTiff", false);
    private final BooleanProperty halfSizeOutput = new SimpleBooleanProperty(this, "halfSizeOutput", false);
    private final IntegerProperty rawQuality = new SimpleIntegerProperty(this, "rawQuality", 1);
    private final BooleanProperty autoBrightness = new SimpleBooleanProperty(this, "autoBrightness", true);
    private final DoubleProperty autoBrightnessThreshold = new SimpleDoubleProperty(this, "autoBrightnessThreshold", 0.01);
    private final DoubleProperty brightNess = new SimpleDoubleProperty(this, "brightNess", 1.0);
    private final BooleanProperty enableExposureCorrection = new SimpleBooleanProperty(this, "enableExposureCorrection", false);
    private final IntegerProperty exposureCorrection = new SimpleIntegerProperty(this, "exposureCorrection", 1);
    private final DoubleProperty exposureShift = new SimpleDoubleProperty(this, "exposureShift", 1.0);
    private final DoubleProperty expoCorrectionHighlight = new SimpleDoubleProperty(this, "expoCorrectionHighlight", 0.0);
    private final IntegerProperty noiseReduction = new SimpleIntegerProperty(this, "noiseReduction", 0);
    private final BooleanProperty fixColorsHighlights = new SimpleBooleanProperty(this, "fixColorsHighlights", false);
    private final BooleanProperty sixteenBitsImage = new SimpleBooleanProperty(this, "sixteenBitsImage", false);
    private final IntegerProperty outputColorSpace = new SimpleIntegerProperty(this, "outputColorSpace", 1);
    private final IntegerProperty saturation = new SimpleIntegerProperty(this, "saturation", 0);
    private final IntegerProperty medianFilterPasses = new SimpleIntegerProperty(this, "medianFilterPasses", 0);
    private final BooleanProperty enableBlackPoint = new SimpleBooleanProperty(this, "enableBlackPoint", false);
    private final IntegerProperty blackPoint = new SimpleIntegerProperty(this, "blackPoint", 0);

    /** Frisches Modell mit den Default-Werten (entspricht "new RawDecoderSettingsFx()"). */
    public static RawDecoderSettingsFx defaults() {
        return new RawDecoderSettingsFx();
    }

    /** Erzeugt ein konfiguriertes, fuer LibRawFX-Map geeignetes Objekt. */
    public RawDecoderSettings toRawDecoderSettings() {
        RawDecoderSettings s = new RawDecoderSettings();
        s.setWhiteBalance(getWhiteBalance());
        s.setAutoWhitebalance(isAutoWhitebalance());
        s.setOutput_tiff(isOutputTiff());
        s.setHalfSizeOutput(isHalfSizeOutput());
        s.setRAWQuality(getRawQuality());
        s.setAutoBrightness(isAutoBrightness());
        s.setAutoBrightnessThreashold((float) getAutoBrightnessThreshold());
        s.setBrightNess((float) getBrightNess());
        s.setEnableExposureCorrection(isEnableExposureCorrection());
        s.setExposureCorrection(getExposureCorrection());
        s.setExposureShift((float) getExposureShift());
        s.setExpoCorrectionHighlight((float) getExpoCorrectionHighlight());
        s.setNoiseReduction(getNoiseReduction());
        s.setFixColorsHighlights(isFixColorsHighlights());
        s.setSixteenBitsImage(isSixteenBitsImage());
        s.setOutputColorSpace(getOutputColorSpace());
        s.setSaturation(getSaturation());
        s.setMedianFilterPasses(getMedianFilterPasses());
        s.setEnableBlackPoint(isEnableBlackPoint());
        s.setBlackPoint(getBlackPoint());
        return s;
    }

    /** Laedt die Werte aus einem RawDecoderSettings (z.B. geladenes Profil). */
    public void fromRawDecoderSettings(RawDecoderSettings s) {
        setWhiteBalance(s.getWhiteBalance());
        setAutoWhitebalance(s.isAutoWhitebalance());
        setOutputTiff(s.isOutput_tiff());
        setHalfSizeOutput(s.isHalfSizeOutput());
        setRawQuality(s.getRAWQuality());
        setAutoBrightness(s.isAutoBrightness());
        setAutoBrightnessThreshold(s.getAutoBrightnessThreashold());
        setBrightNess(s.getBrightNess());
        setEnableExposureCorrection(s.isEnableExposureCorrection());
        setExposureCorrection(s.getExposureCorrection());
        setExposureShift(s.getExposureShift());
        setExpoCorrectionHighlight(s.getExpoCorrectionHighlight());
        setNoiseReduction(s.getNoiseReduction());
        setFixColorsHighlights(s.isFixColorsHighlights());
        setSixteenBitsImage(s.isSixteenBitsImage());
        setOutputColorSpace(s.getOutputColorSpace());
        setSaturation(s.getSaturation());
        setMedianFilterPasses(s.getMedianFilterPasses());
        setEnableBlackPoint(s.isEnableBlackPoint());
        setBlackPoint(s.getBlackPoint());
    }

    // --- Getter/Setter (fuer UI-Bindings und Jackson-Serialisierung) ---

    public StringProperty whiteBalanceProperty() { return whiteBalance; }
    public String getWhiteBalance() { return whiteBalance.get(); }
    public void setWhiteBalance(String v) { whiteBalance.set(v); }

    public BooleanProperty autoWhitebalanceProperty() { return autoWhitebalance; }
    public boolean isAutoWhitebalance() { return autoWhitebalance.get(); }
    public void setAutoWhitebalance(boolean v) { autoWhitebalance.set(v); }

    public BooleanProperty outputTiffProperty() { return outputTiff; }
    public boolean isOutputTiff() { return outputTiff.get(); }
    public void setOutputTiff(boolean v) { outputTiff.set(v); }

    public BooleanProperty halfSizeOutputProperty() { return halfSizeOutput; }
    public boolean isHalfSizeOutput() { return halfSizeOutput.get(); }
    public void setHalfSizeOutput(boolean v) { halfSizeOutput.set(v); }

    public IntegerProperty rawQualityProperty() { return rawQuality; }
    public int getRawQuality() { return rawQuality.get(); }
    public void setRawQuality(int v) { rawQuality.set(v); }

    public BooleanProperty autoBrightnessProperty() { return autoBrightness; }
    public boolean isAutoBrightness() { return autoBrightness.get(); }
    public void setAutoBrightness(boolean v) { autoBrightness.set(v); }

    public DoubleProperty autoBrightnessThresholdProperty() { return autoBrightnessThreshold; }
    public double getAutoBrightnessThreshold() { return autoBrightnessThreshold.get(); }
    public void setAutoBrightnessThreshold(double v) { autoBrightnessThreshold.set(v); }

    public DoubleProperty brightNessProperty() { return brightNess; }
    public double getBrightNess() { return brightNess.get(); }
    public void setBrightNess(double v) { brightNess.set(v); }

    public BooleanProperty enableExposureCorrectionProperty() { return enableExposureCorrection; }
    public boolean isEnableExposureCorrection() { return enableExposureCorrection.get(); }
    public void setEnableExposureCorrection(boolean v) { enableExposureCorrection.set(v); }

    public IntegerProperty exposureCorrectionProperty() { return exposureCorrection; }
    public int getExposureCorrection() { return exposureCorrection.get(); }
    public void setExposureCorrection(int v) { exposureCorrection.set(v); }

    public DoubleProperty exposureShiftProperty() { return exposureShift; }
    public double getExposureShift() { return exposureShift.get(); }
    public void setExposureShift(double v) { exposureShift.set(v); }

    public DoubleProperty expoCorrectionHighlightProperty() { return expoCorrectionHighlight; }
    public double getExpoCorrectionHighlight() { return expoCorrectionHighlight.get(); }
    public void setExpoCorrectionHighlight(double v) { expoCorrectionHighlight.set(v); }

    public IntegerProperty noiseReductionProperty() { return noiseReduction; }
    public int getNoiseReduction() { return noiseReduction.get(); }
    public void setNoiseReduction(int v) { noiseReduction.set(v); }

    public BooleanProperty fixColorsHighlightsProperty() { return fixColorsHighlights; }
    public boolean isFixColorsHighlights() { return fixColorsHighlights.get(); }
    public void setFixColorsHighlights(boolean v) { fixColorsHighlights.set(v); }

    public BooleanProperty sixteenBitsImageProperty() { return sixteenBitsImage; }
    public boolean isSixteenBitsImage() { return sixteenBitsImage.get(); }
    public void setSixteenBitsImage(boolean v) { sixteenBitsImage.set(v); }

    public IntegerProperty outputColorSpaceProperty() { return outputColorSpace; }
    public int getOutputColorSpace() { return outputColorSpace.get(); }
    public void setOutputColorSpace(int v) { outputColorSpace.set(v); }

    public IntegerProperty saturationProperty() { return saturation; }
    public int getSaturation() { return saturation.get(); }
    public void setSaturation(int v) { saturation.set(v); }

    public IntegerProperty medianFilterPassesProperty() { return medianFilterPasses; }
    public int getMedianFilterPasses() { return medianFilterPasses.get(); }
    public void setMedianFilterPasses(int v) { medianFilterPasses.set(v); }

    public BooleanProperty enableBlackPointProperty() { return enableBlackPoint; }
    public boolean isEnableBlackPoint() { return enableBlackPoint.get(); }
    public void setEnableBlackPoint(boolean v) { enableBlackPoint.set(v); }

    public IntegerProperty blackPointProperty() { return blackPoint; }
    public int getBlackPoint() { return blackPoint.get(); }
    public void setBlackPoint(int v) { blackPoint.set(v); }
}