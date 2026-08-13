# API Spec — PicPay Simplificado

Define o contrato HTTP entre cliente e backend: endpoints, headers, requests, responses e erros.

Autenticação **está fora de escopo** (não avaliada pelo desafio). Não há JWT, API key obrigatória nem sessão.

Regras: [requirements.md](requirements.md). Domínio: [domain-model.md](domain-model.md).

IDs de exemplo (seed, UUID v7):

| Papel | UUID v7 |
|-------|---------|
| Usuário comum (payer) | `0190a1b2-c3d4-7000-8000-000000000004` |
| Lojista (payee) | `0190a1b2-c3d4-7000-8000-000000000015` |
| Transferência | `0190a1b2-c3d4-7000-8000-000000000042` |

---

## 1. Convenções

| Item | Decisão |
|------|---------|
| Estilo | REST, JSON (`application/json`) |
| Erros | RFC 7807 `ProblemDetail` (`application/problem+json`) |
| IDs | UUID v7 em string. PKs do banco são `UUID` v7, não `BIGSERIAL` nem ULID. |
| Dinheiro | `number` decimal com 2 casas (BRL implícito) |
| Autenticação | não aplicável neste recorte |
| Idempotência | header `Idempotency-Key` no `POST /transfer` (**obrigatório**) |

Base path: `/` (sem prefixo de versão neste recorte). OpenAPI/Swagger na implementação futura em `adapter/in/web/api`.

O enunciado do desafio usa `payer`/`payee` numéricos (`4`, `15`). Este projeto propõe UUID v7 (IDs comparáveis, ordenados por tempo, alinhados às PKs). Na entrevista, tratar como evolução do contrato.

---

## 2. Endpoint obrigatório

### `POST /transfer`

Transfere valor do `payer` para o `payee`. Processamento **síncrono**: a resposta HTTP já é o resultado final (sucesso ou falha). Não retorna `202 Accepted` nem exige polling.

A fila RabbitMQ **não** entra neste request; ela só dispara a notificação depois do commit.

#### Request

```http
POST /transfer
Content-Type: application/json
Idempotency-Key: 8f14e45f-ea31-4d0c-9b9a-0c1d2e3f4a5b
```

```json
{
  "value": 100.0,
  "payer": "0190a1b2-c3d4-7000-8000-000000000004",
  "payee": "0190a1b2-c3d4-7000-8000-000000000015"
}
```

| Campo   | Tipo          | Obrigatório | Regras |
|---------|---------------|-------------|--------|
| `value` | number        | sim         | > 0; duas casas decimais. |
| `payer` | string (UUID) | sim         | ID do usuário que envia. Deve ser `COMMON`. |
| `payee` | string (UUID) | sim         | ID do usuário que recebe. Distinto do payer. |

#### Headers

| Header            | Obrigatório | Descrição |
|-------------------|-------------|-----------|
| `Content-Type`    | sim         | `application/json` |
| `Idempotency-Key` | **sim**     | UUID (ou string estável) gerado pelo **cliente uma vez por tentativa lógica**. Reenviar a **mesma** chave nos retries. Ausente, vazio ou em branco → `400`. Distinto do `id` da transação (UUID v7 gerado pelo servidor). |

Contrato da chave:

- **obrigatória** em todo `POST /transfer`;
- o cliente gera (ex.: UUID v4) no momento em que o usuário confirma o pagamento, **não** a cada retry HTTP;
- retries por timeout/latência reenviam a mesma chave + o mesmo body;
- a mesma chave com `value`/`payer`/`payee` diferentes é erro 409;
- o servidor reserva a chave de forma atômica (`INSERT` + `UNIQUE`); se uma segunda request chegar enquanto a primeira ainda processa, **espera** e devolve o mesmo resultado.

#### Response 201 Created — sucesso

```json
{
  "id": "0190a1b2-c3d4-7000-8000-000000000042",
  "value": 100.00,
  "payer": "0190a1b2-c3d4-7000-8000-000000000004",
  "payee": "0190a1b2-c3d4-7000-8000-000000000015",
  "status": "COMPLETED",
  "createdAt": "2026-08-12T02:15:30.123Z",
  "completedAt": "2026-08-12T02:15:30.890Z"
}
```

Replay idempotente de uma transferência já `COMPLETED` devolve o **mesmo body** (mesmo `id`) com `200 OK` (não cria outro recurso).

| Status | Quando |
|--------|--------|
| `201 Created` | Primeira conclusão com sucesso. |
| `200 OK`      | Replay da mesma `Idempotency-Key` após sucesso. |

#### Response de erro — `ProblemDetail`

```http
HTTP/1.1 422 Unprocessable Entity
Content-Type: application/problem+json
```

```json
{
  "type": "https://picpay-simplificado.local/problems/insufficient-balance",
  "title": "Saldo insuficiente",
  "status": 422,
  "detail": "O payer 0190a1b2-c3d4-7000-8000-000000000004 não possui saldo para transferir 100.00",
  "instance": "/transfer",
  "payer": "0190a1b2-c3d4-7000-8000-000000000004",
  "value": 100.00
}
```

