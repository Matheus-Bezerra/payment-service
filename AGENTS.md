# AGENTS.md — PicPay Simplificado

Instruções para agentes. Leia **este arquivo primeiro**. Não carregue o restante da documentação de uma vez.

## Stack e decisões fechadas

- Java + Spring Boot, Maven, arquitetura hexagonal pragmática.
- PostgreSQL + Flyway. RabbitMQ **somente** para notificação (retry/backoff/DLQ).
- Transferência **síncrona** no `POST /transfer`. Cliente recebe o resultado na hora.
- Idempotência: header `Idempotency-Key` **obrigatório** + `UNIQUE NOT NULL` em `transactions` (`INSERT` `IN_PROGRESS`, depois processa). Ausente → `400`. Replay espera se ainda `IN_PROGRESS`.
- Identificadores: **UUID v7** (RFC 9562) em PKs/FKs e no contrato HTTP (`payer`, `payee`, `id`). Não usar `BIGSERIAL`, `Long`, UUID v4 (`UUID.randomUUID()` / `gen_random_uuid()`) nem ULID para PKs. `Idempotency-Key` (cliente) ≠ `id` da transação.
- `TransferPolicy` (`domain/service`): teto R$ 20.000; noite 22:00–06:00 R$ 5.000 (`America/Sao_Paulo`); diário R$ 80.000; 5 tx/minuto. Pura (snapshot + Instant); I/O na application. Implementar na ordem POL-01 → POL-04.
- Fora de escopo: autenticação, cadastro (seed ok), frontend.

Não reabra essas decisões sem o usuário pedir.

## Onde está cada verdade

| Precisa de | Leia só |
|------------|---------|
| Regras, fluxo, o que está fora de escopo | [`.docs/requirements.md`](.docs/requirements.md) |
| Entidades, VOs, enums, invariantes, eventos | [`.docs/domain-model.md`](.docs/domain-model.md) |
| Tabelas, constraints, locks, outbox, Flyway | [`.docs/database-design.md`](.docs/database-design.md) |
| Contrato HTTP, erros, `Idempotency-Key` | [`.docs/api-spec.md`](.docs/api-spec.md) |
| Enunciado original do desafio | [`README.md`](README.md) |
| Pastas, ports, adapters, outbox, testes | [`../spring-hexagonal-architecture/SKILL.md`](../spring-hexagonal-architecture/SKILL.md) |
| Detalhe de camadas / feature atravessando domínio | [`../spring-hexagonal-architecture/ARCHITECTURE.md`](../spring-hexagonal-architecture/ARCHITECTURE.md) |
| Logs, métricas, health, DLQ, tracing | [`../spring-hexagonal-architecture/OBSERVABILITY.md`](../spring-hexagonal-architecture/OBSERVABILITY.md) |
| Docker/K8s/AWS/CI | [`../spring-hexagonal-architecture/CLOUD.md`](../spring-hexagonal-architecture/CLOUD.md) |

Em conflito: `.docs` > README do desafio > inferência. A skill manda em **estrutura de código**, não em regra de negócio.

## Economia de contexto

1. Leia **só** os arquivos da tabela que a tarefa exige.
2. Não releia arquivo já no contexto e inalterado.
3. Não copie a skill para dentro deste repo. Siga-a; crie pacotes **quando houver responsabilidade real**.
4. Não gere AWS/K8s/Terraform sem pedido explícito.
5. Altere `.docs` antes do código se a mudança for de regra, modelo, schema ou contrato HTTP.

## Ordem ao implementar

1. Domínio (modelo, invariantes, eventos, exceções).
2. Application service + ports.
3. Adapter in (HTTP) e out (JPA, autorizador, outbox/RabbitMQ).
4. Testes no limite que mudou.

Fila não processa transferência. Worker só notifica depois do commit.
