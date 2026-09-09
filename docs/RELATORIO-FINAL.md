# Relatório Final — Tech Challenge Fase 3

**Curso:** POS TECH — Arquitetura e Desenvolvimento Java
**Tema:** Sistema hospitalar — segurança, GraphQL e comunicação assíncrona
**Repositório:** https://github.com/postech-adjt-equipe1/TECH-CHALLENGE-FASE-03

---

## 1. Visão geral

O sistema é um backend hospitalar dividido em três serviços Spring Boot
independentes, integrados por eventos assíncronos no RabbitMQ:

| Serviço | Porta | Papel |
|---|---|---|
| Agendamento | 8080 | Autenticação/autorização por perfil + CRUD de consultas + **produtor** de eventos |
| Notificações | 8082 | **Consumidor** de eventos → lembretes ao paciente + lembrete de proximidade agendado |
| Histórico | 8083 | **Consumidor** de eventos → read model + **API GraphQL** do histórico |

Fluxo principal: o enfermeiro registra uma consulta no Agendamento → um
evento `consulta.criada` é publicado após o commit da transação →
Notificações registra o lembrete e Histórico projeta a consulta → o médico
edita a consulta (`consulta.editada`) → ambos os consumidores reagem → o
paciente consulta seu histórico via GraphQL.

## 2. Arquitetura

```
                        HTTP Basic (MEDICO / ENFERMEIRO / PACIENTE)
                                          │
        ┌─────────────────────────────────┼─────────────────────────────────┐
        ▼                                 ▼                                 ▼
┌─────────────────┐   consulta.criada / consulta.editada   ┌──────────────────────┐
│  Agendamento    │ ──▶ RabbitMQ: exchange ───────────────▶ │  Notificações        │
│  REST+Security  │     agendamento.consultas (topic)      │  fila .lembretes     │
│  producer       │            │                            │  retry + DLQ         │
└────────┬────────┘            │                            │  @Scheduled          │
         │ Postgres            ▼                            └──────────────────────┘
         │            ┌──────────────────────┐
         │            │  Histórico           │
         └───(evento)─│  fila .consultas     │
                      │  read model + GraphQL│
                      └──────────────────────┘
```

**Por que 3 serviços.** O enunciado exige separação em mais de um serviço e
comunicação assíncrona; o serviço de histórico é "opcional". Optou-se por
implementá-lo como serviço separado porque o evento já carrega todos os
dados da consulta — a projeção é barata e replica um padrão já validado
(Notificações). Isso demonstra de fato a arquitetura orientada a eventos.

**Comunicação assíncrona.** Um único *topic exchange* durável
(`agendamento.consultas`). Cada consumidor declara a própria fila durável com
binding `consulta.*` (cobre criação e edição) e uma *dead letter queue*.
O produtor publica com `@TransactionalEventListener(AFTER_COMMIT)` — nunca
notifica uma consulta cujo `save` sofreu rollback.

**Resiliência do consumo.**
- Idempotência: cada evento tem `eventoId` (UUID); reentrega é detectada e
  ignorada (Notificações persiste o `eventoId`; Histórico guarda o
  `ultimoEventoId`/`ocorreEm` na projeção e ignora evento repetido ou fora de
  ordem).
- Retry: 3 tentativas com backoff exponencial (`spring.rabbitmq.listener.simple.retry.*`).
- DLQ: mensagem malformada (falha permanente) é rejeitada sem requeue e vai
  direto à DLQ; falha transitória vai à DLQ após esgotar o retry.

## 3. Segurança

- **Autenticação**: HTTP Basic. Agendamento valida contra usuários em
  Postgres (`CustomUserDetailsService`, senhas BCrypt); Histórico usa
  `InMemoryUserDetailsManager` com as mesmas credenciais semente (variáveis
  `SEED_*`) para não duplicar o cadastro entre serviços.
- **Autorização**: regras por método/rota em `SecurityConfig` +
  `@EnableMethodSecurity`. Regras que dependem de dados (paciente só vê a
  própria consulta / o próprio histórico) são validadas em runtime no serviço
  e resultam em `403` / erro GraphQL `FORBIDDEN`.
- **Erros padronizados**: `401`, `403`, `400`, `404` retornam o mesmo
  formato JSON no Agendamento; no Histórico, erros GraphQL trazem
  `extensions.classification`.
- **Configuração externalizada**: nenhuma senha fixa em código; tudo por
  variável de ambiente com default de desenvolvimento.

### Matriz perfil × operação

