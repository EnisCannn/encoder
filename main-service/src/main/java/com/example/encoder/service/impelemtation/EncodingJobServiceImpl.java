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

            String outName = request.getOutputFileName() != null
                    ? request.getOutputFileName()
                    : buildSingleOutputFileName(video, preset);

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
    /**
     * Tekli isin cikti dosya adi.
     *
     * Eski sema "encoded_{rastgele}_{yukseklik}p_{sablon adi}.mp4" idi ve uc
     * sorunu vardi: kaynak videonun adi hic gecmiyordu (dosyaya bakip hangi
     * videodan geldigi anlasilmiyordu), rastgele on ek BASTA oldugu icin klasor
     * alfabetik siralandiginda ayni videonun ciktilari birbirinden ayri
     * dusuyordu, ve yukseklik hem on ekte hem sablon adinin icinde tekrarliyordu.
     *
     * Yeni sema anlamli parcalari one aliyor, benzersizlik kodunu sona:
     *   test_video_1080p_4000k_24fps_2e78eb1a.mp4
     * Boylece siralama once kaynaga, sonra cozunurluge gore grupluyor.
     *
     * Ad sablon ADINDAN degil sablonun ALANLARINDAN uretiliyor: sablon adi
     * kullanicinin yazdigi serbest metin, icinde zaten cozunurluk gecince
     * tekrar olusuyordu. Kare hizi yalnizca zorlandiginda yaziliyor; bos
     * birakildiginda kaynagin hizi korundugu icin ada yazmanin bilgisi yok.
     */
    private String buildSingleOutputFileName(Video video, EncodingPreset preset) {
        StringBuilder name = new StringBuilder(sourceLabel(video));

        if (preset.getHeight() != null) {
            name.append('_').append(preset.getHeight()).append('p');
        }
        if (preset.getVideoBitrate() != null) {
            name.append('_').append(preset.getVideoBitrate()).append('k');
        }
        if (preset.getFrameRate() != null) {
            name.append('_')
                .append(preset.getFrameRate().stripTrailingZeros().toPlainString())
                .append("fps");
        }
        name.append('_').append(UUID.randomUUID().toString().substring(0, 8)).append(".mp4");
        return name.toString();
    }

    /** Kaynak dosya adi: uzantisiz, dosya sisteminde guvenli, makul uzunlukta. */
    private String sourceLabel(Video video) {
        String raw = video != null ? video.getOriginalFileName() : null;
        if (raw == null || raw.isBlank()) {
            return "video";
        }
        int dot = raw.lastIndexOf('.');
        if (dot > 0) {
            raw = raw.substring(0, dot);
        }
        String safe = raw.replaceAll("[^A-Za-z0-9.-]", "_").replaceAll("_+", "_");
        if (safe.length() > 40) {
            safe = safe.substring(0, 40);
        }
        return safe.isBlank() ? "video" : safe;
    }

    /** Paket modunda kullaniliyor: ciktilar zaten batch klasorunun altinda. */
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
