# Agendamento — Segurança, Regras de Negócio e Eventos

Tech Challenge Fase 3 (POS TECH — Arquitetura e Desenvolvimento Java).
Este repositório contém o **Serviço de Agendamento** de um sistema hospitalar:
autenticação, perfis de usuário, regras de autorização por endpoint (Bloco 1),
CRUD de consultas com validação de conflito de horário, e publicação de
eventos `consulta.criada` / `consulta.editada` no RabbitMQ (Bloco 2).

> De acordo com a arquitetura da Fase 3, a segurança e as regras de negócio
> vivem **dentro** do Serviço de Agendamento (não é um microsserviço à
> parte). Este projeto é o ponto de partida sobre o qual as próximas partes
> do time constroem: o Serviço de Notificações (consumidor dos eventos) e o
> módulo de histórico em GraphQL.

## Arquitetura

```
┌──────────────────────────────────────────────┐
│              Serviço de Agendamento           │
│                                                │
│  Spring Security (Basic Auth)                 │
│   └─ CustomUserDetailsService (JPA)           │
│                                                │
│  Usuario (MEDICO | ENFERMEIRO | PACIENTE)     │
│  Consulta (paciente, profissional, status)    │
│                                                │
│  ConsultaController ──▶ ConsultaService       │
│                          └─▶ ConsultaRepository│
│                          └─▶ UsuarioRepository │
│                          └─▶ ApplicationEventPublisher
│                                  │ (AFTER_COMMIT)
│                                  ▼
│                          ConsultaEventPublisher
│                                  │
└──────────────────────────────────┼─────────────┘
                                    ▼
                    RabbitMQ — exchange agendamento.consultas
                    routing keys: consulta.criada / consulta.editada
                                    │
                                    ▼
                    Serviço de Notificações (consumidor, outro repo/bloco)
```

- **Autenticação**: HTTP Basic, senhas com hash BCrypt.
- **Autorização**: regras por método/rota em `SecurityConfig`, mais uma
  verificação de posse (paciente só vê a própria consulta) feita no
  `ConsultaService`.
- **Erros padronizados**: qualquer falha de autenticação (401), autorização
  (403), validação (400) ou recurso não encontrado (404) retorna o mesmo
  formato JSON (ver seção "Formato de erro").
- **Persistência**: PostgreSQL (configurável via variáveis de ambiente); os
  testes usam H2 em memória, sem dependência de infraestrutura externa.

## Modelo de domínio

| Entidade | Campos | Observações |
|---|---|---|
| `Usuario` | id, nome, email, senha (hash), perfil, ativo | `email` é o *username* de login |
| `Perfil` (enum) | `MEDICO`, `ENFERMEIRO`, `PACIENTE` | vira a *authority* `ROLE_<PERFIL>` |
| `Consulta` | id, paciente, profissional, dataHora, status, observacoes | `status`: `AGENDADA`, `REALIZADA`, `CANCELADA` |

## Regras de autorização por endpoint

| Método | Endpoint | Médico | Enfermeiro | Paciente |
|---|---|:---:|:---:|:---:|
| POST | `/consultas` (registrar) | ❌ | ✅ | ❌ |
| PUT | `/consultas/{id}` (editar) | ✅ | ❌ | ❌ |
| GET | `/consultas` (histórico completo) | ✅ | ✅ | ❌ |
| GET | `/consultas/{id}` | ✅ | ✅ | ✅ (somente a própria) |
| GET | `/consultas/me` (minhas consultas) | ❌ | ❌ | ✅ |

Qualquer requisição sem credenciais válidas recebe **401**; com credenciais
válidas mas sem permissão para o recurso, **403**. Quando um paciente tenta
acessar `/consultas/{id}` de outro paciente, também recebe **403** (regra
validada em tempo de execução, não apenas por rota).

## Regra de conflito de horário

Um mesmo `profissional` não pode ter duas consultas com status `AGENDADA` no
mesmo `dataHora` (não há campo de duração — "mesmo instante" é a
interpretação literal de "conflito de horário"). A violação é reportada como
`BusinessException` → **400 Bad Request**, reaproveitando o mesmo tipo de
erro já usado para paciente/profissional inexistente — evita introduzir um
segundo status (409) para a mesma categoria de problema. Consultas
`REALIZADA`/`CANCELADA` nunca colidem, então editar o status para um desses
valores nunca é bloqueado por conflito.

O `dataHora` recebido é truncado para o minuto (segundos/frações são
descartados) antes de validar e persistir — agendamento hospitalar não
precisa de precisão de segundo, e isso evita falso-negativo na checagem de
conflito por divergência de precisão de timestamp entre a JVM e o banco.

