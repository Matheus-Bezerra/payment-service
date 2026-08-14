package dev.matheus.payment.adapter.in.web.mapper;

import dev.matheus.payment.adapter.in.web.dto.request.TransferRequest;
import dev.matheus.payment.adapter.in.web.dto.response.TransferResponse;
import dev.matheus.payment.application.command.TransferCommand;
import dev.matheus.payment.domain.model.IdempotencyKey;
import dev.matheus.payment.domain.model.Money;
import dev.matheus.payment.domain.model.Transaction;
import dev.matheus.payment.domain.model.UserId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransferWebMapper {

    default TransferCommand toCommand(TransferRequest request, String idempotencyKey) {
        return new TransferCommand(
                UserId.of(request.payer()),
                UserId.of(request.payee()),
                Money.ofTransfer(request.value()),
                IdempotencyKey.of(idempotencyKey)
        );
    }

    @Mapping(target = "id", expression = "java(transaction.id().value())")
    @Mapping(target = "value", expression = "java(transaction.amount().amount())")
    @Mapping(target = "payer", expression = "java(transaction.payerId().value())")
    @Mapping(target = "payee", expression = "java(transaction.payeeId().value())")
    @Mapping(target = "status", expression = "java(transaction.status().name())")
    @Mapping(target = "createdAt", expression = "java(transaction.createdAt())")
    @Mapping(target = "completedAt", expression = "java(transaction.completedAt())")
    TransferResponse toResponse(Transaction transaction);
}
