package com.example.encoderservice.quality;

import com.example.encoderservice.entity.EncodingJob;
import com.example.encoderservice.entity.JobStatus;
import com.example.encoderservice.entity.Video;
import com.example.encoderservice.entity.enums.SubtitleMode;
import com.example.encoderservice.util.FFmpegProcessRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * VMAF ölçümünü ffmpeg'in libvmaf filtresiyle yapar.
 *
 * Ölçüm encode değildir: yeni bir video üretmez, iki dosyayı okuyup tek bir sayı
 * çıkarır. Çıktı {@code -f null -} ile atılır, sadece JSON log dosyası tutulur.
 */
@Service
@RequiredArgsConstructor
public class FFmpegVmafService implements VmafService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${encoder.ffmpeg.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Value("${encoder.ffmpeg.ffprobe-path:ffprobe}")
    private String ffprobePath;

    @Value("${encoder.vmaf.timeout-seconds:7200}")
    private long timeoutSeconds;

    /**
     * Her iki video da karşılaştırma öncesi bu boyuta getirilir. Farklı
     * çözünürlükteki çıktıların aynı ölçekte karşılaştırılabilmesi için gerekli;
     * VMAF modeli de bu boyut için eğitilmiş.
     */
    @Value("${encoder.vmaf.reference-width:1920}")
    private int referenceWidth;

    @Value("${encoder.vmaf.reference-height:1080}")
    private int referenceHeight;

    /**
     * 0 ise dosyanın tamamı ölçülür (varsayılan, en doğru sonuç).
     * Pozitifse videonun ortasından bu kadar saniye örneklenir; uzun videolarda
     * süreyi kısaltır ama kalite eğilimini yine gösterir.
     */
    @Value("${encoder.vmaf.sample-seconds:0}")
    private int sampleSeconds;