Validado tanto em `POST /consultas` (novo agendamento) quanto em
`PUT /consultas/{id}` (o profissional não muda na edição, só `dataHora` e
`status`).

## Eventos publicados (RabbitMQ)

Sempre que uma consulta é criada ou editada com sucesso, o `ConsultaService`
publica um evento interno que o `ConsultaEventPublisher` envia ao RabbitMQ
**após o commit** da transação (evita notificar uma consulta cujo `save`
sofreu rollback). Esse é o contrato que o Serviço de Notificações consome.

| | |
|---|---|
| Exchange | `agendamento.consultas` (topic, durável) |
| Routing key — nova consulta | `consulta.criada` |
| Routing key — consulta editada | `consulta.editada` |
| Fila/binding | responsabilidade do serviço consumidor |

Exemplo de payload (`application/json`):

```json
{
  "eventoId": "b3f1c2e4-1234-4a56-9abc-1234567890ab",
  "tipoEvento": "CONSULTA_CRIADA",
  "ocorreEm": "2026-08-08T12:00:00Z",
  "consultaId": 1,
  "dataHora": "2026-09-01T14:30:00",
  "status": "AGENDADA",
  "pacienteId": 3,
  "pacienteNome": "Carla Pereira",
  "pacienteEmail": "paciente1@hospital.com",
  "profissionalId": 1,
  "profissionalNome": "Dra. Ana Souza",
  "observacoes": "Consulta de rotina"
}
```

**Limitação conhecida**: a publicação é *best-effort* — se o broker estiver
indisponível no momento do envio, a falha é apenas logada (a consulta já foi
persistida com sucesso e a resposta HTTP não deve virar erro por causa da
mensageria). Uma fila de outbox transacional resolveria isso, mas está fora
do escopo deste bloco.

## Endpoints da API

### `POST /consultas` — registrar consulta (ENFERMEIRO)

```json
{
  "pacienteId": 3,
  "profissionalId": 1,
  "dataHora": "2026-09-01T14:30:00",
  "observacoes": "Consulta de rotina"
}
```

Resposta `201 Created`:

```json
{
  "id": 1,
  "pacienteId": 3,
  "pacienteNome": "Carla Pereira",
  "profissionalId": 1,
  "profissionalNome": "Dra. Ana Souza",
  "dataHora": "2026-09-01T14:30:00",
  "status": "AGENDADA",
  "observacoes": "Consulta de rotina"
}
```

`400 Bad Request` se `pacienteId`/`profissionalId` não existirem, não
tiverem o perfil esperado, ou se o profissional já tiver uma consulta
`AGENDADA` no mesmo `dataHora` (ver "Regra de conflito de horário" acima).

### `PUT /consultas/{id}` — editar consulta (MEDICO)

```json
{
  "dataHora": "2026-09-02T09:00:00",
  "status": "REALIZADA",
  "observacoes": "Paciente atendido"
}
```

Resposta `200 OK` com o mesmo formato de `ConsultaResponse` acima. `400 Bad
Request` se o novo `dataHora` colidir com outra consulta `AGENDADA` do mesmo
profissional.

### `GET /consultas` — histórico completo (MEDICO, ENFERMEIRO)

Resposta `200 OK`: lista de `ConsultaResponse`, ordenada por data/hora.

### `GET /consultas/{id}` — consulta específica (MEDICO, ENFERMEIRO, PACIENTE dono)

Resposta `200 OK` com `ConsultaResponse`, `404` se não existir, `403` se um
paciente tentar acessar consulta de outro paciente.

### `GET /consultas/me` — minhas consultas (PACIENTE)

Resposta `200 OK`: lista de `ConsultaResponse` apenas do paciente autenticado.

### Formato de erro

Todas as respostas de erro (401, 403, 404, 400) seguem o mesmo formato:

```json
{
  "timestamp": "2026-08-03T12:00:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Voce nao tem permissao para acessar este recurso",
  "path": "/consultas"
}
```

## Usuários de demonstração

Ao subir a aplicação com o banco vazio, um `CommandLineRunner`
(`DemoUsersSeeder`) cria 4 usuários de teste. **Nenhuma senha fica fixa no
código-fonte de forma definitiva** — os valores abaixo são apenas defaults
de desenvolvimento e podem ser sobrescritos por variáveis de ambiente
(`SEED_MEDICO_EMAIL`, `SEED_MEDICO_SENHA`, etc.) em outros ambientes.

| Perfil | Email | Senha (default) |
|---|---|---|
| Médico | `medico@hospital.com` | `medico123` |
| Enfermeiro | `enfermeiro@hospital.com` | `enfermeiro123` |
| Paciente 1 | `paciente1@hospital.com` | `paciente123` |
| Paciente 2 | `paciente2@hospital.com` | `paciente123` |

