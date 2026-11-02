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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * CMS isteklerini bildigimiz EncodingJob'lara cevirir. Worker CMS'i tanimiyor:
 * kuyruktaki isleri her zamanki gibi kapiyor, yalnizca outputDirectory ve
 * audioOnly alanlarina bakarak ciktiyi CMS'in klasorune yaziyor.
 */
@Service
@RequiredArgsConstructor
public class CmsJobService {

    private final CmsJobRepository cmsJobRepository;
    private final EncodingJobRepository jobRepository;
    private final EncodeSetRepository encodeSetRepository;
    private final EncodingPresetRepository presetRepository;
    private final VideoService videoService;

    /**
     * Yeni bir CMS isi acar. Hata mesajlari CMS'e aynen donuyor (Piko da hata
     * durumunda is numarasi yerine aciklama donduruyor), o yuzden ne oldugunu
     * soyleyecek kadar acik yaziliyor.
     */
    @Transactional
    public CmsJob create(String presetName, String inputPath, String outputDirectory) {
        String preset = presetName == null ? "" : presetName.trim();
        boolean audioOnly = PikoProfiles.AUDIO_PRESETS.contains(preset);
        if (!audioOnly && !PikoProfiles.VIDEO_PRESETS.contains(preset)) {
            throw new IllegalArgumentException("Bilinmeyen preset: " + presetName);
        }

        Path input = requirePath(inputPath, "Giris dosyasi");
        if (!Files.isRegularFile(input)) {
            throw new IllegalArgumentException("Giris dosyasi bulunamadi: " + inputPath);
        }
        Path output = requirePath(outputDirectory, "Cikti klasoru");

        // Dosya paylasimli diskte zaten duruyor; kopyalamadan kaydediyoruz
        Video video = videoService.registerExisting(input);
        UUID batchId = UUID.randomUUID();

        List<EncodingJob> jobs = new ArrayList<>();
        if (audioOnly) {
            EncodingPreset audioPreset = presetRepository.findByName(PikoProfiles.AUDIO_PRESET_NAME)
                    .orElseThrow(() -> new IllegalStateException(
                            "Ses sablonu bulunamadi: " + PikoProfiles.AUDIO_PRESET_NAME));
            jobs.add(buildJob(video, audioPreset, PikoProfiles.AUDIO_FILE_NAME, output, batchId, null, true));
        } else {
            EncodeSet set = encodeSetRepository.findByNameWithPresets(PikoProfiles.VIDEO_SET_NAME)
                    .orElseThrow(() -> new IllegalStateException(
                            "Paket bulunamadi: " + PikoProfiles.VIDEO_SET_NAME));
            // Paketteki sira profil numarasini belirliyor: 1. sablon video_profile1.mp4
            List<EncodingPreset> presets = set.getPresets();
            for (int i = 0; i < presets.size(); i++) {
                jobs.add(buildJob(video, presets.get(i), PikoProfiles.videoFileName(i + 1),
                        output, batchId, set.getId(), false));
            }
        }
        jobRepository.saveAll(jobs);

        CmsJob cmsJob = new CmsJob();
        cmsJob.setPresetName(preset);
        cmsJob.setInputPath(input.toString());
        cmsJob.setOutputDirectory(output.toString());
        cmsJob.setBatchId(batchId);
        cmsJob.setAudioOnly(audioOnly);
        return cmsJobRepository.save(cmsJob);
    }

    /**
     * Isin ozet durumu. Biri bile hata verdiyse is hatali sayiliyor: CMS eksik
     * profille SMIL'i dogrulayamaz. Ilerleme profillerin ortalamasi.
     */
    @Transactional(readOnly = true)
    public CmsJobStatus status(Integer id) {
        List<EncodingJob> jobs = jobRepository.findByBatchIdOrderByCreatedAtAsc(find(id).getBatchId());
        if (jobs.isEmpty()) {
            return new CmsJobStatus(JobStatus.FAILED, 0, "Ise ait encode kaydi yok", null);
        }

        int progress = (int) Math.round(jobs.stream()
                .mapToInt(job -> job.getProgress() == null ? 0 : job.getProgress())
                .average()
                .orElse(0));

        EncodingJob failed = jobs.stream()
                .filter(job -> job.getStatus() == JobStatus.FAILED)
                .findFirst()
                .orElse(null);
        if (failed != null) {
            return new CmsJobStatus(JobStatus.FAILED, progress, failed.getErrorMessage(), null);
        }
        if (jobs.stream().anyMatch(job -> job.getStatus() == JobStatus.CANCELLED)) {
            return new CmsJobStatus(JobStatus.CANCELLED, progress, null, null);
        }
        if (jobs.stream().allMatch(job -> job.getStatus() == JobStatus.COMPLETED)) {
            Instant completedAt = jobs.stream()
                    .map(EncodingJob::getCompletedAt)
                    .filter(Objects::nonNull)
                    .max(Instant::compareTo)
                    .orElse(Instant.now());
            return new CmsJobStatus(JobStatus.COMPLETED, 100, null, completedAt);
        }
        boolean started = jobs.stream().anyMatch(job -> job.getStatus() == JobStatus.PROCESSING
                || job.getStatus() == JobStatus.COMPLETED);
        return new CmsJobStatus(started ? JobStatus.PROCESSING : JobStatus.PENDING, progress, null, null);
    }

    /**
     * Henuz baslamamis profilleri iptal eder. Calisan ffmpeg sureci durdurulmuyor:
     * worker'in calisan isi kesme yolu yok, o profil bitince kendiliginden durur.
     * CMS bugun VODJobStop/VODJobDelete cagirmiyor; uclar Piko'da var diye karsilaniyor.
     *
     * @return iptal edilen profil sayisi
     */
    @Transactional
    public int cancel(Integer id) {
        List<EncodingJob> pending = jobRepository.findByBatchIdOrderByCreatedAtAsc(find(id).getBatchId())
                .stream()
                .filter(job -> job.getStatus() == JobStatus.PENDING)
                .toList();
        pending.forEach(job -> job.setStatus(JobStatus.CANCELLED));
        jobRepository.saveAll(pending);
        return pending.size();
    }

    private CmsJob find(Integer id) {
        return cmsJobRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Is bulunamadi: " + id));
    }

    private EncodingJob buildJob(Video video, EncodingPreset preset, String outputFileName,
                                 Path outputDirectory, UUID batchId, UUID encodeSetId, boolean audioOnly) {
        EncodingJob job = new EncodingJob();
        job.setVideo(video);
        job.setPreset(preset);
        job.setStatus(JobStatus.PENDING);
        job.setInputFileName(video.getOriginalFileName());
        job.setInputPath(video.getPath());
        job.setOutputFileName(outputFileName);
        job.setOutputDirectory(outputDirectory.toString());
        job.setBatchId(batchId);
        job.setEncodeSetId(encodeSetId);
        job.setAudioOnly(audioOnly);
        // Altyaziyi CMS kendisi webvtt'ye cevirip klasore koyuyor
        job.setSubtitleMode(SubtitleMode.NONE);
        return job;
    }

    private static Path requirePath(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " bos");
        }
        return Paths.get(value.trim());
    }
}
