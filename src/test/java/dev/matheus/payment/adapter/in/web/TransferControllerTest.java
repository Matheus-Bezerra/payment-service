package dev.matheus.payment.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.matheus.payment.adapter.in.web.controller.TransferController;
import dev.matheus.payment.adapter.in.web.exception.GlobalExceptionHandler;
import dev.matheus.payment.adapter.in.web.mapper.TransferWebMapperImpl;
import dev.matheus.payment.application.result.TransferResult;
import dev.matheus.payment.application.service.TransferService;
import dev.matheus.payment.domain.enums.TransactionStatus;
import dev.matheus.payment.domain.exception.IdempotencyKeyConflictException;
import dev.matheus.payment.domain.exception.InsufficientBalanceException;
import dev.matheus.payment.domain.exception.MerchantCannotSendMoneyException;
import dev.matheus.payment.domain.exception.SameAccountTransferException;
import dev.matheus.payment.domain.exception.TransferAmountLimitExceededException;
import dev.matheus.payment.domain.exception.TransferNotAuthorizedException;
import dev.matheus.payment.domain.exception.UserNotFoundException;
import dev.matheus.payment.domain.model.IdempotencyKey;
import dev.matheus.payment.domain.model.Money;
import dev.matheus.payment.domain.model.Transaction;
import dev.matheus.payment.domain.model.TransactionId;
import dev.matheus.payment.domain.model.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(controllers = TransferController.class)
@Import({GlobalExceptionHandler.class, TransferWebMapperImpl.class})
class TransferControllerTest {

    private static final String PAYER = "0190a1b2-c3d4-7000-8000-000000000004";
    private static final String PAYEE = "0190a1b2-c3d4-7000-8000-000000000015";
    private static final String TRANSACTION_ID = "0190a1b2-c3d4-7000-8000-000000000042";
    private static final String IDEMPOTENCY_KEY = "11111111-1111-1111-1111-111111111111";
    private static final String UUID_V4 = "550e8400-e29b-41d4-a716-446655440000";
    private static final Instant CREATED_AT = Instant.parse("2026-08-12T02:15:30.123Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-08-12T02:15:30.890Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransferService transferService;

    @Test
    void firstSuccessReturns201() throws Exception {
        when(transferService.transfer(any())).thenReturn(new TransferResult(completedTransaction(), false));

        postTransfer(body("100.00", PAYER, PAYEE), IDEMPOTENCY_KEY)
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(TRANSACTION_ID))
                .andExpect(jsonPath("$.value").value(100.00))
                .andExpect(jsonPath("$.payer").value(PAYER))
                .andExpect(jsonPath("$.payee").value(PAYEE))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.createdAt").value("2026-08-12T02:15:30.123Z"))
                .andExpect(jsonPath("$.completedAt").value("2026-08-12T02:15:30.890Z"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.document").doesNotExist())
                .andExpect(jsonPath("$.failureReason").doesNotExist())
                .andExpect(jsonPath("$.idempotencyKey").doesNotExist());
    }

    @Test
    void completedReplayReturns200() throws Exception {
        when(transferService.transfer(any())).thenReturn(new TransferResult(completedTransaction(), true));

        postTransfer(body("100.00", PAYER, PAYEE), IDEMPOTENCY_KEY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TRANSACTION_ID))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void missingIdempotencyKeyReturns400() throws Exception {
        mockMvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("100.00", PAYER, PAYEE)))
                .andExpect(problem("missing-idempotency-key", 400));
    }

    @Test
    void blankIdempotencyKeyReturns400() throws Exception {
        postTransfer(body("100.00", PAYER, PAYEE), "   ")
                .andExpect(problem("missing-idempotency-key", 400));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        postTransfer("{not-json", IDEMPOTENCY_KEY)
                .andExpect(problem("invalid-request", 400));
    }

    @Test
    void nonPositiveValueReturns400() throws Exception {
        postTransfer(body("0", PAYER, PAYEE), IDEMPOTENCY_KEY)
                .andExpect(problem("invalid-transfer-amount", 400));
    }

