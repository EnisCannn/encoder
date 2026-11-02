package com.example.encoder.service.impelemtation;

import com.example.encoder.dto.CreatePresetRequest;
import com.example.encoder.dto.PresetResponse;
import com.example.encoder.entity.EncodingPreset;
import com.example.encoder.entity.enums.AudioCodec;
import com.example.encoder.entity.enums.Format;
import com.example.encoder.entity.enums.VideoCodec;
import com.example.encoder.exception.BadRequestException;
import com.example.encoder.exception.ConflictException;
import com.example.encoder.exception.NotFoundException;
import com.example.encoder.repository.EncodingPresetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncodingPresetServiceImplTest {

    @Mock private EncodingPresetRepository repository;

    @InjectMocks private EncodingPresetServiceImpl service;

    @Test
    void create_ayniIsimVarsaConflict() {
        when(repository.existsByName("720p")).thenReturn(true);

        assertThatThrownBy(() -> service.createPreset(request("720p", 128)))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {63, 321, 1000})
    void create_sesBitrateiAraliktaDegilseBadRequest(int audioBitrate) {
        assertThatThrownBy(() -> service.createPreset(request("yeni", audioBitrate)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("64 ile 320");
    }

    @ParameterizedTest
    @ValueSource(ints = {64, 128, 320})
    void create_sinirDegerleriKabulEdilir(int audioBitrate) {
        when(repository.save(any(EncodingPreset.class))).thenAnswer(inv -> inv.getArgument(0));

        PresetResponse response = service.createPreset(request("yeni", audioBitrate));

        assertThat(response.audioBitrate()).isEqualTo(audioBitrate);
    }

    @Test
    void create_tumAlanlariKopyalarVeAktifOlarakKaydeder() {
        when(repository.save(any(EncodingPreset.class))).thenAnswer(inv -> inv.getArgument(0));

        PresetResponse response = service.createPreset(new CreatePresetRequest("Web 720p", "aciklama",
                Format.MP4, VideoCodec.H265, AudioCodec.AAC, 1280, 720, 1500, 128, null));

        assertThat(response.name()).isEqualTo("Web 720p");
        assertThat(response.videoCodec()).isEqualTo(VideoCodec.H265);
        assertThat(response.width()).isEqualTo(1280);
        assertThat(response.height()).isEqualTo(720);
        assertThat(response.videoBitrate()).isEqualTo(1500);
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void update_kayitYoksaNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePreset(id, request("x", 128)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_isimDegismediyseKendiIsmiCakismaSayilmaz() {
        UUID id = UUID.randomUUID();
        EncodingPreset existing = new EncodingPreset();
        existing.setName("720p");
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        service.updatePreset(id, request("720p", 192));

        verify(repository, never()).existsByName(any());
        assertThat(existing.getAudioBitrate()).isEqualTo(192);
    }

    @Test
    void update_isimDegismedigindeDeBitrateKuraliGecerli() {
        UUID id = UUID.randomUUID();
        EncodingPreset existing = new EncodingPreset();
        existing.setName("720p");
        when(repository.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updatePreset(id, request("720p", 500)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void update_baskaBirSablonunIsmineGecilemez() {
        UUID id = UUID.randomUUID();
        EncodingPreset existing = new EncodingPreset();
        existing.setName("720p");
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.existsByName("1080p")).thenReturn(true);

        assertThatThrownBy(() -> service.updatePreset(id, request("1080p", 128)))
                .isInstanceOf(ConflictException.class);
    }

    private static CreatePresetRequest request(String name, Integer audioBitrate) {
        return new CreatePresetRequest(name, null, Format.MP4, VideoCodec.H264, AudioCodec.AAC,
                1280, 720, 1500, audioBitrate, null);
    }
}
