package com.example.encoder.service.impelemtation;

import com.example.encoder.dto.request.CreateEncodingJobRequest;
import com.example.encoder.entity.EncodeSet;
import com.example.encoder.entity.EncodingJob;
import com.example.encoder.entity.EncodingPreset;
import com.example.encoder.entity.JobStatus;
import com.example.encoder.entity.Video;
import com.example.encoder.entity.enums.SubtitleMode;
import com.example.encoder.repository.EncodeSetRepository;
import com.example.encoder.repository.EncodingJobRepository;
import com.example.encoder.repository.EncodingPresetRepository;
import com.example.encoder.repository.VideoRepository;
import com.example.encoder.service.EncodingJobService;
import com.example.encoder.service.SubtitleService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EncodingJobServiceImpl implements EncodingJobService {

    private final EncodingJobRepository jobRepository;
    private final VideoRepository videoRepository;
    private final EncodingPresetRepository presetRepository;
    private final EncodeSetRepository encodeSetRepository;
    private final SubtitleService subtitleService;

    @Value("${encoder.folder.output}")
    private String outputFolder;

    @Override
    @Transactional
    public List<EncodingJob> createJobs(CreateEncodingJobRequest request) {
        if (request.getEncodeSetId() == null && request.getPresetId() == null) {
            throw new RuntimeException("Bir şablon (presetId) ya da bir paket (encodeSetId) seçilmeli!");
        }
        if (request.getEncodeSetId() != null && request.getPresetId() != null) {
            throw new RuntimeException("Aynı anda hem şablon hem paket seçilemez!");
        }

        Video video = videoRepository.findById(request.getVideoId())
                .orElseThrow(() -> new RuntimeException("Video bulunamadı!"));

        // TEKLİ MOD - eski davranış birebir korunuyor
        if (request.getPresetId() != null) {
            EncodingPreset preset = presetRepository.findById(request.getPresetId())
                    .orElseThrow(() -> new RuntimeException("Preset bulunamadı!"));

            // Cikti adi yalnizca kaynak dosya adindan turetiliyordu: ayni videoyu iki
            // farkli sablonla donusturunce ikisi de "encoded_<video>.mp4" dosyasina
            // yaziyor ve ikincisi birincinin uzerine biniyordu. Artik sablon adi ve
            // kisa bir benzersiz on ek ile her isin kendi dosyasi var.
            String outName = request.getOutputFileName() != null
                    ? request.getOutputFileName()
                    : "encoded_" + UUID.randomUUID().toString().substring(0, 8)
                      + "_" + buildRenditionFileName(preset);

            // Tekli işte altyazı klasörü çakışmasın diye rastgele bir alt klasör kullanılıyor
            String vttFileName = prepareSidecarSubtitle(request, "subtitles/" + UUID.randomUUID());

            EncodingJob job = buildJob(request, video, preset, outName, null, null, vttFileName);
            return List.of(jobRepository.save(job));
        }

        // PAKET MODU - setteki her preset için ayrı bir iş
        EncodeSet set = encodeSetRepository.findByIdWithPresets(request.getEncodeSetId())
                .orElseThrow(() -> new RuntimeException("Paket bulunamadı!"));

        if (set.getPresets().isEmpty()) {
            throw new RuntimeException("Seçilen paketin içinde hiç şablon yok: " + set.getName());
        }

        // Aynı yüklemeden doğan tüm işleri birbirine bağlayan kimlik.
        // Çıktılar da bu kimlikle adlandırılan tek bir klasöre yazılır ki
        // Aşama 4/5'te SMIL ve HLS master aynı klasörden üretilebilsin.
        UUID batchId = UUID.randomUUID();

        // Altyazı bir kez çevrilir; paketteki tüm kaliteler aynı .vtt dosyasını paylaşır.
        // Eski davranışta altyazı her kaliteye ayrı ayrı yakılıyordu.
        String vttFileName = prepareSidecarSubtitle(request, batchId + "/subtitles");

        List<EncodingJob> jobs = new ArrayList<>();
        for (EncodingPreset preset : set.getPresets()) {
            String outName = batchId + "/" + buildRenditionFileName(preset);
            jobs.add(buildJob(request, video, preset, outName, batchId, set.getId(), vttFileName));
        }

        return jobRepository.saveAll(jobs);
    }

    // GİZLİ METOT: Ortak iş nesnesi kurulumu
    private EncodingJob buildJob(CreateEncodingJobRequest request, Video video, EncodingPreset preset,
                                 String outputFileName, UUID batchId, UUID encodeSetId,
                                 String subtitleVttFileName) {
        EncodingJob job = new EncodingJob();
        job.setVideo(video);
        job.setPreset(preset);
        job.setStatus(JobStatus.PENDING); // İşi PENDING olarak kuyruğa bırakıyoruz
        job.setInputFileName(video.getOriginalFileName());
        job.setInputPath(video.getPath());
        job.setSubtitlePath(request.getSubtitlePath());
        job.setDubbingPath(request.getDubbingPath());
        job.setOutputFileName(outputFileName);
        job.setBatchId(batchId);
        job.setEncodeSetId(encodeSetId);
        job.setSubtitleMode(resolveSubtitleMode(request));
        job.setSubtitleVttFileName(subtitleVttFileName);
        if (subtitleVttFileName != null) {
            job.setSubtitleLanguage(request.getSubtitleLanguage());
            job.setSubtitleLabel(request.getSubtitleLabel());
        }
        return job;
    }

    // GİZLİ METOT: Altyazı yoksa mod her koşulda NONE'dır
    private SubtitleMode resolveSubtitleMode(CreateEncodingJobRequest request) {
        boolean hasSubtitle = request.getSubtitlePath() != null && !request.getSubtitlePath().isBlank();
        if (!hasSubtitle) {
            return SubtitleMode.NONE;
        }
        return request.getSubtitleMode() != null ? request.getSubtitleMode() : SubtitleMode.SIDECAR;
    }

    /**
     * SIDECAR modunda yüklenen altyazıyı .vtt'ye çevirip çıktı klasörüne yazar.
     * BURN ve NONE modlarında dosya üretilmez (yakma işini worker ffmpeg'de yapar).
     *
     * @return çıktı klasörüne göre göreli .vtt yolu, üretilmediyse null
     */
    private String prepareSidecarSubtitle(CreateEncodingJobRequest request, String relativeDir) {
        if (resolveSubtitleMode(request) != SubtitleMode.SIDECAR) {
            return null;
        }

        String language = request.getSubtitleLanguage() != null && !request.getSubtitleLanguage().isBlank()
                ? request.getSubtitleLanguage().replaceAll("[^A-Za-z0-9_-]", "")
                : "tr";

        Path targetDir = Paths.get(outputFolder).resolve(relativeDir);
        subtitleService.convertToVtt(request.getSubtitlePath(), targetDir, language);

        return relativeDir + "/" + language + ".vtt";
    }

    /**
     * Paket çıktısı için dosya adı: "720p_Web_720p_Tasarruf.mp4" gibi.
     * Çözünürlük başa alınıyor ki klasör listesi kalite sırasına göre okunabilir olsun.
     */
    private String buildRenditionFileName(EncodingPreset preset) {
        String safeName = preset.getName().replaceAll("[^A-Za-z0-9._-]", "_");
        String prefix = preset.getHeight() != null ? preset.getHeight() + "p_" : "";
        return prefix + safeName + ".mp4";
    }

    @Override
    public EncodingJob getJob(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("İş bulunamadı!"));
    }

    @Override
    public List<EncodingJob> getAllJobs() {
        // Tabloyu doldurmak için tüm kayıtları getirir
        return jobRepository.findAll();
    }

    @Override
    public List<EncodingJob> getJobsByBatch(UUID batchId) {
        return jobRepository.findByBatchIdOrderByCreatedAtAsc(batchId);
    }

    @Override
    public void deleteJob(UUID id) {
        // ID'sine göre işi siler
        jobRepository.deleteById(id);
    }
}
