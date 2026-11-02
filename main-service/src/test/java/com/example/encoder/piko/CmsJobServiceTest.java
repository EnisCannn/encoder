package com.example.encoder.piko;

import com.example.encoder.entity.EncodeSet;
import com.example.encoder.entity.EncodingJob;
import com.example.encoder.entity.EncodingPreset;
import com.example.encoder.entity.JobStatus;
import com.example.encoder.entity.Video;
import com.example.encoder.entity.enums.SubtitleMode;
import com.example.encoder.repository.EncodeSetRepository;
import com.example.encoder.repository.EncodingJobRepository;
import com.example.encoder.repository.EncodingPresetRepository;
import com.example.encoder.service.VideoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CmsJobServiceTest {

    @Mock private CmsJobRepository cmsJobRepository;
    @Mock private EncodingJobRepository jobRepository;
    @Mock private EncodeSetRepository encodeSetRepository;
    @Mock private EncodingPresetRepository presetRepository;
    @Mock private VideoService videoService;

    @InjectMocks private CmsJobService service;

    @TempDir Path tempDir;

    private Path inputFile;

    @BeforeEach
    void setUp() throws IOException {
        inputFile = Files.createFile(tempDir.resolve("film.mov"));
    }

    // ---------- create ----------

    @Test
    void create_bilinmeyenPresetiReddeder() {
        assertThatThrownBy(() -> service.create("Uydurma", inputFile.toString(), "/out"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bilinmeyen preset");
        verify(jobRepository, never()).saveAll(any());
    }

    @Test
    void create_girisDosyasiYoksaReddeder() {
        assertThatThrownBy(() -> service.create("MOVVideo", tempDir.resolve("yok.mov").toString(), "/out"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Giris dosyasi bulunamadi");
    }

    @Test
    void create_ciktiKlasoruBossaReddeder() {
        assertThatThrownBy(() -> service.create("MOVVideo", inputFile.toString(), "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cikti klasoru bos");
    }

    @Test
    void create_videoPresetiPaketinHerSablonuIcinSiraliProfilIsiAcar() {
        Video video = video();
        when(videoService.registerExisting(inputFile)).thenReturn(video);
        EncodeSet set = new EncodeSet();
        set.setId(UUID.randomUUID());
        set.setPresets(new ArrayList<>(List.of(preset("p1"), preset("p2"), preset("p3"), preset("p4"))));
        when(encodeSetRepository.findByNameWithPresets(PikoProfiles.VIDEO_SET_NAME)).thenReturn(Optional.of(set));
        when(cmsJobRepository.save(any(CmsJob.class))).thenAnswer(inv -> inv.getArgument(0));

        CmsJob cmsJob = service.create(" MOVVideo ", inputFile.toString(), "/media/out");

        List<EncodingJob> jobs = capturedJobs();
        assertThat(jobs).extracting(EncodingJob::getOutputFileName)
                .containsExactly("video_profile1.mp4", "video_profile2.mp4",
                        "video_profile3.mp4", "video_profile4.mp4");
        assertThat(jobs).extracting(job -> job.getPreset().getName())
                .containsExactly("p1", "p2", "p3", "p4");
        assertThat(jobs).allSatisfy(job -> {
            assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
            assertThat(job.getOutputDirectory()).isEqualTo(Path.of("/media/out").toString());
            assertThat(job.getBatchId()).isEqualTo(cmsJob.getBatchId());
            assertThat(job.getEncodeSetId()).isEqualTo(set.getId());
            assertThat(job.getAudioOnly()).isFalse();
            assertThat(job.getSubtitleMode()).isEqualTo(SubtitleMode.NONE);
            assertThat(job.getVideo()).isSameAs(video);
        });
        assertThat(cmsJob.getPresetName()).isEqualTo("MOVVideo");
        assertThat(cmsJob.getAudioOnly()).isFalse();
    }

    @Test
    void create_sesPresetiTekBirSadeceSesIsiAcar() {
        when(videoService.registerExisting(inputFile)).thenReturn(video());
        EncodingPreset audio = preset(PikoProfiles.AUDIO_PRESET_NAME);
        when(presetRepository.findByName(PikoProfiles.AUDIO_PRESET_NAME)).thenReturn(Optional.of(audio));
        when(cmsJobRepository.save(any(CmsJob.class))).thenAnswer(inv -> inv.getArgument(0));

        CmsJob cmsJob = service.create("WAVAudio", inputFile.toString(), "/media/out");

        List<EncodingJob> jobs = capturedJobs();
        assertThat(jobs).singleElement().satisfies(job -> {
            assertThat(job.getOutputFileName()).isEqualTo(PikoProfiles.AUDIO_FILE_NAME);
            assertThat(job.getAudioOnly()).isTrue();
            assertThat(job.getPreset()).isSameAs(audio);
            assertThat(job.getEncodeSetId()).isNull();
        });
        assertThat(cmsJob.getAudioOnly()).isTrue();
    }

    @Test
    void create_pikoPaketiTanimliDegilseHataVerir() {
        when(videoService.registerExisting(inputFile)).thenReturn(video());
        when(encodeSetRepository.findByNameWithPresets(PikoProfiles.VIDEO_SET_NAME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("MP4Video", inputFile.toString(), "/out"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(PikoProfiles.VIDEO_SET_NAME);
    }

    // ---------- status ----------

    @Test
    void status_birProfilHataliysaIsHatalidir() {
        givenJobs(job(JobStatus.COMPLETED, 100), failedJob("ffmpeg patladi"), job(JobStatus.PROCESSING, 40));

        CmsJobStatus status = service.status(1);

        assertThat(status.status()).isEqualTo(JobStatus.FAILED);
        assertThat(status.errorMessage()).isEqualTo("ffmpeg patladi");
    }

    @Test
    void status_iptalEdilenProfilVarsaIsIptaldir() {
        givenJobs(job(JobStatus.COMPLETED, 100), job(JobStatus.CANCELLED, 0));

        assertThat(service.status(1).status()).isEqualTo(JobStatus.CANCELLED);
    }

    @Test
    void status_hepsiBittiyseYuzdeYuzVeEnGecBitisZamani() {
        Instant early = Instant.parse("2026-11-01T10:00:00Z");
        Instant late = Instant.parse("2026-11-01T10:05:00Z");
        EncodingJob first = job(JobStatus.COMPLETED, 100);
        first.setCompletedAt(late);
        EncodingJob second = job(JobStatus.COMPLETED, 100);
        second.setCompletedAt(early);
        givenJobs(first, second);

        CmsJobStatus status = service.status(1);

        assertThat(status.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(status.progress()).isEqualTo(100);
        assertThat(status.completedAt()).isEqualTo(late);
    }

    @Test
    void status_devamEdenIsteIlerlemeProfillerinOrtalamasidir() {
        givenJobs(job(JobStatus.COMPLETED, 100), job(JobStatus.PROCESSING, 50),
                job(JobStatus.PENDING, null), job(JobStatus.PENDING, 0));

        CmsJobStatus status = service.status(1);

        assertThat(status.status()).isEqualTo(JobStatus.PROCESSING);
        assertThat(status.progress()).isEqualTo(38); // (100 + 50 + 0 + 0) / 4 = 37.5 -> 38
    }

    @Test
    void status_hicbiriBaslamadiysaBekliyor() {
        givenJobs(job(JobStatus.PENDING, 0), job(JobStatus.PENDING, 0));

        CmsJobStatus status = service.status(1);

        assertThat(status.status()).isEqualTo(JobStatus.PENDING);
        assertThat(status.progress()).isZero();
    }

    @Test
    void status_encodeKaydiYoksaHatali() {
        givenJobs();

        assertThat(service.status(1).status()).isEqualTo(JobStatus.FAILED);
    }

    @Test
    void status_bilinmeyenIsNumarasiniReddeder() {
        when(cmsJobRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.status(99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Is bulunamadi");
    }

    // ---------- cancel ----------

    @Test
    void cancel_yalnizcaBekleyenProfilleriIptalEder() {
        EncodingJob running = job(JobStatus.PROCESSING, 30);
        EncodingJob pending1 = job(JobStatus.PENDING, 0);
        EncodingJob pending2 = job(JobStatus.PENDING, 0);
        givenJobs(running, pending1, pending2);

        int cancelled = service.cancel(1);

        assertThat(cancelled).isEqualTo(2);
        assertThat(running.getStatus()).isEqualTo(JobStatus.PROCESSING);
        assertThat(pending1.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(pending2.getStatus()).isEqualTo(JobStatus.CANCELLED);
    }

    // ---------- yardimcilar ----------

    @SuppressWarnings("unchecked")
    private List<EncodingJob> capturedJobs() {
        ArgumentCaptor<List<EncodingJob>> captor = ArgumentCaptor.forClass(List.class);
        verify(jobRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private void givenJobs(EncodingJob... jobs) {
        CmsJob cmsJob = new CmsJob();
        cmsJob.setId(1);
        cmsJob.setBatchId(UUID.randomUUID());
        when(cmsJobRepository.findById(1)).thenReturn(Optional.of(cmsJob));
        when(jobRepository.findByBatchIdOrderByCreatedAtAsc(cmsJob.getBatchId())).thenReturn(List.of(jobs));
    }

    private static EncodingJob job(JobStatus status, Integer progress) {
        EncodingJob job = new EncodingJob();
        job.setStatus(status);
        job.setProgress(progress);
        return job;
    }

    private static EncodingJob failedJob(String message) {
        EncodingJob job = job(JobStatus.FAILED, 20);
        job.setErrorMessage(message);
        return job;
    }

    private static EncodingPreset preset(String name) {
        EncodingPreset preset = new EncodingPreset();
        preset.setName(name);
        return preset;
    }

    private Video video() {
        Video video = new Video();
        video.setOriginalFileName("film.mov");
        video.setPath(inputFile.toString());
        return video;
    }
}
