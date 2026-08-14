package dev.matheus.payment.adapter.out.persistence.repository;

import dev.matheus.payment.adapter.out.persistence.entity.UserJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {
}
