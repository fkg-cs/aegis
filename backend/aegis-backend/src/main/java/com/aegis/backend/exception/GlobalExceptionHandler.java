package com.aegis.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Gestisce gli errori di Sicurezza (quelli che lanciamo noi manualmente)
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<String> handleSecurityException(SecurityException ex) {
        // Restituisce 403 Forbidden con il testo esatto dell'errore ("VIOLAZIONE CLEARANCE...")
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
    }

    // Gestisce gli errori di Accesso negato di Spring Security (@PreAuthorize)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> handleAccessDeniedException(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("ACCESSO NEGATO: " + ex.getMessage());
    }

    // Gestisce il caso "Utente non trovato" o altri errori runtime
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntimeException(RuntimeException ex) {
        // Se è un nostro errore specifico, lo mostriamo. Altrimenti 500.
        if (ex.getMessage().contains("ERRORE:")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Errore interno del server.");
    }
}