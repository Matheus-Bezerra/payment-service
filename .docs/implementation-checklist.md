# Implementation Checklist — PicPay Simplificado

Ordem de implementação em etapas revisáveis. Cada etapa vira **um commit** (ou um PR). Não misture domínio com Dockerfile, nem HTTP com schema.

Fontes de verdade: [requirements.md](requirements.md), [domain-model.md](domain-model.md), [database-design.md](database-design.md), [api-spec.md](api-spec.md). Estrutura de pastas: skill hexagonal. Docker local: [CLOUD.md](../../spring-hexagonal-architecture/CLOUD.md) — sem Kubernetes, Terraform ou AWS neste recorte.

Em conflito: `.docs` > README do desafio > inferência.

---

## Como usar

- Marque o item só quando o critério de aceite da etapa estiver cumprido.
- Não pule etapa. A seguinte assume a anterior pronta.
- Não crie pacotes vazios só para completar a árvore hexagonal.
- Teste no limite que a etapa mudou; não antecipe Testcontainers de RabbitMQ no setup.
- Fila **não** processa transferência. Worker só notifica depois do commit.

---

## 0. Decisões do setup (fechar antes do código)

- [x] Pacote-base definido: `dev.matheus.payment`.
- [x] Java 25 LTS + Spring Boot 4.1.0.
- [x] Maven Wrapper.
- [x] PostgreSQL: imagem oficial `postgres:18` (alinhada a `uuidv7()` no [database-design.md](database-design.md)).
- [x] RabbitMQ **fora** do primeiro commit: entra na etapa 8. Não adicionar `spring-boot-starter-amqp` no setup.
- [x] Sem AWS, Kubernetes ou Terraform.

---

## 1. Setup do projeto + Docker

Objetivo: `docker compose up --build` sobe Postgres oficial e a API; `GET /actuator/health` responde.

### Incluir

- [x] `pom.xml` + Maven Wrapper.
- [x] Classe `*Application` e `application.yml` (config por ambiente / variáveis).
- [x] Spring Web + Actuator (`/actuator/health`).
- [x] Driver JDBC PostgreSQL + Flyway **ligado, sem migrations de negócio**.
- [x] `Dockerfile` multi-stage (JDK 25 no build, JRE 25 no runtime, usuário não-root).
- [x] `.dockerignore`.
- [x] `compose.yml`: serviço `postgres` (`postgres:18`, volume, healthcheck) + serviço `app` (`depends_on` healthy, JDBC via env).
- [x] `.gitignore`.
- [x] README: como subir tudo no Docker e como rodar só o banco no Docker + API no IDE.

### Não incluir

- [x] Pacotes `domain/`, `application/`, `adapter/` vazios.
- [x] Tabelas de negócio, entidades JPA, controllers, Swagger.
- [x] RabbitMQ no Compose ou no `pom`.
- [x] Secrets de produção versionados. Senha local `payment` no Compose é aceitável para o desafio.

### Aceite

- [x] `docker compose up --build` sobe sem erro.
- [x] `GET /actuator/health` → `UP` (e `db` `UP`, se exposto).
- [x] Host do banco no Compose é o nome do serviço (`postgres`), não `localhost`.

---

## 2. Schema Flyway

Objetivo: banco com as tabelas do [database-design.md](database-design.md). Sem JPA ainda, se a etapa ficar mais fácil de revisar.

- [x] `V1` (ou equivalente): `users`, `wallets`, `transactions`, `notification_outbox`.
- [x] PKs/FKs `uuid` (UUID v7). Sem `BIGSERIAL`, `gen_random_uuid()` ou ULID.
- [x] Constraints e índices do documento (documento/e-mail únicos, `idempotency_key UNIQUE NOT NULL`, checks de saldo/status).
- [x] Compose sobe e Flyway aplica no boot da API.

Não incluir entidades JPA, repositórios nem seed (seed é a etapa 9).

---

## 3. Domain

Objetivo: regras e invariantes isoladas, sem Spring/JPA/HTTP/RabbitMQ.

- [x] Modelo, value objects, enums, exceções e eventos (nomes no passado).
- [x] Identificadores UUID v7 gerados na aplicação. Sem `UUID.randomUUID()` em PK.
- [x] `TransferPolicy` em `domain/service`, pura (snapshot + `Instant`). Timezone `America/Sao_Paulo`.
- [x] Política na ordem: POL-01 → POL-02 → POL-03 → POL-04 (pode ser mais de um commit dentro desta etapa).
- [x] Testes de unidade do domínio e da política.

