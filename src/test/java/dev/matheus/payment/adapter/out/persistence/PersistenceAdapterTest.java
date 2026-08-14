package dev.matheus.payment.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.matheus.payment.adapter.out.persistence.adapter.OutboxPersistenceAdapter;
import dev.matheus.payment.adapter.out.persistence.adapter.TransactionPersistenceAdapter;
import dev.matheus.payment.adapter.out.persistence.adapter.UserPersistenceAdapter;
import dev.matheus.payment.adapter.out.persistence.adapter.WalletPersistenceAdapter;
import dev.matheus.payment.adapter.out.persistence.entity.NotificationOutboxJpaEntity;
import dev.matheus.payment.adapter.out.persistence.entity.OutboxStatus;
import dev.matheus.payment.adapter.out.persistence.entity.UserJpaEntity;
import dev.matheus.payment.adapter.out.persistence.entity.WalletJpaEntity;
import dev.matheus.payment.adapter.out.persistence.mapper.TransactionPersistenceMapperImpl;
import dev.matheus.payment.adapter.out.persistence.mapper.UserPersistenceMapperImpl;
import dev.matheus.payment.adapter.out.persistence.mapper.WalletPersistenceMapperImpl;
import dev.matheus.payment.adapter.out.persistence.repository.NotificationOutboxJpaRepository;
import dev.matheus.payment.adapter.out.persistence.repository.TransactionJpaRepository;
import dev.matheus.payment.adapter.out.persistence.repository.UserJpaRepository;
import dev.matheus.payment.adapter.out.persistence.repository.WalletJpaRepository;
import dev.matheus.payment.application.exception.DuplicateIdempotencyKeyException;
import dev.matheus.payment.application.port.out.OutboxPort;
import dev.matheus.payment.application.port.out.TransactionRepository;
import dev.matheus.payment.application.port.out.UserRepository;
import dev.matheus.payment.application.port.out.WalletRepository;
import dev.matheus.payment.domain.enums.DocumentType;
import dev.matheus.payment.domain.enums.TransactionStatus;
import dev.matheus.payment.domain.enums.UserType;
import dev.matheus.payment.domain.event.TransferCompleted;
import dev.matheus.payment.domain.model.IdempotencyKey;
import dev.matheus.payment.domain.model.Money;
import dev.matheus.payment.domain.model.Transaction;
import dev.matheus.payment.domain.model.User;
import dev.matheus.payment.domain.model.UserId;
import dev.matheus.payment.domain.model.Wallet;
import dev.matheus.payment.domain.model.WalletId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({
        UserPersistenceAdapter.class,
        UserPersistenceMapperImpl.class,
        WalletPersistenceAdapter.class,
        WalletPersistenceMapperImpl.class,
        TransactionPersistenceAdapter.class,
        TransactionPersistenceMapperImpl.class,
        OutboxPersistenceAdapter.class
})
class PersistenceAdapterTest {

    private static final AtomicLong DOCUMENTS = new AtomicLong(100_000_000_00L);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired
    UserRepository userRepository;
    @Autowired
    WalletRepository walletRepository;
    @Autowired
    TransactionRepository transactionRepository;
    @Autowired
    OutboxPort outboxPort;
    @Autowired
    UserJpaRepository userJpaRepository;
    @Autowired
    WalletJpaRepository walletJpaRepository;
    @Autowired
    NotificationOutboxJpaRepository outboxJpaRepository;
    @Autowired
    TransactionJpaRepository transactionJpaRepository;
    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void mapsUserAndWalletRoundTrip() {
        UserId userId = persistUser(UserType.COMMON);
        WalletId walletId = persistWallet(userId, "1000.00");

        User user = userRepository.findById(userId).orElseThrow();
        Wallet wallet = walletRepository.findByOwnerId(userId).orElseThrow();

        assertEquals(userId, user.id());
        assertEquals(UserType.COMMON, user.type());
        assertEquals(walletId, wallet.id());
        assertEquals(new BigDecimal("1000.00"), wallet.balance().amount());
    }

    @Test
    void insertInProgressRejectsDuplicateIdempotencyKey() {
        UserId payer = persistUser(UserType.COMMON);
        UserId payee = persistUser(UserType.MERCHANT);
        IdempotencyKey key = IdempotencyKey.of("key-" + UUID.randomUUID());

        transactionRepository.insertInProgress(transfer(payer, payee, "10.00", key));

        assertThrows(
                DuplicateIdempotencyKeyException.class,
                () -> transactionRepository.insertInProgress(transfer(payer, payee, "10.00", key))
        );
    }

    @Test
    void policyQueriesIgnoreFailedAndCurrentTransaction() {
        UserId payer = persistUser(UserType.COMMON);
        UserId payee = persistUser(UserType.MERCHANT);
        Instant now = Instant.now();

        Transaction current = transfer(payer, payee, "10.00", uniqueKey());
        Transaction completed = transfer(payer, payee, "25.00", uniqueKey());
        completed.complete();
        Transaction failed = transfer(payer, payee, "40.00", uniqueKey());
        failed.fail("denied");
        Transaction inProgress = transfer(payer, payee, "15.00", uniqueKey());

        transactionRepository.insertInProgress(current);
        transactionRepository.insertInProgress(completed);
        transactionRepository.update(completed);
        transactionRepository.insertInProgress(failed);
        transactionRepository.update(failed);
        transactionRepository.insertInProgress(inProgress);

        Money spent = transactionRepository.sumPayerCompletedOrInProgressToday(
                payer,
                now.minusSeconds(3600),
                now.plusSeconds(3600),
                current.id()
        );
        int count = transactionRepository.countPayerCompletedOrInProgressSince(
                payer,
                now.minusSeconds(3600),
                current.id()
        );

        assertEquals(new BigDecimal("40.00"), spent.amount());
        assertEquals(2, count);
    }

