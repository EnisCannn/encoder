package com.example.encoderservice.entity.enums;

/**
 * main-service'teki ayni isimli enum ile ayni sabitleri tasimali. Deger
 * veritabaninda metin olarak duruyor; burada eksik bir sabit varsa isci o
 * satiri okurken patlar. Cikti uzantisini main-service belirledigi icin
 * burada uzantiya gerek yok.
 */
public enum Format {
    MP4,
    MKV,
    HLS
}
