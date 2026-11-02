package com.example.encoder.exception;

/**
 * Istek gecerli ama mevcut durumla catisiyor: ayni isimde kayit zaten var ya da
 * paket henuz islem icin hazir degil. HTTP 409 doner.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
