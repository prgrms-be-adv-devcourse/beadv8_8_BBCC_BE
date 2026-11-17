package com.bbcc.kidly.member.web.error;

import com.bbcc.kidly.member.support.error.BusinessException;
import com.bbcc.kidly.member.support.error.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException e, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetails.of(e.getErrorCode(), e.getDetail(), request.getRequestURI());
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<ProblemDetail> handleConflict(DataAccessException e, HttpServletRequest request) {
        log.warn("DB 충돌: {}", request.getRequestURI(), e);
        ProblemDetail problem = ProblemDetails.of(CommonErrorCode.CONFLICT, request.getRequestURI());
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("처리하지 못한 예외: {}", request.getRequestURI(), e);
        ProblemDetail problem = ProblemDetails.of(CommonErrorCode.INTERNAL_ERROR, request.getRequestURI());
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));
        ProblemDetail problem = ProblemDetails.of(CommonErrorCode.INVALID_INPUT, detail, requestUri(request));
        return handleExceptionInternal(e, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
        HttpMessageNotReadableException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetails.of(CommonErrorCode.INVALID_INPUT, requestUri(request));
        return handleExceptionInternal(e, problem, headers, status, request);
    }

    // 부모가 만든 ProblemDetail은 type·instance가 비어 있을 수 있어 채운다
    @Override
    protected ResponseEntity<Object> createResponseEntity(
        Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            if (problem.getType() == null) {
                problem.setType(ProblemDetails.DEFAULT_TYPE);
            }
            if (problem.getInstance() == null) {
                problem.setInstance(URI.create(requestUri(request)));
            }
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private String requestUri(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }
}
