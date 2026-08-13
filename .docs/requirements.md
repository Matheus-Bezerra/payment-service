# Requirements — PicPay Simplificado

Define o que o sistema deve fazer: regras de negócio, fluxos, permissões e requisitos.

Fonte original: [README.md](../README.md).
Arquitetura de implementação (futura): [spring-hexagonal-architecture](../../spring-hexagonal-architecture/ARCHITECTURE.md).

---

## 1. Objetivo

O PicPay Simplificado é uma plataforma de pagamentos. Nela é possível depositar e realizar transferências de dinheiro entre usuários.

Existem dois tipos de usuário:

- **Comum (`COMMON`)**: pode enviar e receber transferências.
- **Lojista (`MERCHANT`)**: só recebe transferências; nunca envia.

Ambos possuem carteira com saldo.

O recorte avaliado pelo desafio é o **fluxo de transferência entre dois usuários**. Cadastro, autenticação e frontend estão fora de escopo de avaliação, mas o modelo de dados precisa existir para a transferência funcionar (seed ou endpoint auxiliar).

---

## 2. Regras de negócio

### RN-01 — Dados do usuário

Para ambos os tipos, o sistema precisa de:

- nome completo;
- documento (CPF ou CNPJ);
- e-mail;
- senha.

CPF/CNPJ e e-mail devem ser **únicos**. Não pode haver dois cadastros com o mesmo documento ou o mesmo e-mail.

### RN-02 — Permissões de transferência

- Usuário comum pode enviar dinheiro para outro comum ou para lojista.
- Lojista **não envia** dinheiro para ninguém.
- Payer e payee devem ser usuários distintos.
- Payer e payee devem existir e possuir carteira.

### RN-03 — Saldo

Antes de concluir a transferência, o payer precisa ter saldo suficiente. O saldo da carteira **nunca pode ficar negativo**.

### RN-04 — Valor

O valor da transferência deve ser **maior que zero**. Valores nulos, zero ou negativos são inválidos.

### RN-04.1 — Política interna de transferência (`TransferPolicy`)

Além do saldo e do autorizador externo, a transferência precisa passar por limites internos (anti-fraude / compliance). Timezone: **`America/Sao_Paulo`**.

| Código | Regra | Valor |
|--------|-------|-------|
| POL-01 | Valor máximo por transferência | R$ 20.000,00 |
| POL-02 | Entre 22:00 (inclusive) e 06:00 (exclusive), teto por transferência | R$ 5.000,00 |
| POL-03 | Limite diário por payer (soma no dia civil) | R$ 80.000,00 |
| POL-04 | Máximo de transferências do payer em 1 minuto | 5 |

Detalhes:

- POL-02 **substitui** o teto de POL-01 no horário noturno (`max = 5.000`), não soma com ele.
- POL-03 e POL-04 consideram transações do **payer** com status `IN_PROGRESS` ou `COMPLETED` (`FAILED` não consome limite).
- Dia civil de POL-03: meia-noite às 24h em `America/Sao_Paulo`.
- Janela de POL-04: `now - 60 segundos` até `now`.
- A política roda **antes** do autorizador externo (não gasta o mock se o limite interno já barra).
- Replay idempotente **não** reavalia a política: devolve o resultado já persistido.

A implementação pode nascer por partes (POL-01 → POL-02 → POL-03 → POL-04). O contrato e o domínio já consideram as quatro.

### RN-05 — Autorização externa (síncrona)

Antes de finalizar a transferência, o sistema deve consultar um autorizador externo:

```text
GET https://util.devi.tools/api/v2/authorize
```

- A consulta é **síncrona** e acontece dentro do `POST /transfer`.
- Se o autorizador recusar ou estiver indisponível, a transferência **não é concluída** e nenhum saldo é alterado de forma persistente.
- O cliente recebe o resultado final na resposta HTTP (não há polling).

### RN-06 — Atomicidade da transferência

A operação de débito/crédito deve ser uma **transação de banco**:

- débito na carteira do payer e crédito na carteira do payee acontecem juntos;
- qualquer inconsistência reverte o débito;
- o dinheiro volta (ou nunca sai) da carteira de quem envia.

