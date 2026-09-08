package com.example.encoder.service;

import com.example.encoder.dto.CalibrationSweepRequest;
import com.example.encoder.entity.EncodingJob;

import java.util.List;

public interface CalibrationSweepService {

    /** Her bitrate için geçici bir şablon ve bir encode işi açar. */
    List<EncodingJob> startSweep(CalibrationSweepRequest request);
}
