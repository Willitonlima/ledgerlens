package com.ledgerlens.presentation;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converte erros em respostas JSON (RFC 9457 / ProblemDetail).
 * Os erros padrão do Spring MVC (parâmetro ausente, enum inválido etc.) já são tratados pela classe base.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /** Métodos ainda não implementados viram 501 com mensagem legível para o frontend. */
    @ExceptionHandler(UnsupportedOperationException.class)
    public ProblemDetail handleNotImplemented(UnsupportedOperationException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_IMPLEMENTED);
        problem.setTitle("Funcionalidade não implementada");
        problem.setDetail(ex.getMessage());
        return problem;
    }
}
