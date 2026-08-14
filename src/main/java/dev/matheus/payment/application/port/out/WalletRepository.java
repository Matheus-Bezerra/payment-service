package dev.matheus.payment.application.port.out;

import dev.matheus.payment.domain.model.UserId;
import dev.matheus.payment.domain.model.Wallet;
import java.util.List;
import java.util.Optional;

public interface WalletRepository {

    Optional<Wallet> findByOwnerId(UserId ownerId);

    /**
     * Locks both wallets in deterministic owner-id order ({@code FOR UPDATE}).
     */
    List<Wallet> lockByOwnerIds(UserId firstOwnerId, UserId secondOwnerId);

    void update(Wallet wallet);
}
