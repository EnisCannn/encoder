package com.example.encoderservice.encoder;

import com.example.encoderservice.entity.EncodingJob;
import com.example.encoderservice.entity.EncodingPreset;
import com.example.encoderservice.entity.Video;
import com.example.encoderservice.entity.enums.AudioCodec;
import com.example.encoderservice.entity.enums.SubtitleMode;
import com.example.encoderservice.entity.enums.VideoCodec;
import com.example.encoderservice.repository.EncodingJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FFmpegEncoderServiceTest {

    private static final Path OUTPUT = Path.of("/out/video.mp4");

    @Mock private EncodingJobRepository jobRepository;

    @InjectMocks private FFmpegEncoderService service;

    private EncodingJob job;
    private EncodingPreset preset;

    @BeforeEach
    void setUp() {
        Video video = new Video();
        video.setPath("/in/kaynak.mov");
        video.setDuration(new BigDecimal("200"));

        preset = new EncodingPreset();
        preset.setVideoCodec(VideoCodec.H264);
        preset.setAudioCodec(AudioCodec.AAC);
        preset.setWidth(1280);
        preset.setHeight(720);
        preset.setVideoBitrate(1500);
        preset.setAudioBitrate(128);

        job = new EncodingJob();
        job.setVideo(video);
        job.setPreset(preset);
    }

    // ---------- buildCommand ----------

    @Test
    void h264SablonuCbrOlarakKurulur() {
        List<String> command = service.buildCommand(job, OUTPUT);

        assertThat(command).startsWith("ffmpeg", "-y", "-nostdin", "-i", "/in/kaynak.mov");
        assertThat(String.join(" ", command)).contains(
                "-c:v libx264",
                "-b:v 1500k -minrate 1500k -maxrate 1500k -bufsize 3000k",
                "-vf scale=1280:720",
                "-c:a aac -b:a 128k");
        assertThat(command).last().isEqualTo(OUTPUT.toString());
    }

    @Test
    void h265VeAv1DogruKutuphaneyiSecer() {
        preset.setVideoCodec(VideoCodec.H265);
        assertThat(service.buildCommand(job, OUTPUT)).containsSequence("-c:v", "libx265");

        preset.setVideoCodec(VideoCodec.AV1);
        assertThat(service.buildCommand(job, OUTPUT)).containsSequence("-c:v", "libsvtav1");
    }

    @Test
    void av1deMinrateMaxrateVerilmez() {
        // SVT-AV1 CBR'yi desteklemiyor, verilirse ffmpeg hata veriyor
        preset.setVideoCodec(VideoCodec.AV1);

        List<String> command = service.buildCommand(job, OUTPUT);

        assertThat(command).containsSequence("-b:v", "1500k").doesNotContain("-minrate", "-maxrate");
    }

    @Test
    void kareHiziYalnizcaSablondaVarsaZorlanir() {
        assertThat(service.buildCommand(job, OUTPUT)).doesNotContain("-r");

        preset.setFrameRate(new BigDecimal("25"));
        assertThat(service.buildCommand(job, OUTPUT)).containsSequence("-r", "25");
    }

    @Test
    void dublajVarsaGoruntuKaynaktanSesDublajdanAlinir() {
        job.setDubbingPath("/in/dublaj.wav");

        assertThat(service.buildCommand(job, OUTPUT))
                .containsSequence("-i", "/in/dublaj.wav", "-map", "0:v", "-map", "1:a");
    }

    @Test
    void burnModundaAltyaziGoruntuyeYakilir() {
        job.setSubtitleMode(SubtitleMode.BURN);
        job.setSubtitlePath("C:\\alt\\film.srt");

        List<String> command = service.buildCommand(job, OUTPUT);

        // Ters bolu duz boluye, iki nokta ffmpeg filtresi icin kacirilir
        assertThat(command).containsSequence("-vf", "scale=1280:720,subtitles='C\\:/alt/film.srt'");
    }

    @Test
    void sidecarModundaAltyaziYakilmaz() {
        job.setSubtitleMode(SubtitleMode.SIDECAR);
        job.setSubtitlePath("/alt/film.srt");

        assertThat(String.join(" ", service.buildCommand(job, OUTPUT))).doesNotContain("subtitles=");
    }

    @Test
    void sadeceSesIsindeGoruntuAyarlariAtlanir() {
        job.setAudioOnly(true);
        job.setDubbingPath("/in/dublaj.wav");
        job.setSubtitleMode(SubtitleMode.BURN);
        job.setSubtitlePath("/alt/film.srt");

        List<String> command = service.buildCommand(job, OUTPUT);

        assertThat(command).containsSequence("-map", "0:a:0", "-vn")
                .doesNotContain("-c:v", "-b:v", "-vf", "/in/dublaj.wav")
                .containsSequence("-c:a", "aac", "-b:a", "128k");
    }

    @Test
    void cozunurlukYarimTanimliysaOlceklenmez() {
        preset.setHeight(null);

        assertThat(service.buildCommand(job, OUTPUT)).doesNotContain("-vf");
    }

    // ---------- updateProgress ----------

    @Test
    void ilerlemeSuredenHesaplanipKaydedilir() {
        service.updateProgress(job, job.getVideo(), "frame= 100 fps=25 time=00:01:40.00 bitrate=1500k");

        assertThat(job.getProgress()).isEqualTo(50); // 100 sn / 200 sn
        verify(jobRepository).saveAndFlush(job);
    }

    @Test
    void ayniYuzdeTekrarKaydedilmez() {
        service.updateProgress(job, job.getVideo(), "time=00:01:40.00");
        service.updateProgress(job, job.getVideo(), "time=00:01:40.20");

        verify(jobRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void ilerlemeYuzuGecmez() {
        service.updateProgress(job, job.getVideo(), "time=00:05:00.00");

        assertThat(job.getProgress()).isEqualTo(100);
    }

    @Test
    void zamanSatiriDegilseVeyaSureBilinmiyorsaAtlanir() {
        service.updateProgress(job, job.getVideo(), "Stream #0:0: Video: h264");
        service.updateProgress(job, job.getVideo(), "time=bozuk");
        job.getVideo().setDuration(null);
        service.updateProgress(job, job.getVideo(), "time=00:01:40.00");

        // Is 0'dan basliyor; hicbir satir onu degistirmemeli
        assertThat(job.getProgress()).isZero();
        verify(jobRepository, never()).saveAndFlush(any());
    }
}
