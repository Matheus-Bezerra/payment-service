package dev.matheus.payment.adapter.out.persistence.adapter;

import dev.matheus.payment.adapter.out.persistence.mapper.UserPersistenceMapper;
import dev.matheus.payment.adapter.out.persistence.repository.UserJpaRepository;
import dev.matheus.payment.application.port.out.UserRepository;
import dev.matheus.payment.domain.model.User;
import dev.matheus.payment.domain.model.UserId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;
    private final UserPersistenceMapper mapper;

    @Override
    public Optional<User> findById(UserId id) {
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }
}
