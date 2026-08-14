package dev.matheus.payment.adapter.out.persistence.adapter;

import dev.matheus.payment.adapter.out.persistence.entity.WalletJpaEntity;
import dev.matheus.payment.adapter.out.persistence.mapper.WalletPersistenceMapper;
import dev.matheus.payment.adapter.out.persistence.repository.WalletJpaRepository;
import dev.matheus.payment.application.port.out.WalletRepository;
import dev.matheus.payment.domain.exception.WalletNotFoundException;
import dev.matheus.payment.domain.model.UserId;
import dev.matheus.payment.domain.model.Wallet;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WalletPersistenceAdapter implements WalletRepository {

    private final WalletJpaRepository jpaRepository;
    private final WalletPersistenceMapper mapper;

    @Override
    public Optional<Wallet> findByOwnerId(UserId ownerId) {
        return jpaRepository.findByUserId(ownerId.value()).map(mapper::toDomain);
    }

    @Override
    public List<Wallet> lockByOwnerIds(UserId firstOwnerId, UserId secondOwnerId) {
        return jpaRepository.findByUserIdInOrderByUserIdAsc(
                        Set.of(firstOwnerId.value(), secondOwnerId.value())
                )
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public void update(Wallet wallet) {
        WalletJpaEntity entity = jpaRepository.findById(wallet.id().value())
                .orElseThrow(() -> new WalletNotFoundException(
                        "wallet not found: " + wallet.id()
                ));
        entity.setBalance(wallet.balance().amount());
        entity.setUpdatedAt(Instant.now());
    }
}
