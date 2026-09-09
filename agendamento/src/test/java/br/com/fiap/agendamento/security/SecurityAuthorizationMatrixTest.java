package br.com.fiap.agendamento.security;

import br.com.fiap.agendamento.domain.Usuario;
import br.com.fiap.agendamento.repository.UsuarioRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matriz perfil x endpoint: valida que cada papel (Medico, Enfermeiro, Paciente)
 * so acessa exatamente o que a regra de negocio permite, alem dos casos 401/403.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityAuthorizationMatrixTest {

    private static final String MEDICO_EMAIL = "medico@hospital.com";
    private static final String MEDICO_SENHA = "medico123";
    private static final String ENFERMEIRO_EMAIL = "enfermeiro@hospital.com";
    private static final String ENFERMEIRO_SENHA = "enfermeiro123";
    private static final String PACIENTE1_EMAIL = "paciente1@hospital.com";
    private static final String PACIENTE1_SENHA = "paciente123";
    private static final String PACIENTE2_EMAIL = "paciente2@hospital.com";
    private static final String PACIENTE2_SENHA = "paciente123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ObjectMapper objectMapper;

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

    private String corpoNovaConsulta(Long pacienteId, Long profissionalId) {
        String dataHora = LocalDateTime.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return """
                {"pacienteId": %d, "profissionalId": %d, "dataHora": "%s", "observacoes": "consulta de rotina"}
                """.formatted(pacienteId, profissionalId, dataHora);
    }

    private Long registrarConsultaComoEnfermeiro(Long pacienteId, Long profissionalId) throws Exception {
        MvcResult result = mockMvc.perform(post("/consultas")
                        .with(httpBasic(ENFERMEIRO_EMAIL, ENFERMEIRO_SENHA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNovaConsulta(pacienteId, profissionalId)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    @Test
    void requisicaoSemCredenciaisRetorna401() throws Exception {
        mockMvc.perform(get("/consultas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void credenciaisInvalidasRetornam401() throws Exception {
        mockMvc.perform(get("/consultas").with(httpBasic(MEDICO_EMAIL, "senha-errada")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void medicoPodeListarHistoricoCompleto() throws Exception {
        mockMvc.perform(get("/consultas").with(httpBasic(MEDICO_EMAIL, MEDICO_SENHA)))
                .andExpect(status().isOk());
    }

    @Test
    void enfermeiroPodeListarHistoricoCompleto() throws Exception {
        mockMvc.perform(get("/consultas").with(httpBasic(ENFERMEIRO_EMAIL, ENFERMEIRO_SENHA)))
                .andExpect(status().isOk());
    }

    @Test
    void pacienteNaoPodeListarHistoricoCompleto() throws Exception {
        mockMvc.perform(get("/consultas").with(httpBasic(PACIENTE1_EMAIL, PACIENTE1_SENHA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void enfermeiroPodeRegistrarConsulta() throws Exception {
        mockMvc.perform(post("/consultas")
                        .with(httpBasic(ENFERMEIRO_EMAIL, ENFERMEIRO_SENHA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNovaConsulta(paciente1Id, medicoId)))
                .andExpect(status().isCreated());
    }

    @Test
    void medicoNaoPodeRegistrarConsulta() throws Exception {
        mockMvc.perform(post("/consultas")
                        .with(httpBasic(MEDICO_EMAIL, MEDICO_SENHA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNovaConsulta(paciente1Id, medicoId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void pacienteNaoPodeRegistrarConsulta() throws Exception {
        mockMvc.perform(post("/consultas")
                        .with(httpBasic(PACIENTE1_EMAIL, PACIENTE1_SENHA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNovaConsulta(paciente1Id, medicoId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void medicoPodeEditarConsultaExistente() throws Exception {
        Long consultaId = registrarConsultaComoEnfermeiro(paciente1Id, medicoId);
        String novaDataHora = LocalDateTime.now().plusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String corpo = """
                {"dataHora": "%s", "status": "REALIZADA", "observacoes": "reavaliado"}
                """.formatted(novaDataHora);

        mockMvc.perform(put("/consultas/" + consultaId)
                        .with(httpBasic(MEDICO_EMAIL, MEDICO_SENHA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REALIZADA"));
    }

    @Test
    void enfermeiroNaoPodeEditarConsulta() throws Exception {
        Long consultaId = registrarConsultaComoEnfermeiro(paciente1Id, medicoId);
        String corpo = """
                {"dataHora": "%s", "status": "REALIZADA", "observacoes": "tentativa"}
                """.formatted(LocalDateTime.now().plusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        mockMvc.perform(put("/consultas/" + consultaId)
                        .with(httpBasic(ENFERMEIRO_EMAIL, ENFERMEIRO_SENHA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isForbidden());
    }

    @Test
    void pacientePodeVerApenasAPropriaConsulta() throws Exception {
        Long consultaId = registrarConsultaComoEnfermeiro(paciente1Id, medicoId);

        mockMvc.perform(get("/consultas/" + consultaId).with(httpBasic(PACIENTE1_EMAIL, PACIENTE1_SENHA)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/consultas/" + consultaId).with(httpBasic(PACIENTE2_EMAIL, PACIENTE2_SENHA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void pacienteListaApenasAsProprasConsultasEmMe() throws Exception {
        registrarConsultaComoEnfermeiro(paciente1Id, medicoId);

        mockMvc.perform(get("/consultas/me").with(httpBasic(PACIENTE1_EMAIL, PACIENTE1_SENHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pacienteId").value(paciente1Id));
    }

    @Test
    void medicoNaoPodeAcessarConsultasMe() throws Exception {
        mockMvc.perform(get("/consultas/me").with(httpBasic(MEDICO_EMAIL, MEDICO_SENHA)))
                .andExpect(status().isForbidden());
    }
}
