# Sistema Hospitalar — Tech Challenge Fase 3

Tech Challenge Fase 3 (POS TECH — Arquitetura e Desenvolvimento Java):
backend de agendamento de consultas hospitalares, com autenticação por
perfil, comunicação assíncrona entre serviços e histórico via GraphQL.

Cada serviço é uma aplicação Spring Boot independente, com seu próprio
`pom.xml`, testes, `Dockerfile` e documentação:

| Serviço | Pasta | Responsabilidade |
|---|---|---|
| Agendamento | [`agendamento/`](agendamento/README.md) | Autenticação/autorização por perfil (médico, enfermeiro, paciente), CRUD de consultas, publicação dos eventos `consulta.criada`/`consulta.editada` no RabbitMQ |
| Notificações | [`notificacoes/`](notificacoes/README.md) | Consome os eventos do Agendamento e registra o lembrete correspondente ao paciente |

## Arquitetura

```
Agendamento ──(RabbitMQ: exchange agendamento.consultas)──▶ Notificações
```

Cada serviço documenta no próprio README: modelo de domínio, endpoints,
variáveis de ambiente, como rodar isoladamente (`docker compose up` dentro
da pasta do serviço) e como rodar o fluxo ponta a ponta.
