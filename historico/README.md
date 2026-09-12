# Histórico — Consultas Médicas via GraphQL

Tech Challenge Fase 3 (POS TECH — Arquitetura e Desenvolvimento Java).
Este é o **Serviço de Histórico**: mantém uma projeção (read model) das
consultas, alimentada pelos eventos `consulta.criada` / `consulta.editada`
publicados pelo Serviço de Agendamento no RabbitMQ, e disponibiliza esse
histórico por uma **API GraphQL** com consultas flexíveis (todos os
atendimentos de um paciente, ou apenas os futuros).

> Contrato do evento consumido: documentado em
> [`../agendamento/README.md`](../agendamento/README.md), seção "Eventos
> publicados (RabbitMQ)". Este serviço mantém uma cópia local do payload
> (`ConsultaEventoPayload`) porque os serviços evoluem sem biblioteca
> compartilhada — mesmo padrão do Serviço de Notificações.

## Arquitetura

```
RabbitMQ — exchange agendamento.consultas (topic, durável)
                │  routing keys: consulta.criada / consulta.editada
                ▼
     fila historico.consultas (binding: consulta.*)     ──falha──▶ historico.consultas.dlq
                │
                ▼
    ConsultaEventoListener ──▶ HistoricoService ──▶ AtendimentoRepository
                                                            │
                                                            ▼
                                                   Postgres (hospital_historico)
                                                            │
                                                            ▼
                            HistoricoGraphQlController  (POST /graphql, GET /graphiql)
```

- **Read model**: a entidade `Atendimento` é chaveada pelo `consultaId` de
  origem. `consulta.criada` insere; `consulta.editada` atualiza a mesma
  linha — o histórico reflete sempre o estado corrente da consulta.
- **Idempotência / ordenação**: cada `Atendimento` guarda o `ultimoEventoId`
  e o `ocorreEm` aplicados. Reentrega do mesmo evento, ou um evento mais
  antigo que o último aplicado, é ignorada.
- **Tolerância a falha no consumo**: mensagem malformada é rejeitada sem
  requeue e vai direto para a DLQ (`historico.consultas.dlq`); falha
  transitória (ex.: banco fora) é retentada 3× com backoff exponencial e,
  esgotadas as tentativas, também cai na DLQ.
- **Autenticação**: HTTP Basic no endpoint GraphQL. O serviço não tem tabela
  de usuários própria — usa um `InMemoryUserDetailsManager` com as mesmas
  credenciais semente do Serviço de Agendamento, injetadas pelas mesmas
  variáveis `SEED_*` (o `docker-compose.yml` da raiz define uma vez só).

## Modelo de domínio

| Entidade | Campos | Observações |
|---|---|---|
| `Atendimento` | consultaId (PK), pacienteId, pacienteNome, pacienteEmail, profissionalId, profissionalNome, dataHora, status, observacoes, ultimoEventoId, ocorreEm, atualizadoEm | `status`: `AGENDADA`, `REALIZADA`, `CANCELADA` |

## API GraphQL

- **Endpoint**: `POST /graphql`
- **GraphiQL** (UI interativa): `GET /graphiql` (habilitado por
  `GRAPHIQL_ENABLED`, default `true`)
- **Schema**: [`src/main/resources/graphql/schema.graphqls`](src/main/resources/graphql/schema.graphqls)

### Queries

| Query | Argumentos | Retorno | Descrição |
|---|---|---|---|
| `historicoPaciente` | `pacienteId: ID` | `[Atendimento!]!` | Todos os atendimentos (passados e futuros) de um paciente, ordenados por data/hora |
| `consultasFuturas` | `pacienteId: ID` | `[Atendimento!]!` | Apenas os atendimentos com `dataHora > agora` |

### Regras de acesso (mesmas do enunciado)

| Perfil | Comportamento |
|---|---|
| `MEDICO` / `ENFERMEIRO` | Consultam o histórico de qualquer paciente. `pacienteId` é obrigatório. |
| `PACIENTE` | Consulta **apenas o próprio** histórico. O `pacienteId` é resolvido pelo usuário autenticado (e-mail → `pacienteId` via read model); informar o id de outro paciente resulta em erro `FORBIDDEN`. |

Requisição sem credenciais → `401`. Erros de regra viram erros GraphQL com
`extensions.classification` = `FORBIDDEN` ou `BAD_REQUEST`
(ver `GraphQlExceptionResolver`).

### Exemplo (curl)

