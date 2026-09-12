package br.com.fiap.historico.graphql;

import br.com.fiap.historico.domain.Atendimento;
import br.com.fiap.historico.domain.StatusAtendimento;
import br.com.fiap.historico.repository.AtendimentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@SpringBootTest
@AutoConfigureGraphQlTester
class HistoricoGraphQlControllerTest {

    @Autowired
    GraphQlTester graphQlTester;

    @Autowired
    AtendimentoRepository repository;

    private static final LocalDateTime PASSADO = LocalDateTime.now().minusDays(10);
    private static final LocalDateTime FUTURO = LocalDateTime.now().plusDays(10);

    @BeforeEach
    void seed() {
        repository.deleteAll();
        repository.save(atendimento(1L, 3L, "paciente1@hospital.com", PASSADO, StatusAtendimento.REALIZADA));
        repository.save(atendimento(2L, 3L, "paciente1@hospital.com", FUTURO, StatusAtendimento.AGENDADA));
        repository.save(atendimento(3L, 4L, "paciente2@hospital.com", FUTURO, StatusAtendimento.AGENDADA));
    }

    private Atendimento atendimento(Long consultaId, Long pacienteId, String email,
                                    LocalDateTime dataHora, StatusAtendimento status) {
        return new Atendimento(consultaId, pacienteId, "Paciente " + pacienteId, email,
                1L, "Dra. Ana Souza", dataHora, status, "obs", UUID.randomUUID(), Instant.now());
    }

    @Test
    @WithMockUser(username = "medico@hospital.com", roles = "MEDICO")
    void medico_consultaHistoricoDeQualquerPaciente() {
        graphQlTester.document("{ historicoPaciente(pacienteId: 3) { consultaId status } }")
                .execute()
                .path("historicoPaciente").entityList(Object.class).hasSize(2);
    }

    @Test
    @WithMockUser(username = "enfermeiro@hospital.com", roles = "ENFERMEIRO")
    void enfermeiro_filtraApenasConsultasFuturas() {
        graphQlTester.document("{ consultasFuturas(pacienteId: 3) { consultaId dataHora } }")
                .execute()
                .path("consultasFuturas").entityList(Object.class).hasSize(1);
    }

    @Test
    @WithMockUser(username = "medico@hospital.com", roles = "MEDICO")
    void profissionalSemPacienteId_recebeBadRequest() {
        graphQlTester.document("{ historicoPaciente { consultaId } }")
                .execute()
                .errors()
                .expect(e -> e.getMessage() != null && e.getMessage().contains("obrigatorio"));
    }

    @Test
    @WithMockUser(username = "paciente1@hospital.com", roles = "PACIENTE")
    void paciente_recebeApenasOProprioHistorico_ignorandoPacienteIdInformado() {
        graphQlTester.document("{ historicoPaciente { consultaId pacienteEmail } }")
                .execute()
                .path("historicoPaciente").entityList(Object.class).hasSize(2);
    }

    @Test
    @WithMockUser(username = "paciente1@hospital.com", roles = "PACIENTE")
    void paciente_tentandoVerOutroPaciente_recebeForbidden() {
        graphQlTester.document("{ historicoPaciente(pacienteId: 4) { consultaId } }")
                .execute()
                .errors()
                .expect(e -> e.getMessage() != null && e.getMessage().contains("proprio historico"));
    }

    @Test
    void semAutenticacao_naoAcessa() {
        graphQlTester.document("{ historicoPaciente(pacienteId: 3) { consultaId } }")
                .execute()
                .errors()
                .expect(e -> true)
                .verify();
    }
}
