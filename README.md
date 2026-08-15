# PicPay Simplificado

Implementação do desafio back-end [PicPay Simplificado](https://github.com/PicPay/picpay-desafio-backend): plataforma de pagamentos com transferência entre usuários comuns e lojistas.

**Stack deste repositório:** Java, Spring Boot, Maven, PostgreSQL, RabbitMQ.

O recorte principal é o fluxo de **`POST /transfer`**. Cadastro, autenticação e frontend não entram na avaliação do desafio (seed de usuários basta). Notificação ao payee é assíncrona (outbox + RabbitMQ) e não faz parte da resposta HTTP.

Documentação de regras, modelo, schema, contrato HTTP e checklist de implementação: [`.docs/`](.docs/).

**Stack de implementação:** Java 25, Spring Boot 4.1, Maven, PostgreSQL 18, RabbitMQ 4, Docker Compose.

---

## Como executar

### Tudo no Docker

```bash
docker compose up --build
```

Sobe PostgreSQL (`postgres:18`), RabbitMQ (`rabbitmq:4-management`) e a API na porta `8080`.
O painel do RabbitMQ fica em `http://localhost:15672` (usuário/senha `payment`/`payment`) — só para uso local.

### Só o banco e o broker no Docker (API no IDE / Maven)

```bash
docker compose up postgres rabbitmq
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

### Health check

```bash
curl -s http://localhost:8080/actuator/health
```

Esperado: status `UP` (com detalhe `db` e `rabbit` `UP` quando Postgres e RabbitMQ estiverem acessíveis).

### Swagger

Com a API no ar, a UI fica em [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) (spec OpenAPI em `/v3/api-docs`).

Dá para testar o `POST /transfer` pelo **Try it out**, usando os IDs da tabela abaixo. O header `Idempotency-Key` é obrigatório: **gere uma UUID nova a cada tentativa** (no Swagger, não reutilize a chave de exemplo). A mesma chave + o mesmo body é replay do resultado já gravado — se a primeira falhou com 502, as próximas com a mesma chave também voltam 502, sem chamar o autorizador de novo.

O mock `GET https://util.devi.tools/api/v2/authorize` é instável de propósito (às vezes 200, às vezes 5xx) e o certificado TLS dele está expirado. No Docker e no profile `local` a API ignora essa verificação (`PAYMENT_HTTP_SSL_VERIFY=false`) só para conseguir falar com o mock. Timeout: 2s. Lojista não envia — `payer` precisa ser `COMMON`.

### Usuários de seed

Quatro usuários entram no boot **só no ambiente local** (`payment.seed.enabled=true`): Docker Compose (`PAYMENT_SEED_ENABLED=true`) e IDE com `SPRING_PROFILES_ACTIVE=local`. Em UAT/prod o default é `false` — o Flyway cria só o schema (V1–V4). Sem endpoint de cadastro. Senha local de todos: `123456` (no banco só o hash BCrypt). Restart não reseta saldo já movimentado.

| Papel | Nome | User ID | E-mail | Saldo |
|-------|------|---------|--------|-------|
| COMMON (payer) | João Exemplo | `0190a1b2-c3d4-7000-8000-000000000004` | `joao.comum@example.com` | `50000.00` |
| COMMON | Matheus | `0190a1b2-c3d4-7000-8000-000000000006` | `matheus@example.com` | `100000.00` |
| MERCHANT (payee) | Loja Exemplo | `0190a1b2-c3d4-7000-8000-000000000015` | `loja@example.com` | `10000.00` |
| MERCHANT | Mercado Exemplo | `0190a1b2-c3d4-7000-8000-000000000016` | `mercado@example.com` | `10000.00` |

Exemplo de transferência (João → Loja). `uuidgen` evita reusar a mesma chave:

```bash
curl -s -X POST http://localhost:8080/transfer \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "value": 100.0,
    "payer": "0190a1b2-c3d4-7000-8000-000000000004",
    "payee": "0190a1b2-c3d4-7000-8000-000000000015"
  }'
```

Checklist de etapas: [`.docs/implementation-checklist.md`](.docs/implementation-checklist.md).

---

## Objetivo

O PicPay Simplificado é uma plataforma de pagamentos simplificada. Nela é possível depositar e realizar transferências de dinheiro entre usuários. Há 2 tipos de usuário — comuns e lojistas — ambos com carteira; as transferências ocorrem entre eles conforme as regras abaixo.

## Requisitos

- Para ambos os tipos: nome completo, CPF/CNPJ, e-mail e senha. CPF/CNPJ e e-mails devem ser únicos.
- Usuários comuns podem enviar dinheiro para lojistas e para outros usuários.
- Lojistas só recebem transferências; não enviam dinheiro.
- Validar saldo do pagador antes da transferência.
- Antes de finalizar, consultar o autorizador externo: `GET https://util.devi.tools/api/v2/authorize`.
- A transferência deve ser transacional: em inconsistência, o dinheiro volta para a carteira de quem envia.
- No recebimento, notificar o destinatário via serviço de terceiro (e-mail/SMS), que pode estar indisponível. Mock: `POST https://util.devi.tools/api/v1/notify`.
- O serviço deve ser RESTful.

## Endpoint de transferência

O desafio avalia o fluxo de transferência entre dois usuários:

```http
POST /transfer
Content-Type: application/json
Idempotency-Key: <gere uma UUID nova a cada tentativa>

{
  "value": 100.0,
  "payer": "0190a1b2-c3d4-7000-8000-000000000004",
  "payee": "0190a1b2-c3d4-7000-8000-000000000015"
}
```

## O que não será avaliado

- Fluxo de cadastro de usuários e lojistas
- Frontend (apenas a API REST)
- Autenticação

## O que o desafio valoriza

- REST, Git, código limpo e organizado
- SOLID, design patterns quando fizer sentido
- Containers (Docker etc.)
- Documentação e manuseio do projeto
- Testes de unidade e integração
- Banco relacional, modelagem e tratamento de erros
- Observabilidade, CI e boas práticas da stack escolhida
- Saber argumentar escolhas e propor melhorias

## Materiais úteis

- [Sobre o PicPay](https://picpay.com/site/sobre-nos)
- [Design patterns](https://refactoring.guru/)
- [Tipos de teste](https://www.atlassian.com/continuous-delivery/software-testing/types-of-software-testing)
- [Microsserviços (Martin Fowler)](https://martinfowler.com/articles/microservices.html)
- [REST](https://www.devmedia.com.br/rest-tutorial/28912)
