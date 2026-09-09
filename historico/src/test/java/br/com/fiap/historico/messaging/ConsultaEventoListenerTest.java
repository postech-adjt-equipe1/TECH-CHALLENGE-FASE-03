package br.com.fiap.historico.messaging;

import br.com.fiap.historico.service.HistoricoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ConsultaEventoListenerTest {

    @Mock
    HistoricoService historicoService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Message message(String json) {
        return new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }

    @Test
    void eventoValido_eEncaminhadoParaProjecao() {
        ConsultaEventoListener listener = new ConsultaEventoListener(historicoService, objectMapper);
        String json = """
                {
                  "eventoId": "b3f1c2e4-1234-4a56-9abc-1234567890ab",
                  "tipoEvento": "CONSULTA_CRIADA",
                  "ocorreEm": "2026-08-08T12:00:00Z",
                  "consultaId": 1,
                  "dataHora": "2026-09-01T14:30:00",
                  "status": "AGENDADA",
                  "pacienteId": 3,
                  "pacienteNome": "Carla Pereira",
                  "pacienteEmail": "paciente1@hospital.com",
                  "profissionalId": 1,
                  "profissionalNome": "Dra. Ana Souza",
                  "observacoes": "Consulta de rotina"
                }
                """;

        listener.aoReceberEventoConsulta(message(json));

        ArgumentCaptor<ConsultaEventoPayload> captor = ArgumentCaptor.forClass(ConsultaEventoPayload.class);
        verify(historicoService).projetar(captor.capture());
        assertThat(captor.getValue().consultaId()).isEqualTo(1L);
        assertThat(captor.getValue().status()).isEqualTo("AGENDADA");
    }

    @Test
    void eventoMalformado_vaiParaDlqSemRequeue() {
        ConsultaEventoListener listener = new ConsultaEventoListener(historicoService, objectMapper);

        assertThatThrownBy(() -> listener.aoReceberEventoConsulta(message("{ isto nao e json")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verifyNoInteractions(historicoService);
    }
}
