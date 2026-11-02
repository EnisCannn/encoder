package com.example.encoder.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import java.util.HashMap;
import java.util.Map;

/**
 * Hatalari turune gore dogru HTTP koduna cevirir. Govde her zaman {"hata": "..."}:
 * arayuz mesaji bu alandan okuyor.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Validasyon kuralı ihlal edildiğinde bu metot otomatik devreye girer
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));
        return errors;
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(NotFoundException ex) {
        return body(ex.getMessage());
    }

    // UUID.fromString gibi cagrilar bozuk girdide IllegalArgumentException atiyor
    @ExceptionHandler({BadRequestException.class, IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(RuntimeException ex) {
        return body(ex.getMessage());
    }

    // Adresteki id UUID degilse (/api/jobs/abc gibi)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return body("Gecersiz deger: " + ex.getName() + "=" + ex.getValue());
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleConflict(ConflictException ex) {
        return body(ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.CONTENT_TOO_LARGE)
    public Map<String, String> handleTooLarge(MaxUploadSizeExceededException ex) {
        return body("Dosya izin verilen boyuttan buyuk.");
    }

    // Spring'in kendi durum kodu tasiyan hatalari (ResponseStatusException vb.) 500'e dusmesin
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<Map<String, String>> handleErrorResponse(ErrorResponseException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(body(ex.getBody().getDetail()));
    }

    // Geri kalan her sey sunucu hatasi: ProcessingException ve beklenmedik hatalar
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleServerError(RuntimeException ex) {
        log.error("Islenemeyen hata", ex);
        return body(ex.getMessage());
    }

    private static Map<String, String> body(String message) {
        Map<String, String> error = new HashMap<>();
        error.put("hata", message);
        return error;
    }
}
