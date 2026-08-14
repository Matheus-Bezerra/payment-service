package dev.matheus.payment.adapter.out.persistence.mapper;

import dev.matheus.payment.adapter.out.persistence.entity.TransactionJpaEntity;
import dev.matheus.payment.domain.model.IdempotencyKey;
import dev.matheus.payment.domain.model.Money;
import dev.matheus.payment.domain.model.Transaction;
import dev.matheus.payment.domain.model.TransactionId;
import dev.matheus.payment.domain.model.UserId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransactionPersistenceMapper {

    default Transaction toDomain(TransactionJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Transaction.reconstitute(
                TransactionId.of(entity.getId()),
                IdempotencyKey.of(entity.getIdempotencyKey()),
                UserId.of(entity.getPayerId()),
                UserId.of(entity.getPayeeId()),
                Money.ofTransfer(entity.getAmount()),
                entity.getStatus(),
                entity.getFailureReason(),
                entity.getCreatedAt(),
                entity.getCompletedAt()
        );
    }

    @Mapping(target = "id", expression = "java(transaction.id().value())")
    @Mapping(target = "idempotencyKey", expression = "java(transaction.idempotencyKey().value())")
    @Mapping(target = "payerId", expression = "java(transaction.payerId().value())")
    @Mapping(target = "payeeId", expression = "java(transaction.payeeId().value())")
    @Mapping(target = "amount", expression = "java(transaction.amount().amount())")
    @Mapping(target = "status", expression = "java(transaction.status())")
    @Mapping(target = "failureReason", expression = "java(transaction.failureReason())")
    @Mapping(target = "createdAt", expression = "java(transaction.createdAt())")
    @Mapping(target = "completedAt", expression = "java(transaction.completedAt())")
    TransactionJpaEntity toEntity(Transaction transaction);
}
