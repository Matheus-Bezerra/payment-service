package dev.matheus.payment.application.service;

import dev.matheus.payment.application.command.TransferCommand;
import dev.matheus.payment.application.exception.AuthorizationUnavailableException;
import dev.matheus.payment.application.exception.DuplicateIdempotencyKeyException;
import dev.matheus.payment.application.exception.TransferAlreadyFailedException;
import dev.matheus.payment.application.result.TransferResult;
import dev.matheus.payment.application.port.out.AuthorizationPort;
import dev.matheus.payment.application.port.out.ClockPort;
import dev.matheus.payment.application.port.out.OutboxPort;
import dev.matheus.payment.application.port.out.TransactionRepository;
import dev.matheus.payment.application.port.out.UserRepository;
import dev.matheus.payment.application.port.out.WalletRepository;
import dev.matheus.payment.domain.enums.TransactionStatus;
import dev.matheus.payment.domain.event.DomainEvent;
import dev.matheus.payment.domain.event.TransferCompleted;
import dev.matheus.payment.domain.exception.DomainException;
import dev.matheus.payment.domain.exception.IdempotencyKeyConflictException;
import dev.matheus.payment.domain.exception.MerchantCannotSendMoneyException;
import dev.matheus.payment.domain.exception.TransferNotAuthorizedException;
import dev.matheus.payment.domain.exception.UserNotFoundException;
import dev.matheus.payment.domain.exception.WalletNotFoundException;
import dev.matheus.payment.domain.model.Money;
import dev.matheus.payment.domain.model.Transaction;
import dev.matheus.payment.domain.model.TransferPolicySnapshot;
import dev.matheus.payment.domain.model.User;
import dev.matheus.payment.domain.model.UserId;
import dev.matheus.payment.domain.model.Wallet;
import dev.matheus.payment.domain.service.TransferPolicy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Service
@RequiredArgsConstructor
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final AuthorizationPort authorizationPort;
    private final OutboxPort outboxPort;
    private final ClockPort clockPort;
    private final TransactionOperations transactionOperations;

    public TransferResult transfer(TransferCommand command) {
        Transaction transaction = Transaction.start(
                command.payerId(),
                command.payeeId(),
                command.amount(),
                command.idempotencyKey()
        );

        try {
            transactionRepository.insertInProgress(transaction);
        } catch (DuplicateIdempotencyKeyException ex) {
            return replayExisting(command);
        }

        log.info(
                "transfer started transactionId={} payer={} payee={}",
                transaction.id().value(),
                command.payerId().value(),
                command.payeeId().value()
        );

        try {
            User payer = requireUser(command.payerId(), "payer");
            requireUser(command.payeeId(), "payee");

            if (!payer.canSendMoney()) {
                throw new MerchantCannotSendMoneyException("merchants cannot send money");
            }

            requireWallet(command.payerId());
            requireWallet(command.payeeId());

            assertPolicy(transaction, command);

            if (!authorizationPort.authorize()) {
                throw new TransferNotAuthorizedException("transfer was not authorized");
            }

            Transaction completed = settle(transaction);
            log.info("transfer completed transactionId={}", completed.id().value());
            return new TransferResult(completed, false);
        } catch (DomainException | AuthorizationUnavailableException ex) {
            log.info(
                    "transfer failed transactionId={} reason={}",
                    transaction.id().value(),
                    ex.getClass().getSimpleName()
            );
            markFailed(transaction, ex.getMessage());
            throw ex;
        }
    }

    private TransferResult replayExisting(TransferCommand command) {
        Transaction existing = transactionRepository.requireByIdempotencyKeyForUpdate(
                command.idempotencyKey()
        );

        if (!existing.matchesPayload(command.payerId(), command.payeeId(), command.amount())) {
            throw new IdempotencyKeyConflictException(
                    "idempotency key was reused with a different payload"
            );
        }

        if (existing.status() == TransactionStatus.FAILED) {
            throw new TransferAlreadyFailedException(existing.failureReason());
        }

        log.info("transfer replayed transactionId={} status={}", existing.id().value(), existing.status());
        return new TransferResult(existing, true);
    }

    private void assertPolicy(Transaction transaction, TransferCommand command) {
        Instant now = clockPort.now();
        ZoneId zone = TransferPolicySnapshot.ZONE;
        LocalDate day = now.atZone(zone).toLocalDate();
        Instant dayStart = day.atStartOfDay(zone).toInstant();
        Instant dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant();
        Instant minuteAgo = now.minus(Duration.ofMinutes(1));

        Money spentToday = transactionRepository.sumPayerCompletedOrInProgressToday(
                command.payerId(),
                dayStart,
                dayEnd,
                transaction.id()
        );
        int transfersInLastMinute = transactionRepository.countPayerCompletedOrInProgressSince(
                command.payerId(),
                minuteAgo,
                transaction.id()
        );

        TransferPolicy.assertAllowed(
                TransferPolicySnapshot.of(
                        command.amount(),
                        now,
                        spentToday,
                        transfersInLastMinute
                )
        );
    }

    private Transaction settle(Transaction transaction) {
        return transactionOperations.execute(status -> {
            UserId payerId = transaction.payerId();
            UserId payeeId = transaction.payeeId();

            UserId first = min(payerId, payeeId);
            UserId second = payerId.equals(first) ? payeeId : payerId;

            List<Wallet> locked = walletRepository.lockByOwnerIds(first, second);
            Wallet payerWallet = walletByOwner(locked, payerId);
            Wallet payeeWallet = walletByOwner(locked, payeeId);

            payerWallet.debit(transaction.amount());
            payeeWallet.credit(transaction.amount());
            transaction.complete();

            walletRepository.update(payerWallet);
            walletRepository.update(payeeWallet);
            transactionRepository.update(transaction);

            for (DomainEvent event : transaction.pullEvents()) {
                if (event instanceof TransferCompleted completed) {
                    outboxPort.save(completed);
                }
            }

            return transaction;
        });
    }

    private void markFailed(Transaction transaction, String reason) {
        if (transaction.status() != TransactionStatus.IN_PROGRESS) {
            return;
        }
        transactionOperations.executeWithoutResult(status -> {
            transaction.fail(reason);
            transactionRepository.update(transaction);
        });
    }

    private User requireUser(UserId id, String role) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(role + " not found: " + id));
    }

    private Wallet requireWallet(UserId ownerId) {
        return walletRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new WalletNotFoundException("wallet not found for user: " + ownerId));
    }

    private static Wallet walletByOwner(List<Wallet> wallets, UserId ownerId) {
        return wallets.stream()
                .filter(wallet -> wallet.ownerId().equals(ownerId))
                .findFirst()
                .orElseThrow(() -> new WalletNotFoundException("wallet not found for user: " + ownerId));
    }

    private static UserId min(UserId left, UserId right) {
        return Comparator.comparing(UserId::value).compare(left, right) <= 0 ? left : right;
    }
}
