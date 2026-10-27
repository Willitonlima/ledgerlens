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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converte erros em respostas JSON (RFC 9457 / ProblemDetail).
 * Os erros padrão do Spring MVC (parâmetro ausente, enum inválido etc.) já são tratados pela classe base.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

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
        String detail = "A requisição contém dados inválidos.";
        if ("O parâmetro page deve ser maior ou igual a zero.".equals(ex.getMessage())
                || "O parâmetro size deve estar entre 1 e 100.".equals(ex.getMessage())) {
            detail = ex.getMessage();
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        LOGGER.error("Erro inesperado ao processar requisição da API.", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível processar a requisição.");
        problem.setTitle("Erro interno");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, safeDetail(status));
        problem.setTitle(status.is4xxClientError() ? "Requisição inválida" : "Erro interno");
        return ResponseEntity.status(status).headers(headers).body(problem);
    }

    private static String safeDetail(HttpStatus status) {
        if (status.is4xxClientError()) {
            return switch (status) {
                case NOT_FOUND -> "O recurso solicitado não foi encontrado.";
                case METHOD_NOT_ALLOWED -> "O método não é permitido para este recurso.";
                case UNSUPPORTED_MEDIA_TYPE -> "O tipo de conteúdo enviado não é compatível.";
                case TOO_MANY_REQUESTS -> "Limite de requisições excedido. Tente novamente em instantes.";
                default -> "A requisição contém dados inválidos.";
            };
        }
        return "Não foi possível processar a requisição.";
    }
}
