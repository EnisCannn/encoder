package com.example.encoderservice.quality;

import com.example.encoderservice.entity.EncodingJob;

public interface VmafService {

    /**
     * İşin çıktısını kaynak videoyla karşılaştırıp VMAF skorunu hesaplar.
     * Ölçülemeyecek işlerde (elenen durumlar) hata fırlatmaz, SKIPPED döner.
     */
    VmafOutcome measure(EncodingJob job);
}
