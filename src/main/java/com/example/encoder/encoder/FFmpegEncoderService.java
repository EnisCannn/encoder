package com.example.encoder.encoder;

import com.example.encoder.entity.EncodingPreset;
import com.example.encoder.entity.Video;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class FFmpegEncoderService implements EncoderService {

    @Override
    public EncodingResult encode(Video video, EncodingPreset preset, Path output) {
        List<String> command = new ArrayList<>();
        command.add("C:/ffmpeg/bin/ffmpeg.exe"); // Ajanın (FFmpeg) gerçek adresi
        command.add("-y"); // Aynı isimde dosya varsa üzerine yaz
        command.add("-i");
        command.add(video.getPath()); // Girdi dosyası
        command.add("-nostdin");
        // Video Codec Ayarı (Enum düzeltmesi yapıldı)
        if (preset.getVideoCodec() != null) {
            String vCodec = preset.getVideoCodec().name();
            if ("H264".equalsIgnoreCase(vCodec)) {
                command.add("-c:v"); command.add("libx264");
            } else if ("H265".equalsIgnoreCase(vCodec)) {
                command.add("-c:v"); command.add("libx265");
            }
        }

        // Video Bitrate
        if (preset.getVideoBitrate() != null) {
            command.add("-b:v"); command.add(preset.getVideoBitrate() + "k");
        }

        // Çözünürlük (Örn: -vf scale=854:480)
        if (preset.getWidth() != null && preset.getHeight() != null) {
            command.add("-vf"); command.add("scale=" + preset.getWidth() + ":" + preset.getHeight());
        }

        // Ses Ayarları (Enum düzeltmesi yapıldı)
        if (preset.getAudioCodec() != null) {
            String aCodec = preset.getAudioCodec().name();
            if ("AAC".equalsIgnoreCase(aCodec)) {
                command.add("-c:a"); command.add("aac");
                if (preset.getAudioBitrate() != null) {
                    command.add("-b:a"); command.add(preset.getAudioBitrate() + "k");
                }
            }
        }

        // Çıktı dosya yolu
        command.add(output.toString());

        try {
            ProcessBuilder builder = new ProcessBuilder(command);

            // YENİ: Java'nın tamponunu (kovasını) devre dışı bırakır, logları doğrudan konsola basar.
            // "Buffer Deadlock" (Kilitlenme) sorununu %100 çözer.
            builder.inheritIO();

            System.out.println("--- FFMPEG MOTORU ÇALIŞMAYA BAŞLADI ---");
            Process process = builder.start();

            // DİKKAT: Buradaki eski BufferedReader ve while(...) döngüsünü SİLDİK!
            // Çünkü inheritIO komutu logları bizim yerimize konsola akıtacak.

            int exitCode = process.waitFor();
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
}