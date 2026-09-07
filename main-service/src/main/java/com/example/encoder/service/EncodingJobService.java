package com.example.encoder.service;

import com.example.encoder.dto.request.CreateEncodingJobRequest;
import com.example.encoder.entity.EncodingJob;

import java.util.List;
import java.util.UUID;

public interface EncodingJobService {

    /**
     * presetId geldiyse tek iş, encodeSetId geldiyse setteki her preset için bir iş açar.
     * Her iki durumda da liste döner; tekli modda tek elemanlıdır.
     */
    List<EncodingJob> createJobs(CreateEncodingJobRequest request);

    EncodingJob getJob(UUID jobId);
    List<EncodingJob> getAllJobs();
    List<EncodingJob> getJobsByBatch(UUID batchId);
    void deleteJob(UUID id);
}