    @Test
    void samePayerAndPayeeReturns400() throws Exception {
        when(transferService.transfer(any()))
                .thenThrow(new SameAccountTransferException("payer and payee must be different"));

        postTransfer(body("100.00", PAYER, PAYER), IDEMPOTENCY_KEY)
                .andExpect(problem("invalid-request", 400));
    }

    @Test
    void nonV7UuidReturns400() throws Exception {
        postTransfer(body("100.00", UUID_V4, PAYEE), IDEMPOTENCY_KEY)
                .andExpect(problem("invalid-request", 400));
    }

    @Test
    void merchantPayerReturns403() throws Exception {
        when(transferService.transfer(any()))
                .thenThrow(new MerchantCannotSendMoneyException("merchants cannot send money"));

        postTransfer(body("100.00", PAYER, PAYEE), IDEMPOTENCY_KEY)
                .andExpect(problem("merchant-cannot-send-money", 403));
    }

    @Test
    void unauthorizedTransferReturns403() throws Exception {
        when(transferService.transfer(any()))
                .thenThrow(new TransferNotAuthorizedException("transfer was not authorized"));

        postTransfer(body("100.00", PAYER, PAYEE), IDEMPOTENCY_KEY)
                .andExpect(problem("transfer-not-authorized", 403));
    }

    @Test
    void unknownUserReturns404() throws Exception {
        when(transferService.transfer(any()))
                .thenThrow(new UserNotFoundException("payer not found: " + PAYER));

        postTransfer(body("100.00", PAYER, PAYEE), IDEMPOTENCY_KEY)
                .andExpect(problem("user-not-found", 404));
    }

    @Test
    void idempotencyKeyConflictReturns409() throws Exception {
        when(transferService.transfer(any()))
                .thenThrow(new IdempotencyKeyConflictException("idempotency key was reused with a different payload"));

        postTransfer(body("50.00", PAYER, PAYEE), IDEMPOTENCY_KEY)
                .andExpect(problem("idempotency-key-conflict", 409));
    }

    @Test
    void insufficientBalanceReturns422() throws Exception {
        when(transferService.transfer(any()))
                .thenThrow(new InsufficientBalanceException("insufficient balance"));

        postTransfer(body("100.00", PAYER, PAYEE), IDEMPOTENCY_KEY)
                .andExpect(problem("insufficient-balance", 422));
    }

    @Test
    void amountLimitExceededReturns422() throws Exception {
        when(transferService.transfer(any()))
                .thenThrow(new TransferAmountLimitExceededException("transfer amount exceeds maximum of 20000.00"));

        postTransfer(body("20000.01", PAYER, PAYEE), IDEMPOTENCY_KEY)
                .andExpect(problem("transfer-amount-limit-exceeded", 422));
    }

    private ResultActions postTransfer(String json, String idempotencyKey) throws Exception {
        return mockMvc.perform(post("/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey)
                .content(json));
    }

    private static org.springframework.test.web.servlet.ResultMatcher problem(String slug, int status) {
        return result -> {
            status().is(status).match(result);
            content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON).match(result);
            jsonPath("$.type").value("https://picpay-simplificado.local/problems/" + slug).match(result);
            jsonPath("$.status").value(status).match(result);
            jsonPath("$.instance").value("/transfer").match(result);
            jsonPath("$.title").exists().match(result);
            jsonPath("$.password").doesNotExist().match(result);
            jsonPath("$.document").doesNotExist().match(result);
        };
    }

    private static String body(String value, String payer, String payee) {
        return """
                {
                  "value": %s,
                  "payer": "%s",
                  "payee": "%s"
                }
                """.formatted(value, payer, payee);
    }

    private static Transaction completedTransaction() {
        return Transaction.reconstitute(
                TransactionId.of(UUID.fromString(TRANSACTION_ID)),
                IdempotencyKey.of(IDEMPOTENCY_KEY),
                UserId.of(UUID.fromString(PAYER)),
                UserId.of(UUID.fromString(PAYEE)),
                Money.ofTransfer(new BigDecimal("100.00")),
                TransactionStatus.COMPLETED,
                null,
                CREATED_AT,
                COMPLETED_AT
        );
    }
}
