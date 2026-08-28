# Notificações — Lembretes de Consulta

Tech Challenge Fase 3 (POS TECH — Arquitetura e Desenvolvimento Java).
Este é o **Serviço de Notificações**: consome os eventos `consulta.criada` /
`consulta.editada` publicados pelo Serviço de Agendamento no RabbitMQ e
registra o lembrete correspondente ao paciente.

> Contrato do evento consumido: documentado em
> [`../agendamento/README.md`](../agendamento/README.md), seção "Eventos
> publicados (RabbitMQ)".
> Este serviço mantém uma cópia local do payload (`ConsultaEventoPayload`)
> porque os dois serviços evoluem sem biblioteca compartilhada.

## Arquitetura

```
RabbitMQ — exchange agendamento.consultas (topic, durável)
                │  routing keys: consulta.criada / consulta.editada
                ▼
     fila notificacoes.lembretes (binding: consulta.*)
                │
                ▼
      NotificacaoListener ──▶ NotificacaoService ──▶ NotificacaoRepository
                                                              │
                                                              ▼
                                                     Postgres (hospital_notificacoes)
```

- **Envio do lembrete**: nesta fase o "envio" é simulado — o serviço grava
  um registro de `Notificacao` (status `ENVIADA`) e loga a mensagem. Não há
  envio de e-mail/SMS real (ver "Decisões e limitações conhecidas" abaixo).
- **Idempotência**: cada evento carrega um `eventoId` (UUID) único. Se a
  mesma mensagem for reentregue pelo broker, o serviço detecta pelo
  `eventoId` já persistido e ignora, em vez de duplicar o lembrete.
- **Tolerância a falha de parsing**: se uma mensagem chegar malformada, o
  listener loga o erro e descarta em vez de relançar — evita um loop
  infinito de redelivery para uma mensagem que nunca vai processar (mesmo
  espírito best-effort documentado no Serviço de Agendamento).

## Modelo de domínio

| Entidade | Campos | Observações |
|---|---|---|
| `Notificacao` | id, eventoId, tipoEvento, consultaId, pacienteId, pacienteNome, pacienteEmail, dataHoraConsulta, mensagem, status, criadoEm | `eventoId` é único (idempotência); `status`: `ENVIADA`, `FALHA` |

## Endpoints da API

Somente leitura — usados para inspecionar os lembretes já processados
(demonstração e QA ponta a ponta). Não há autenticação/perfil de usuário
neste serviço: ele não tem usuários próprios, apenas consome eventos de
outro serviço (decisão de escopo, ver seção abaixo).

### `GET /notificacoes` — lista todos os lembretes, mais recentes primeiro

### `GET /notificacoes/{id}` — um lembrete específico

`404 Not Found` se não existir.

### `GET /notificacoes/paciente/{pacienteId}` — lembretes de um paciente

## Configuração e execução

### Pré-requisitos

- Java 21
- PostgreSQL 14+ e um broker RabbitMQ acessível (próprio, ou o mesmo do
  Serviço de Agendamento — ver variáveis abaixo)

### Variáveis de ambiente

| Variável | Default | Descrição |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/hospital_notificacoes` | URL JDBC |
| `DB_USERNAME` | `postgres` | usuário do banco |
| `DB_PASSWORD` | `postgres` | senha do banco |
| `JPA_DDL_AUTO` | `update` | estratégia do Hibernate (`validate` em produção) |
| `SERVER_PORT` | `8082` | porta HTTP |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | broker RabbitMQ |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | `guest` / `guest` | credenciais do broker |
| `RABBITMQ_EXCHANGE` | `agendamento.consultas` | precisa bater com o exchange do Agendamento |
| `RABBITMQ_QUEUE` | `notificacoes.lembretes` | fila deste serviço |
| `RABBITMQ_ROUTING_KEY_PATTERN` | `consulta.*` | binding — cobre criação e edição |

### Rodando isoladamente (Postgres + RabbitMQ próprios)

```bash
docker compose up
```

Sobe Postgres na porta `5434`, RabbitMQ (AMQP `5675`, management UI
`15675`) e a aplicação na `8082`. Útil para desenvolver/testar este
serviço sozinho, mas nesse modo ele não recebe eventos reais do
Agendamento (brokers diferentes).

### Rodando o fluxo ponta a ponta com o Serviço de Agendamento

Suba o broker do Agendamento (`docker compose up rabbitmq` na raiz do
repositório) e aponte este serviço para ele:

```bash
RABBITMQ_HOST=localhost RABBITMQ_PORT=5673 ./mvnw spring-boot:run
```

(porta `5673` é o mapeamento host do `docker-compose.yml` da raiz — ver
README do Agendamento).

### Rodando os testes

```bash
./mvnw test
```

Usa H2 em memória; não depende de Postgres/RabbitMQ reais. O listener é
testado chamando o método diretamente com uma `Message` construída em
memória (sem broker real), no mesmo espírito dos testes de publicação do
Agendamento.

## Collection para teste

`postman/notificacoes.postman_collection.json` — importe no Postman
(variável `base_url`, default `http://localhost:8082`).

## Decisões e limitações conhecidas

- **Lembrete simulado, não e-mail real**: optou-se por persistência +
  log em vez de SMTP real para esta fase — evita depender de infra de
  e-mail (Mailhog/SMTP) só para demonstrar o consumo do evento. O texto da
  mensagem já é montado como seria enviado a um paciente; plugar um envio
  real (`JavaMailSender` + Mailhog no compose) é uma extensão direta se
  necessário para a demonstração em vídeo.
- **Sem fila de erro/retry (DLQ)**: mensagem malformada é logada e
  descartada, não redirecionada para uma dead-letter queue. Suficiente
  para o escopo desta fase; uma DLQ seria o próximo passo natural.
- **Sem autenticação**: este serviço não modela usuários — os únicos
  dados de identidade que ele vê vêm dentro do próprio evento consumido.
