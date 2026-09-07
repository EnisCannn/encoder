package com.example.encoder.service.impelemtation;

import com.example.encoder.entity.EncodingJob;
import com.example.encoder.entity.EncodingPreset;
import com.example.encoder.entity.JobStatus;
import com.example.encoder.entity.enums.SubtitleMode;
import com.example.encoder.repository.EncodingJobRepository;
import com.example.encoder.service.HlsPackagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HlsPackagerServiceImpl implements HlsPackagerService {

    public static final String HLS_DIR = "hls";
    public static final String MASTER_FILE_NAME = "master.m3u8";

    private static final int SEGMENT_SECONDS = 6;
    private static final String SUBTITLE_GROUP = "subs";

    private final EncodingJobRepository jobRepository;

    @Value("${encoder.folder.output}")
    private String outputFolder;

    @Value("${encoder.ffmpeg.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Value("${encoder.ffmpeg.ffprobe-path:ffprobe}")
    private String ffprobePath;

    @Override
    public Path packageBatch(UUID batchId) {
        List<EncodingJob> jobs = jobRepository.findByBatchIdOrderByCreatedAtAsc(batchId).stream()
                .filter(j -> j.getStatus() == JobStatus.COMPLETED)
                .sorted(Comparator.comparing(this::pixelCount).reversed())
                .toList();

        if (jobs.isEmpty()) {
            throw new RuntimeException("Bu pakette tamamlanmış iş yok: " + batchId);
        }

        Path batchDir = Paths.get(outputFolder).resolve(batchId.toString());
        Path hlsDir = batchDir.resolve(HLS_DIR);

        try {
            Files.createDirectories(hlsDir);

            StringBuilder master = new StringBuilder();
            master.append("#EXTM3U\n");
            master.append("#EXT-X-VERSION:3\n");

            // Seçilebilir altyazı varsa önce EXT-X-MEDIA satırı yazılır,
            // sonra her varyant SUBTITLES="subs" ile bu gruba bağlanır.
            EncodingJob withSubtitle = jobs.stream()
                    .filter(j -> j.getSubtitleMode() == SubtitleMode.SIDECAR && j.getSubtitleVttFileName() != null)
                    .findFirst()
                    .orElse(null);

            boolean hasSubtitles = false;
            if (withSubtitle != null) {
                String subPlaylist = writeSubtitlePlaylist(hlsDir, batchDir, withSubtitle);
                if (subPlaylist != null) {
                    String language = withSubtitle.getSubtitleLanguage() != null
                            ? withSubtitle.getSubtitleLanguage() : "tr";
                    String label = withSubtitle.getSubtitleLabel() != null
                            ? withSubtitle.getSubtitleLabel() : "Altyazı";

                    master.append("#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID=\"").append(SUBTITLE_GROUP).append("\"")
                            .append(",NAME=\"").append(escapeAttr(label)).append("\"")
                            .append(",LANGUAGE=\"").append(escapeAttr(language)).append("\"")
                            .append(",DEFAULT=NO,AUTOSELECT=YES,FORCED=NO")
                            .append(",URI=\"").append(subPlaylist).append("\"\n");
                    hasSubtitles = true;
                }
            }

            for (EncodingJob job : jobs) {
                String variantName = baseName(job.getOutputFileName());
                Path variantDir = hlsDir.resolve(variantName);
                Path source = batchDir.resolve(fileNameOnly(job.getOutputFileName()));

                if (!Files.exists(source)) {
                    throw new RuntimeException("Kaynak çıktı bulunamadı: " + source);
                }

                segmentToHls(source, variantDir);

                EncodingPreset preset = job.getPreset();
                master.append("#EXT-X-STREAM-INF:BANDWIDTH=").append(totalBitrateBps(preset));
                if (preset != null && preset.getWidth() != null && preset.getHeight() != null) {
                    master.append(",RESOLUTION=").append(preset.getWidth()).append("x").append(preset.getHeight());
                }
                if (hasSubtitles) {
                    master.append(",SUBTITLES=\"").append(SUBTITLE_GROUP).append("\"");
                }
                master.append("\n").append(variantName).append("/index.m3u8\n");
            }

            Path masterPath = hlsDir.resolve(MASTER_FILE_NAME);
            Files.writeString(masterPath, master.toString(), StandardCharsets.UTF_8);
            return masterPath;

        } catch (IOException e) {
            throw new RuntimeException("HLS paketlenemedi: " + e.getMessage(), e);
        }
    }

    /**
     * mp4'ü yeniden kodlamadan TS segmentlerine böler.
     * Presetler zaten H264/AAC ürettiği için -c copy yeterli; CPU maliyeti neredeyse sıfır.
     */
    private void segmentToHls(Path source, Path variantDir) throws IOException {
        Files.createDirectories(variantDir);

        List<String> command = List.of(
                ffmpegPath, "-y", "-nostdin",
                "-i", source.toString(),
                "-c", "copy",
                "-f", "hls",
                "-hls_time", String.valueOf(SEGMENT_SECONDS),
                "-hls_playlist_type", "vod",
                "-hls_flags", "independent_segments",
                "-hls_segment_filename", variantDir.resolve("seg_%03d.ts").toString(),
                variantDir.resolve("index.m3u8").toString()
        );

        runProcess(command, "HLS segmentleme");
    }

    /**
     * WebVTT için tek segmentlik altyazı playlist'i.
     * .vtt dosyası hls/ klasörünün dışında durduğu için göreli yolla gösterilir,
     * böylece dosyanın ikinci bir kopyası tutulmaz.
     */
    private String writeSubtitlePlaylist(Path hlsDir, Path batchDir, EncodingJob job) throws IOException {
        String vttRelativeToBatch = fileNameRelativeToBatch(job.getSubtitleVttFileName());
        Path vtt = batchDir.resolve(vttRelativeToBatch);
        if (!Files.exists(vtt)) {
            return null;
        }

        int duration = resolveDurationSeconds(job, batchDir);
        String language = job.getSubtitleLanguage() != null ? job.getSubtitleLanguage() : "tr";

        Path subDir = hlsDir.resolve(SUBTITLE_GROUP);
        Files.createDirectories(subDir);
        Path playlist = subDir.resolve(language + ".m3u8");

        // Playlist hls/subs/ içinde, .vtt ise <batchId>/subtitles/ içinde: iki seviye yukarı
        String content = "#EXTM3U\n"
                + "#EXT-X-VERSION:3\n"
                + "#EXT-X-TARGETDURATION:" + duration + "\n"
                + "#EXT-X-MEDIA-SEQUENCE:0\n"
                + "#EXT-X-PLAYLIST-TYPE:VOD\n"
                + "#EXTINF:" + duration + ".000,\n"
                + "../../" + vttRelativeToBatch + "\n"
                + "#EXT-X-ENDLIST\n";

        Files.writeString(playlist, content, StandardCharsets.UTF_8);
        return SUBTITLE_GROUP + "/" + language + ".m3u8";
    }

    // GİZLİ METOT: Altyazı playlist'i videonun tamamını kapsamalı
    private int resolveDurationSeconds(EncodingJob job, Path batchDir) {
        if (job.getVideo() != null && job.getVideo().getDuration() != null) {
            return (int) Math.ceil(job.getVideo().getDuration().doubleValue());
        }
        try {
            Path source = batchDir.resolve(fileNameOnly(job.getOutputFileName()));
            List<String> command = List.of(ffprobePath, "-v", "error", "-show_entries",
                    "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", source.toString());
            return (int) Math.ceil(Double.parseDouble(runProcess(command, "Süre okuma").trim()));
        } catch (Exception e) {
            return 3600; // Okunamazsa geniş bir üst sınır; oynatmayı engellemez
        }
    }

    // GİZLİ METOT: Ortak süreç çalıştırıcı
    private String runProcess(List<String> command, String label) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException(label + " başarısız (çıkış kodu " + exitCode + "): " + output);
            }
            return output.toString();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(label + " yarıda kesildi", e);
        } catch (IOException e) {
            throw new RuntimeException(label + " çalıştırılamadı: " + e.getMessage(), e);
        }
    }

    // "<batchId>/720p_x.mp4" -> "720p_x.mp4"
    private String fileNameOnly(String outputFileName) {
        int slash = outputFileName.indexOf('/');
        return slash >= 0 ? outputFileName.substring(slash + 1) : outputFileName;
    }

    // "<batchId>/subtitles/tr.vtt" -> "subtitles/tr.vtt"
    private String fileNameRelativeToBatch(String path) {
        int slash = path.indexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    // "<batchId>/720p_x.mp4" -> "720p_x" (varyant klasör adı)
    private String baseName(String outputFileName) {
        String name = fileNameOnly(outputFileName);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private long totalBitrateBps(EncodingPreset preset) {
        if (preset == null) {
            return 0L;
        }
        int video = preset.getVideoBitrate() != null ? preset.getVideoBitrate() : 0;
        int audio = preset.getAudioBitrate() != null ? preset.getAudioBitrate() : 0;
        return (long) (video + audio) * 1000L;
    }

    private long pixelCount(EncodingJob job) {
        EncodingPreset preset = job.getPreset();
        if (preset == null || preset.getWidth() == null || preset.getHeight() == null) {
            return 0L;
        }
        return (long) preset.getWidth() * preset.getHeight();
    }

    // M3U8 öznitelikleri çift tırnakla sarılı, içerideki tırnak kaçırılmalı
    private String escapeAttr(String value) {
        return value == null ? "" : value.replace("\"", "'");
    }
}
