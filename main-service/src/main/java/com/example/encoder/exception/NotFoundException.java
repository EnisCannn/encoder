package com.example.encoder.exception;

/** Istenen kayit (video, sablon, paket, is, yayin) yok. HTTP 404 doner. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