    @Override
    public VmafOutcome measure(EncodingJob job) {
        // --- Eleme kuralları: ölçümü anlamsız kılan durumlar ---
        if (job.getStatus() != JobStatus.COMPLETED) {
            return VmafOutcome.skipped("İş tamamlanmadan kalite ölçülemez.");
        }

        // Yakılmış altyazı kaynakta yok; VMAF onu bozulma sayar ve skor yanıltır.
        if (job.getSubtitleMode() == SubtitleMode.BURN) {
            return VmafOutcome.skipped(
                    "Altyazı görüntüye yakıldığı için kaynakla karşılaştırma anlamlı sonuç vermez.");
        }

        Video video = job.getVideo();
        if (video == null || video.getPath() == null || video.getPath().isBlank()) {
            return VmafOutcome.skipped("Karşılaştırma için kaynak video bulunamadı.");
        }

        Path reference = Paths.get(video.getPath());
        if (!Files.exists(reference)) {
            return VmafOutcome.skipped("Kaynak video diskte yok: " + reference);
        }

        if (job.getOutputPath() == null || job.getOutputPath().isBlank()) {
            return VmafOutcome.skipped("Çıktı dosyası kaydedilmemiş.");
        }

        Path distorted = Paths.get(job.getOutputPath());
        if (!Files.exists(distorted)) {
            return VmafOutcome.skipped("Çıktı dosyası diskte yok: " + distorted);
        }

        // --- Ölçüm ---
        Path logFile = null;
        try {
            logFile = Files.createTempFile("vmaf_", ".json");

            List<String> command = buildCommand(job, distorted, reference, logFile);
            System.out.println("[VMAF CMD]: " + String.join(" ", command));

            FFmpegProcessRunner.Result result = FFmpegProcessRunner.run(command, timeoutSeconds);

            if (!result.isSuccess()) {
                return VmafOutcome.failed("ffmpeg çıkış kodu " + result.exitCode()
                        + ": " + tail(result.output()));
            }

            return parseLog(logFile).withOutputFrameRate(probeFrameRate(distorted));

        } catch (Exception e) {
            return VmafOutcome.failed("Ölçüm sırasında hata: " + e.getMessage());
        } finally {
            if (logFile != null) {
                try {
                    Files.deleteIfExists(logFile);
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * ffmpeg komutunu kurar. libvmaf'ın ilk girdisi bozulmuş (çıktı), ikincisi
     * referans (kaynak) videodur; sıra ters olursa skor anlamsız çıkar.
     */
    private List<String> buildCommand(EncodingJob job, Path distorted, Path reference, Path logFile) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-nostdin");
        command.add("-y");

        Integer sampleStart = sampleStartSeconds(job);

        // -ss girdiden SONRA veriliyor: kare hassas arama yapar. Girdiden önce
        // verilseydi ffmpeg en yakın anahtar kareye kayardı ve kaynak ile çıktı
        // farklı karelerden başlayabilirdi - bu da skoru bozar.
        command.add("-i");
        command.add(distorted.toString());
        if (sampleStart != null) {
            command.add("-ss");
            command.add(String.valueOf(sampleStart));
            command.add("-t");
            command.add(String.valueOf(sampleSeconds));
        }

        command.add("-i");
        command.add(reference.toString());
        if (sampleStart != null) {
            command.add("-ss");
            command.add(String.valueOf(sampleStart));
            command.add("-t");
            command.add(String.valueOf(sampleSeconds));
        }

        command.add("-lavfi");
        command.add(buildFilter(job, logFile));

        // Ölçüm video üretmez; çıktı çöpe gider.
        command.add("-f");
        command.add("null");
        command.add("-");
        return command;
    }

    private String buildFilter(EncodingJob job, Path logFile) {

        // Kare hızı BİLEREK eşitlenmiyor.
        //
        // Başlangıçta referansa `fps=<şablon hızı>` uygulanıyordu; ölçümle
        // doğrulandı ki bu skoru düşürüyor. 25 fps kaynaktan 24 fps'e geçen bir
        // çıktıda encoder'ın (-r) attığı kareler ile fps filtresinin attıkları
        // aynı kareler değil; filtre eşleme düzeltmek yerine kaydırıyor.
        // Aynı dosyada ölçülen fark: fps filtresiyle 63.8, filtresiz 71.6.
        //
        // ffmpeg'in framesync'i iki girdiyi zaten zaman damgasına göre
        // eşleştirdiği için yeniden örneklemeye gerek yok.
        String normalize = "scale=" + referenceWidth + ":" + referenceHeight + ":flags=bicubic"
                + ",setsar=1";

        String logPath = logFile.toAbsolutePath().toString().replace("\\", "/");

        return "[0:v]" + normalize + "[dist];"
                + "[1:v]" + normalize + "[ref];"
                + "[dist][ref]libvmaf=log_path=" + logPath + ":log_fmt=json"
                + ":n_threads=" + Runtime.getRuntime().availableProcessors();
    }

    /** Örnekleme açıksa videonun ortasından başlar; kapalıysa null (tamamı ölçülür). */
    private Integer sampleStartSeconds(EncodingJob job) {
        if (sampleSeconds <= 0) {
            return null;
        }
        Video video = job.getVideo();
        if (video == null || video.getDuration() == null) {
            return 0;
        }
        double duration = video.getDuration().doubleValue();
        if (duration <= sampleSeconds) {
            return null;
        }
        return (int) Math.floor((duration - sampleSeconds) / 2);
    }

    private VmafOutcome parseLog(Path logFile) throws Exception {
        File file = logFile.toFile();
        if (!file.exists() || file.length() == 0) {
            return VmafOutcome.failed("VMAF log dosyası üretilmedi.");
        }

        JsonNode root = objectMapper.readTree(file);
        JsonNode vmaf = root.path("pooled_metrics").path("vmaf");

        if (vmaf.isMissingNode() || !vmaf.hasNonNull("mean")) {
            // Eski libvmaf sürümleri skoru kökte "VMAF score" olarak yazıyordu
            JsonNode legacy = root.path("VMAF score");
            if (legacy.isNumber()) {
                return VmafOutcome.measured(legacy.asDouble(), null, null, sampleOrNull());
            }
            return VmafOutcome.failed("VMAF skoru log dosyasında bulunamadı.");
        }

        double mean = vmaf.path("mean").asDouble();
        Double min = vmaf.hasNonNull("min") ? vmaf.path("min").asDouble() : null;
        Double harmonic = vmaf.hasNonNull("harmonic_mean")
                ? vmaf.path("harmonic_mean").asDouble() : null;

        return VmafOutcome.measured(mean, min, harmonic, sampleOrNull());
    }

    private Integer sampleOrNull() {
        return sampleSeconds > 0 ? sampleSeconds : null;
    }

    /**
     * Ciktinin gercek kare hizini okur. ffprobe "25/1" ya da "24000/1001" gibi
     * bir kesir dondurur; boluyoruz. Okunamazsa null doner, olcum yine de gecerli.
     */
    private Double probeFrameRate(Path file) {
        try {
            List<String> command = List.of(
                    ffprobePath, "-v", "error",
                    "-select_streams", "v:0",
                    "-show_entries", "stream=r_frame_rate",
                    "-of", "default=nw=1:nk=1",
                    file.toString());

            FFmpegProcessRunner.Result result = FFmpegProcessRunner.run(command, 60);
            if (!result.isSuccess()) {
                return null;
            }

            String value = result.output().trim().split("\\s+")[0];
            if (value.isEmpty() || value.equals("0/0")) {
                return null;
            }

            if (value.contains("/")) {
                String[] parts = value.split("/");
                double den = Double.parseDouble(parts[1]);
                if (den == 0) {
                    return null;
                }
                return Math.round((Double.parseDouble(parts[0]) / den) * 100.0) / 100.0;
            }
            return Double.parseDouble(value);
        } catch (Exception e) {
            System.out.println("Kare hizi okunamadi: " + e.getMessage());
            return null;
        }
    }

    private String tail(String output) {
        if (output == null) {
            return "";
        }
        String trimmed = output.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(trimmed.length() - 500);
    }
}
