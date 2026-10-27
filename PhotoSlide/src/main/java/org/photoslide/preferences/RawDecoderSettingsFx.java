package org.photoslide.preferences;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import java.util.List;
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
    private final DoubleProperty exposureShift = new SimpleDoubleProperty(this, "exposureShift", 1.0);
    private final DoubleProperty expoCorrectionHighlight = new SimpleDoubleProperty(this, "expoCorrectionHighlight", 0.0);
    private final IntegerProperty noiseReduction = new SimpleIntegerProperty(this, "noiseReduction", 0);
    private final BooleanProperty fixColorsHighlights = new SimpleBooleanProperty(this, "fixColorsHighlights", false);
    private final BooleanProperty sixteenBitsImage = new SimpleBooleanProperty(this, "sixteenBitsImage", false);
    private final IntegerProperty outputColorSpace = new SimpleIntegerProperty(this, "outputColorSpace", 1);
    private final IntegerProperty saturation = new SimpleIntegerProperty(this, "saturation", -1);
    private final IntegerProperty medianFilterPasses = new SimpleIntegerProperty(this, "medianFilterPasses", 0);
    private final BooleanProperty enableBlackPoint = new SimpleBooleanProperty(this, "enableBlackPoint", false);
    private final IntegerProperty blackPoint = new SimpleIntegerProperty(this, "blackPoint", 0);

    // --- Neu in LibRawFX 2.x (alles vom Translator uebersetzt) ---
    private final BooleanProperty useUserMultipliers = new SimpleBooleanProperty(this, "useUserMultipliers", false);
    private final DoubleProperty userMulR = new SimpleDoubleProperty(this, "userMulR", 1.0);
    private final DoubleProperty userMulG1 = new SimpleDoubleProperty(this, "userMulG1", 1.0);
    private final DoubleProperty userMulB = new SimpleDoubleProperty(this, "userMulB", 1.0);
    private final DoubleProperty userMulG2 = new SimpleDoubleProperty(this, "userMulG2", 1.0);
    private final BooleanProperty useGreyBox = new SimpleBooleanProperty(this, "useGreyBox", false);
    private final IntegerProperty greyBoxX = new SimpleIntegerProperty(this, "greyBoxX", 0);
    private final IntegerProperty greyBoxY = new SimpleIntegerProperty(this, "greyBoxY", 0);
    private final IntegerProperty greyBoxW = new SimpleIntegerProperty(this, "greyBoxW", 0);
    private final IntegerProperty greyBoxH = new SimpleIntegerProperty(this, "greyBoxH", 0);

    private final BooleanProperty interpolate4Colors = new SimpleBooleanProperty(this, "interpolate4Colors", false);
    private final BooleanProperty greenMatching = new SimpleBooleanProperty(this, "greenMatching", false);
    private final IntegerProperty nrThreshold = new SimpleIntegerProperty(this, "nrThreshold", 0);
    private final IntegerProperty dcbIterations = new SimpleIntegerProperty(this, "dcbIterations", -1);
    private final BooleanProperty dcbEnhanceFl = new SimpleBooleanProperty(this, "dcbEnhanceFl", false);
    private final DoubleProperty aberRed = new SimpleDoubleProperty(this, "aberRed", 1.0);
    private final DoubleProperty aberBlue = new SimpleDoubleProperty(this, "aberBlue", 1.0);

    private final IntegerProperty unclipColors = new SimpleIntegerProperty(this, "unclipColors", 0);
    private final DoubleProperty adjustMaximumThreshold = new SimpleDoubleProperty(this, "adjustMaximumThreshold", 0.75);
    private final BooleanProperty useCustomGamma = new SimpleBooleanProperty(this, "useCustomGamma", false);
    private final DoubleProperty gammaPower = new SimpleDoubleProperty(this, "gammaPower", 0.45);
    private final DoubleProperty gammaToeSlope = new SimpleDoubleProperty(this, "gammaToeSlope", 4.5);

    private final BooleanProperty useChannelBlack = new SimpleBooleanProperty(this, "useChannelBlack", false);
    private final IntegerProperty channelBlack0 = new SimpleIntegerProperty(this, "channelBlack0", 0);
    private final IntegerProperty channelBlack1 = new SimpleIntegerProperty(this, "channelBlack1", 0);
    private final IntegerProperty channelBlack2 = new SimpleIntegerProperty(this, "channelBlack2", 0);
    private final IntegerProperty channelBlack3 = new SimpleIntegerProperty(this, "channelBlack3", 0);

    private final IntegerProperty useCameraMatrix = new SimpleIntegerProperty(this, "useCameraMatrix", -1);

    private final IntegerProperty userFlip = new SimpleIntegerProperty(this, "userFlip", -1);
    private final BooleanProperty dontStretchPixels = new SimpleBooleanProperty(this, "dontStretchPixels", false);
    private final BooleanProperty useCropBox = new SimpleBooleanProperty(this, "useCropBox", false);
    private final IntegerProperty cropBoxX = new SimpleIntegerProperty(this, "cropBoxX", 0);
    private final IntegerProperty cropBoxY = new SimpleIntegerProperty(this, "cropBoxY", 0);
    private final IntegerProperty cropBoxW = new SimpleIntegerProperty(this, "cropBoxW", 0);
    private final IntegerProperty cropBoxH = new SimpleIntegerProperty(this, "cropBoxH", 0);
    private final IntegerProperty shotSelect = new SimpleIntegerProperty(this, "shotSelect", 0);

    private final DoubleProperty coolScanNEFGamma = new SimpleDoubleProperty(this, "coolScanNEFGamma", 1.0);
    private final IntegerProperty rawProcessingOptions = new SimpleIntegerProperty(this, "rawProcessingOptions", 0);
    private final StringProperty outputProfilePath = new SimpleStringProperty(this, "outputProfilePath", "");
    private final StringProperty cameraProfilePath = new SimpleStringProperty(this, "cameraProfilePath", "");
    private final StringProperty badPixelsPath = new SimpleStringProperty(this, "badPixelsPath", "");
    private final StringProperty darkFramePath = new SimpleStringProperty(this, "darkFramePath", "");

    /** Frisches Modell mit den Default-Werten (entspricht "new RawDecoderSettingsFx()"). */
    public static RawDecoderSettingsFx defaults() {
        return new RawDecoderSettingsFx();
    }

    /** Alle Properties (fuer Live-Listener: Automatische Preview bei Aenderung). */
    public List<Property<?>> properties() {
        return List.of(whiteBalance, autoWhitebalance, outputTiff, halfSizeOutput, rawQuality,
                autoBrightness, autoBrightnessThreshold, brightNess, enableExposureCorrection,
                exposureShift, expoCorrectionHighlight, noiseReduction, nrThreshold,
                fixColorsHighlights, unclipColors, adjustMaximumThreshold, useCustomGamma,
                gammaPower, gammaToeSlope, sixteenBitsImage, outputColorSpace, useCameraMatrix,
                saturation, medianFilterPasses, enableBlackPoint, blackPoint,
                useUserMultipliers, userMulR, userMulG1, userMulB, userMulG2,
                useGreyBox, greyBoxX, greyBoxY, greyBoxW, greyBoxH,
                interpolate4Colors, greenMatching, dcbIterations, dcbEnhanceFl,
                aberRed, aberBlue, useChannelBlack, channelBlack0, channelBlack1,
                channelBlack2, channelBlack3, userFlip, dontStretchPixels,
                useCropBox, cropBoxX, cropBoxY, cropBoxW, cropBoxH, shotSelect,
                coolScanNEFGamma, rawProcessingOptions, outputProfilePath,
                cameraProfilePath, badPixelsPath, darkFramePath);
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
        // setExposureCorrection entfaellt: im Settings-Bean von enableExposureCorrection abgeleitet
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
        // --- Neu in LibRawFX 2.x ---
        s.setUseUserMultipliers(isUseUserMultipliers());
        s.setUserMultipliers((float) getUserMulR(), (float) getUserMulG1(), (float) getUserMulB(), (float) getUserMulG2());
        s.setUseGreyBox(isUseGreyBox());
        s.setGreyBox(getGreyBoxX(), getGreyBoxY(), getGreyBoxW(), getGreyBoxH());
        s.setRGBInterpolate4Colors(isInterpolate4Colors());
        s.setGreenMatching(isGreenMatching());
        s.setNRThreshold(getNrThreshold());
        s.setDcbIterations(getDcbIterations());
        s.setDcbEnhanceFl(isDcbEnhanceFl());
        s.setAber(getAberRed(), getAberBlue());
        s.setUnclipColors(getUnclipColors());
        s.setAdjustMaximumThreshold((float) getAdjustMaximumThreshold());
        s.setUseCustomGamma(isUseCustomGamma());
        s.setGamma(getGammaPower(), getGammaToeSlope());
        s.setUseChannelBlack(isUseChannelBlack());
        s.setChannelBlack(getChannelBlack0(), getChannelBlack1(), getChannelBlack2(), getChannelBlack3());
        s.setUseCameraMatrix(getUseCameraMatrix());
        s.setUserFlip(getUserFlip());
        s.setDontStretchPixels(isDontStretchPixels());
        s.setUseCropBox(isUseCropBox());
        s.setCropBox(getCropBoxX(), getCropBoxY(), getCropBoxW(), getCropBoxH());
        s.setShotSelect(getShotSelect());
        s.setCoolScanNEFGamma((float) getCoolScanNEFGamma());
        s.setRawProcessingOptions(getRawProcessingOptions());
        s.setOutputProfilePath(getOutputProfilePath().isBlank() ? null : getOutputProfilePath());
        s.setCameraProfilePath(getCameraProfilePath().isBlank() ? null : getCameraProfilePath());
        s.setBadPixelsPath(getBadPixelsPath().isBlank() ? null : getBadPixelsPath());
        s.setDarkFramePath(getDarkFramePath().isBlank() ? null : getDarkFramePath());
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
        // --- Neu in LibRawFX 2.x ---
        setUseUserMultipliers(s.isUseUserMultipliers());
        float[] mul = s.getUserMultipliers();
        setUserMulR(mul[0]);
        setUserMulG1(mul[1]);
        setUserMulB(mul[2]);
        setUserMulG2(mul[3]);
        setUseGreyBox(s.isUseGreyBox());
        int[] gb = s.getGreyBox();
        setGreyBoxX(gb[0]);
        setGreyBoxY(gb[1]);
        setGreyBoxW(gb[2]);
        setGreyBoxH(gb[3]);
        setInterpolate4Colors(s.isRGBInterpolate4Colors());
        setGreenMatching(s.isGreenMatching());
        setNrThreshold(s.getNRThreshold());
        setDcbIterations(s.getDcbIterations());
        setDcbEnhanceFl(s.isDcbEnhanceFl());
        setAberRed(s.getAberRed());
        setAberBlue(s.getAberBlue());
        setUnclipColors(s.getUnclipColors());
        setAdjustMaximumThreshold(s.getAdjustMaximumThreshold());
        setUseCustomGamma(s.isUseCustomGamma());
        setGammaPower(s.getGammaPower());
        setGammaToeSlope(s.getGammaToeSlope());
        setUseChannelBlack(s.isUseChannelBlack());
        int[] cb = s.getChannelBlack();
        setChannelBlack0(cb[0]);
        setChannelBlack1(cb[1]);
        setChannelBlack2(cb[2]);
        setChannelBlack3(cb[3]);
        setUseCameraMatrix(s.getUseCameraMatrix());
        setUserFlip(s.getUserFlip());
        setDontStretchPixels(s.isDontStretchPixels());
        setUseCropBox(s.isUseCropBox());
        int[] cbx = s.getCropBox();
        setCropBoxX(cbx[0]);
        setCropBoxY(cbx[1]);
        setCropBoxW(cbx[2]);
        setCropBoxH(cbx[3]);
        setShotSelect(s.getShotSelect());
        setCoolScanNEFGamma(s.getCoolScanNEFGamma());
        setRawProcessingOptions(s.getRawProcessingOptions());
        setOutputProfilePath(s.getOutputProfilePath() == null ? "" : s.getOutputProfilePath());
        setCameraProfilePath(s.getCameraProfilePath() == null ? "" : s.getCameraProfilePath());
        setBadPixelsPath(s.getBadPixelsPath() == null ? "" : s.getBadPixelsPath());
        setDarkFramePath(s.getDarkFramePath() == null ? "" : s.getDarkFramePath());
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

    // --- Getter/Setter der neuen LibRawFX-2.x-Felder ---

    public BooleanProperty useUserMultipliersProperty() { return useUserMultipliers; }
    public boolean isUseUserMultipliers() { return useUserMultipliers.get(); }
    public void setUseUserMultipliers(boolean v) { useUserMultipliers.set(v); }

    public DoubleProperty userMulRProperty() { return userMulR; }
    public double getUserMulR() { return userMulR.get(); }
    public void setUserMulR(double v) { userMulR.set(v); }

    public DoubleProperty userMulG1Property() { return userMulG1; }
    public double getUserMulG1() { return userMulG1.get(); }
    public void setUserMulG1(double v) { userMulG1.set(v); }

    public DoubleProperty userMulBProperty() { return userMulB; }
    public double getUserMulB() { return userMulB.get(); }
    public void setUserMulB(double v) { userMulB.set(v); }

    public DoubleProperty userMulG2Property() { return userMulG2; }
    public double getUserMulG2() { return userMulG2.get(); }
    public void setUserMulG2(double v) { userMulG2.set(v); }

    public BooleanProperty useGreyBoxProperty() { return useGreyBox; }
    public boolean isUseGreyBox() { return useGreyBox.get(); }
    public void setUseGreyBox(boolean v) { useGreyBox.set(v); }

    public IntegerProperty greyBoxXProperty() { return greyBoxX; }
    public int getGreyBoxX() { return greyBoxX.get(); }
    public void setGreyBoxX(int v) { greyBoxX.set(v); }

    public IntegerProperty greyBoxYProperty() { return greyBoxY; }
    public int getGreyBoxY() { return greyBoxY.get(); }
    public void setGreyBoxY(int v) { greyBoxY.set(v); }

    public IntegerProperty greyBoxWProperty() { return greyBoxW; }
    public int getGreyBoxW() { return greyBoxW.get(); }
    public void setGreyBoxW(int v) { greyBoxW.set(v); }

    public IntegerProperty greyBoxHProperty() { return greyBoxH; }
    public int getGreyBoxH() { return greyBoxH.get(); }
    public void setGreyBoxH(int v) { greyBoxH.set(v); }

    public BooleanProperty interpolate4ColorsProperty() { return interpolate4Colors; }
    public boolean isInterpolate4Colors() { return interpolate4Colors.get(); }
    public void setInterpolate4Colors(boolean v) { interpolate4Colors.set(v); }

    public BooleanProperty greenMatchingProperty() { return greenMatching; }
    public boolean isGreenMatching() { return greenMatching.get(); }
    public void setGreenMatching(boolean v) { greenMatching.set(v); }

    public IntegerProperty nrThresholdProperty() { return nrThreshold; }
    public int getNrThreshold() { return nrThreshold.get(); }
    public void setNrThreshold(int v) { nrThreshold.set(v); }

    public IntegerProperty dcbIterationsProperty() { return dcbIterations; }
    public int getDcbIterations() { return dcbIterations.get(); }
    public void setDcbIterations(int v) { dcbIterations.set(v); }

    public BooleanProperty dcbEnhanceFlProperty() { return dcbEnhanceFl; }
    public boolean isDcbEnhanceFl() { return dcbEnhanceFl.get(); }
    public void setDcbEnhanceFl(boolean v) { dcbEnhanceFl.set(v); }

    public DoubleProperty aberRedProperty() { return aberRed; }
    public double getAberRed() { return aberRed.get(); }
    public void setAberRed(double v) { aberRed.set(v); }

    public DoubleProperty aberBlueProperty() { return aberBlue; }
    public double getAberBlue() { return aberBlue.get(); }
    public void setAberBlue(double v) { aberBlue.set(v); }

    public IntegerProperty unclipColorsProperty() { return unclipColors; }
    public int getUnclipColors() { return unclipColors.get(); }
    public void setUnclipColors(int v) { unclipColors.set(v); }

    public DoubleProperty adjustMaximumThresholdProperty() { return adjustMaximumThreshold; }
    public double getAdjustMaximumThreshold() { return adjustMaximumThreshold.get(); }
    public void setAdjustMaximumThreshold(double v) { adjustMaximumThreshold.set(v); }

    public BooleanProperty useCustomGammaProperty() { return useCustomGamma; }
    public boolean isUseCustomGamma() { return useCustomGamma.get(); }
    public void setUseCustomGamma(boolean v) { useCustomGamma.set(v); }

    public DoubleProperty gammaPowerProperty() { return gammaPower; }
    public double getGammaPower() { return gammaPower.get(); }
    public void setGammaPower(double v) { gammaPower.set(v); }

    public DoubleProperty gammaToeSlopeProperty() { return gammaToeSlope; }
    public double getGammaToeSlope() { return gammaToeSlope.get(); }
    public void setGammaToeSlope(double v) { gammaToeSlope.set(v); }

    public BooleanProperty useChannelBlackProperty() { return useChannelBlack; }
    public boolean isUseChannelBlack() { return useChannelBlack.get(); }
    public void setUseChannelBlack(boolean v) { useChannelBlack.set(v); }

    public IntegerProperty channelBlack0Property() { return channelBlack0; }
    public int getChannelBlack0() { return channelBlack0.get(); }
    public void setChannelBlack0(int v) { channelBlack0.set(v); }

    public IntegerProperty channelBlack1Property() { return channelBlack1; }
    public int getChannelBlack1() { return channelBlack1.get(); }
    public void setChannelBlack1(int v) { channelBlack1.set(v); }

    public IntegerProperty channelBlack2Property() { return channelBlack2; }
    public int getChannelBlack2() { return channelBlack2.get(); }
    public void setChannelBlack2(int v) { channelBlack2.set(v); }

    public IntegerProperty channelBlack3Property() { return channelBlack3; }
    public int getChannelBlack3() { return channelBlack3.get(); }
    public void setChannelBlack3(int v) { channelBlack3.set(v); }

    public IntegerProperty useCameraMatrixProperty() { return useCameraMatrix; }
    public int getUseCameraMatrix() { return useCameraMatrix.get(); }
    public void setUseCameraMatrix(int v) { useCameraMatrix.set(v); }

    public IntegerProperty userFlipProperty() { return userFlip; }
    public int getUserFlip() { return userFlip.get(); }
    public void setUserFlip(int v) { userFlip.set(v); }

    public BooleanProperty dontStretchPixelsProperty() { return dontStretchPixels; }
    public boolean isDontStretchPixels() { return dontStretchPixels.get(); }
    public void setDontStretchPixels(boolean v) { dontStretchPixels.set(v); }

    public BooleanProperty useCropBoxProperty() { return useCropBox; }
    public boolean isUseCropBox() { return useCropBox.get(); }
    public void setUseCropBox(boolean v) { useCropBox.set(v); }

    public IntegerProperty cropBoxXProperty() { return cropBoxX; }
    public int getCropBoxX() { return cropBoxX.get(); }
    public void setCropBoxX(int v) { cropBoxX.set(v); }

    public IntegerProperty cropBoxYProperty() { return cropBoxY; }
    public int getCropBoxY() { return cropBoxY.get(); }
    public void setCropBoxY(int v) { cropBoxY.set(v); }

    public IntegerProperty cropBoxWProperty() { return cropBoxW; }
    public int getCropBoxW() { return cropBoxW.get(); }
    public void setCropBoxW(int v) { cropBoxW.set(v); }

    public IntegerProperty cropBoxHProperty() { return cropBoxH; }
    public int getCropBoxH() { return cropBoxH.get(); }
    public void setCropBoxH(int v) { cropBoxH.set(v); }

    public IntegerProperty shotSelectProperty() { return shotSelect; }
    public int getShotSelect() { return shotSelect.get(); }
    public void setShotSelect(int v) { shotSelect.set(v); }

    public DoubleProperty coolScanNEFGammaProperty() { return coolScanNEFGamma; }
    public double getCoolScanNEFGamma() { return coolScanNEFGamma.get(); }
    public void setCoolScanNEFGamma(double v) { coolScanNEFGamma.set(v); }

    public IntegerProperty rawProcessingOptionsProperty() { return rawProcessingOptions; }
    public int getRawProcessingOptions() { return rawProcessingOptions.get(); }
    public void setRawProcessingOptions(int v) { rawProcessingOptions.set(v); }

    public StringProperty outputProfilePathProperty() { return outputProfilePath; }
    public String getOutputProfilePath() { return outputProfilePath.get(); }
    public void setOutputProfilePath(String v) { outputProfilePath.set(v == null ? "" : v); }

    public StringProperty cameraProfilePathProperty() { return cameraProfilePath; }
    public String getCameraProfilePath() { return cameraProfilePath.get(); }
    public void setCameraProfilePath(String v) { cameraProfilePath.set(v == null ? "" : v); }

    public StringProperty badPixelsPathProperty() { return badPixelsPath; }
    public String getBadPixelsPath() { return badPixelsPath.get(); }
    public void setBadPixelsPath(String v) { badPixelsPath.set(v == null ? "" : v); }

    public StringProperty darkFramePathProperty() { return darkFramePath; }
    public String getDarkFramePath() { return darkFramePath.get(); }
    public void setDarkFramePath(String v) { darkFramePath.set(v == null ? "" : v); }
}