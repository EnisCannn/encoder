package com.example.encoder.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Her hata turu dogru HTTP koduna ve arayuzun okudugu {"hata": "..."} govdesine
 * donuyor mu? Uygulamanin tamamini ayaga kaldirmadan, yalnizca handler ile.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new HataFirlatanController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void notFound404() throws Exception {
        mvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.hata").value("Video bulunamadı!"));
    }

    @Test
    void badRequest400() throws Exception {
        mvc.perform(get("/test/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.hata").value("Bitrate gecersiz"));
    }

    @Test
    void illegalArgument400() throws Exception {
        mvc.perform(get("/test/illegal-argument"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adrestekiBozukUuid400() throws Exception {
        mvc.perform(get("/test/uuid/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.hata").value("Gecersiz deger: id=abc"));
    }

    @Test
    void conflict409() throws Exception {
        mvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.hata").value("Bu isimde bir paket zaten var"));
    }

    @Test
    void responseStatusExceptionKendiKodunuKorur() throws Exception {
        mvc.perform(get("/test/response-status"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.hata").value("Yetki yok"));
    }

    @Test
    void processingException500() throws Exception {
        mvc.perform(get("/test/processing"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.hata").value("FFmpeg kesme işlemi başarısız oldu."));
    }

    @Test
    void beklenmedikHata500() throws Exception {
        // Onceden her RuntimeException 404 donuyordu
        mvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError());
    }

    @RestController
    static class HataFirlatanController {

        @GetMapping("/test/not-found")
        void notFound() {
            throw new NotFoundException("Video bulunamadı!");
        }

        @GetMapping("/test/bad-request")
        void badRequest() {
            throw new BadRequestException("Bitrate gecersiz");
        }

        @GetMapping("/test/illegal-argument")
        void illegalArgument() {
            UUID.fromString("uuid-degil");
        }

        @GetMapping("/test/uuid/{id}")
        void uuid(@PathVariable UUID id) {
        }

        @GetMapping("/test/conflict")
        void conflict() {
            throw new ConflictException("Bu isimde bir paket zaten var");
        }

        @GetMapping("/test/response-status")
        void responseStatus() {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Yetki yok");
        }

        @GetMapping("/test/processing")
        void processing() {
            throw new ProcessingException("FFmpeg kesme işlemi başarısız oldu.");
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("beklenmedik");
        }
    }
}
