package dev.matheus.payment.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.matheus.payment.application.command.TransferCommand;
import dev.matheus.payment.application.exception.DuplicateIdempotencyKeyException;
import dev.matheus.payment.application.exception.TransferAlreadyFailedException;
import dev.matheus.payment.application.result.TransferResult;
import dev.matheus.payment.application.port.out.AuthorizationPort;
import dev.matheus.payment.application.port.out.ClockPort;
import dev.matheus.payment.application.port.out.OutboxPort;
import dev.matheus.payment.application.port.out.TransactionRepository;
import dev.matheus.payment.application.port.out.UserRepository;
import dev.matheus.payment.application.port.out.WalletRepository;
import dev.matheus.payment.domain.enums.DocumentType;
import dev.matheus.payment.domain.enums.TransactionStatus;
import dev.matheus.payment.domain.enums.UserType;
import dev.matheus.payment.domain.event.TransferCompleted;
import dev.matheus.payment.domain.exception.IdempotencyKeyConflictException;
import dev.matheus.payment.domain.exception.MerchantCannotSendMoneyException;
import dev.matheus.payment.domain.exception.TransferAmountLimitExceededException;
import dev.matheus.payment.domain.exception.TransferNotAuthorizedException;
import dev.matheus.payment.domain.model.Document;
import dev.matheus.payment.domain.model.Email;
import dev.matheus.payment.domain.model.IdempotencyKey;
import dev.matheus.payment.domain.model.Money;
import dev.matheus.payment.domain.model.Transaction;
import dev.matheus.payment.domain.model.User;
import dev.matheus.payment.domain.model.UserId;
import dev.matheus.payment.domain.model.Wallet;
import dev.matheus.payment.domain.model.WalletId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionOperations;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Instant DAYTIME = LocalDateTime.of(2026, 8, 13, 15, 0)
            .atZone(ZONE)
            .toInstant();

    @Mock
    private UserRepository userRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AuthorizationPort authorizationPort;
    @Mock
    private OutboxPort outboxPort;
    @Mock
    private ClockPort clockPort;

    private TransferService transferService;

    private UserId payerId;
    private UserId payeeId;
    private Wallet payerWallet;
    private Wallet payeeWallet;

    @BeforeEach
    void setUp() {
        transferService = new TransferService(
                userRepository,
                walletRepository,
                transactionRepository,
                authorizationPort,
                outboxPort,
                clockPort,
                TransactionOperations.withoutTransaction()
        );

        payerId = UserId.generate();
        payeeId = UserId.generate();
        payerWallet = Wallet.create(
                WalletId.generate(),
                payerId,
                Money.ofBalance(new BigDecimal("1000.00"))
        );
        payeeWallet = Wallet.create(
                WalletId.generate(),
                payeeId,
                Money.ofBalance(new BigDecimal("100.00"))
        );
    }

    @Test
    void happyPathCompletesAndSavesOutbox() {
        stubCommonUsersAndWallets(UserType.COMMON);
        when(clockPort.now()).thenReturn(DAYTIME);
        when(transactionRepository.sumPayerCompletedOrInProgressToday(any(), any(), any(), any()))
                .thenReturn(Money.zero());
        when(transactionRepository.countPayerCompletedOrInProgressSince(any(), any(), any()))
                .thenReturn(0);
        when(authorizationPort.authorize()).thenReturn(true);
        stubLockWallets();

        TransferResult result = transferService.transfer(command("50.00"));

        assertFalse(result.replay());
        assertEquals(TransactionStatus.COMPLETED, result.transaction().status());
        assertEquals(new BigDecimal("950.00"), payerWallet.balance().amount());
        assertEquals(new BigDecimal("150.00"), payeeWallet.balance().amount());
        verify(walletRepository).update(payerWallet);
        verify(walletRepository).update(payeeWallet);
        verify(transactionRepository).update(result.transaction());
        verify(outboxPort).save(any(TransferCompleted.class));
    }

    @Test
    void merchantPayerFailsWithoutCallingAuthorizer() {
        when(userRepository.findById(payerId)).thenReturn(Optional.of(user(payerId, UserType.MERCHANT, "11144477735")));
        when(userRepository.findById(payeeId)).thenReturn(Optional.of(user(payeeId, UserType.COMMON, "12345678901")));

        assertThrows(
                MerchantCannotSendMoneyException.class,
                () -> transferService.transfer(command("50.00"))
        );

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).update(captor.capture());
        assertEquals(TransactionStatus.FAILED, captor.getValue().status());
        verify(authorizationPort, never()).authorize();
        verify(outboxPort, never()).save(any());
    }

    @Test
    void policyViolationFailsWithoutCallingAuthorizer() {
        stubCommonUsersAndWallets(UserType.COMMON);
        when(clockPort.now()).thenReturn(DAYTIME);
        when(transactionRepository.sumPayerCompletedOrInProgressToday(any(), any(), any(), any()))
                .thenReturn(Money.zero());
        when(transactionRepository.countPayerCompletedOrInProgressSince(any(), any(), any()))
                .thenReturn(0);

        assertThrows(
                TransferAmountLimitExceededException.class,
                () -> transferService.transfer(command("20000.01"))
        );

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).update(captor.capture());
        assertEquals(TransactionStatus.FAILED, captor.getValue().status());
        verify(authorizationPort, never()).authorize();
        verify(walletRepository, never()).lockByOwnerIds(any(), any());
    }

    @Test
    void authorizerRefusalFailsWithoutMovingBalance() {
        stubCommonUsersAndWallets(UserType.COMMON);
        when(clockPort.now()).thenReturn(DAYTIME);
        when(transactionRepository.sumPayerCompletedOrInProgressToday(any(), any(), any(), any()))
                .thenReturn(Money.zero());
        when(transactionRepository.countPayerCompletedOrInProgressSince(any(), any(), any()))
                .thenReturn(0);
        when(authorizationPort.authorize()).thenReturn(false);

        assertThrows(
                TransferNotAuthorizedException.class,
                () -> transferService.transfer(command("50.00"))
        );

        assertEquals(new BigDecimal("1000.00"), payerWallet.balance().amount());
        assertEquals(new BigDecimal("100.00"), payeeWallet.balance().amount());
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).update(captor.capture());
        assertEquals(TransactionStatus.FAILED, captor.getValue().status());
        verify(outboxPort, never()).save(any());
    }

    @Test
    void idempotentReplayReturnsExistingWithoutReprocessing() {
        Transaction existing = Transaction.start(
                payerId,
                payeeId,
                Money.ofTransfer(new BigDecimal("50.00")),
                IdempotencyKey.of("same-key")
        );
        existing.complete();

        doThrow(new DuplicateIdempotencyKeyException("duplicate"))
                .when(transactionRepository)
                .insertInProgress(any());
        when(transactionRepository.requireByIdempotencyKeyForUpdate(IdempotencyKey.of("same-key")))
                .thenReturn(existing);

        TransferResult result = transferService.transfer(command("50.00", "same-key"));

        assertTrue(result.replay());
        assertEquals(existing, result.transaction());
        assertEquals(TransactionStatus.COMPLETED, result.transaction().status());
        verify(authorizationPort, never()).authorize();
        verify(walletRepository, never()).lockByOwnerIds(any(), any());
        verify(outboxPort, never()).save(any());
    }

    @Test
    void idempotentReplayOfFailedThrowsWithoutReprocessing() {
        Transaction existing = Transaction.start(
                payerId,
                payeeId,
                Money.ofTransfer(new BigDecimal("50.00")),
                IdempotencyKey.of("same-key")
        );
        existing.fail("merchants cannot send money");

        doThrow(new DuplicateIdempotencyKeyException("duplicate"))
                .when(transactionRepository)
                .insertInProgress(any());
        when(transactionRepository.requireByIdempotencyKeyForUpdate(IdempotencyKey.of("same-key")))
                .thenReturn(existing);

        TransferAlreadyFailedException ex = assertThrows(
                TransferAlreadyFailedException.class,
                () -> transferService.transfer(command("50.00", "same-key"))
        );

        assertEquals("merchants cannot send money", ex.getMessage());
        verify(authorizationPort, never()).authorize();
        verify(walletRepository, never()).lockByOwnerIds(any(), any());
        verify(outboxPort, never()).save(any());
    }

    @Test
    void idempotencyKeyConflictWhenPayloadDiffers() {
        Transaction existing = Transaction.start(
                payerId,
                payeeId,
                Money.ofTransfer(new BigDecimal("50.00")),
                IdempotencyKey.of("same-key")
        );
        existing.complete();

        doThrow(new DuplicateIdempotencyKeyException("duplicate"))
                .when(transactionRepository)
                .insertInProgress(any());
        when(transactionRepository.requireByIdempotencyKeyForUpdate(IdempotencyKey.of("same-key")))
                .thenReturn(existing);

        assertThrows(
                IdempotencyKeyConflictException.class,
                () -> transferService.transfer(command("99.00", "same-key"))
        );

        verify(authorizationPort, never()).authorize();
    }

    private void stubCommonUsersAndWallets(UserType payerType) {
        when(userRepository.findById(payerId)).thenReturn(Optional.of(user(payerId, payerType, "11144477735")));
        when(userRepository.findById(payeeId)).thenReturn(Optional.of(user(payeeId, UserType.COMMON, "12345678901")));
        when(walletRepository.findByOwnerId(payerId)).thenReturn(Optional.of(payerWallet));
        when(walletRepository.findByOwnerId(payeeId)).thenReturn(Optional.of(payeeWallet));
    }

    private void stubLockWallets() {
        UserId first = Comparator.comparing(UserId::value).compare(payerId, payeeId) <= 0 ? payerId : payeeId;
        UserId second = payerId.equals(first) ? payeeId : payerId;
        Wallet firstWallet = first.equals(payerId) ? payerWallet : payeeWallet;
        Wallet secondWallet = second.equals(payerId) ? payerWallet : payeeWallet;
        when(walletRepository.lockByOwnerIds(eq(first), eq(second)))
                .thenReturn(List.of(firstWallet, secondWallet));
    }

    private TransferCommand command(String amount) {
        return command(amount, "idempotency-key-1");
    }

    private TransferCommand command(String amount, String key) {
        return new TransferCommand(
                payerId,
                payeeId,
                Money.ofTransfer(new BigDecimal(amount)),
                IdempotencyKey.of(key)
        );
    }

    private static User user(UserId id, UserType type, String cpf) {
        return User.create(
                id,
                "Test User",
                Document.of(DocumentType.CPF, cpf),
                Email.of(id.value() + "@example.com"),
                "hash",
                type
        );
    }
}
