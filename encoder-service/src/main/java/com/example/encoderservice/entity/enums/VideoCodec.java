package com.example.encoderservice.entity.enums;

/**
 * main-service'teki ayni isimli enum ile birebir ayni olmali. Sabitler
 * veritabaninda metin olarak (EnumType.STRING) tutuluyor; burada eksik bir
 * sabit varsa isci o satiri okurken patlar. AV1 bir sure boyle eksikti.
 */
public enum VideoCodec {
    H264,
    H265,
    AV1
}