A autorização externa **não** deve ocorrer com locks de carteira abertos: valida-se e autoriza-se primeiro; o débito/crédito ocorre depois, sob lock, revalidando o saldo.

### RN-07 — Notificação (assíncrona, best-effort)

No recebimento de pagamento, o payee (comum ou lojista) precisa ser notificado por um serviço de terceiro (e-mail/SMS). Esse serviço pode estar indisponível ou instável:

```text
POST https://util.devi.tools/api/v1/notify
```

Regras:

- A notificação **não faz parte** da transação de dinheiro.
- A transferência já commitada **não é revertida** se a notificação falhar.
- A notificação é publicada em **fila RabbitMQ** após o commit e processada por um worker com retry, backoff e DLQ.

### RN-08 — Idempotência da transferência

O cliente pode reenviar o mesmo `POST /transfer` (timeout, latência, retry). O sistema não pode debitar duas vezes a mesma tentativa lógica.

- O header `Idempotency-Key` é **obrigatório** (UUID gerado **uma vez** por tentativa lógica e reenviado igual nos retries). Ausente ou em branco → `400`, a transferência nem começa.
- A garantia é atômica no banco: reserva-se a chave com `INSERT` + `UNIQUE` em `transactions.idempotency_key` (`NOT NULL`) e status `IN_PROGRESS`, depois processa.
- Replay da mesma chave + mesmo payload devolve a **mesma resposta** já persistida.
- Mesma chave + payload diferente (`value`, `payer` ou `payee`) é erro de uso da chave.

### RN-09 — Serviço REST

A API é RESTful. O contrato obrigatório é `POST /transfer`. Identificadores (`payer`, `payee`, `id` da transação) são **UUID v7**, não inteiros — proposta em relação ao enunciado original. Detalhes em [api-spec.md](api-spec.md).

---

## 3. Permissões (resumo)

| Ação                         | Comum | Lojista |
|------------------------------|-------|---------|
| Receber transferência        | sim   | sim     |
| Enviar transferência (payer) | sim   | não     |
| Possuir carteira             | sim   | sim     |

Autenticação de usuário final **não** é requisito deste recorte.

---

## 4. Fluxo principal — transferência

```text
Cliente
  → POST /transfer (Idempotency-Key obrigatório)
  → reserva atômica da chave (INSERT IN_PROGRESS) OU espera/replay
  → valida payer, payee, tipos, valor
  → TransferPolicy (limites internos)
  → consulta autorizador externo (síncrono)
  → transação de banco:
       lock das carteiras (FOR UPDATE)
       revalida saldo
       debita payer / credita payee
       atualiza Transaction para COMPLETED
       grava registro de outbox (TransferCompleted)
  → COMMIT
  → responde HTTP com o resultado final
  → OutboxPublisher publica TransferCompleted no RabbitMQ
       → worker de notificação consome
       → chama POST /notify (retry / backoff / DLQ)
```

### 4.1 Passo a passo

1. **Entrada HTTP** — recebe `value`, `payer`, `payee` e `Idempotency-Key` (obrigatório). Sem a chave → `400`.
2. **Reserva de idempotência**:
   - tenta `INSERT` da transação com status `IN_PROGRESS`;
   - se a unique constraint falhar:
     - status `COMPLETED` ou `FAILED` → replay da mesma resposta (se o payload bater);
     - status `IN_PROGRESS` → aguarda lock de linha (`SELECT ... FOR UPDATE`) até a primeira request terminar e devolve o mesmo resultado;
     - payload diferente → erro 409.
3. **Validações de negócio** — usuários existem, payer não é lojista, payer ≠ payee, valor > 0.
4. **TransferPolicy** — teto por valor, horário, limite diário e taxa (snapshot carregado pela application). Violação → `FAILED`, sem chamar o autorizador.
5. **Autorizador** — `GET /api/v2/authorize`. Recusa ou indisponibilidade → marca transação `FAILED` e responde erro. Nenhum saldo é alterado.
6. **Movimentação** — em uma única transação de banco, lock das duas carteiras, revalida saldo, debita/credita, marca `COMPLETED`, grava outbox.
7. **Resposta ao cliente** — sucesso ou falha síncrona. Sem polling.
8. **Notificação** — fora da transação de dinheiro. Worker consome a fila e chama o notificador. Falha de notificação não desfaz a transferência.

