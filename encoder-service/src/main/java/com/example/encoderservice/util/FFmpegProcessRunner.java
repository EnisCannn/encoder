package com.example.encoderservice.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * ffmpeg / ffprobe süreçlerini ortak biçimde çalıştırır.
 *
 * Elle yazılan her çağrıda tekrarlanan iki tuzağı birden kapatır:
 *
 * 1) Çıktı okunmazsa pipe tamponu dolduğunda ffmpeg yazamaz hale gelip kilitlenir.
 *    Burada çıktı her zaman boşaltılır.
 *
 * 2) {@code process.waitFor()} süresizdir; takılan bir süreç isteği sonsuza kadar
 *    bloklar. Bekçi thread süre aşımında süreci öldürür, çıktı akışı kapanınca
 *    okuma döngüsü de kendiliğinden biter.
 *
 * Okuma çağıran thread'de kaldığı için {@code lineConsumer} içinden veritabanına
 * yazmak güvenlidir (encode ilerlemesi böyle kaydediliyor).
 */
public final class FFmpegProcessRunner {

    /** Hata mesajlarında taşınacak çıktı üst sınırı; uzun encode'lar belleği şişirmesin. */
    private static final int MAX_OUTPUT_CHARS = 20_000;

    private FFmpegProcessRunner() {
    }

    public record Result(int exitCode, String output) {
        public boolean isSuccess() {
            return exitCode == 0;
        }
    }

    public static Result run(List<String> command, long timeoutSeconds)
            throws IOException, InterruptedException {
        return run(command, timeoutSeconds, null);
    }

    public static Result run(List<String> command, long timeoutSeconds, Consumer<String> lineConsumer)
            throws IOException, InterruptedException {

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        AtomicBoolean timedOut = new AtomicBoolean(false);
        Thread watchdog = new Thread(() -> {
            try {
                if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                    timedOut.set(true);
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        watchdog.setDaemon(true);
        watchdog.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() < MAX_OUTPUT_CHARS) {
                    output.append(line).append('\n');
                }
                if (lineConsumer != null) {
                    lineConsumer.accept(line);
                }
            }
        }

        int exitCode = process.waitFor();
        watchdog.interrupt();

        if (timedOut.get()) {
            throw new RuntimeException("ffmpeg " + timeoutSeconds
                    + " saniyede tamamlanamadı, süreç durduruldu.");
        }

        return new Result(exitCode, output.toString());
    }
}
