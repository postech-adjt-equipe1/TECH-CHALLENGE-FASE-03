# Agendamento — Segurança e Controle de Acesso

Tech Challenge Fase 3 (POS TECH — Arquitetura e Desenvolvimento Java).
Este repositório contém a **Parte 1** do projeto: a base de segurança do
Serviço de Agendamento de um sistema hospitalar (autenticação, perfis de
usuário e regras de autorização por endpoint).

> De acordo com a arquitetura da Fase 3, a segurança vive **dentro** do
> Serviço de Agendamento (não é um microsserviço à parte). Este projeto é o
> ponto de partida sobre o qual as próximas partes do time constroem:
> publicação de eventos (RabbitMQ/Kafka), serviço de notificações e o
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
└──────────────────────────────────────────────┘
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

### `PUT /consultas/{id}` — editar consulta (MEDICO)

```json
{
  "dataHora": "2026-09-02T09:00:00",
  "status": "REALIZADA",
  "observacoes": "Paciente atendido"
}
```

Resposta `200 OK` com o mesmo formato de `ConsultaResponse` acima.

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

### Subindo um Postgres local rápido (opcional)

```bash
docker run --name hospital-postgres -e POSTGRES_DB=hospital_agendamento \
  -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 -d postgres:16
```

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

## O que fica para as próximas partes do time

- **Serviço de Agendamento (Luciano)**: evoluir as validações de negócio
  (conflito de horário), publicar eventos `consulta.criada` /
  `consulta.editada` no RabbitMQ/Kafka a partir do `ConsultaService`.
- **Serviço de Notificações (Caio)**: consumir os eventos acima e disparar
  lembretes; Docker Compose com Postgres + broker; documentação e
  collection Postman.
- **Histórico via GraphQL (Igor)**: schema/resolvers de consulta sobre os
  dados de `Consulta`; QA geral e testes de integração ponta a ponta.
