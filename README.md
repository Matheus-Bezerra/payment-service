# PicPay Simplificado

Implementação do desafio back-end [PicPay Simplificado](https://github.com/PicPay/picpay-desafio-backend): plataforma de pagamentos com transferência entre usuários comuns e lojistas.

**Stack deste repositório:** Java, Spring Boot, Maven, PostgreSQL.

O recorte principal é o fluxo de **`POST /transfer`**. Cadastro, autenticação e frontend não entram na avaliação do desafio (seed de usuários basta).

Documentação de regras, modelo, schema e contrato HTTP: [`.docs/`](.docs/).

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

{
  "value": 100.0,
  "payer": 4,
  "payee": 15
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
