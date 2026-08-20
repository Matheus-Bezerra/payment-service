package dev.matheus.payment.adapter.in.web.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.matheus.payment.adapter.in.web.controller.TransferController;
import dev.matheus.payment.adapter.in.web.exception.GlobalExceptionHandler;
import dev.matheus.payment.adapter.in.web.mapper.TransferWebMapperImpl;
import dev.matheus.payment.application.result.TransferResult;
import dev.matheus.payment.application.service.TransferService;
import dev.matheus.payment.domain.enums.TransactionStatus;
import dev.matheus.payment.domain.model.IdempotencyKey;
import dev.matheus.payment.domain.model.Money;
import dev.matheus.payment.domain.model.Transaction;
import dev.matheus.payment.domain.model.TransactionId;
import dev.matheus.payment.domain.model.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(controllers = TransferController.class)
@Import({GlobalExceptionHandler.class, TransferWebMapperImpl.class, CorrelationIdFilter.class})
class CorrelationIdFilterTest {

    private static final String PAYER = "0190a1b2-c3d4-7000-8000-000000000004";
    private static final String PAYEE = "0190a1b2-c3d4-7000-8000-000000000015";
    private static final String TRANSACTION_ID = "0190a1b2-c3d4-7000-8000-000000000042";
    private static final String IDEMPOTENCY_KEY = "11111111-1111-1111-1111-111111111111";
    private static final String UUID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransferService transferService;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesCorrelationIdWhenHeaderMissing() throws Exception {
        when(transferService.transfer(any())).thenReturn(new TransferResult(completedTransaction(), false));

        MvcResult result = mockMvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .content(body()))
                .andExpect(status().isCreated())
                .andReturn();

        String correlationId = result.getResponse().getHeader(CorrelationIdFilter.HEADER);
        assertNotNull(correlationId);
        assertTrue(correlationId.matches(UUID_PATTERN));
    }

    @Test
    void echoesIncomingCorrelationId() throws Exception {
        when(transferService.transfer(any())).thenReturn(new TransferResult(completedTransaction(), false));
        String incoming = "client-correlation-42";

        mockMvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .header(CorrelationIdFilter.HEADER, incoming)
                        .content(body()))
                .andExpect(status().isCreated())
                .andExpect(header().string(CorrelationIdFilter.HEADER, incoming));
    }

    @Test
    void errorResponseStillIncludesCorrelationId() throws Exception {
        String incoming = "error-correlation-9";

        mockMvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(CorrelationIdFilter.HEADER, incoming)
                        .content(body()))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(CorrelationIdFilter.HEADER, incoming));
    }

    @Test
    void putsCorrelationIdInMdcAndClearsAfterRequest() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "mdc-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertEquals("mdc-id", MDC.get(CorrelationIdFilter.MDC_KEY));
            assertEquals("mdc-id", ((MockHttpServletResponse) res).getHeader(CorrelationIdFilter.HEADER));
        });

        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    private static String body() {
        return """
                {
                  "value": 100.00,
                  "payer": "%s",
                  "payee": "%s"
                }
                """.formatted(PAYER, PAYEE);
    }

    private static Transaction completedTransaction() {
        Instant now = Instant.parse("2026-08-12T02:15:30.123Z");
        return Transaction.reconstitute(
                TransactionId.of(UUID.fromString(TRANSACTION_ID)),
                IdempotencyKey.of(IDEMPOTENCY_KEY),
                UserId.of(UUID.fromString(PAYER)),
                UserId.of(UUID.fromString(PAYEE)),
                Money.ofTransfer(new BigDecimal("100.00")),
                TransactionStatus.COMPLETED,
                null,
                now,
                now
        );
    }
}
