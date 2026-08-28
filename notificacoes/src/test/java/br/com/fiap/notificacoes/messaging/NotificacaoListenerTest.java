package br.com.fiap.notificacoes.messaging;

import br.com.fiap.notificacoes.service.NotificacaoService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificacaoListenerTest {

    @Mock
    private NotificacaoService notificacaoService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private NotificacaoListener listener;

    private Message mensagemJson(String json) {
        return new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }

    @Test
    void parseiaCorpoDaMensagemEDelegaParaOService() throws Exception {
        listener = new NotificacaoListener(notificacaoService, objectMapper);
        UUID eventoId = UUID.randomUUID();
        ConsultaEventoPayload payload = new ConsultaEventoPayload(
                eventoId, TipoEvento.CONSULTA_CRIADA, Instant.now(), 10L,
                LocalDateTime.of(2026, 9, 1, 14, 30), "AGENDADA",
                3L, "Carla Pereira", "paciente1@hospital.com",
                1L, "Dra. Ana Souza", "Consulta de rotina"
        );
        String json = objectMapper.writeValueAsString(payload);

        listener.aoReceberEventoConsulta(mensagemJson(json));

        ArgumentCaptor<ConsultaEventoPayload> captor = ArgumentCaptor.forClass(ConsultaEventoPayload.class);
        verify(notificacaoService).processar(captor.capture());
        assertThat(captor.getValue().eventoId()).isEqualTo(eventoId);
        assertThat(captor.getValue().pacienteNome()).isEqualTo("Carla Pereira");
    }

    @Test
    void naoRelancaExcecaoQuandoCorpoDaMensagemEstaMalFormado() {
        listener = new NotificacaoListener(notificacaoService, objectMapper);

        listener.aoReceberEventoConsulta(mensagemJson("{ isso nao e json valido"));

        verify(notificacaoService, never()).processar(org.mockito.ArgumentMatchers.any());
    }
}
