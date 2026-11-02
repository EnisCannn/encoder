package com.example.encoder.exception;

/** Istek kurallara uymuyor (eksik/celisen alan, gecersiz deger). HTTP 400 doner. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
