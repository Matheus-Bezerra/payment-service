package dev.matheus.payment.application.port.out;

import dev.matheus.payment.domain.model.User;
import dev.matheus.payment.domain.model.UserId;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(UserId id);
}