## Configuração e execução

### Pré-requisitos

- Java 21
- PostgreSQL 14+ (ou ajuste as variáveis abaixo para outro banco)

### Variáveis de ambiente

| Variável | Default | Descrição |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/hospital_agendamento` | URL JDBC |
| `DB_USERNAME` | `postgres` | usuário do banco |
| `DB_PASSWORD` | `postgres` | senha do banco |
| `JPA_DDL_AUTO` | `update` | estratégia do Hibernate (`validate` em produção) |
| `SERVER_PORT` | `8080` | porta HTTP |
| `SEED_MEDICO_EMAIL` / `SEED_MEDICO_SENHA` | ver tabela acima | credenciais do médico semente |
| `SEED_ENFERMEIRO_EMAIL` / `SEED_ENFERMEIRO_SENHA` | ver tabela acima | credenciais do enfermeiro semente |
| `SEED_PACIENTE1_EMAIL` / `SEED_PACIENTE1_SENHA` | ver tabela acima | credenciais do paciente 1 semente |
| `SEED_PACIENTE2_EMAIL` / `SEED_PACIENTE2_SENHA` | ver tabela acima | credenciais do paciente 2 semente |
| `RABBITMQ_HOST` | `localhost` | host do broker |
| `RABBITMQ_PORT` | `5672` | porta AMQP do broker |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | `guest` / `guest` | credenciais do broker |
| `RABBITMQ_EXCHANGE` | `agendamento.consultas` | exchange onde os eventos de consulta são publicados |

### Subindo Postgres e RabbitMQ localmente (opcional)

```bash
docker run --name hospital-postgres -e POSTGRES_DB=hospital_agendamento \
  -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 -d postgres:16

docker run --name hospital-rabbitmq -p 5672:5672 -p 15672:15672 \
  -d rabbitmq:3-management
```

Ou suba tudo de uma vez via compose: `docker compose up db rabbitmq`. O
management UI do RabbitMQ fica em `http://localhost:15673` (usuário/senha
`guest`/`guest`) quando subido pelo `docker-compose.yml` deste repositório
(porta `15672` no `docker run` acima, se rodado à parte).

### Rodando a aplicação

```bash
./mvnw spring-boot:run
```

### Rodando os testes

```bash
./mvnw test
```

Os testes usam H2 em memória (`src/test/resources/application.properties`)
e não exigem Postgres rodando.

### Exemplo de chamada (curl)

```bash
curl -u enfermeiro@hospital.com:enfermeiro123 \
  -H "Content-Type: application/json" \
  -d '{"pacienteId":3,"profissionalId":1,"dataHora":"2026-09-01T14:30:00","observacoes":"Consulta de rotina"}' \
  http://localhost:8080/consultas
```

## Testes automatizados

- `SecurityAuthorizationMatrixTest`: matriz perfil × endpoint (401, 403,
  200/201 para cada combinação de papel e rota), incluindo a checagem de
  posse do paciente sobre a própria consulta.
- `CustomUserDetailsServiceTest`: carregamento de usuário e mapeamento de
  perfil para `GrantedAuthority`.
- `ConsultaServiceTest`: regras de negócio do agendamento em isolamento
  (Mockito) — paciente/profissional inválidos, conflito de horário ao
  registrar e ao editar, e publicação do evento correto em cada caso de
  sucesso.
- `ConsultaBusinessRulesIntegrationTest`: os mesmos cenários de negócio
  ponta a ponta via MockMvc (400/200), com `RabbitTemplate` mockado para não
  depender de um broker real.
- `ConsultaEventPublisherTest`: roteamento por tipo de evento e tolerância a
  falha do broker (loga em vez de relançar).

Rode `./mvnw test` — o `jacoco-maven-plugin` gera o relatório de cobertura em
`target/site/jacoco/index.html`.

## Serviços que consomem estes eventos

- **Serviço de Notificações** ([`../notificacoes/`](../notificacoes/README.md)):
  consome `consulta.criada` / `consulta.editada` (contrato em "Eventos
  publicados (RabbitMQ)" acima) e registra lembretes ao paciente, incluindo
  um lembrete de proximidade agendado; retry + DLQ no consumo.
- **Serviço de Histórico (GraphQL)** ([`../historico/`](../historico/README.md)):
  projeta os mesmos eventos num read model e expõe `historicoPaciente` /
  `consultasFuturas` via GraphQL, com as mesmas regras de acesso por perfil.

## Collection para teste

`postman/agendamento.postman_collection.json` — importe no Postman (variável
`base_url`, default `http://localhost:8080`). Cobre a matriz perfil ×
endpoint, incluindo os casos `401` / `403` e a checagem de posse do paciente.
