package com.example.encoder.service.impelemtation;

import com.example.encoder.dto.CreateEncodeSetRequest;
import com.example.encoder.dto.EncodeSetResponse;
import com.example.encoder.dto.PresetResponse;
import com.example.encoder.entity.EncodeSet;
import com.example.encoder.entity.EncodingPreset;
import com.example.encoder.exception.BadRequestException;
import com.example.encoder.exception.ConflictException;
import com.example.encoder.exception.NotFoundException;
import com.example.encoder.repository.EncodeSetRepository;
import com.example.encoder.repository.EncodingPresetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncodeSetServiceImplTest {

    @Mock private EncodeSetRepository repository;
    @Mock private EncodingPresetRepository presetRepository;

    @InjectMocks private EncodeSetServiceImpl service;

    @Test
    void create_presetSirasiIstektekiSirayaGoreKorunur() {
        // SMIL ve HLS varyantlari bu sirayla yaziliyor
        EncodingPreset p1080 = preset("1080p");
        EncodingPreset p720 = preset("720p");
        EncodingPreset p540 = preset("540p");
        when(presetRepository.findById(p540.getId())).thenReturn(Optional.of(p540));
        when(presetRepository.findById(p1080.getId())).thenReturn(Optional.of(p1080));
        when(presetRepository.findById(p720.getId())).thenReturn(Optional.of(p720));
        when(repository.save(any(EncodeSet.class))).thenAnswer(inv -> inv.getArgument(0));

        EncodeSetResponse response = service.createSet(new CreateEncodeSetRequest("Web", null,
                List.of(p540.getId(), p1080.getId(), p720.getId())));

        assertThat(response.presets()).extracting(PresetResponse::name).containsExactly("540p", "1080p", "720p");
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void create_ayniIsimVarsaConflict() {
        when(repository.existsByName("Web")).thenReturn(true);

        assertThatThrownBy(() -> service.createSet(new CreateEncodeSetRequest("Web", null, List.of(UUID.randomUUID()))))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void create_olmayanPresetNotFound() {
        UUID missing = UUID.randomUUID();
        when(presetRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createSet(new CreateEncodeSetRequest("Web", null, List.of(missing))))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(missing.toString());
    }

    @Test
    void create_ayniPresetIkiKezEklenemez() {
        EncodingPreset p = preset("720p");
        when(presetRepository.findById(p.getId())).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.createSet(new CreateEncodeSetRequest("Web", null, List.of(p.getId(), p.getId()))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("iki kez");
    }

    @Test
    void update_kendiIsminiKorumakCakismaDegildir() {
        UUID id = UUID.randomUUID();
        EncodeSet existing = new EncodeSet();
        existing.setName("Web");
        existing.setPresets(new ArrayList<>());
        EncodingPreset p = preset("720p");
        when(repository.findByIdWithPresets(id)).thenReturn(Optional.of(existing));
        when(presetRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(repository.save(existing)).thenReturn(existing);

        EncodeSetResponse response = service.updateSet(id, new CreateEncodeSetRequest("Web", "yeni", List.of(p.getId())));

        verify(repository, never()).existsByName(any());
        assertThat(response.description()).isEqualTo("yeni");
        assertThat(existing.getUpdatedAt()).isNotNull();
    }

    @Test
    void update_baskaPaketinIsmineGecilemez() {
        UUID id = UUID.randomUUID();
        EncodeSet existing = new EncodeSet();
        existing.setName("Web");
        when(repository.findByIdWithPresets(id)).thenReturn(Optional.of(existing));
        when(repository.existsByName("Mobil")).thenReturn(true);

        assertThatThrownBy(() -> service.updateSet(id, new CreateEncodeSetRequest("Mobil", null, List.of(UUID.randomUUID()))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void getVeDelete_olmayanPaketNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdWithPresets(id)).thenReturn(Optional.empty());
        when(repository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.getSet(id)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.deleteSet(id)).isInstanceOf(NotFoundException.class);
        verify(repository, never()).deleteById(any());
    }

    @Test
    void delete_varOlanPaketiSiler() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(true);

        service.deleteSet(id);

        verify(repository).deleteById(id);
    }

    private static EncodingPreset preset(String name) {
        EncodingPreset preset = new EncodingPreset();
        preset.setId(UUID.randomUUID());
        preset.setName(name);
        return preset;
    }
}
