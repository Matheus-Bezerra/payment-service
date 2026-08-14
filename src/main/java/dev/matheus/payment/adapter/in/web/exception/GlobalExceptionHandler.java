package dev.matheus.payment.adapter.in.web.exception;

import dev.matheus.payment.application.exception.AuthorizationUnavailableException;
import dev.matheus.payment.application.exception.TransferAlreadyFailedException;
import dev.matheus.payment.domain.exception.DailyTransferLimitExceededException;
import dev.matheus.payment.domain.exception.IdempotencyKeyConflictException;
import dev.matheus.payment.domain.exception.InsufficientBalanceException;
import dev.matheus.payment.domain.exception.InvalidMoneyException;
import dev.matheus.payment.domain.exception.InvalidTransferAmountException;
import dev.matheus.payment.domain.exception.MerchantCannotSendMoneyException;
import dev.matheus.payment.domain.exception.SameAccountTransferException;
import dev.matheus.payment.domain.exception.TransferAmountLimitExceededException;
import dev.matheus.payment.domain.exception.TransferNotAuthorizedException;
import dev.matheus.payment.domain.exception.TransferRateLimitExceededException;
import dev.matheus.payment.domain.exception.UserNotFoundException;
import dev.matheus.payment.domain.exception.WalletNotFoundException;
import java.math.BigDecimal;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String TYPE_PREFIX = "https://picpay-simplificado.local/problems/";
    private static final URI TRANSFER_INSTANCE = URI.create("/transfer");

    @ExceptionHandler(MissingIdempotencyKeyException.class)
    ResponseEntity<ProblemDetail> handleMissingIdempotencyKey(MissingIdempotencyKeyException ex) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "missing-idempotency-key",
                "Missing Idempotency-Key",
                messageOrDefault(ex, "The Idempotency-Key header is required")
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        boolean invalidAmount = ex.getBindingResult().getFieldErrors().stream()
                .anyMatch(error -> "value".equals(error.getField())
                        && error.getRejectedValue() instanceof Number number
                        && new BigDecimal(number.toString()).signum() <= 0);
        if (invalidAmount) {
            return problem(
                    HttpStatus.BAD_REQUEST,
                    "invalid-transfer-amount",
                    "Invalid transfer amount",
                    "Transfer amount must be greater than zero"
            );
        }
        return problem(
                HttpStatus.BAD_REQUEST,
                "invalid-request",
                "Invalid request",
                "Request body is invalid"
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "invalid-request",
                "Invalid request",
                "Malformed JSON or invalid field types"
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "invalid-request",
                "Invalid request",
                messageOrDefault(ex, "Request is invalid")
        );
    }

    @ExceptionHandler({InvalidMoneyException.class, InvalidTransferAmountException.class})
    ResponseEntity<ProblemDetail> handleInvalidAmount(RuntimeException ex) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "invalid-transfer-amount",
                "Invalid transfer amount",
                messageOrDefault(ex, "Transfer amount must be greater than zero")
        );
    }

    @ExceptionHandler(SameAccountTransferException.class)
    ResponseEntity<ProblemDetail> handleSameAccount(SameAccountTransferException ex) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "invalid-request",
                "Invalid request",
                messageOrDefault(ex, "Payer and payee must be different")
        );
    }

    @ExceptionHandler(MerchantCannotSendMoneyException.class)
    ResponseEntity<ProblemDetail> handleMerchant(MerchantCannotSendMoneyException ex) {
        return problem(
                HttpStatus.FORBIDDEN,
                "merchant-cannot-send-money",
                "Merchant cannot send money",
                messageOrDefault(ex, "Merchants cannot send money")
        );
    }

    @ExceptionHandler(TransferNotAuthorizedException.class)
    ResponseEntity<ProblemDetail> handleNotAuthorized(TransferNotAuthorizedException ex) {
        return problem(
                HttpStatus.FORBIDDEN,
                "transfer-not-authorized",
                "Transfer not authorized",
                messageOrDefault(ex, "The authorization service refused the transfer")
        );
    }

    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<ProblemDetail> handleUserNotFound(UserNotFoundException ex) {
        return problem(
                HttpStatus.NOT_FOUND,
                "user-not-found",
                "User not found",
                messageOrDefault(ex, "User not found")
        );
    }

    @ExceptionHandler(WalletNotFoundException.class)
    ResponseEntity<ProblemDetail> handleWalletNotFound(WalletNotFoundException ex) {
        return problem(
                HttpStatus.NOT_FOUND,
                "wallet-not-found",
                "Wallet not found",
                messageOrDefault(ex, "Wallet not found")
        );
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    ResponseEntity<ProblemDetail> handleIdempotencyConflict(IdempotencyKeyConflictException ex) {
        return problem(
                HttpStatus.CONFLICT,
                "idempotency-key-conflict",
                "Idempotency key conflict",
                messageOrDefault(ex, "Idempotency key was reused with a different payload")
        );
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    ResponseEntity<ProblemDetail> handleInsufficientBalance(InsufficientBalanceException ex) {
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "insufficient-balance",
                "Insufficient balance",
                messageOrDefault(ex, "Insufficient balance to complete the transfer")
        );
    }

    @ExceptionHandler(TransferAmountLimitExceededException.class)
    ResponseEntity<ProblemDetail> handleAmountLimit(TransferAmountLimitExceededException ex) {
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "transfer-amount-limit-exceeded",
                "Transfer amount limit exceeded",
                messageOrDefault(ex, "Transfer amount exceeds the allowed limit")
        );
    }

    @ExceptionHandler(DailyTransferLimitExceededException.class)
    ResponseEntity<ProblemDetail> handleDailyLimit(DailyTransferLimitExceededException ex) {
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "daily-transfer-limit-exceeded",
                "Daily transfer limit exceeded",
                messageOrDefault(ex, "Daily transfer limit would be exceeded")
        );
    }

    @ExceptionHandler(TransferRateLimitExceededException.class)
    ResponseEntity<ProblemDetail> handleRateLimit(TransferRateLimitExceededException ex) {
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "transfer-rate-limit-exceeded",
                "Transfer rate limit exceeded",
                messageOrDefault(ex, "Transfer rate limit exceeded")
        );
    }

    @ExceptionHandler(AuthorizationUnavailableException.class)
    ResponseEntity<ProblemDetail> handleAuthorizerUnavailable(AuthorizationUnavailableException ex) {
        return problem(
                HttpStatus.BAD_GATEWAY,
                "authorization-service-unavailable",
                "Authorization service unavailable",
                messageOrDefault(ex, "authorization service unavailable")
        );
    }

    @ExceptionHandler(TransferAlreadyFailedException.class)
    ResponseEntity<ProblemDetail> handleAlreadyFailed(TransferAlreadyFailedException ex) {
        String detail = messageOrDefault(ex, "Transfer already failed");
        String lower = detail.toLowerCase();
        if (lower.contains("unavailable") || lower.contains("timeout")) {
            return handleAuthorizerUnavailable(new AuthorizationUnavailableException(detail));
        }
        if (lower.contains("merchant")) {
            return handleMerchant(new MerchantCannotSendMoneyException(detail));
        }
        if (lower.contains("not authorized")) {
            return handleNotAuthorized(new TransferNotAuthorizedException(detail));
        }
        if (lower.contains("wallet not found")) {
            return handleWalletNotFound(new WalletNotFoundException(detail));
        }
        if (lower.contains("not found")) {
            return handleUserNotFound(new UserNotFoundException(detail));
        }
        if (lower.contains("insufficient")) {
            return handleInsufficientBalance(new InsufficientBalanceException(detail));
        }
        if (lower.contains("daily")) {
            return handleDailyLimit(new DailyTransferLimitExceededException(detail));
        }
        if (lower.contains("per minute")) {
            return handleRateLimit(new TransferRateLimitExceededException(detail));
        }
        if (lower.contains("exceeds") || lower.contains("maximum")) {
            return handleAmountLimit(new TransferAmountLimitExceededException(detail));
        }
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "invalid-request", "Invalid request", detail);
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String slug,
            String title,
            String detail
    ) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setType(URI.create(TYPE_PREFIX + slug));
        body.setTitle(title);
        body.setInstance(TRANSFER_INSTANCE);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    private static String messageOrDefault(Throwable ex, String fallback) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }
}
