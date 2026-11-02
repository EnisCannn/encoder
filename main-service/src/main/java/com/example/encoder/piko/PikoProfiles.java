package com.example.encoder.piko;

import java.util.Set;

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

    /** CMS'in video isleri icin gonderdigi preset adlari (kaynak uzantisina gore seciyor). */
    public static final Set<String> VIDEO_PRESETS = Set.of("MP4Video", "TSVideo", "MOVVideo");

    /** CMS'in dublaj isleri icin gonderdigi preset adlari. */
    public static final Set<String> AUDIO_PRESETS = Set.of("WAVAudio", "MP4Audio", "TSAudio");

    /** Paketteki n. sablonun (1'den baslar) cikti adi: video_profile1.mp4, ... */
    public static String videoFileName(int index) {
        return "video_profile" + index + ".mp4";
    }
}
