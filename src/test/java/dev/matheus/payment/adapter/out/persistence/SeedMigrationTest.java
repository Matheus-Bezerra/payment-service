package dev.matheus.payment.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.matheus.payment.adapter.out.persistence.entity.UserJpaEntity;
import dev.matheus.payment.adapter.out.persistence.entity.WalletJpaEntity;
import dev.matheus.payment.adapter.out.persistence.repository.UserJpaRepository;
import dev.matheus.payment.adapter.out.persistence.repository.WalletJpaRepository;
import dev.matheus.payment.domain.enums.UserType;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Sql("/db/seeds/seed_local.sql")
class SeedMigrationTest {

    private static final UUID JOAO_ID = UUID.fromString("0190a1b2-c3d4-7000-8000-000000000004");
    private static final UUID MATHEUS_ID = UUID.fromString("0190a1b2-c3d4-7000-8000-000000000006");
    private static final UUID LOJA_ID = UUID.fromString("0190a1b2-c3d4-7000-8000-000000000015");
    private static final UUID MERCADO_ID = UUID.fromString("0190a1b2-c3d4-7000-8000-000000000016");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired
    UserJpaRepository userJpaRepository;

    @Autowired
    WalletJpaRepository walletJpaRepository;

    @Test
    void localSeedCreatesCommonAndMerchantUsersWithWallets() {
        UserJpaEntity joao = user(JOAO_ID);
        UserJpaEntity matheus = user(MATHEUS_ID);
        UserJpaEntity loja = user(LOJA_ID);
        UserJpaEntity mercado = user(MERCADO_ID);

        assertEquals("João Exemplo", joao.getFullName());
        assertEquals("joao.comum@example.com", joao.getEmail());
        assertEquals(UserType.COMMON, joao.getType());
        assertBalance(JOAO_ID, "50000.00");

        assertEquals("Matheus", matheus.getFullName());
        assertEquals("matheus@example.com", matheus.getEmail());
        assertEquals(UserType.COMMON, matheus.getType());
        assertBalance(MATHEUS_ID, "100000.00");

        assertEquals("Loja Exemplo", loja.getFullName());
        assertEquals("loja@example.com", loja.getEmail());
        assertEquals(UserType.MERCHANT, loja.getType());
        assertBalance(LOJA_ID, "10000.00");

        assertEquals("Mercado Exemplo", mercado.getFullName());
        assertEquals("mercado@example.com", mercado.getEmail());
        assertEquals(UserType.MERCHANT, mercado.getType());
        assertBalance(MERCADO_ID, "10000.00");

        assertEquals(4, userJpaRepository.count());
        assertEquals(4, walletJpaRepository.count());
        assertTrue(joao.getPasswordHash().startsWith("$2a$10$"));
        assertEquals(joao.getPasswordHash(), matheus.getPasswordHash());
    }

    private UserJpaEntity user(UUID id) {
        return userJpaRepository.findById(id).orElseThrow();
    }

    private void assertBalance(UUID userId, String expected) {
        WalletJpaEntity wallet = walletJpaRepository.findByUserId(userId).orElseThrow();
        assertEquals(0, new BigDecimal(expected).compareTo(wallet.getBalance()));
        assertEquals(userId, wallet.getUserId());
    }
}
