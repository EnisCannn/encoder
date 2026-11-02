package com.example.encoder.piko;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * CMS'in gordugu tek is. CMS bir VODGenerateJobNew istegi icin tek bir is
 * numarasi bekliyor; arkada bu istekten birden fazla EncodingJob dogabilir
 * (video icin 4 profil). Hepsi batchId ile bu kayda bagli.
 */
@Entity
@Table(name = "cms_jobs")
@Getter
@Setter
@NoArgsConstructor
public class CmsJob {

    /** CMS'e donen is numarasi. Piko tam sayi dondurdugu icin UUID degil. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** CMS'in gonderdigi Piko preset adi: MP4Video, WAVAudio, ... */
    private String presetName;

    private String inputPath;

    private String outputDirectory;

    /** Bu istekten dogan EncodingJob'lari baglayan kimlik. */
    private UUID batchId;

    private Boolean audioOnly = false;

    private Instant createdAt = Instant.now();
}