Leia só [domain-model.md](domain-model.md) e as regras em [requirements.md](requirements.md).

---

## 4. Application + ports

Objetivo: orquestração da transferência síncrona, sem conhecer controller, JPA ou broker.

- [ ] Application service da transferência.
- [ ] Ports de saída: persistência, autorizador, outbox (interfaces). Sem `port/in` a menos que traga ganho real.
- [ ] Idempotência: `Idempotency-Key` obrigatória; `INSERT` `IN_PROGRESS`; replay espera se ainda `IN_PROGRESS`.
- [ ] Service não recebe DTO HTTP/mensageria.
- [ ] Testes do service com ports mockadas.

Fila não entra no fluxo síncrono do `POST /transfer`.

---

## 5. Adapter out — persistência

Objetivo: JPA implementa os ports de repositório.

- [ ] Entidades JPA em `adapter/out/persistence/entity` (não são o domínio).
- [ ] Spring Data repositories + persistence adapters + mappers `Domain ↔ JpaEntity`.
- [ ] Locks e ordem de lock das carteiras conforme [database-design.md](database-design.md).
- [ ] Outbox persistida **na mesma transação** do débito/crédito. Ainda sem publicar no RabbitMQ.
- [ ] Testes de persistência (Testcontainers PostgreSQL).

---

## 6. Adapter in — HTTP

Objetivo: contrato de [api-spec.md](api-spec.md) no `POST /transfer`.

- [ ] Interface de API + anotações OpenAPI em `adapter/in/web/api`.
- [ ] Controller fino; DTOs em `request` / `response`; mapper para tipos internos.
- [ ] Header `Idempotency-Key` obrigatório; ausente → `400`.
- [ ] `payer` / `payee` / `id` como UUID v7 no JSON.
- [ ] Erros em RFC 7807 `ProblemDetail`.
- [ ] Não expor entidade de domínio nem JPA.
- [ ] Testes MockMvc do contrato e dos erros.

---

## 7. Autorizador externo

Objetivo: consulta síncrona ao mock antes de finalizar a transferência.

- [ ] Cliente HTTP em `adapter/out/client` implementando a port.
- [ ] `GET https://util.devi.tools/api/v2/authorize`.
- [ ] Timeout e mapeamento de recusa/indisponibilidade.
- [ ] Política interna roda **antes** do autorizador (não gasta o mock se o limite já barra).
- [ ] Testes do adapter (WireMock ou equivalente).

---

## 8. Outbox + RabbitMQ

Objetivo: notificar o payee depois do commit, com retry/backoff/DLQ.

- [ ] `rabbitmq` no `compose.yml` (imagem oficial). Ainda sem misturar com o setup antigo.
- [ ] Publisher lê a outbox **depois** do commit e publica no RabbitMQ.
- [ ] Consumer/worker só notifica (`POST https://util.devi.tools/api/v1/notify`). Não processa transferência.
- [ ] Retry com backoff + DLQ. Consumidor idempotente.
- [ ] Envelope de evento alinhado à skill (ids de correlação, `eventType`, payload).
- [ ] Testes de publicação, idempotência, retry e DLQ (Testcontainers RabbitMQ).

---

## 9. Seed de usuários

Objetivo: dados para exercitar `POST /transfer`. Cadastro e autenticação continuam fora de escopo.

- [ ] Seed (migration ou runner local) com usuários/carteiras do [api-spec.md](api-spec.md).
- [ ] Pelo menos um `COMMON` (payer) e um `MERCHANT` (payee), com saldo.
- [ ] Sem endpoint de cadastro, a menos que o usuário peça depois.

---

## 10. Testes de fluxo e observabilidade extra

Objetivo: fechar o recorte avaliado e o baseline operacional. Não antecipar tracing/dashboards sem necessidade.

- [ ] Teste de integração do fluxo feliz `POST /transfer`.
- [ ] Casos: lojista como payer, saldo insuficiente, autorizador recusa, replay de `Idempotency-Key`, políticas POL-01–04.
- [ ] Baseline da skill: logs em `stdout`, sem dados sensíveis, `correlationId`, health checks.
- [ ] Micrometer / OpenTelemetry / dashboards só se aprovados.
- [ ] CI (build + testes) só se pedido.

---

## Fora deste checklist

Não fazer sem pedido explícito:

- autenticação, cadastro público, frontend;
- Kafka, Redis, segundo broker;
- Kubernetes, Terraform, AWS;
- copiar a skill para dentro deste repositório.
