package com.example.encoder.service;

import com.example.encoder.dto.request.CreateEncodingJobRequest;
import com.example.encoder.entity.EncodingJob;

import java.util.List;
import java.util.UUID;

public interface EncodingJobService {
    EncodingJob createJob(CreateEncodingJobRequest request);

    EncodingJob getJob(UUID jobId);

    List<EncodingJob> getAllJobs();

    void deleteJob(UUID id);
}