| Operação | Médico | Enfermeiro | Paciente |
|---|:---:|:---:|:---:|
| `POST /consultas` | ❌ | ✅ | ❌ |
| `PUT /consultas/{id}` | ✅ | ❌ | ❌ |
| `GET /consultas` (histórico completo) | ✅ | ✅ | ❌ |
| `GET /consultas/{id}` | ✅ | ✅ | ✅ (só a própria) |
| `GET /consultas/me` | ❌ | ❌ | ✅ |
| GraphQL `historicoPaciente` / `consultasFuturas` | ✅ (qualquer) | ✅ (qualquer) | ✅ (só o próprio) |

## 4. GraphQL (histórico)

- Endpoint `POST /graphql`; GraphiQL em `/graphiql`.
- Schema: `historico/src/main/resources/graphql/schema.graphqls`.
- Queries:
  - `historicoPaciente(pacienteId: ID): [Atendimento!]!` — todos os
    atendimentos (passados e futuros) de um paciente.
  - `consultasFuturas(pacienteId: ID): [Atendimento!]!` — só `dataHora > agora`.
- O read model `Atendimento` é chaveado pelo `consultaId` de origem —
  `consulta.editada` atualiza a mesma linha, então o histórico reflete sempre
  o estado corrente.

## 5. Endpoints (resumo)

**Agendamento** — `http://localhost:8080`
`POST /consultas` · `PUT /consultas/{id}` · `GET /consultas` ·
`GET /consultas/{id}` · `GET /consultas/me`

**Notificações** — `http://localhost:8082`
`GET /notificacoes` · `GET /notificacoes/{id}` ·
`GET /notificacoes/paciente/{pacienteId}`

**Histórico** — `http://localhost:8083`
`POST /graphql` · `GET /graphiql`

Detalhamento (bodies, respostas de sucesso/erro) nos READMEs de cada serviço
e nas collections Postman.

## 6. Como executar

```bash
docker compose up --build      # Postgres (3 bancos) + RabbitMQ + 3 serviços
./scripts/e2e.sh               # valida o fluxo ponta a ponta
```

Testes por módulo: `./mvnw test` em `agendamento/`, `notificacoes/`,
`historico/` (H2 em memória; relatório JaCoCo em `target/site/jacoco/`).

## 7. Decisões técnicas

| Decisão | Motivo |
|---|---|
| RabbitMQ (topic exchange) | Roteamento por padrão `consulta.*`; um exchange, N consumidores desacoplados |
| Evento publicado `AFTER_COMMIT` | Não notificar consulta que sofreu rollback |
| Payload do evento duplicado em cada serviço | Sem biblioteca compartilhada; serviços evoluem isolados |
| Histórico como serviço separado consumindo eventos | Demonstra CQRS/orientação a eventos; evento já tem os dados |
| Read model chaveado por `consultaId` | Edição atualiza a mesma linha; histórico = estado corrente |
| Autenticação in-memory no Histórico | Evita duplicar cadastro de usuários entre serviços |
| Retry + DLQ | Falha transitória se recupera; malformada não entra em loop |
| Lembrete de proximidade `@Scheduled` | Requisito "lembretes automáticos sobre consultas futuras" além do event-driven |

## 8. Limitações conhecidas

- Envio do lembrete é simulado (persistência + log), sem SMTP/SMS real.
- Publicação do evento é *best-effort* (sem outbox transacional).
- DLQ sem consumidor de reprocessamento automático.
- Consistência eventual entre Agendamento e Histórico/Notificações.

## 9. Mapeamento aos critérios de avaliação

| Critério (Fase 3) | Onde é atendido |
|---|---|
| **Funcionalidade** — todos os requisitos, endpoints seguros | Agendamento (Security + CRUD + eventos), Notificações (consumo + lembretes), Histórico (GraphQL) — validado por `scripts/e2e.sh` |
| **Qualidade do código** — práticas Spring Boot, modularização | `docs/CODE-REVIEW.md`; construtor injection, DTOs `record`, camadas padronizadas, config externalizada |
| **Documentação** — arquitetura, endpoints, execução | Este relatório + `README.md` da raiz + READMEs por serviço |
| **Collections para teste** | `*/postman/*.postman_collection.json` (3 collections) |
| **Repositório** | GitHub público, branches por serviço/bloco, pull requests |

## 10. Divisão do trabalho

| Bloco | Responsável | Entrega |
|---|---|---|
| 1 — Segurança e Controle de Acesso | Armando | Spring Security, perfis, matriz de autorização, erros 401/403 |
| 2 — Serviço de Agendamento + Eventos | Luciano | Entidade Consulta, CRUD, regras de negócio, producer RabbitMQ |
| 3 — Serviço de Notificações + Docs | Caio | Consumer, lembretes, docker-compose de dev, README, collection |
| 4 — Histórico (GraphQL) + QA | Igor | Serviço de Histórico + GraphQL, retry/DLQ, docker-compose integrado, e2e, code review, este relatório |