    @Test
    void lockByOwnerIdsOrdersByUserIdRegardlessOfArgumentOrder() {
        UserId first = persistUser(UserType.COMMON);
        UserId second = persistUser(UserType.COMMON);
        persistWallet(first, "100.00");
        persistWallet(second, "200.00");

        UserId smaller = Comparator.comparing(UserId::value).compare(first, second) <= 0 ? first : second;
        UserId larger = first.equals(smaller) ? second : first;

        List<Wallet> locked = walletRepository.lockByOwnerIds(larger, smaller);

        assertEquals(2, locked.size());
        assertEquals(smaller, locked.get(0).ownerId());
        assertEquals(larger, locked.get(1).ownerId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void outboxIsRolledBackTogetherWithWalletUpdate() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UserId payerId = persistUser(UserType.COMMON);
        UserId payeeId = persistUser(UserType.MERCHANT);
        persistWallet(payerId, "100.00");
        persistWallet(payeeId, "0.00");
        Transaction transaction = transfer(payerId, payeeId, "10.00", uniqueKey());
        transactionRepository.insertInProgress(transaction);

        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            List<Wallet> locked = walletRepository.lockByOwnerIds(payerId, payeeId);
            Wallet payer = locked.stream()
                    .filter(wallet -> wallet.ownerId().equals(payerId))
                    .findFirst()
                    .orElseThrow();
            payer.debit(Money.ofTransfer(new BigDecimal("10.00")));
            walletRepository.update(payer);
            transaction.complete();
            transactionRepository.update(transaction);
            transaction.pullEvents().forEach(event -> {
                if (event instanceof TransferCompleted completed) {
                    outboxPort.save(completed);
                }
            });
            throw new IllegalStateException("boom");
        }));

        Wallet reloaded = walletRepository.findByOwnerId(payerId).orElseThrow();
        assertEquals(new BigDecimal("100.00"), reloaded.balance().amount());
        assertEquals(0, outboxJpaRepository.count());
        assertEquals(
                TransactionStatus.IN_PROGRESS,
                transactionJpaRepository.findById(transaction.id().value()).orElseThrow().getStatus()
        );
    }

    @Test
    void savesOutboxPayloadForCompletedTransfer() {
        UserId payerId = persistUser(UserType.COMMON);
        UserId payeeId = persistUser(UserType.MERCHANT);
        Transaction transaction = transfer(payerId, payeeId, "10.00", uniqueKey());
        transactionRepository.insertInProgress(transaction);
        transaction.complete();
        transactionRepository.update(transaction);
        TransferCompleted event = (TransferCompleted) transaction.pullEvents().getFirst();

        outboxPort.save(event);

        NotificationOutboxJpaEntity row = outboxJpaRepository.findAll().getFirst();
        assertEquals(OutboxStatus.PENDING, row.getStatus());
        assertEquals("TransferCompleted", row.getEventType());
        assertEquals(transaction.id().value(), row.getTransactionId());
        assertEquals(new BigDecimal("10.00"), row.getPayload().amount());
        assertEquals(7, row.getEventId().version());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void lockingWalletsFromBothDirectionsDoesNotDeadlock() throws Exception {
        UserId payer = persistUser(UserType.COMMON);
        UserId payee = persistUser(UserType.MERCHANT);
        persistWallet(payer, "100.00");
        persistWallet(payee, "100.00");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        try {
            Future<?> left = pool.submit(() -> {
                ready.countDown();
                await(ready);
                tx.executeWithoutResult(status -> {
                    walletRepository.lockByOwnerIds(payer, payee);
                    sleepBriefly();
                });
            });
            Future<?> right = pool.submit(() -> {
                ready.countDown();
                await(ready);
                tx.executeWithoutResult(status -> {
                    walletRepository.lockByOwnerIds(payee, payer);
                    sleepBriefly();
                });
            });
            left.get(5, TimeUnit.SECONDS);
            right.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private UserId persistUser(UserType type) {
        TransactionTemplate isolated = new TransactionTemplate(transactionManager);
        isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return isolated.execute(status -> {
            UserId id = UserId.generate();
            UserJpaEntity entity = new UserJpaEntity();
            entity.setId(id.value());
            entity.setFullName("Test User");
            entity.setDocumentType(DocumentType.CPF);
            entity.setDocumentValue(nextCpf());
            entity.setEmail(id.value() + "@example.com");
            entity.setPasswordHash("hash");
            entity.setType(type);
            entity.setCreatedAt(Instant.now());
            userJpaRepository.saveAndFlush(entity);
            return id;
        });
    }

    private WalletId persistWallet(UserId ownerId, String balance) {
        TransactionTemplate isolated = new TransactionTemplate(transactionManager);
        isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return isolated.execute(status -> {
            WalletId id = WalletId.generate();
            WalletJpaEntity entity = new WalletJpaEntity();
            entity.setId(id.value());
            entity.setUserId(ownerId.value());
            entity.setBalance(new BigDecimal(balance));
            entity.setUpdatedAt(Instant.now());
            walletJpaRepository.saveAndFlush(entity);
            return id;
        });
    }

    private static Transaction transfer(UserId payer, UserId payee, String amount, IdempotencyKey key) {
        return Transaction.start(payer, payee, Money.ofTransfer(new BigDecimal(amount)), key);
    }

    private static IdempotencyKey uniqueKey() {
        return IdempotencyKey.of("key-" + UUID.randomUUID());
    }

    private static String nextCpf() {
        return String.format("%011d", DOCUMENTS.incrementAndGet());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for workers");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(80);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
