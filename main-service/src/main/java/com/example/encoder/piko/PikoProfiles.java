package com.example.encoder.piko;

/**
 * CMS'in (turksat-tvhy) Piko encoder'dan bekledigi cikti adlari ve bizdeki
 * karsiliklari. Dosya adlari CMS'teki SmilFileGenerator'da sabit: SMIL'i CMS
 * kendisi yaziyor, burada degistirilirse SMIL ile dosyalar uyusmaz.
 */
public final class PikoProfiles {

    private PikoProfiles() {
    }

    /** Video isleri (MP4Video/TSVideo/MOVVideo) bu paketle encode edilir. */
    public static final String VIDEO_SET_NAME = "Piko_Uyumlu";

    /** Dublaj isleri (WAVAudio/MP4Audio/TSAudio) bu sablonla encode edilir. */
    public static final String AUDIO_PRESET_NAME = "Piko_Ses_AAC_128k";

    public static final String AUDIO_FILE_NAME = "audio_encoded_profile1.mp4";

    /** Paketteki n. sablonun (1'den baslar) cikti adi: video_profile1.mp4, ... */
    public static String videoFileName(int index) {
        return "video_profile" + index + ".mp4";
    }
}
