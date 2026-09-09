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
     fila notificacoes.lembretes (binding: consulta.*)   ──falha──▶ notificacoes.lembretes.dlq
                │
                ▼
      NotificacaoListener ──▶ NotificacaoService ──▶ NotificacaoRepository
                │                                             │
                │                                             ▼
  LembreteProximidadeScheduler (@Scheduled)          Postgres (hospital_notificacoes)
```

- **Envio do lembrete**: nesta fase o "envio" é simulado — o serviço grava
  um registro de `Notificacao` (status `ENVIADA`) e loga a mensagem. Não há
  envio de e-mail/SMS real (ver "Decisões e limitações conhecidas" abaixo).
- **Lembrete event-driven**: ao consumir `consulta.criada` / `consulta.editada`
  o serviço registra o lembrete correspondente na hora.
- **Lembrete de proximidade** (`LembreteProximidadeScheduler`): um job
  agendado varre periodicamente (default: a cada 1h) as consultas que vão
  ocorrer dentro de uma janela (default: próximas 24h) e dispara um lembrete
  extra ao paciente — atende ao requisito "lembretes automáticos sobre
  consultas futuras". Idempotente: cada consulta recebe o lembrete de
  proximidade uma única vez (flag `lembreteProximidadeEnviado`).
- **Idempotência**: cada evento carrega um `eventoId` (UUID) único. Se a
  mesma mensagem for reentregue pelo broker, o serviço detecta pelo
  `eventoId` já persistido e ignora, em vez de duplicar o lembrete.
- **Retry + Dead Letter Queue**: falha transitória ao processar (ex.: banco
  fora) é retentada 3× com backoff exponencial (`spring.rabbitmq.listener.simple.retry.*`);
  esgotadas as tentativas, ou se a mensagem chegar malformada (falha
  permanente), ela é rejeitada sem requeue e roteada para
  `notificacoes.lembretes.dlq` — sem loop infinito de redelivery.

## Modelo de domínio

| Entidade | Campos | Observações |
|---|---|---|
| `Notificacao` | id, eventoId, tipoEvento, consultaId, pacienteId, pacienteNome, pacienteEmail, dataHoraConsulta, mensagem, status, criadoEm, lembreteProximidadeEnviado | `eventoId` é único (idempotência); `status`: `ENVIADA`, `FALHA`; `lembreteProximidadeEnviado` controla o job de proximidade |

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
| `RABBITMQ_DLX` / `RABBITMQ_DLQ` | `notificacoes.lembretes.dlx` / `notificacoes.lembretes.dlq` | dead-letter (mensagem descartada após retries) |
| `LEMBRETE_PROXIMIDADE_ENABLED` | `true` | liga/desliga o job de lembrete de proximidade |
| `LEMBRETE_PROXIMIDADE_INTERVALO_MS` | `3600000` | período do job (ms) |
| `LEMBRETE_PROXIMIDADE_JANELA_HORAS` | `24` | antecedência do lembrete de proximidade |

### Rodando isoladamente (Postgres + RabbitMQ próprios)

```bash
docker compose up
```

Sobe Postgres na porta `5434`, RabbitMQ (AMQP `5675`, management UI
`15675`) e a aplicação na `8082`. Útil para desenvolver/testar este
serviço sozinho, mas nesse modo ele não recebe eventos reais do
Agendamento (brokers diferentes).

### Rodando o fluxo ponta a ponta

Use o `docker-compose.yml` da **raiz** do repositório — ele sobe os 3
serviços com um broker RabbitMQ e um Postgres compartilhados:

```bash
docker compose up --build            # na raiz do repositório
./scripts/e2e.sh                     # valida o fluxo completo
```

### Rodando os testes

```bash
./mvnw test
```

Usa H2 em memória; não depende de Postgres/RabbitMQ reais. O listener e o
agendador de proximidade são testados chamando os métodos diretamente (sem
broker real), no mesmo espírito dos testes de publicação do Agendamento.
Cobertura (JaCoCo): `target/site/jacoco/index.html`.

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
- **DLQ sem consumidor**: as mensagens que caem em
  `notificacoes.lembretes.dlq` ficam lá para inspeção manual (via RabbitMQ
  Management UI) — não há um consumer de reprocessamento automático da DLQ,
  o que seria o próximo passo natural.
- **Sem autenticação**: este serviço não modela usuários — os únicos
  dados de identidade que ele vê vêm dentro do próprio evento consumido.
