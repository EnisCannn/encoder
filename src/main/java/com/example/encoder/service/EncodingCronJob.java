package com.example.encoder.service;

import com.example.encoder.entity.EncodingJob;
import com.example.encoder.entity.JobStatus;
import com.example.encoder.repository.EncodingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EncodingCronJob {

    private final EncodingJobRepository jobRepository;
    private final EncodingJobService jobService;

    // Test için 10 saniyede bir çalışır (10.000 ms). 
    // İleride 10 dakika yapmak için 600000 yazabilirsin.
    @Scheduled(fixedDelay = 300000)
    public void checkAndProcessJobs() {

        // Kural 1: Devam eden işlem varsa başka başlatma
        if (jobRepository.existsByStatus(JobStatus.PROCESSING)) {
            System.out.println("Şu an çalışan bir encode işlemi var, yenisi başlatılmıyor...");
            return;
        }

        // Kural 2: Bekleyen (PENDING) iş var mı bak, varsa al ve otomatiğe bağla
        jobRepository.findFirstByStatusOrderByCreatedAtAsc(JobStatus.PENDING)
                .ifPresent(job -> {
                    System.out.println("Yeni bir PENDING iş bulundu, dönüşüm başlatılıyor! ID: " + job.getId());
                    // Servisteki mevcut dönüştürme metodumuzu çağırıyoruz
                    jobService.startJob(job.getId());
                });
    }
}