### 4.2 Onde a fila entra (e onde não entra)

A fila **não** processa a transferência. Débito, crédito e autorização acontecem no request HTTP.

A fila existe só para **desacoplar a notificação**, que é instável e não pode bloquear nem reverter o pagamento.

### 4.3 Fluxos de erro

| Situação                                      | Efeito no saldo | Status da transação | HTTP típico |
|-----------------------------------------------|-----------------|---------------------|-------------|
| Payload inválido (valor ≤ 0, campos ausentes) | nenhum          | não persiste / FAILED | 400       |
| `Idempotency-Key` ausente ou em branco        | nenhum          | não persiste          | 400         |
| Payer ou payee inexistente                    | nenhum          | FAILED              | 404         |
| Lojista como payer                            | nenhum          | FAILED              | 403         |
| Payer = payee                                 | nenhum          | FAILED              | 400         |
| Saldo insuficiente                            | nenhum          | FAILED              | 422         |
| Violação de TransferPolicy (teto, horário, diário, taxa) | nenhum | FAILED | 422 |
| Autorizador recusou ou indisponível           | nenhum          | FAILED              | 403 / 502   |
| Mesma Idempotency-Key + payload diferente     | nenhum          | inalterado          | 409         |
| Mesma chave em replay após sucesso/falha      | nenhum extra    | inalterado          | mesmo da 1ª |
| Notificador indisponível                      | já commitado    | COMPLETED           | 2xx já enviado; retry na fila |

---

## 5. Fora de escopo (não avaliado pelo desafio)

- Fluxo de cadastro de usuários e lojistas (seed ou endpoint auxiliar apenas para viabilizar testes).
- Frontend.
- Autenticação e autorização de API (JWT, OAuth, etc.).
- Depósito na carteira como produto (mencionado no objetivo; não é o recorte de avaliação).

---

## 6. Requisitos não funcionais

### NFR-01 — Idempotência

Evitar débito duplicado em retry do cliente. A garantia vem da constraint `UNIQUE` no banco, não de um check-then-insert na aplicação (evita race condition TOCTOU quando a internet do cliente está lenta e várias requests concorrentes chegam com a mesma chave).

### NFR-02 — Concorrência de saldo

Duas transferências simultâneas do mesmo payer não podem ultrapassar o saldo. Usar lock de linha nas carteiras (`SELECT ... FOR UPDATE`) dentro da transação de movimentação.

### NFR-03 — Resiliência de integrações

- **Autorizador**: síncrono; timeout curto; falha = transferência não concluída.
- **Notificador**: assíncrono via RabbitMQ; retry com backoff; DLQ após esgotar tentativas; métrica/alerta na DLQ.

### NFR-04 — Consistência evento × dinheiro

Alteração de saldo e registro de outbox (`TransferCompleted`) na **mesma transação**. O publisher lê a outbox depois do commit e publica no RabbitMQ. Evita perder a notificação se o processo cair entre o commit e o `publish`.

### NFR-05 — Observabilidade básica

- logs em stdout sem senha, documento completo desnecessário ou tokens;
- `correlationId` por request;
- health checks (app, banco, broker).

### NFR-06 — Stack alvo

- Java + Spring Boot (arquitetura hexagonal pragmática).
- PostgreSQL + Flyway.
- RabbitMQ para a fila de notificação.
- Docker Compose para ambiente local.

---

## 7. Integrações externas

| Integração  | Método | URL                                         | Momento              | Falha |
|-------------|--------|---------------------------------------------|----------------------|-------|
| Autorizador | GET    | `https://util.devi.tools/api/v2/authorize`  | Antes do débito/crédito | Aborta transferência |
| Notificador | POST   | `https://util.devi.tools/api/v1/notify`     | Após commit, via fila   | Retry / DLQ; não reverte saldo |

---

## 8. Relação com os demais documentos

| Documento | Papel |
|-----------|--------|
| [domain-model.md](domain-model.md) | Como o negócio é modelado (entidades, invariantes, eventos). |
| [database-design.md](database-design.md) | Como os dados são persistidos (tabelas, constraints, locks). |
| [api-spec.md](api-spec.md) | Contrato HTTP com o cliente. |