Campos extras (`payer`, `value`, `transactionId`, etc.) são opcionais e úteis para debug. Não incluir senha, hash nem documento completo desnecessariamente.

---

## 3. Catálogo de erros de `POST /transfer`

| HTTP | `title` (exemplo)              | Quando | Saldo |
|------|--------------------------------|--------|-------|
| 400  | Invalid request                | JSON malformado, campo ausente, `value` ≤ 0, payer = payee, UUID inválido | inalterado |
| 400  | Missing Idempotency-Key        | Header `Idempotency-Key` ausente, vazio ou em branco | inalterado |
| 403  | Merchant cannot send money     | `payer` é lojista | inalterado |
| 403  | Transfer not authorized        | Autorizador recusou | inalterado |
| 404  | User not found                 | Payer ou payee inexistente | inalterado |
| 404  | Wallet not found               | Usuário sem carteira | inalterado |
| 409  | Idempotency key conflict       | Mesma chave, payload diferente | inalterado |
| 422  | Insufficient balance           | Saldo do payer < `value` | inalterado |
| 422  | Transfer amount limit exceeded | `value` > R$ 20.000, ou > R$ 5.000 entre 22:00 e 06:00 (`America/Sao_Paulo`) | inalterado |
| 422  | Daily transfer limit exceeded  | Soma do dia do payer + `value` > R$ 80.000 | inalterado |
| 422  | Transfer rate limit exceeded   | Payer já tem 5 transferências (`IN_PROGRESS`/`COMPLETED`) no último minuto | inalterado |
| 502  | Authorization service unavailable | Autorizador indisponível / timeout | inalterado |
| 500  | Internal error                 | Falha inesperada após reserva; transação marcada `FAILED` se possível | inalterado |

Replay após falha já persistida (`FAILED`) devolve o **mesmo erro** da primeira tentativa (mesmo status HTTP e mesmo `detail` estável), sem chamar o autorizador de novo e sem nova movimentação.

---

## 4. Exemplos

### 4.1 Sucesso (comum → lojista)

```http
POST /transfer
Content-Type: application/json
Idempotency-Key: 11111111-1111-1111-1111-111111111111

{
  "value": 100.0,
  "payer": "0190a1b2-c3d4-7000-8000-000000000004",
  "payee": "0190a1b2-c3d4-7000-8000-000000000015"
}
```

```http
HTTP/1.1 201 Created
Content-Type: application/json

{
  "id": "0190a1b2-c3d4-7000-8000-000000000042",
  "value": 100.00,
  "payer": "0190a1b2-c3d4-7000-8000-000000000004",
  "payee": "0190a1b2-c3d4-7000-8000-000000000015",
  "status": "COMPLETED",
  "createdAt": "2026-08-12T02:15:30.123Z",
  "completedAt": "2026-08-12T02:15:30.890Z"
}
```

### 4.2 Replay (mesma chave, mesmo body)

O cliente não recebeu a 201 por timeout e reenvia.

```http
POST /transfer
Content-Type: application/json
Idempotency-Key: 11111111-1111-1111-1111-111111111111

{
  "value": 100.0,
  "payer": "0190a1b2-c3d4-7000-8000-000000000004",
  "payee": "0190a1b2-c3d4-7000-8000-000000000015"
}
```

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": "0190a1b2-c3d4-7000-8000-000000000042",
  "value": 100.00,
  "payer": "0190a1b2-c3d4-7000-8000-000000000004",
  "payee": "0190a1b2-c3d4-7000-8000-000000000015",
  "status": "COMPLETED",
  "createdAt": "2026-08-12T02:15:30.123Z",
  "completedAt": "2026-08-12T02:15:30.890Z"
}
```

Nenhum débito extra. Mesmo `id`.

### 4.3 Mesma chave, payload diferente

```http
POST /transfer
Content-Type: application/json
Idempotency-Key: 11111111-1111-1111-1111-111111111111

{
  "value": 50.0,
  "payer": "0190a1b2-c3d4-7000-8000-000000000004",
  "payee": "0190a1b2-c3d4-7000-8000-000000000015"
}
```

```http
HTTP/1.1 409 Conflict
Content-Type: application/problem+json

