# Sistema Hospitalar — Tech Challenge Fase 3

POS TECH — Arquitetura e Desenvolvimento Java.

Backend modular para um ambiente hospitalar: **agendamento de consultas**,
**histórico de pacientes** e **lembretes automáticos**, com foco em
**segurança** (autenticação/autorização por perfil) e **comunicação
assíncrona** entre serviços.

## Arquitetura

Três aplicações Spring Boot independentes (cada uma com seu `pom.xml`,
testes, `Dockerfile`, `docker-compose.yml` de dev e README próprio),
integradas por eventos no RabbitMQ:

```
                          HTTP Basic (perfis: MEDICO / ENFERMEIRO / PACIENTE)
                                            │
            ┌───────────────────────────────┼───────────────────────────────┐
            ▼                               ▼                               ▼
┌───────────────────────┐      evento consulta.criada        ┌────────────────────────┐
│  Serviço Agendamento  │      / consulta.editada            │  Serviço Notificações  │
│   REST + Spring       │ ───▶ RabbitMQ ──────────────────▶  │  consumer + lembrete   │
│   Security (8080)     │   exchange agendamento.consultas   │  + retry/DLQ (8082)    │
└───────────┬───────────┘   (topic, durável)  │              │  + lembrete agendado   │
            │                                 │              └────────────────────────┘
            │                                 ▼
            │                     ┌────────────────────────┐
            │                     │  Serviço Histórico     │
            └──── (mesmos eventos)│  read model + GraphQL  │
                                  │  (8083, /graphql)      │
                                  └────────────────────────┘
```

| Serviço | Pasta | Porta | Responsabilidade |
|---|---|---|---|
| **Agendamento** | [`agendamento/`](agendamento/README.md) | 8080 | Spring Security (Basic Auth) + autorização por perfil; CRUD de consultas com regras de negócio (paciente/profissional válidos, conflito de horário); **publica** `consulta.criada` / `consulta.editada` |
| **Notificações** | [`notificacoes/`](notificacoes/README.md) | 8082 | **Consome** os eventos e registra o lembrete ao paciente; lembrete de proximidade agendado (`@Scheduled`); retry + Dead Letter Queue |
| **Histórico** | [`historico/`](historico/README.md) | 8083 | **Consome** os eventos, projeta um read model e expõe **GraphQL** (`historicoPaciente`, `consultasFuturas`) com as mesmas regras de acesso por perfil |

O Serviço de Histórico é o serviço "opcional" do enunciado; foi implementado
como serviço separado (consumindo os mesmos eventos) para exercitar de fato a
separação em serviços e a comunicação assíncrona.

### Contrato do evento (RabbitMQ)

| | |
|---|---|
| Exchange | `agendamento.consultas` (topic, durável) |
| Routing key — nova consulta | `consulta.criada` |
| Routing key — consulta editada | `consulta.editada` |
| Payload | JSON — `eventoId`, `tipoEvento`, `ocorreEm`, `consultaId`, `dataHora`, `status`, `pacienteId/Nome/Email`, `profissionalId/Nome`, `observacoes` |

