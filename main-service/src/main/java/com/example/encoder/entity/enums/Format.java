package com.example.encoder.entity.enums;

/**
 * Sablonun cikti bicimi. Uzantiyi bu belirliyor (bkz. EncodingJobServiceImpl).
 *
 * HLS burada MP4 gibi davranir: is once bir MP4 uretir, HLS paketlemesi
 * daha sonra HlsPackagerService tarafindan o dosyadan yapilir.
 */
public enum Format {
    MP4("mp4"),
    MKV("mkv"),
    HLS("mp4");

    private final String extension;

    Format(String extension) {
        this.extension = extension;
    }

    public String getExtension() {
        return extension;
    }
}
