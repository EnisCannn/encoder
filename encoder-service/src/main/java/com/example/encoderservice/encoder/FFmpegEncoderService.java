package com.example.encoderservice.encoder;

import com.example.encoderservice.entity.EncodingJob;
import com.example.encoderservice.entity.EncodingPreset;
import com.example.encoderservice.entity.Video;
import com.example.encoderservice.entity.enums.SubtitleMode;
import com.example.encoderservice.repository.EncodingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.example.encoderservice.util.FFmpegProcessRunner;
import org.springframework.beans.factory.annotation.Value;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FFmpegEncoderService implements EncoderService {

    private final EncodingJobRepository jobRepository;

    // Takilan bir ffmpeg surecinin worker'i sonsuza kadar mesgul etmesini engeller
    @Value("${encoder.ffmpeg.encode-timeout-seconds:7200}")
    private long encodeTimeoutSeconds;

    @Override
    public EncodingResult encode(EncodingJob job, Path output) {
        Video video = job.getVideo();
        EncodingPreset preset = job.getPreset();

        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-y");
        command.add("-nostdin");

        // 1. Orijinal Görüntü
        command.add("-i");
        command.add(video.getPath());

        // 2. Dublaj Ses Dosyası
        boolean hasDubbing = job.getDubbingPath() != null && !job.getDubbingPath().isEmpty();
        if (hasDubbing) {
            command.add("-i"); command.add(job.getDubbingPath());
            command.add("-map"); command.add("0:v");
            command.add("-map"); command.add("1:a");
        }

        // 3. Video Codec
        if (preset.getVideoCodec() != null) {
            String vCodec = preset.getVideoCodec().name();
            if ("H264".equalsIgnoreCase(vCodec)) {
                command.add("-c:v");
                command.add("libx264");
            } else if ("H265".equalsIgnoreCase(vCodec)) {
                command.add("-c:v");
                command.add("libx265");
            } else if ("AV1".equalsIgnoreCase(vCodec)) {
                command.add("-c:v");
                command.add("libsvtav1");
            }
        }

        // 4. Video Bitrate (CBR - Sabit Bit Hızı ZORLAMASI)
        if (preset.getVideoBitrate() != null) {
            String bitrate = preset.getVideoBitrate() + "k";
            command.add("-b:v"); command.add(bitrate);
            // Özelliklerde birebir aynı görünmesi için maksimum ve minimum oranları sabitliyoruz
            command.add("-minrate"); command.add(bitrate);
            command.add("-maxrate"); command.add(bitrate);
            command.add("-bufsize"); command.add((preset.getVideoBitrate() * 2) + "k");
        }

        // 5. FPS (Kare Hızı) ZORLAMASI - YENİ EKLENDİ
        if (preset.getFrameRate() != null) {
            command.add("-r");
            command.add(preset.getFrameRate().toString());
        }

        // 6. Filtreleri Birleştirme (Çözünürlük ve Altyazı)
        List<String> videoFilters = new ArrayList<>();

        if (preset.getWidth() != null && preset.getHeight() != null) {
            videoFilters.add("scale=" + preset.getWidth() + ":" + preset.getHeight());
        }

        // Altyazı yalnızca BURN modunda görüntüye yakılır.
        // SIDECAR modunda main-service ayrı bir .vtt üretir, burada yakma yapılmaz;
        // böylece altyazı oynatıcıdan kapatılabilir ve her kaliteye tekrar gömülmez.
        boolean burnSubtitle = job.getSubtitleMode() == SubtitleMode.BURN
                && job.getSubtitlePath() != null
                && !job.getSubtitlePath().isEmpty();

        if (burnSubtitle) {
            String escapedSubtitlePath = job.getSubtitlePath().replace("\\", "/").replace(":", "\\:");
            videoFilters.add("subtitles='" + escapedSubtitlePath + "'");
        }

        if (!videoFilters.isEmpty()) {
            command.add("-vf");
            command.add(String.join(",", videoFilters));
        }

        // 7. Ses Codec ve Bitrate
        if (preset.getAudioCodec() != null) {
            String aCodec = preset.getAudioCodec().name();
            if ("AAC".equalsIgnoreCase(aCodec)) {
                command.add("-c:a"); command.add("aac");
                if (preset.getAudioBitrate() != null) {
                    command.add("-b:a"); command.add(preset.getAudioBitrate() + "k");
                }
            }
        }

        // En kısa kaynak dosya (video veya ses) bittiğinde işlemi sonlandır
        command.add("-shortest");

        command.add(output.toString());

        try {
            System.out.println("--- FFMPEG MOTORU ÇALIŞMAYA BAŞLADI (Job ID: " + job.getId() + ") ---");
            System.out.println("[FFMPEG CMD]: " + String.join(" ", command));

            // Ilerleme okuma cagiran thread'de kaliyor; jobRepository yazmasi
            // scheduler thread'inde oldugu gibi devam ediyor.
            FFmpegProcessRunner.Result result = FFmpegProcessRunner.run(
                    command, encodeTimeoutSeconds, line -> updateProgress(job, video, line));

            int exitCode = result.exitCode();
            System.out.println("--- FFMPEG MOTORU DURDU, Çıkış Kodu: " + exitCode + " ---");

            return EncodingResult.builder()
                    .success(exitCode == 0)
                    .outputPath(output.toString())
                    .exitCode(exitCode)
                    .errorMessage(exitCode != 0 ? "FFmpeg işlemi başarısız oldu" : null)
                    .build();

        } catch (Exception e) {
            System.out.println("SİSTEM HATASI: " + e.getMessage());
            return EncodingResult.builder()
                    .success(false)
                    .errorMessage("Sistem hatası: " + e.getMessage())
                    .build();
        }
    }

    /** ffmpeg'in "time=00:00:12.34" satirindan yuzdelik ilerlemeyi cikarip kaydeder. */
    private void updateProgress(EncodingJob job, Video video, String line) {
        if (!line.contains("time=")) {
            return;
        }
        try {
            int timeIndex = line.indexOf("time=") + 5;
            String timeString = line.substring(timeIndex, timeIndex + 11);

            String[] parts = timeString.split(":");
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            double seconds = Double.parseDouble(parts[2]);

            double currentSeconds = (hours * 3600) + (minutes * 60) + seconds;

            if (video.getDuration() != null && video.getDuration().doubleValue() > 0) {
                double totalSeconds = video.getDuration().doubleValue();
                int progress = (int) Math.round((currentSeconds / totalSeconds) * 100);
                progress = Math.min(progress, 100);

                if (job.getProgress() == null || !job.getProgress().equals(progress)) {
                    job.setProgress(progress);
                    jobRepository.saveAndFlush(job);
                    System.out.println("[PROGRESS]: %" + progress + " tamamlandı.");
                }
            }
        } catch (Exception e) {
            // Beklenmedik format gelirse atla
        }
    }
}