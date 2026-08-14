package dev.matheus.payment.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.matheus.payment.domain.enums.DocumentType;
import dev.matheus.payment.domain.enums.TransactionStatus;
import dev.matheus.payment.domain.enums.UserType;
import dev.matheus.payment.domain.event.TransferCompleted;
import dev.matheus.payment.domain.exception.InsufficientBalanceException;
import dev.matheus.payment.domain.exception.SameAccountTransferException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserWalletTransactionTest {

    @Test
    void onlyCommonUserCanSendMoney() {
        User common = user(UserType.COMMON);
        User merchant = user(UserType.MERCHANT);
        assertTrue(common.canSendMoney());
        assertFalse(merchant.canSendMoney());
    }

    @Test
    void walletDebitAndCredit() {
        Wallet wallet = Wallet.create(WalletId.generate(), UserId.generate(), Money.ofBalance(new BigDecimal("100.00")));
        wallet.debit(Money.ofTransfer(new BigDecimal("30.00")));
        wallet.credit(Money.ofTransfer(new BigDecimal("5.00")));
        assertEquals(new BigDecimal("75.00"), wallet.balance().amount());
    }

    @Test
    void walletDebitFailsWhenInsufficient() {
        Wallet wallet = Wallet.create(WalletId.generate(), UserId.generate(), Money.ofBalance(new BigDecimal("10.00")));
        assertThrows(
                InsufficientBalanceException.class,
                () -> wallet.debit(Money.ofTransfer(new BigDecimal("10.01")))
        );
    }

    @Test
    void transactionStartRejectsSameAccounts() {
        UserId id = UserId.generate();
        assertThrows(
                SameAccountTransferException.class,
                () -> Transaction.start(
                        id,
                        id,
                        Money.ofTransfer(new BigDecimal("10.00")),
                        IdempotencyKey.of("key-1")
                )
        );
    }

    @Test
    void transactionCompleteEmitsTransferCompleted() {
        UserId payer = UserId.generate();
        UserId payee = UserId.generate();
        Transaction tx = Transaction.start(
                payer,
                payee,
                Money.ofTransfer(new BigDecimal("10.00")),
                IdempotencyKey.of("key-2")
        );

        assertEquals(TransactionStatus.IN_PROGRESS, tx.status());
        assertEquals(7, tx.id().value().version());

        tx.complete();
        assertEquals(TransactionStatus.COMPLETED, tx.status());

        List<?> events = tx.pullEvents();
        assertEquals(1, events.size());
        assertTrue(events.getFirst() instanceof TransferCompleted);
        assertTrue(tx.pullEvents().isEmpty());
    }

    @Test
    void matchesPayloadDetectsConflict() {
        UserId payer = UserId.generate();
        UserId payee = UserId.generate();
        Money amount = Money.ofTransfer(new BigDecimal("10.00"));
        Transaction tx = Transaction.start(payer, payee, amount, IdempotencyKey.of("key-3"));

        assertTrue(tx.matchesPayload(payer, payee, amount));
        assertFalse(tx.matchesPayload(payer, payee, Money.ofTransfer(new BigDecimal("11.00"))));
    }

    @Test
    void uuidV7HasVersionSeven() {
        assertEquals(7, UuidV7.generate().version());
        assertEquals(7, UserId.generate().value().version());
    }

    private static User user(UserType type) {
        return User.create(
                UserId.generate(),
                "Test User",
                Document.of(DocumentType.CPF, "12345678901"),
                Email.of("user@example.com"),
                "hash",
                type
        );
    }
}
