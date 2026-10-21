package com.ledgerlens.presentation;

import com.ledgerlens.application.CsvValidationException;
import com.ledgerlens.application.InvalidUploadException;
import com.ledgerlens.application.ReconciliationPrerequisiteException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.context.request.WebRequest;

/**
 * Converte erros em respostas JSON (RFC 9457 / ProblemDetail).
 * Os erros padrão do Spring MVC (parâmetro ausente, enum inválido etc.) já são tratados pela classe base.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(CsvValidationException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCsv(CsvValidationException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setTitle("CSV inválido");
        problem.setDetail(ex.getMessage());
        problem.setProperty("errors", ex.getErrors());
        problem.setProperty("totalErrors", ex.getTotalErrors());
        return ResponseEntity.unprocessableEntity().body(problem);
    }

    @ExceptionHandler(InvalidUploadException.class)
    public ResponseEntity<ProblemDetail> handleInvalidUpload(InvalidUploadException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problem.setTitle(ex.getStatus() == HttpStatus.UNSUPPORTED_MEDIA_TYPE
                ? "Tipo de arquivo não suportado" : "Arquivo inválido");
        return ResponseEntity.status(ex.getStatus()).body(problem);
    }

    @ExceptionHandler(ReconciliationPrerequisiteException.class)
    public ResponseEntity<ProblemDetail> handleReconciliationPrerequisite(ReconciliationPrerequisiteException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conciliação indisponível");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleInvalidParameter(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Parâmetro inválido");
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "O arquivo excede o limite de 10 MB.");
        problem.setTitle("Arquivo muito grande");
        return ResponseEntity.badRequest().headers(headers).body(problem);
    }

    /** Métodos ainda não implementados viram 501 com mensagem legível para o frontend. */
    @ExceptionHandler(UnsupportedOperationException.class)
    public ProblemDetail handleNotImplemented(UnsupportedOperationException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_IMPLEMENTED);
        problem.setTitle("Funcionalidade não implementada");
        problem.setDetail(ex.getMessage());
        return problem;
    }
}
