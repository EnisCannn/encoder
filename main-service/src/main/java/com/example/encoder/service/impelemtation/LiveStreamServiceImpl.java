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

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-err_detect"); command.add("ignore_err");
        command.add("-user_agent");
        command.add("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        // Ağ kesintilerinde ffmpeg kendi kendine yeniden bağlansın
        command.add("-reconnect"); command.add("1");
        command.add("-reconnect_streamed"); command.add("1");
        command.add("-reconnect_delay_max"); command.add("10");

        // KRİTİK: Kaynak dosya/http video bittiğinde baştan sarıp devam eder.
        // Süreç ölmez, kullanıcı durdurana kadar tek bir sürekli akış olarak sürer.
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
        // Her segmentin gerçek saatini playlist'e yazar; klip hesabı bunu kullanır
        command.add("-hls_flags"); command.add("program_date_time");

        Path playlistPath = Paths.get(streamDir.getAbsolutePath(), "index.m3u8");
        command.add(playlistPath.toString());

        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();

            activeStreams.put(stream.getId(), process);
            stream.setStatus("LIVE");
            repository.save(stream);

            final UUID streamId = stream.getId();
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
                    // -stream_loop sayesinde süreç normalde kendiliğinden bitmez;
                    // buraya düşülmesi kullanıcının durdurması ya da gerçek bir
                    // hata anlamına gelir. Durumu ona göre güncelle.
                    repository.findById(streamId).ifPresent(s -> {
                        if ("LIVE".equals(s.getStatus()) || "STARTING".equals(s.getStatus())) {
                            s.setStatus("STOPPED");
                            if (s.getStreamEndTime() == null) {
                                s.setStreamEndTime(System.currentTimeMillis());
                            }
                            repository.save(s);
                        }
                    });
                    System.out.println("--- YAYIN SÜRECİ SONLANDI: " + streamId + " ---");
                }
            }).start();
        } catch (Exception e) {
            stream.setStatus("ERROR");
            repository.save(stream);
            throw new RuntimeException("Yayın başlatılamadı: " + e.getMessage());
        }
        return stream;
    }

    @Override
    public void stopStream(UUID streamId) {
        Process process = activeStreams.get(streamId);
        if (process != null) {
            process.destroy();
            activeStreams.remove(streamId);
        }
        repository.findById(streamId).ifPresent(stream -> {
            stream.setStatus("STOPPED");
            stream.setStreamEndTime(System.currentTimeMillis());
            repository.save(stream);
        });
    }

    @Override
    public void deleteStream(UUID streamId) {
        Process process = activeStreams.get(streamId);
        if (process != null) {
            process.destroy();
            activeStreams.remove(streamId);
        }
        repository.findById(streamId).ifPresent(repository::delete);
    }

    @Override
    public String createLiveClip(LiveClipRequest request) {
        LiveStream stream = repository.findById(UUID.fromString(request.getStreamId()))
                .orElseThrow(() -> new RuntimeException("Yayın bulunamadı!"));

        EncodingPreset preset = presetRepository.findById(UUID.fromString(request.getPresetId()))
                .orElseThrow(() -> new RuntimeException("Şablon bulunamadı!"));

        // Klip DAİMA diskteki gerçek yayın kaydından (index.m3u8) kesilir,
        // kullanıcının orijinal yüklediği dosyadan değil.
        Path m3u8Path = Paths.get(stream.getOutputFolder(), "index.m3u8");
        File clipsDir = new File(clipsBaseFolder);
        if (!clipsDir.exists()) {
            clipsDir.mkdirs();
        }

        if (!m3u8Path.toFile().exists()) {
            m3u8Path = Paths.get(stream.getOutputFolder(), "index.m3u8.tmp");
        }

        // --- CANLI YAYIN DONMA ÇÖZÜMÜ ---
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

        // Playlist'teki ilk PROGRAM-DATE-TIME değeri, kaydın gerçek başlangıç saatidir.
        // Bulunamazsa yayının başlatıldığı ana düşülür.
        long timelineOrigin = readFirstProgramDateTime(lines);
        if (timelineOrigin <= 0) {
            timelineOrigin = stream.getStreamStartTime();
        }

        long startOffsetSec = (request.getStartTime() - timelineOrigin) / 1000;
        long endOffsetSec = (request.getEndTime() - timelineOrigin) / 1000;

        if (startOffsetSec < 0) startOffsetSec = 0;
        if (endOffsetSec < 0) endOffsetSec = 0;

        if (endOffsetSec <= startOffsetSec) {
            try { java.nio.file.Files.deleteIfExists(snapshotM3u8); } catch (Exception ignored) {}
            throw new RuntimeException("Zamanlama Hatası! Bitiş süresi başlangıçtan büyük olmalıdır.");
        }

        long durationSec = endOffsetSec - startOffsetSec;

        String formattedStartTime = String.format("%02d:%02d:%02d", startOffsetSec / 3600, (startOffsetSec % 3600) / 60, startOffsetSec % 60);
        String formattedDuration = String.format("%02d:%02d:%02d", durationSec / 3600, (durationSec % 3600) / 60, durationSec % 60);

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

    private long readFirstProgramDateTime(List<String> lines) {
        for (String line : lines) {
            if (line.startsWith("#EXT-X-PROGRAM-DATE-TIME:")) {
                String value = line.substring("#EXT-X-PROGRAM-DATE-TIME:".length()).trim();
                try {
                    return java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli();
                } catch (Exception e) {
                    try {
                        return java.time.Instant.parse(value).toEpochMilli();
                    } catch (Exception ignored) {
                        return 0;
                    }
                }
            }
        }
        return 0;
    }
}