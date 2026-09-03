package com.example.encoder.service;

import com.example.encoder.dto.LiveClipRequest;
import com.example.encoder.dto.LiveStreamRequest;
import com.example.encoder.entity.LiveStream;

import java.util.List;
import java.util.UUID;

public interface LiveStreamService {
    List<LiveStream> getAllStreams();
    LiveStream startStream(LiveStreamRequest request);
    void stopStream(UUID streamId);
    String createLiveClip(LiveClipRequest request);
    void deleteStream(UUID streamId);
}
