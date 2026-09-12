package br.com.fiap.historico.graphql;

import br.com.fiap.historico.messaging.ConsultaEventoListener;
import br.com.fiap.historico.repository.AtendimentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.security.test.context.support.WithMockUser;

import java.nio.charset.StandardCharsets;

/**
 * Integracao "ponta a ponta" dentro do servico de Historico: um evento de
 * consulta chega pelo listener (como chegaria do RabbitMQ) e passa a ser
 * visivel na consulta GraphQL — cobre projecao + leitura + autorizacao.
 * A cadeia autenticacao -> agendamento -> publicacao do evento e coberta
 * pelos testes de integracao do modulo de Agendamento.
 */
@SpringBootTest
@AutoConfigureGraphQlTester
class HistoricoFluxoEventoParaGraphQlTest {

    @Autowired
    ConsultaEventoListener listener;

    @Autowired
    GraphQlTester graphQlTester;

    @Autowired
    AtendimentoRepository repository;

    @BeforeEach
    void limpar() {
        repository.deleteAll();
    }

    private Message evento(String json) {
        return new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }

    @Test
    @WithMockUser(username = "paciente1@hospital.com", roles = "PACIENTE")
    void eventoConsultaCriada_ficaVisivelNoGraphQlDoPaciente() {
        listener.aoReceberEventoConsulta(evento("""
                {
                  "eventoId": "11111111-1111-1111-1111-111111111111",
                  "tipoEvento": "CONSULTA_CRIADA",
                  "ocorreEm": "2026-08-08T12:00:00Z",
                  "consultaId": 42,
                  "dataHora": "2999-01-01T09:00:00",
                  "status": "AGENDADA",
                  "pacienteId": 3,
                  "pacienteNome": "Carla Pereira",
                  "pacienteEmail": "paciente1@hospital.com",
                  "profissionalId": 1,
                  "profissionalNome": "Dra. Ana Souza",
                  "observacoes": "Consulta de rotina"
                }
                """));

        graphQlTester.document("{ consultasFuturas { consultaId status pacienteEmail } }")
                .execute()
                .path("consultasFuturas[0].consultaId").entity(String.class).isEqualTo("42")
                .path("consultasFuturas[0].status").entity(String.class).isEqualTo("AGENDADA");
    }

    @Test
    @WithMockUser(username = "medico@hospital.com", roles = "MEDICO")
    void eventoConsultaEditada_atualizaOStatusNoGraphQl() {
        listener.aoReceberEventoConsulta(evento("""
                {
                  "eventoId": "22222222-2222-2222-2222-222222222222",
                  "tipoEvento": "CONSULTA_CRIADA",
                  "ocorreEm": "2026-08-08T12:00:00Z",
                  "consultaId": 43,
                  "dataHora": "2999-02-02T10:00:00",
                  "status": "AGENDADA",
                  "pacienteId": 3,
                  "pacienteNome": "Carla Pereira",
                  "pacienteEmail": "paciente1@hospital.com",
                  "profissionalId": 1,
                  "profissionalNome": "Dra. Ana Souza",
                  "observacoes": null
                }
                """));
        listener.aoReceberEventoConsulta(evento("""
                {
                  "eventoId": "33333333-3333-3333-3333-333333333333",
                  "tipoEvento": "CONSULTA_EDITADA",
                  "ocorreEm": "2026-08-09T12:00:00Z",
                  "consultaId": 43,
                  "dataHora": "2999-02-02T10:00:00",
                  "status": "REALIZADA",
                  "pacienteId": 3,
                  "pacienteNome": "Carla Pereira",
                  "pacienteEmail": "paciente1@hospital.com",
                  "profissionalId": 1,
                  "profissionalNome": "Dra. Ana Souza",
                  "observacoes": "Paciente atendido"
                }
                """));

        graphQlTester.document("{ historicoPaciente(pacienteId: 3) { consultaId status observacoes } }")
                .execute()
                .path("historicoPaciente[0].status").entity(String.class).isEqualTo("REALIZADA")
                .path("historicoPaciente[0].observacoes").entity(String.class).isEqualTo("Paciente atendido");
    }
}
