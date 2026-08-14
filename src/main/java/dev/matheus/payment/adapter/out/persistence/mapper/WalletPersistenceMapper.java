package dev.matheus.payment.adapter.out.persistence.mapper;

import dev.matheus.payment.adapter.out.persistence.entity.WalletJpaEntity;
import dev.matheus.payment.domain.model.Money;
import dev.matheus.payment.domain.model.UserId;
import dev.matheus.payment.domain.model.Wallet;
import dev.matheus.payment.domain.model.WalletId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WalletPersistenceMapper {

    default Wallet toDomain(WalletJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Wallet.create(
                WalletId.of(entity.getId()),
                UserId.of(entity.getUserId()),
                Money.ofBalance(entity.getBalance())
        );
    }

    @Mapping(target = "id", expression = "java(wallet.id().value())")
    @Mapping(target = "userId", expression = "java(wallet.ownerId().value())")
    @Mapping(target = "balance", expression = "java(wallet.balance().amount())")
    @Mapping(target = "updatedAt", expression = "java(java.time.Instant.now())")
    WalletJpaEntity toEntity(Wallet wallet);
}
