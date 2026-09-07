package com.example.encoder.service.impelemtation;

import com.example.encoder.service.SubtitleService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SubtitleServiceImpl implements SubtitleService {

    @Value("${encoder.ffmpeg.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Override
    public Path convertToVtt(String sourcePath, Path targetDir, String baseName) {
        Path source = Paths.get(sourcePath);
        if (!Files.exists(source)) {
            throw new RuntimeException("Altyazı dosyası bulunamadı: " + sourcePath);
        }

        try {
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(baseName + ".vtt");

            // Kaynak zaten WebVTT ise dönüştürmeye gerek yok
            if (source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".vtt")) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                return target;
            }

            // ffmpeg .srt -> .vtt dönüşümünü tek komutta yapar
            ProcessBuilder builder = new ProcessBuilder(
                    ffmpegPath, "-y", "-nostdin", "-i", source.toString(), target.toString());
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
            if (exitCode != 0 || !Files.exists(target)) {
                throw new RuntimeException("Altyazı .vtt'ye çevrilemedi (ffmpeg çıkış kodu "
                        + exitCode + "): " + output);
            }

            return target;

        } catch (RuntimeException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Altyazı dönüştürme yarıda kesildi", e);
        } catch (Exception e) {
            throw new RuntimeException("Altyazı dönüştürme hatası: " + e.getMessage(), e);
        }
    }
}
