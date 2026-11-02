package com.example.encoder.service.impelemtation;

import com.example.encoder.dto.request.CreateEncodingJobRequest;
import com.example.encoder.entity.EncodeSet;
import com.example.encoder.entity.EncodingJob;
import com.example.encoder.entity.EncodingPreset;
import com.example.encoder.entity.JobStatus;
import com.example.encoder.entity.Video;
import com.example.encoder.entity.enums.Format;
import com.example.encoder.entity.enums.SubtitleMode;
import com.example.encoder.exception.BadRequestException;
import com.example.encoder.exception.NotFoundException;
import com.example.encoder.repository.EncodeSetRepository;
import com.example.encoder.repository.EncodingJobRepository;
import com.example.encoder.repository.EncodingPresetRepository;
import com.example.encoder.repository.VideoRepository;
import com.example.encoder.service.SubtitleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncodingJobServiceImplTest {

    @Mock private EncodingJobRepository jobRepository;
    @Mock private VideoRepository videoRepository;
    @Mock private EncodingPresetRepository presetRepository;
    @Mock private EncodeSetRepository encodeSetRepository;
    @Mock private SubtitleService subtitleService;

    @InjectMocks private EncodingJobServiceImpl service;

    private final UUID videoId = UUID.randomUUID();
    private Video video;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "outputFolder", "/video_test/output");
        video = new Video();
        video.setOriginalFileName("Test Video (1).mov");
        video.setPath("/video_test/input/abc.mov");
    }

    // ---------- dogrulamalar ----------

    @Test
    void sablonVePaketSecilmemisseReddeder() {
        assertThatThrownBy(() -> service.createJobs(request(null, null)))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(videoRepository);
    }

    @Test
    void sablonVePaketBirlikteSecilmisseReddeder() {
        assertThatThrownBy(() -> service.createJobs(request(UUID.randomUUID(), UUID.randomUUID())))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Aynı anda");
    }

    @Test
    void videoYoksaNotFound() {
        when(videoRepository.findById(videoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createJobs(request(UUID.randomUUID(), null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Video");
    }

    @Test
    void sablonYoksaNotFound() {
        UUID presetId = UUID.randomUUID();
        when(videoRepository.findById(videoId)).thenReturn(Optional.of(video));
        when(presetRepository.findById(presetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createJobs(request(presetId, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Preset");
    }

    @Test
    void bosPaketReddedilir() {
        UUID setId = UUID.randomUUID();
        EncodeSet set = new EncodeSet();
        set.setName("Bos");
        set.setPresets(new ArrayList<>());
        when(videoRepository.findById(videoId)).thenReturn(Optional.of(video));
        when(encodeSetRepository.findByIdWithPresets(setId)).thenReturn(Optional.of(set));

        assertThatThrownBy(() -> service.createJobs(request(null, setId)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("hiç şablon yok");
        verify(jobRepository, never()).saveAll(anyList());
    }

    // ---------- tekli mod ----------

    @Test
    void tekliMod_dosyaAdiKaynakCozunurlukBitrateVeKisaKoddanOlusur() {
        UUID presetId = UUID.randomUUID();
        EncodingPreset preset = preset("1080p Web", 1080, 4000, Format.MP4);
        preset.setFrameRate(new BigDecimal("25.00"));
        when(videoRepository.findById(videoId)).thenReturn(Optional.of(video));
        when(presetRepository.findById(presetId)).thenReturn(Optional.of(preset));
        when(jobRepository.save(any(EncodingJob.class))).thenAnswer(inv -> inv.getArgument(0));

        EncodingJob job = service.createJobs(request(presetId, null)).get(0);

        // "Test Video (1).mov" -> guvenli karakterler, uzanti atilir; fps sondaki sifirlar olmadan
        assertThat(job.getOutputFileName()).matches("Test_Video_1_1080p_4000k_25fps_[0-9a-f]{8}\\.mp4");
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getBatchId()).isNull();
        assertThat(job.getInputPath()).isEqualTo(video.getPath());
    }

    @Test
    void tekliMod_kullanicininVerdigiDosyaAdiKullanilir() {
        UUID presetId = UUID.randomUUID();
        when(videoRepository.findById(videoId)).thenReturn(Optional.of(video));
        when(presetRepository.findById(presetId)).thenReturn(Optional.of(preset("x", 720, 1500, Format.MKV)));
        when(jobRepository.save(any(EncodingJob.class))).thenAnswer(inv -> inv.getArgument(0));
        CreateEncodingJobRequest request = request(presetId, null);
        request.setOutputFileName("benim.mkv");

        assertThat(service.createJobs(request).get(0).getOutputFileName()).isEqualTo("benim.mkv");
    }

    @Test
    void altyaziYoksaModHerZamanNoneOlurVeVttUretilmez() {
        UUID presetId = UUID.randomUUID();
        when(videoRepository.findById(videoId)).thenReturn(Optional.of(video));
        when(presetRepository.findById(presetId)).thenReturn(Optional.of(preset("x", 720, 1500, null)));
        when(jobRepository.save(any(EncodingJob.class))).thenAnswer(inv -> inv.getArgument(0));
        CreateEncodingJobRequest request = request(presetId, null);
        request.setSubtitleMode(SubtitleMode.BURN);

        EncodingJob job = service.createJobs(request).get(0);

        assertThat(job.getSubtitleMode()).isEqualTo(SubtitleMode.NONE);
        assertThat(job.getSubtitleVttFileName()).isNull();
        // Format bos ise uzanti mp4
        assertThat(job.getOutputFileName()).endsWith(".mp4");
        verifyNoInteractions(subtitleService);
    }

    @Test
    void sidecarAltyaziBirKezVttyeCevrilir() {
        UUID presetId = UUID.randomUUID();
        when(videoRepository.findById(videoId)).thenReturn(Optional.of(video));
        when(presetRepository.findById(presetId)).thenReturn(Optional.of(preset("x", 720, 1500, Format.MP4)));
        when(jobRepository.save(any(EncodingJob.class))).thenAnswer(inv -> inv.getArgument(0));
        CreateEncodingJobRequest request = request(presetId, null);
        request.setSubtitlePath("/video_test/input/alt.srt");
        request.setSubtitleLanguage("t r/../en");

        EncodingJob job = service.createJobs(request).get(0);

        // Dil kodu dosya adina girdigi icin yol karakterleri temizlenir
        assertThat(job.getSubtitleMode()).isEqualTo(SubtitleMode.SIDECAR);
        assertThat(job.getSubtitleVttFileName()).startsWith("subtitles/").endsWith("/tren.vtt");
        verify(subtitleService).convertToVtt(eq("/video_test/input/alt.srt"), any(Path.class), eq("tren"));
    }

    // ---------- paket modu ----------

    @Test
    void paketModu_herSablonIcinAyniBatchteBirIsAcilir() {
        UUID setId = UUID.randomUUID();
        EncodeSet set = new EncodeSet();
        set.setId(setId);
        set.setName("Web");
        set.setPresets(new ArrayList<>(List.of(
                preset("1080p Yuksek!", 1080, 4000, Format.MP4),
                preset("Ses", null, null, Format.MKV))));
        when(videoRepository.findById(videoId)).thenReturn(Optional.of(video));
        when(encodeSetRepository.findByIdWithPresets(setId)).thenReturn(Optional.of(set));
        when(jobRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<EncodingJob> jobs = service.createJobs(request(null, setId));

        assertThat(jobs).hasSize(2);
        UUID batchId = jobs.get(0).getBatchId();
        assertThat(batchId).isNotNull();
        assertThat(jobs).allSatisfy(job -> {
            assertThat(job.getBatchId()).isEqualTo(batchId);
            assertThat(job.getEncodeSetId()).isEqualTo(setId);
        });
        assertThat(jobs).extracting(EncodingJob::getOutputFileName).containsExactly(
                batchId + "/1080p_1080p_Yuksek_.mp4",
                batchId + "/Ses.mkv");
    }

    @Test
    void getJob_yoksaNotFound() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJob(id)).isInstanceOf(NotFoundException.class);
    }

    // ---------- yardimcilar ----------

    private CreateEncodingJobRequest request(UUID presetId, UUID encodeSetId) {
        CreateEncodingJobRequest request = new CreateEncodingJobRequest();
        request.setVideoId(videoId);
        request.setPresetId(presetId);
        request.setEncodeSetId(encodeSetId);
        return request;
    }

    private static EncodingPreset preset(String name, Integer height, Integer bitrate, Format format) {
        EncodingPreset preset = new EncodingPreset();
        preset.setName(name);
        preset.setHeight(height);
        preset.setVideoBitrate(bitrate);
        preset.setFormat(format);
        return preset;
    }
}