```bash
# Médico: todos os atendimentos do paciente 3
curl -u medico@hospital.com:medico123 -H 'Content-Type: application/json' \
  -d '{"query":"{ historicoPaciente(pacienteId: 3) { consultaId dataHora status profissionalNome } }"}' \
  http://localhost:8083/graphql

# Paciente: só o próprio histórico futuro (pacienteId ignorado)
curl -u paciente1@hospital.com:paciente123 -H 'Content-Type: application/json' \
  -d '{"query":"{ consultasFuturas { consultaId dataHora status } }"}' \
  http://localhost:8083/graphql
```

## Configuração e execução

### Pré-requisitos

- Java 21
- PostgreSQL 14+ e um broker RabbitMQ acessível (o mesmo do Serviço de
  Agendamento, para receber eventos reais)

### Variáveis de ambiente

| Variável | Default | Descrição |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/hospital_historico` | URL JDBC |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres` | credenciais do banco |
| `JPA_DDL_AUTO` | `update` | estratégia do Hibernate (`validate` em produção) |
| `SERVER_PORT` | `8083` | porta HTTP |
| `GRAPHIQL_ENABLED` | `true` | expõe a UI GraphiQL em `/graphiql` |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | broker RabbitMQ |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | `guest` / `guest` | credenciais do broker |
| `RABBITMQ_EXCHANGE` | `agendamento.consultas` | precisa bater com o exchange do Agendamento |
| `RABBITMQ_QUEUE` | `historico.consultas` | fila deste serviço |
| `RABBITMQ_ROUTING_KEY_PATTERN` | `consulta.*` | binding — cobre criação e edição |
| `RABBITMQ_DLX` / `RABBITMQ_DLQ` | `historico.consultas.dlx` / `historico.consultas.dlq` | dead-letter |
| `SEED_MEDICO_EMAIL` / `SEED_MEDICO_SENHA` … | ver Agendamento | credenciais Basic aceitas pelo GraphQL |

### Rodando isoladamente (Postgres + RabbitMQ próprios)

```bash
docker compose up
```

Sobe Postgres (`5436`), RabbitMQ (AMQP `5676`, UI `15676`) e a aplicação
(`8083`). Nesse modo o serviço não recebe eventos reais do Agendamento
(brokers diferentes) — use o `docker-compose.yml` da **raiz** do repositório
para o fluxo ponta a ponta.

### Rodando os testes

```bash
./mvnw test
```

Usa H2 em memória; não depende de Postgres/RabbitMQ reais. O relatório de
cobertura (JaCoCo) fica em `target/site/jacoco/index.html`.

## Collection para teste

`postman/historico.postman_collection.json` — importe no Postman (variável
`base_url`, default `http://localhost:8083`). Cobre as duas queries por
perfil, o bloqueio do paciente e o caso `401`.

## Testes automatizados

- `HistoricoServiceTest` — projeção: insert em `consulta.criada`, update em
  `consulta.editada`, idempotência (reentrega e evento fora de ordem).
- `ConsultaEventoListenerTest` — parsing do payload cru e roteamento de
  mensagem malformada para a DLQ.
- `HistoricoGraphQlControllerTest` — matriz perfil × query (médico/enfermeiro
  em qualquer paciente, paciente só o próprio, `BAD_REQUEST` sem `pacienteId`,
  `FORBIDDEN` ao pedir outro paciente, `401` sem autenticação).
- `HistoricoFluxoEventoParaGraphQlTest` — integração dentro do serviço:
  evento entra pelo listener e passa a ser visível na query GraphQL
  (projeção + leitura + autorização).

## Decisões e limitações conhecidas

- **Serviço separado consumindo eventos** (em vez de um módulo GraphQL dentro
  do Agendamento): o enunciado descreve os 3 serviços e o evento assíncrono
  já carrega todos os dados da consulta, então a projeção é natural e de
  baixo risco — mesmo padrão já validado pelo Serviço de Notificações. Custo:
  consistência eventual (a projeção reflete o estado depois que o evento é
  consumido).
- **Sem `spring-graphql` subscriptions / mutations**: o enunciado pede
  *consultas* flexíveis sobre o histórico; a escrita de consultas continua no
  Serviço de Agendamento (REST + Security).
- **Autenticação in-memory**: sem banco de usuários próprio. Suficiente para
  aplicar as regras de acesso por perfil do enunciado sem duplicar o cadastro
  de usuários entre serviços.
