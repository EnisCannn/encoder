package com.example.encoder.service.impelemtation;

import com.example.encoder.dto.LiveClipRequest;
import com.example.encoder.dto.LiveStreamRequest;
import com.example.encoder.entity.EncodingPreset;
import com.example.encoder.entity.LiveStream;
import com.example.encoder.repository.EncodingPresetRepository;
import com.example.encoder.repository.LiveStreamRepository;
import com.example.encoder.repository.VideoRepository;
import com.example.encoder.service.LiveStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class LiveStreamServiceImpl implements LiveStreamService {

    private final LiveStreamRepository repository;
    private final EncodingPresetRepository presetRepository;
    private final VideoRepository videoRepository;

    private final Map<UUID, Process> activeStreams = new ConcurrentHashMap<>();
    // Kullanıcının bilerek durdurduğu yayınlar (bunlar yeniden başlatılmaz)
    private final Map<UUID, Boolean> manuallyStopped = new ConcurrentHashMap<>();
    // Yeniden başlatırken aynı ayarları kullanabilmek için istekleri saklıyoruz
    private final Map<UUID, LiveStreamRequest> activeRequests = new ConcurrentHashMap<>();

    @Value("${encoder.folder.live}")
    private String liveBaseFolder;

    @Value("${encoder.folder.live-clips}")
    private String clipsBaseFolder;

    @Value("${encoder.ffmpeg.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Override
    public List<LiveStream> getAllStreams() {
        return repository.findAll();
    }

    @Override
    public LiveStream startStream(LiveStreamRequest request) {
        LiveStream stream = new LiveStream();
        stream.setStreamName(request.getStreamName());
        stream.setInputUrl(request.getInputUrl());
        stream.setStatus("STARTING");
        stream.setStreamStartTime(System.currentTimeMillis());
        stream = repository.save(stream);

        File streamDir = new File(liveBaseFolder, stream.getId().toString());
        if (!streamDir.exists()) {
            streamDir.mkdirs();
        }
        stream.setOutputFolder(streamDir.getAbsolutePath());

        activeRequests.put(stream.getId(), request);
        manuallyStopped.remove(stream.getId());

        try {
            launchFfmpeg(stream, request);
            stream.setStatus("LIVE");
            repository.save(stream);
        } catch (Exception e) {
            stream.setStatus("ERROR");
            repository.save(stream);
            throw new RuntimeException("Yayın başlatılamadı: " + e.getMessage());
        }
        return stream;
    }

    // ffmpeg komutunu kurar. Her tur için ayrı segment ön eki kullanılır ki
    // yeniden başlatmada eski segmentler ezilmesin.
    private List<String> buildStreamCommand(LiveStream stream, LiveStreamRequest request) {
        String streamDir = stream.getOutputFolder();
        long runId = System.currentTimeMillis();

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-err_detect"); command.add("ignore_err");
        command.add("-user_agent");
        command.add("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        command.add("-reconnect"); command.add("1");
        command.add("-reconnect_streamed"); command.add("1");
        command.add("-reconnect_delay_max"); command.add("10");

        // Dosya kaynaklarında döngü için; HLS'te etkisizdir, orada yeniden
        // başlatma mekanizması devreye girer.
        command.add("-stream_loop"); command.add("-1");
        command.add("-fflags"); command.add("+genpts");

        command.add("-i");
        command.add(stream.getInputUrl());

        boolean hasDubbing = request.getDubbingPath() != null && !request.getDubbingPath().isEmpty();
        if (hasDubbing) {
            command.add("-stream_loop"); command.add("-1");
            command.add("-i"); command.add(request.getDubbingPath());
            command.add("-map"); command.add("0:v");
            command.add("-map"); command.add("1:a");
        }

        if (request.getSubtitlePath() != null && !request.getSubtitlePath().isEmpty()) {
            String escapedSubtitlePath = request.getSubtitlePath().replace("\\", "/").replace(":", "\\:");
            command.add("-vf"); command.add("subtitles='" + escapedSubtitlePath + "'");
        }

        command.add("-c:v"); command.add("libx264");
        command.add("-preset"); command.add("ultrafast");
        command.add("-tune"); command.add("zerolatency");
        command.add("-c:a"); command.add("aac");
        command.add("-ar"); command.add("44100");
        command.add("-b:a"); command.add("128k");
        command.add("-ac"); command.add("2");
        command.add("-f"); command.add("hls");
        command.add("-hls_time"); command.add("4");
        command.add("-hls_list_size"); command.add("0");

        // append_list  : Yeniden başlatmada playlist sıfırlanmaz, üstüne eklenir.
        //                Yayın geçmişi böylece baştan sona birikir.
        // omit_endlist : ffmpeg çıkarken "bitti" etiketi yazmaz, akış canlı kalır.
        // discont_start: Yeni tur başlarken süreksizlik işareti koyar (PTS sıfırlanır).
        // program_date_time: Her segmentin gerçek saatini yazar, klip hesabı bunu kullanır.
        command.add("-hls_flags");
        command.add("append_list+omit_endlist+discont_start+program_date_time");

        // Her turun segmentleri farklı isim alsın
        command.add("-hls_segment_filename");
        command.add(Paths.get(streamDir, "seg_" + runId + "_%06d.ts").toString());

        command.add(Paths.get(streamDir, "index.m3u8").toString());
        return command;
    }

    // ffmpeg'i başlatır; süreç kendiliğinden biterse yayını kesmeden yeniden başlatır
    private void launchFfmpeg(LiveStream stream, LiveStreamRequest request) throws Exception {
        final UUID streamId = stream.getId();
        List<String> command = buildStreamCommand(stream, request);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        activeStreams.put(streamId, process);

        new Thread(() -> {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("YAYIN LOGU: " + line);
                }
            } catch (Exception ignored) {
            } finally {
                try {
                    process.waitFor();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                activeStreams.remove(streamId);

                if (Boolean.TRUE.equals(manuallyStopped.get(streamId))) {
                    System.out.println("--- YAYIN KULLANICI TARAFINDAN DURDURULDU: " + streamId + " ---");
                    return;
                }

                // Kaynak tükendi: playlist'e ekleyerek kaldığı yerden devam et
                System.out.println("--- KAYNAK BİTTİ, YAYIN DEVAM ETTİRİLİYOR: " + streamId + " ---");
                try {
                    Thread.sleep(300);
                    LiveStream fresh = repository.findById(streamId).orElse(null);
                    LiveStreamRequest req = activeRequests.get(streamId);
                    if (fresh != null && req != null && !Boolean.TRUE.equals(manuallyStopped.get(streamId))) {
                        launchFfmpeg(fresh, req);
                    }
                } catch (Exception e) {
                    System.out.println("Devam ettirme hatası: " + e.getMessage());
                    repository.findById(streamId).ifPresent(s -> {
                        s.setStatus("ERROR");
                        s.setStreamEndTime(System.currentTimeMillis());
                        repository.save(s);
                    });
                }
            }
        }).start();
    }

    @Override
    public void stopStream(UUID streamId) {
        manuallyStopped.put(streamId, true);
        Process process = activeStreams.get(streamId);
        if (process != null) {
            process.destroy();
            activeStreams.remove(streamId);
        }
        activeRequests.remove(streamId);
        repository.findById(streamId).ifPresent(stream -> {
            stream.setStatus("STOPPED");
            stream.setStreamEndTime(System.currentTimeMillis());
            repository.save(stream);
        });
    }

    @Override
    public void deleteStream(UUID streamId) {
        manuallyStopped.put(streamId, true);
        Process process = activeStreams.get(streamId);
        if (process != null) {
            process.destroy();
            activeStreams.remove(streamId);
        }
        activeRequests.remove(streamId);
        repository.findById(streamId).ifPresent(repository::delete);
        manuallyStopped.remove(streamId);
    }

    @Override
    public String createLiveClip(LiveClipRequest request) {
        LiveStream stream = repository.findById(UUID.fromString(request.getStreamId()))
                .orElseThrow(() -> new RuntimeException("Yayın bulunamadı!"));

        EncodingPreset preset = presetRepository.findById(UUID.fromString(request.getPresetId()))
                .orElseThrow(() -> new RuntimeException("Şablon bulunamadı!"));

        // Klip DAİMA diskteki yayın kaydından kesilir, orijinal kaynak dosyadan değil.
        Path m3u8Path = Paths.get(stream.getOutputFolder(), "index.m3u8");
        File clipsDir = new File(clipsBaseFolder);
        if (!clipsDir.exists()) {
            clipsDir.mkdirs();
        }

        if (!m3u8Path.toFile().exists()) {
            m3u8Path = Paths.get(stream.getOutputFolder(), "index.m3u8.tmp");
        }

        Path snapshotM3u8 = Paths.get(stream.getOutputFolder(), "snapshot_" + System.currentTimeMillis() + ".m3u8");
        List<String> lines;
        try {
            lines = java.nio.file.Files.readAllLines(m3u8Path, StandardCharsets.UTF_8);
            List<String> snapshot = new ArrayList<>(lines);
            if (!snapshot.contains("#EXT-X-ENDLIST")) {
                snapshot.add("#EXT-X-ENDLIST");
            }
            java.nio.file.Files.write(snapshotM3u8, snapshot, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Playlist kopyalanamadı: " + e.getMessage());
        }

        String extension = preset.getFormat() != null ? preset.getFormat().name().toLowerCase() : "mp4";
        String outputFileName = "live_clip_" + System.currentTimeMillis() + "." + extension;
        Path outputPath = Paths.get(clipsDir.getAbsolutePath(), outputFileName);

        // Gerçek saati playlist zaman çizgisine çeviriyoruz.
        // Yeniden başlatmalarda PTS sıfırlandığı için düz çıkarma yanlış sonuç verir.
        double startOffsetSec = wallClockToPlaylistOffset(lines, request.getStartTime(), stream.getStreamStartTime());
        double endOffsetSec = wallClockToPlaylistOffset(lines, request.getEndTime(), stream.getStreamStartTime());

        if (startOffsetSec < 0) startOffsetSec = 0;
        if (endOffsetSec < 0) endOffsetSec = 0;

        if (endOffsetSec <= startOffsetSec) {
            try { java.nio.file.Files.deleteIfExists(snapshotM3u8); } catch (Exception ignored) {}
            throw new RuntimeException("Zamanlama Hatası! Bitiş süresi başlangıçtan büyük olmalıdır.");
        }

        double durationSec = endOffsetSec - startOffsetSec;

        String formattedStartTime = formatSeconds(startOffsetSec);
        String formattedDuration = formatSeconds(durationSec);

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-nostdin");
        command.add("-y");
        command.add("-threads"); command.add("0");

        command.add("-ss"); command.add(formattedStartTime);
        command.add("-t"); command.add(formattedDuration);

        command.add("-i"); command.add(snapshotM3u8.toString());

        if (preset.getVideoCodec() != null) {
            command.add("-c:v");
            command.add(preset.getVideoCodec().getFfmpegFlag());
            command.add("-preset");
            command.add("ultrafast");
        }
        if (preset.getVideoBitrate() != null) {
            command.add("-b:v");
            command.add(preset.getVideoBitrate() + "k");
        }
        if (preset.getWidth() != null && preset.getHeight() != null) {
            command.add("-vf");
            command.add("scale=" + preset.getWidth() + ":" + preset.getHeight());
        }
        if (preset.getFrameRate() != null) {
            command.add("-r");
            command.add(preset.getFrameRate().toString());
        }
        if (preset.getAudioCodec() != null) {
            command.add("-c:a");
            command.add(preset.getAudioCodec().name().toLowerCase());
        } else {
            command.add("-c:a"); command.add("aac");
        }
        if (preset.getAudioBitrate() != null) {
            command.add("-b:a");
            command.add(preset.getAudioBitrate() + "k");
        }

        command.add(outputPath.toString());

        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            System.out.println("--- YAYINDAN KLİP ALMA İŞLEMİ BAŞLADI ---");
            Process process = builder.start();

            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            int exitCode = process.waitFor();
            System.out.println("--- YAYINDAN KLİP ALMA BİTTİ. Çıkış Kodu: " + exitCode + " ---");

            if (exitCode != 0) {
                throw new RuntimeException("Klip başarısız oldu. Lütfen container loglarındaki FFmpeg loglarını kontrol et.");
            }

            com.example.encoder.entity.Video clipVideo = new com.example.encoder.entity.Video();
            clipVideo.setOriginalFileName(outputFileName);
            clipVideo.setStoredFileName(outputFileName);
            clipVideo.setPath(outputPath.toString());
            videoRepository.save(clipVideo);

            return outputFileName;
        } catch (Exception e) {
            throw new RuntimeException("Sistem hatası: " + e.getMessage());
        } finally {
            try {
                java.nio.file.Files.deleteIfExists(snapshotM3u8);
            } catch (Exception ignored) {}
        }
    }

    /**
     * İstenen gerçek saatin (epoch ms) playlist zaman çizgisinde kaçıncı saniyeye
     * denk geldiğini bulur. Segmentleri sırayla yürür: her segmentin gerçek başlangıç
     * saati PROGRAM-DATE-TIME'dan, süresi EXTINF'ten okunur.
     */
    private double wallClockToPlaylistOffset(List<String> lines, long targetEpochMs, long fallbackStartMs) {
        double cumulative = 0;          // Playlist başından itibaren geçen süre
        long segmentWallStart = -1;     // İçinde bulunduğumuz segmentin gerçek başlangıcı
        double pendingDuration = -1;    // Okunan ama henüz işlenmemiş EXTINF süresi

        for (String raw : lines) {
            String line = raw.trim();

            if (line.startsWith("#EXT-X-PROGRAM-DATE-TIME:")) {
                segmentWallStart = parseEpochMillis(line.substring("#EXT-X-PROGRAM-DATE-TIME:".length()).trim());
                continue;
            }

            if (line.startsWith("#EXTINF:")) {
                String value = line.substring("#EXTINF:".length()).replace(",", "").trim();
                try {
                    pendingDuration = Double.parseDouble(value);
                } catch (Exception e) {
                    pendingDuration = -1;
                }
                continue;
            }

            // Segment dosya adı satırı: burada segment tamamlanmış olur
            if (!line.isEmpty() && !line.startsWith("#") && pendingDuration > 0) {
                if (segmentWallStart > 0) {
                    long segmentWallEnd = segmentWallStart + (long) (pendingDuration * 1000);
                    if (targetEpochMs >= segmentWallStart && targetEpochMs < segmentWallEnd) {
                        return cumulative + (targetEpochMs - segmentWallStart) / 1000.0;
                    }
                    // Hedef bu segmentten önceyse, playlist'in başına yakınsayalım
                    if (targetEpochMs < segmentWallStart) {
                        return cumulative;
                    }
                }
                cumulative += pendingDuration;
                pendingDuration = -1;
                segmentWallStart = -1;
            }
        }

        // PROGRAM-DATE-TIME hiç bulunamadıysa eski yönteme düşeriz
        if (cumulative == 0 && fallbackStartMs > 0) {
            return Math.max(0, (targetEpochMs - fallbackStartMs) / 1000.0);
        }

        // Hedef kaydın sonundan ilerideyse, kaydın sonunu döneriz
        return cumulative;
    }

    private long parseEpochMillis(String value) {
        try {
            return java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli();
        } catch (Exception e) {
            try {
                return java.time.Instant.parse(value).toEpochMilli();
            } catch (Exception ignored) {
                return -1;
            }
        }
    }

    private String formatSeconds(double totalSeconds) {
        long total = (long) Math.floor(totalSeconds);
        long h = total / 3600;
        long m = (total % 3600) / 60;
        double s = totalSeconds - (h * 3600) - (m * 60);
        return String.format(java.util.Locale.US, "%02d:%02d:%06.3f", h, m, s);
    }
}