{
  "type": "https://picpay-simplificado.local/problems/idempotency-key-conflict",
  "title": "Idempotency key conflict",
  "status": 409,
  "detail": "A Idempotency-Key já foi usada com outro payload",
  "instance": "/transfer"
}
```

### 4.4 Lojista como payer

```json
{
  "type": "https://picpay-simplificado.local/problems/merchant-cannot-send-money",
  "title": "Merchant cannot send money",
  "status": 403,
  "detail": "Lojistas apenas recebem transferências",
  "instance": "/transfer",
  "payer": "0190a1b2-c3d4-7000-8000-000000000015"
}
```

### 4.5 Saldo insuficiente

```json
{
  "type": "https://picpay-simplificado.local/problems/insufficient-balance",
  "title": "Insufficient balance",
  "status": 422,
  "detail": "Saldo insuficiente para concluir a transferência",
  "instance": "/transfer",
  "payer": "0190a1b2-c3d4-7000-8000-000000000004",
  "value": 100.00
}
```

### 4.6 Autorizador recusou

```json
{
  "type": "https://picpay-simplificado.local/problems/transfer-not-authorized",
  "title": "Transfer not authorized",
  "status": 403,
  "detail": "O serviço autorizador recusou a transferência",
  "instance": "/transfer"
}
```

### 4.7 Violação de política (teto / noite / diário / taxa)

```json
{
  "type": "https://picpay-simplificado.local/problems/transfer-amount-limit-exceeded",
  "title": "Transfer amount limit exceeded",
  "status": 422,
  "detail": "O valor máximo por transferência é 5000.00 neste horário",
  "instance": "/transfer",
  "value": 10000.00
}
```

Outros `type` da mesma família HTTP 422: `daily-transfer-limit-exceeded`, `transfer-rate-limit-exceeded`.

### 4.8 `Idempotency-Key` ausente

```http
POST /transfer
Content-Type: application/json

{
  "value": 100.0,
  "payer": "0190a1b2-c3d4-7000-8000-000000000004",
  "payee": "0190a1b2-c3d4-7000-8000-000000000015"
}
```

```http
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json

{
  "type": "https://picpay-simplificado.local/problems/missing-idempotency-key",
  "title": "Missing Idempotency-Key",
  "status": 400,
  "detail": "O header Idempotency-Key é obrigatório",
  "instance": "/transfer"
}
```

### 4.9 Valor inválido

```json
{
  "type": "https://picpay-simplificado.local/problems/invalid-transfer-amount",
  "title": "Invalid transfer amount",
  "status": 400,
  "detail": "O valor da transferência deve ser maior que zero",
  "instance": "/transfer",
  "value": 0
}
```

---

## 5. Endpoints propostos (não obrigatórios)

O README convida a propor endpoints extras. Não fazem parte do contrato mínimo de avaliação. Úteis para seed local, debug e entrevista.

### `GET /transactions/{id}`

Consulta uma transferência pelo id interno (UUID v7).

```http
GET /transactions/0190a1b2-c3d4-7000-8000-000000000042
```

```json
{
  "id": "0190a1b2-c3d4-7000-8000-000000000042",
  "value": 100.00,
  "payer": "0190a1b2-c3d4-7000-8000-000000000004",
  "payee": "0190a1b2-c3d4-7000-8000-000000000015",
  "status": "COMPLETED",
  "createdAt": "2026-08-12T02:15:30.123Z",
  "completedAt": "2026-08-12T02:15:30.890Z"
}
```

`404` se não existir. `400` se o path param não for UUID válido.

Como a transferência é síncrona, este endpoint **não** é necessário para o cliente saber o resultado do `POST /transfer`. Serve para consulta posterior / suporte.

### `GET /users/{id}/wallet`

Consulta saldo (útil em demo e testes).

```http
GET /users/0190a1b2-c3d4-7000-8000-000000000004/wallet
```

```json
{
  "userId": "0190a1b2-c3d4-7000-8000-000000000004",
  "balance": 900.00,
  "currency": "BRL"
}
```

`404` se usuário ou carteira não existirem.

### `POST /users` (auxiliar / seed — fora de avaliação)

O desafio **não avalia** cadastro. Um endpoint auxiliar ou um seed Flyway basta para popular comuns e lojistas. Se existir endpoint:

```json
{
  "fullName": "João Silva",
  "document": "12345678901",
  "documentType": "CPF",
  "email": "joao@example.com",
  "password": "secret",
  "type": "COMMON"
}
```

Resposta `201` com `id` UUID v7 (sem devolver a senha). Unicidade de documento e e-mail → `409`.

---

## 6. O que a API não expõe

- Entidades de domínio ou JPA.
- Hash de senha.
- Conteúdo da outbox / fila.
- Resultado do notificador (best-effort, assíncrono; falha de notify não altera o HTTP do `POST /transfer`).

---

## 7. Health (operação local)

Não faz parte do contrato de negócio. Esperado pelo Actuator:

```text
GET /actuator/health
```

Dependências: aplicação, PostgreSQL, RabbitMQ.

---

## 8. Notas para a implementação web

```text
adapter/in/web/api/           TransferApi (contrato + OpenAPI)
adapter/in/web/controller/    TransferController
adapter/in/web/dto/request/   TransferRequest
adapter/in/web/dto/response/  TransferResponse
adapter/in/web/mapper/        TransferWebMapper
adapter/in/web/exception/     GlobalExceptionHandler → ProblemDetail
```

O controller não contém regra de negócio. Converte DTO → comando interno e chama `TransferService`. Exceções de domínio viram os status da seção 3. DTOs usam `java.util.UUID`, não `Long`. PKs geradas como UUID v7.
