package dev.matheus.payment.adapter.in.web.controller;

import dev.matheus.payment.adapter.in.web.api.TransferApi;
import dev.matheus.payment.adapter.in.web.dto.request.TransferRequest;
import dev.matheus.payment.adapter.in.web.dto.response.TransferResponse;
import dev.matheus.payment.adapter.in.web.exception.MissingIdempotencyKeyException;
import dev.matheus.payment.adapter.in.web.mapper.TransferWebMapper;
import dev.matheus.payment.application.result.TransferResult;
import dev.matheus.payment.application.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TransferController implements TransferApi {

    private final TransferService transferService;
    private final TransferWebMapper mapper;

    @Override
    public ResponseEntity<TransferResponse> transfer(String idempotencyKey, TransferRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new MissingIdempotencyKeyException("The Idempotency-Key header is required");
        }

        TransferResult result = transferService.transfer(mapper.toCommand(request, idempotencyKey));
        HttpStatus status = result.replay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(mapper.toResponse(result.transaction()));
    }
}
