package com.loop.loop_backend.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 응답 쓰기 전에 클라이언트가 연결을 끊었을 때 나는 메시지 문구.
    // 별도 예외 타입이 아니라 평범한 IOException/SocketException의 메시지라 캐치올 안에서 내용으로 구분한다.
    // (IOException을 타입째로 잡으면 S3 업로드 실패 같은 진짜 장애성 IOException까지 조용해져서 위험함)
    private static final List<String> CLIENT_DISCONNECT_MESSAGE_HINTS =
            List.of("Broken pipe", "Connection reset by peer");

    // 예상 가능한 비즈니스/입력값 예외: 요청 컨텍스트만 WARN으로 남기고 스택트레이스는 안 찍는다.
    // (대부분 정상적인 유저 플로우라 매번 풀스택을 찍으면 로그가 오히려 더 지저분해짐)
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<CommonResponse<Void>> handleBusinessException(BusinessException e, HttpServletRequest request) {
        logWarn(request, e.getErrorCode(), null);
        return respond(e.getErrorCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResponse<Void>> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        logWarn(request, ErrorCode.INVALID_INPUT, e.getMessage());
        return respond(ErrorCode.INVALID_INPUT);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CommonResponse<Void>> handleNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        logWarn(request, ErrorCode.INVALID_REQUEST_FORMAT, e.getMessage());
        return respond(ErrorCode.INVALID_REQUEST_FORMAT);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CommonResponse<Void>> handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        logWarn(request, ErrorCode.INVALID_INPUT, e.getMessage());
        return respond(ErrorCode.INVALID_INPUT);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<CommonResponse<Void>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e, HttpServletRequest request) {
        logWarn(request, ErrorCode.FILE_TOO_LARGE, e.getMessage());
        return respond(ErrorCode.FILE_TOO_LARGE);
    }

    // 클라이언트가 응답 받기 전에 연결을 끊어서(새로고침/이탈/앱 종료/AbortController) 나는 예외 — 서버 장애 아님.
    @ExceptionHandler({AsyncRequestNotUsableException.class, ClientAbortException.class})
    public ResponseEntity<CommonResponse<Void>> handleClientDisconnect(Exception e, HttpServletRequest request) {
        log.warn("client disconnected method={}, uri={}, type={}",
                request.getMethod(), request.getRequestURI(), e.getClass().getSimpleName());
        return respond(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    // 예상 못 한 예외: 원인 추적이 필요하니 풀 스택트레이스까지 ERROR로 남긴다.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<CommonResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException e, HttpServletRequest request) {
        log.error("request failed method={}, uri={}, errorCode={}",
                request.getMethod(), request.getRequestURI(), ErrorCode.DATA_INTEGRITY_VIOLATION, e);
        return respond(ErrorCode.DATA_INTEGRITY_VIOLATION);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponse<Void>> handleUnexpected(Exception e, HttpServletRequest request) {
        if (isClientDisconnect(e)) {
            log.warn("client disconnected method={}, uri={}, type={}",
                    request.getMethod(), request.getRequestURI(), e.getClass().getSimpleName());
            return respond(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        log.error("request failed method={}, uri={}, errorCode={}",
                request.getMethod(), request.getRequestURI(), ErrorCode.INTERNAL_SERVER_ERROR, e);
        return respond(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private boolean isClientDisconnect(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null && CLIENT_DISCONNECT_MESSAGE_HINTS.stream().anyMatch(message::contains)) {
                return true;
            }
        }
        return false;
    }

    private void logWarn(HttpServletRequest request, ErrorCode errorCode, String detail) {
        if (detail != null) {
            log.warn("request failed method={}, uri={}, errorCode={}, message={}",
                    request.getMethod(), request.getRequestURI(), errorCode, detail);
        } else {
            log.warn("request failed method={}, uri={}, errorCode={}",
                    request.getMethod(), request.getRequestURI(), errorCode);
        }
    }

    private ResponseEntity<CommonResponse<Void>> respond(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus()).body(CommonResponse.fail(errorCode));
    }
}