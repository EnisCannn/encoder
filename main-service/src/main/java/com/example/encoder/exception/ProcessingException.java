package com.example.encoder.exception;

/**
 * Sunucu tarafinda bir islem basarisiz oldu (ffmpeg, dosya yazma/okuma, surec).
 * Kullanicinin hatasi degil; HTTP 500 doner ve tam hata loglanir.
 */
public class ProcessingException extends RuntimeException {

    public ProcessingException(String message) {
        super(message);
    }

    public ProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
