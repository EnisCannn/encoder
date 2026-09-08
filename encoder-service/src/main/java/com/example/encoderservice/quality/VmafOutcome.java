package com.example.encoderservice.quality;

/**
 * Bir VMAF ölçümünün sonucu.
 *
 * Üç ayrı son var: ölçüldü, ölçülemez (elendi), hata. "Elendi" bir hata değil —
 * yakılmış altyazı gibi durumlarda referansla karşılaştırma anlamsız olduğu için
 * bilerek atlanıyor ve sebebi kullanıcıya yazılıyor.
 */
public final class VmafOutcome {

    public enum Kind {
        MEASURED,
        SKIPPED,
        FAILED
    }

    private final Kind kind;
    private final String message;
    private final Double mean;
    private final Double min;
    private final Double harmonicMean;
    private final Integer sampledSeconds;
    private Double outputFrameRate;

    private VmafOutcome(Kind kind, String message, Double mean, Double min,
                        Double harmonicMean, Integer sampledSeconds) {
        this.kind = kind;
        this.message = message;
        this.mean = mean;
        this.min = min;
        this.harmonicMean = harmonicMean;
        this.sampledSeconds = sampledSeconds;
    }

    public static VmafOutcome measured(double mean, Double min, Double harmonicMean, Integer sampledSeconds) {
        return new VmafOutcome(Kind.MEASURED, null, mean, min, harmonicMean, sampledSeconds);
    }

    public static VmafOutcome skipped(String reason) {
        return new VmafOutcome(Kind.SKIPPED, reason, null, null, null, null);
    }

    public static VmafOutcome failed(String reason) {
        return new VmafOutcome(Kind.FAILED, reason, null, null, null, null);
    }

    public Kind getKind() {
        return kind;
    }

    public String getMessage() {
        return message;
    }

    public Double getMean() {
        return mean;
    }

    public Double getMin() {
        return min;
    }

    public Double getHarmonicMean() {
        return harmonicMean;
    }

    public Integer getSampledSeconds() {
        return sampledSeconds;
    }

    public Double getOutputFrameRate() {
        return outputFrameRate;
    }

    public VmafOutcome withOutputFrameRate(Double frameRate) {
        this.outputFrameRate = frameRate;
        return this;
    }
}
