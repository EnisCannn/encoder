package com.example.auth.service;

/** Kimligi dogrulanmis ama yetkisi olmayan istek; disariya 403 olarak cikar. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
