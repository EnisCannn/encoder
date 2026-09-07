package com.example.encoder.service.impelemtation;

import com.example.encoder.entity.EncodingJob;
import com.example.encoder.entity.EncodingPreset;
import com.example.encoder.entity.JobStatus;
import com.example.encoder.entity.enums.SubtitleMode;
import com.example.encoder.repository.EncodingJobRepository;
import com.example.encoder.service.SmilGeneratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SmilGeneratorServiceImpl implements SmilGeneratorService {

    public static final String SMIL_FILE_NAME = "playlist.smil";

    private final EncodingJobRepository jobRepository;

    @Value("${encoder.folder.output}")
    private String outputFolder;

    @Override
    public Path generate(UUID batchId) {
        List<EncodingJob> jobs = jobRepository.findByBatchIdOrderByCreatedAtAsc(batchId);
        if (jobs.isEmpty()) {
            throw new RuntimeException("Bu paket için iş bulunamadı: " + batchId);
        }

        List<EncodingJob> completed = jobs.stream()
                .filter(j -> j.getStatus() == JobStatus.COMPLETED)
                .sorted(Comparator.comparing(this::pixelCount).reversed())
                .toList();

        if (completed.isEmpty()) {
            throw new RuntimeException("Bu pakette tamamlanmış iş yok: " + batchId);
        }

        Path targetDir = Paths.get(outputFolder).resolve(batchId.toString());
        Path target = targetDir.resolve(SMIL_FILE_NAME);

        try {
            Files.createDirectories(targetDir);
            Files.writeString(target, buildSmil(completed), StandardCharsets.UTF_8);
            return target;
        } catch (IOException e) {
            throw new RuntimeException("SMIL yazılamadı: " + e.getMessage(), e);
        }
    }

    @Override
    public String readOrGenerate(UUID batchId) {
        Path target = Paths.get(outputFolder).resolve(batchId.toString()).resolve(SMIL_FILE_NAME);
        try {
            if (Files.exists(target)) {
                return Files.readString(target, StandardCharsets.UTF_8);
            }
            return Files.readString(generate(batchId), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("SMIL okunamadı: " + e.getMessage(), e);
        }
    }

    /**
     * Wowza'nın beklediği SMIL yapısı: <switch> içinde her kalite bir <video>,
     * seçilebilir altyazı varsa bir <textstream>.
     *
     * Yollar SMIL dosyasına göre göreli yazılır; dosya <batchId>/ içinde durduğu için
     * çıktı adlarındaki "<batchId>/" öneki kırpılır.
     */
    private String buildSmil(List<EncodingJob> jobs) {
        EncodingJob first = jobs.get(0);
        String title = first.getInputFileName() != null ? first.getInputFileName() : "video";

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<smil title=\"").append(escape(title)).append("\">\n");
        sb.append("  <head/>\n");
        sb.append("  <body>\n");
        sb.append("    <switch>\n");

        for (EncodingJob job : jobs) {
            EncodingPreset preset = job.getPreset();
            sb.append("      <video src=\"").append(escape(relativeName(job.getOutputFileName()))).append("\"");
            sb.append(" system-bitrate=\"").append(totalBitrateBps(preset)).append("\"");
            if (preset != null && preset.getWidth() != null && preset.getHeight() != null) {
                sb.append(" width=\"").append(preset.getWidth()).append("\"");
                sb.append(" height=\"").append(preset.getHeight()).append("\"");
            }
            sb.append("/>\n");
        }

        // Seçilebilir altyazı paketteki tüm kaliteler için ortaktır, bir kez yazılır
        EncodingJob withSubtitle = jobs.stream()
                .filter(j -> j.getSubtitleMode() == SubtitleMode.SIDECAR && j.getSubtitleVttFileName() != null)
                .findFirst()
                .orElse(null);

        if (withSubtitle != null) {
            sb.append("      <textstream src=\"")
                    .append(escape(relativeName(withSubtitle.getSubtitleVttFileName()))).append("\"");
            if (withSubtitle.getSubtitleLanguage() != null) {
                sb.append(" system-language=\"").append(escape(withSubtitle.getSubtitleLanguage())).append("\"");
            }
            if (withSubtitle.getSubtitleLabel() != null) {
                sb.append(" title=\"").append(escape(withSubtitle.getSubtitleLabel())).append("\"");
            }
            sb.append("/>\n");
        }

        sb.append("    </switch>\n");
        sb.append("  </body>\n");
        sb.append("</smil>\n");
        return sb.toString();
    }

    // GİZLİ METOT: "<batchId>/720p_x.mp4" -> "720p_x.mp4"
    private String relativeName(String fileName) {
        if (fileName == null) {
            return "";
        }
        int slash = fileName.indexOf('/');
        return slash >= 0 ? fileName.substring(slash + 1) : fileName;
    }

    // GİZLİ METOT: SMIL bit hızını bit/saniye ister, preset kbps tutar
    private long totalBitrateBps(EncodingPreset preset) {
        if (preset == null) {
            return 0L;
        }
        int video = preset.getVideoBitrate() != null ? preset.getVideoBitrate() : 0;
        int audio = preset.getAudioBitrate() != null ? preset.getAudioBitrate() : 0;
        return (long) (video + audio) * 1000L;
    }

    // GİZLİ METOT: Kaliteye göre sıralama için piksel sayısı
    private long pixelCount(EncodingJob job) {
        EncodingPreset preset = job.getPreset();
        if (preset == null || preset.getWidth() == null || preset.getHeight() == null) {
            return 0L;
        }
        return (long) preset.getWidth() * preset.getHeight();
    }

    // GİZLİ METOT: XML öznitelik kaçışları
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
