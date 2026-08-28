package br.com.fiap.notificacoes.controller;

import br.com.fiap.notificacoes.domain.Notificacao;
import br.com.fiap.notificacoes.messaging.TipoEvento;
import br.com.fiap.notificacoes.repository.NotificacaoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificacaoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificacaoRepository notificacaoRepository;

    private Notificacao salvarNotificacao(Long pacienteId) {
        Notificacao notificacao = new Notificacao(
                UUID.randomUUID(), TipoEvento.CONSULTA_CRIADA, 10L, pacienteId,
                "Carla Pereira", "paciente1@hospital.com",
                LocalDateTime.of(2026, 9, 1, 14, 30),
                "Ola Carla Pereira, sua consulta foi agendada.",
                br.com.fiap.notificacoes.domain.StatusNotificacao.ENVIADA
        );
        return notificacaoRepository.save(notificacao);
    }

    @Test
    void listaTodasAsNotificacoes() throws Exception {
        salvarNotificacao(3L);

        mockMvc.perform(get("/notificacoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void buscaPorIdRetorna404QuandoNaoExiste() throws Exception {
        mockMvc.perform(get("/notificacoes/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void filtraPorPaciente() throws Exception {
        salvarNotificacao(3L);
        salvarNotificacao(4L);

        mockMvc.perform(get("/notificacoes/paciente/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].pacienteId").value(3));
    }
}
