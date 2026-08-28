package br.com.fiap.agendamento.controller;

import br.com.fiap.agendamento.domain.Usuario;
import br.com.fiap.agendamento.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConsultaBusinessRulesIntegrationTest {

    private static final String MEDICO_EMAIL = "medico@hospital.com";
    private static final String MEDICO_SENHA = "medico123";
    private static final String ENFERMEIRO_EMAIL = "enfermeiro@hospital.com";
    private static final String ENFERMEIRO_SENHA = "enfermeiro123";
    private static final String PACIENTE1_EMAIL = "paciente1@hospital.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    private Long medicoId;
    private Long enfermeiroId;
    private Long paciente1Id;

    @BeforeEach
    void carregarUsuariosSemeados() {
        medicoId = idDe(MEDICO_EMAIL);
        enfermeiroId = idDe(ENFERMEIRO_EMAIL);
        paciente1Id = idDe(PACIENTE1_EMAIL);
    }

    private Long idDe(String email) {
        return usuarioRepository.findByEmail(email)
                .map(Usuario::getId)
                .orElseThrow(() -> new IllegalStateException("Usuario semente nao encontrado: " + email));
    }

    private String corpoNovaConsulta(Long pacienteId, Long profissionalId, LocalDateTime dataHora) {
        return """
                {"pacienteId": %d, "profissionalId": %d, "dataHora": "%s", "observacoes": "consulta de rotina"}
                """.formatted(pacienteId, profissionalId, dataHora.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    private Long registrarConsultaComoEnfermeiro(Long pacienteId, Long profissionalId, LocalDateTime dataHora) throws Exception {
        String resposta = mockMvc.perform(post("/consultas")
                        .with(httpBasic(ENFERMEIRO_EMAIL, ENFERMEIRO_SENHA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNovaConsulta(pacienteId, profissionalId, dataHora)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.valueOf(resposta.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void registrarComPacienteInexistenteRetorna400() throws Exception {
        LocalDateTime dataHora = LocalDateTime.now().plusDays(1);
        mockMvc.perform(post("/consultas")
                        .with(httpBasic(ENFERMEIRO_EMAIL, ENFERMEIRO_SENHA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNovaConsulta(9999L, medicoId, dataHora)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrarComProfissionalDePerfilPacienteRetorna400() throws Exception {
        LocalDateTime dataHora = LocalDateTime.now().plusDays(1);
        mockMvc.perform(post("/consultas")
                        .with(httpBasic(ENFERMEIRO_EMAIL, ENFERMEIRO_SENHA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNovaConsulta(paciente1Id, paciente1Id, dataHora)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrarConsultaComHorarioConflitanteParaMesmoProfissionalRetorna400() throws Exception {
        LocalDateTime dataHora = LocalDateTime.now().plusDays(1);
        registrarConsultaComoEnfermeiro(paciente1Id, medicoId, dataHora);

        mockMvc.perform(post("/consultas")
                        .with(httpBasic(ENFERMEIRO_EMAIL, ENFERMEIRO_SENHA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNovaConsulta(paciente1Id, medicoId, dataHora)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editarConsultaParaHorarioQueConflitaComOutraDoMesmoProfissionalRetorna400() throws Exception {
        LocalDateTime dataHoraOcupada = LocalDateTime.now().plusDays(1);
        LocalDateTime dataHoraLivre = LocalDateTime.now().plusDays(2);
        registrarConsultaComoEnfermeiro(paciente1Id, medicoId, dataHoraOcupada);
        Long consultaLivreId = registrarConsultaComoEnfermeiro(paciente1Id, medicoId, dataHoraLivre);

        String corpo = """
                {"dataHora": "%s", "status": "AGENDADA", "observacoes": "tentativa de conflito"}
                """.formatted(dataHoraOcupada.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        mockMvc.perform(put("/consultas/" + consultaLivreId)
                        .with(httpBasic(MEDICO_EMAIL, MEDICO_SENHA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editarConsultaParaRealizadaNoMesmoHorarioDeOutraNaoConflita() throws Exception {
        LocalDateTime dataHoraCompartilhada = LocalDateTime.now().plusDays(1);
        Long consultaId = registrarConsultaComoEnfermeiro(paciente1Id, medicoId, dataHoraCompartilhada);
        registrarConsultaComoEnfermeiro(paciente1Id, enfermeiroId, dataHoraCompartilhada);

        String corpo = """
                {"dataHora": "%s", "status": "REALIZADA", "observacoes": "atendido"}
                """.formatted(dataHoraCompartilhada.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        mockMvc.perform(put("/consultas/" + consultaId)
                        .with(httpBasic(MEDICO_EMAIL, MEDICO_SENHA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk());
    }
}