Detalhes e exemplo de payload: [`agendamento/README.md`](agendamento/README.md#eventos-publicados-rabbitmq).
Cada consumidor tem sua própria fila (`notificacoes.lembretes`,
`historico.consultas`) com binding `consulta.*` e uma DLQ associada.

## Perfis e regras de acesso

Usuários semente criados no primeiro start do Agendamento (`DemoUsersSeeder`,
credenciais sobrescrevíveis por variáveis `SEED_*`):

| Perfil | E-mail (login) | Senha (default) | Pode |
|---|---|---|---|
| Médico | `medico@hospital.com` | `medico123` | editar consultas (`PUT`), ver histórico completo, GraphQL de qualquer paciente |
| Enfermeiro | `enfermeiro@hospital.com` | `enfermeiro123` | registrar consultas (`POST`), ver histórico completo, GraphQL de qualquer paciente |
| Paciente 1 | `paciente1@hospital.com` | `paciente123` | ver só as próprias consultas (`GET /consultas/me`, `GET /consultas/{id}` própria), GraphQL só do próprio histórico |
| Paciente 2 | `paciente2@hospital.com` | `paciente123` | idem |

Sem credenciais → `401`; autenticado sem permissão → `403` (JSON padronizado
em todos os serviços).

## Endpoints

### Agendamento (REST) — `http://localhost:8080`

| Método | Rota | Médico | Enfermeiro | Paciente |
|---|---|:---:|:---:|:---:|
| `POST` | `/consultas` | ❌ | ✅ | ❌ |
| `PUT` | `/consultas/{id}` | ✅ | ❌ | ❌ |
| `GET` | `/consultas` | ✅ | ✅ | ❌ |
| `GET` | `/consultas/{id}` | ✅ | ✅ | ✅ (só a própria) |
| `GET` | `/consultas/me` | ❌ | ❌ | ✅ |

### Notificações (REST, leitura) — `http://localhost:8082`

`GET /notificacoes` · `GET /notificacoes/{id}` · `GET /notificacoes/paciente/{pacienteId}`

### Histórico (GraphQL) — `http://localhost:8083/graphql` (GraphiQL em `/graphiql`)

```graphql
{ historicoPaciente(pacienteId: 3) { consultaId dataHora status profissionalNome } }
{ consultasFuturas(pacienteId: 3) { consultaId dataHora status } }
```

Para o perfil `PACIENTE`, `pacienteId` é ignorado (retorna sempre o próprio
histórico); pedir o id de outro paciente → erro `FORBIDDEN`.

## Como rodar (ambiente completo)

Pré-requisitos: Docker + Docker Compose. (Para build/test local: Java 21 e o
Maven Wrapper de cada módulo.)

```bash
docker compose up --build
```

Sobe **1 Postgres** (3 bancos, criados por [`db/init/`](db/init/)), **1
RabbitMQ** (UI em `http://localhost:15672`, `guest`/`guest`) e os **3
serviços**. Todas as credenciais/portas são variáveis de ambiente (ver
`docker-compose.yml` e o `.env` opcional).

### Validar o fluxo ponta a ponta

Com o ambiente no ar:

```bash
./scripts/e2e.sh
```

O script cobre: autenticação por perfil → enfermeiro registra consulta →
evento assíncrono → lembrete em Notificações → projeção no Histórico
(GraphQL) → médico edita para `REALIZADA` → Histórico reflete o novo status
→ paciente bloqueado ao consultar histórico de outro paciente.

### Rodar um serviço isolado

Cada pasta tem seu próprio `docker-compose.yml` (infra dedicada) — útil para
desenvolver aquele módulo sozinho. Só o compose da raiz integra os três com
broker/banco compartilhados.

## Testes e cobertura

```bash
cd agendamento  && ./mvnw test
cd notificacoes && ./mvnw test
cd historico    && ./mvnw test
```

Todos usam H2 em memória (sem Postgres/RabbitMQ reais). Cada módulo gera o
relatório JaCoCo em `target/site/jacoco/index.html`. Destaques:

- **Agendamento** — matriz perfil × endpoint (`SecurityAuthorizationMatrixTest`),
  regras de negócio (`ConsultaServiceTest`, `ConsultaBusinessRulesIntegrationTest`),
  publicação de evento e tolerância a falha do broker.
- **Notificações** — consumo idempotente, roteamento de mensagem malformada
  para a DLQ (`NotificacaoListenerTest`), lembrete de proximidade
  (`LembreteProximidadeSchedulerTest`).
- **Histórico** — projeção e idempotência (`HistoricoServiceTest`), parsing +
  DLQ (`ConsultaEventoListenerTest`), matriz perfil × query GraphQL e
  integração evento → GraphQL (`HistoricoGraphQlControllerTest`,
  `HistoricoFluxoEventoParaGraphQlTest`).

## Collections (Postman)

| Serviço | Arquivo |
|---|---|
| Agendamento | [`agendamento/postman/agendamento.postman_collection.json`](agendamento/postman/agendamento.postman_collection.json) |
| Notificações | [`notificacoes/postman/notificacoes.postman_collection.json`](notificacoes/postman/notificacoes.postman_collection.json) |
| Histórico | [`historico/postman/historico.postman_collection.json`](historico/postman/historico.postman_collection.json) |

## Estrutura do repositório

```
agendamento/      Serviço de Agendamento (REST + Spring Security + producer)
notificacoes/     Serviço de Notificações (consumer + @Scheduled + DLQ)
historico/        Serviço de Histórico (consumer + read model + GraphQL)
db/init/          Script de criação dos 3 bancos (usado pelo compose da raiz)
scripts/e2e.sh    Teste ponta a ponta do fluxo completo
docs/             Relatório final e notas de code review
docker-compose.yml  Ambiente integrado (Postgres + RabbitMQ + 3 serviços)
```

## Documentação adicional

- [`docs/RELATORIO-FINAL.md`](docs/RELATORIO-FINAL.md) — arquitetura,
  decisões técnicas, mensageria e mapeamento aos critérios de avaliação.
- [`docs/CODE-REVIEW.md`](docs/CODE-REVIEW.md) — checklist de revisão geral
  (nomenclatura, organização de pacotes, boas práticas Spring Boot).
