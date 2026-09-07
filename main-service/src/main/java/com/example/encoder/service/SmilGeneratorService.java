package com.example.encoder.service;

import java.nio.file.Path;
import java.util.UUID;

public interface SmilGeneratorService {

    /**
     * Bir paketin (batch) tamamlanmış işlerinden Wowza uyumlu SMIL manifestini üretir
     * ve <outputFolder>/<batchId>/playlist.smil olarak yazar.
     *
     * @return yazılan dosyanın yolu
     */
    Path generate(UUID batchId);

    /** Manifest daha önce üretildiyse onu döner, üretilmediyse üretir. */
    String readOrGenerate(UUID batchId);
}
