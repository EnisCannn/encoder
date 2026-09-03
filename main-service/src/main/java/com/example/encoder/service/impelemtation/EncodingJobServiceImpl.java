package com.example.encoder.service.impelemtation; // Kendi paket adınla aynı kalsın

import com.example.encoder.dto.request.CreateEncodingJobRequest;
import com.example.encoder.entity.EncodingJob;
import com.example.encoder.entity.EncodingPreset;
import com.example.encoder.entity.JobStatus;
import com.example.encoder.entity.Video;
import com.example.encoder.repository.EncodingJobRepository;
import com.example.encoder.repository.EncodingPresetRepository;
import com.example.encoder.repository.VideoRepository;
import com.example.encoder.service.EncodingJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EncodingJobServiceImpl implements EncodingJobService {

    private final EncodingJobRepository jobRepository;
    private final VideoRepository videoRepository;
    private final EncodingPresetRepository presetRepository;

    @Override
    public EncodingJob createJob(CreateEncodingJobRequest request) {
        Video video = videoRepository.findById(request.getVideoId())
                .orElseThrow(() -> new RuntimeException("Video bulunamadı!"));
        EncodingPreset preset = presetRepository.findById(request.getPresetId())
                .orElseThrow(() -> new RuntimeException("Preset bulunamadı!"));

        EncodingJob job = new EncodingJob();
        job.setVideo(video);
        job.setPreset(preset);
        job.setStatus(JobStatus.PENDING); // İşi PENDING olarak kuyruğa bırakıyoruz
        job.setInputFileName(video.getOriginalFileName());
        job.setInputPath(video.getPath());

        // Yeni eklenen altyazı ve dublaj yolları
        job.setSubtitlePath(request.getSubtitlePath());
        job.setDubbingPath(request.getDubbingPath());

        String outName = request.getOutputFileName() != null ? request.getOutputFileName() : "encoded_" + video.getOriginalFileName();
        job.setOutputFileName(outName);

        // Veritabanına kaydeder kaydetmez kullanıcıya anında cevap dönüyor!
        return jobRepository.save(job);
    }

    @Override
    public EncodingJob getJob(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("İş bulunamadı!"));
    }

    // --- YENİ EKLENEN METODLAR ---

    @Override
    public List<EncodingJob> getAllJobs() {
        // Tabloyu doldurmak için tüm kayıtları getirir
        return jobRepository.findAll();
    }

    @Override
    public void deleteJob(UUID id) {
        // ID'sine göre işi siler
        jobRepository.deleteById(id);
    }
}