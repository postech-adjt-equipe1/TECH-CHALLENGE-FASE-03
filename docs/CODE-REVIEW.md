# Code Review Geral — Fase 3

Revisão transversal dos três serviços: nomenclatura, organização de pacotes,
boas práticas Spring Boot, testes. Itens marcados ✅ estão atendidos no
código entregue; ⚠️ são limitações conscientes documentadas.

## Nomenclatura e organização de pacotes

- ✅ Pacote raiz consistente por serviço: `br.com.fiap.<servico>`.
- ✅ Camadas separadas em pacotes previsíveis e iguais entre os serviços:
  `domain`, `dto`, `repository`, `service`, `controller` / `graphql`,
  `messaging`, `config`, `exception`, `security`.
- ✅ Nomes em português alinhados ao domínio do enunciado (`Consulta`,
  `Atendimento`, `Notificacao`, `Perfil`, `StatusConsulta`) e verbos claros
  nos serviços (`registrar`, `editar`, `projetar`, `processar`).
- ✅ Enums de contrato (`TipoEvento`, `StatusConsulta`/`StatusAtendimento`)
  duplicados de propósito entre serviços, com Javadoc explicando que são
  cópias do contrato (sem biblioteca compartilhada).

## Boas práticas Spring Boot

- ✅ Construtor injection em tudo (`@RequiredArgsConstructor`); nenhum
  `@Autowired` em campo.
- ✅ Configuração sensível 100% externalizada por variável de ambiente com
  default de desenvolvimento (`application.properties` de cada serviço);
  nenhuma senha fixa em código (o `DemoUsersSeeder` usa `@Value` com default).
- ✅ `spring.jpa.open-in-view=false` nos três serviços.
- ✅ DTOs de entrada/saída como `record`; entidade JPA nunca sai direto pelo
  controller REST (no GraphQL, `AtendimentoView` isola o schema da entidade).
- ✅ Tratamento de erro padronizado: `@RestControllerAdvice` +
  `AuthenticationEntryPoint` / `AccessDeniedHandler` em JSON no Agendamento;
  `DataFetcherExceptionResolver` no Histórico (classifica `FORBIDDEN` /
  `BAD_REQUEST`).
- ✅ Segurança: `SecurityFilterChain` por regra de rota + `@EnableMethodSecurity`
  e checagem de posse em runtime (paciente só vê o que é dele) — não confia
  só no path.
- ✅ Transações: `@Transactional` no serviço; publicação do evento **após o
  commit** (`ApplicationEventPublisher` + `@TransactionalEventListener`
  `AFTER_COMMIT`) para não notificar consulta que sofreu rollback.
- ✅ Mensageria: exchange declarado igual nos dois lados; consumidores com
  fila própria + binding `consulta.*`; retry com backoff + DLQ; consumo
  idempotente por `eventoId`.
- ✅ `@Scheduled` isolado em um bean próprio, condicional
  (`@ConditionalOnProperty`), com janela/intervalo configuráveis.

## Testes

- ✅ Pirâmide saudável: unitários com Mockito no núcleo de regras; slice /
  `@SpringBootTest` para segurança, integração de regras de negócio e GraphQL.
- ✅ Nenhum teste depende de Postgres/RabbitMQ reais (H2 + chamada direta ao
  listener) — build reproduzível offline.
- ✅ JaCoCo configurado nos três módulos (`prepare-agent` + `report` na fase
  `test`).
- ✅ `scripts/e2e.sh` cobre a cadeia completa contra o `docker-compose` da raiz.

## Limitações conscientes (documentadas nos READMEs)

- ⚠️ "Envio" do lembrete é simulado (persistência + log), sem SMTP/SMS real.
- ⚠️ Publicação do evento é *best-effort* (sem outbox transacional): se o
  broker estiver fora no instante do envio, a falha é logada e a consulta
  permanece persistida.
- ⚠️ DLQ sem consumidor de reprocessamento (inspeção manual pela UI do
  RabbitMQ).
- ⚠️ Histórico usa autenticação in-memory (mesmas credenciais semente), não
  um cadastro de usuários próprio — evita duplicar o cadastro entre serviços.
- ⚠️ Consistência eventual no Histórico: a projeção reflete o estado depois
  que o evento é consumido.

## Ajustes aplicados nesta revisão

- Serviço de Histórico (GraphQL) criado do zero, com read model, consumo de
  eventos, autorização por perfil e testes.
- Notificações: adicionados retry + DLQ no consumo e o lembrete de
  proximidade agendado; listener passou a distinguir falha permanente
  (parsing → DLQ) de transitória (retry → DLQ).
- `docker-compose.yml` na raiz integrando os três serviços com Postgres e
  RabbitMQ compartilhados; `db/init` para os 3 bancos.
- Collections Postman de Agendamento e Histórico.
- READMEs revisados e `docs/RELATORIO-FINAL.md`.
