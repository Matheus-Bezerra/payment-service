package dev.matheus.payment.adapter.out.persistence.repository;

import dev.matheus.payment.adapter.out.persistence.entity.WalletJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface WalletJpaRepository extends JpaRepository<WalletJpaEntity, UUID> {

    Optional<WalletJpaEntity> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<WalletJpaEntity> findByUserIdInOrderByUserIdAsc(Collection<UUID> userIds);
}
