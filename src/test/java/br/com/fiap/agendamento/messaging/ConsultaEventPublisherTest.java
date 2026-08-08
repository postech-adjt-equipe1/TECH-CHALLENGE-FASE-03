package br.com.fiap.agendamento.messaging;

import br.com.fiap.agendamento.domain.Consulta;
import br.com.fiap.agendamento.domain.Perfil;
import br.com.fiap.agendamento.domain.StatusConsulta;
import br.com.fiap.agendamento.domain.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConsultaEventPublisherTest {

    private static final String EXCHANGE = "agendamento.consultas";

    @Mock
    private RabbitTemplate rabbitTemplate;

    private ConsultaEventPublisher publisher;

    @BeforeEach
    void configurarPublisher() {
        publisher = new ConsultaEventPublisher(rabbitTemplate);
        ReflectionTestUtils.setField(publisher, "exchange", EXCHANGE);
    }

    private ConsultaEventoPayload payload(TipoEvento tipoEvento) {
        Usuario paciente = Usuario.builder().id(1L).nome("Carla Pereira").email("paciente1@hospital.com")
                .senha("hash").perfil(Perfil.PACIENTE).ativo(true).build();
        Usuario medico = Usuario.builder().id(2L).nome("Dra. Ana Souza").email("medico@hospital.com")
                .senha("hash").perfil(Perfil.MEDICO).ativo(true).build();
        Consulta consulta = Consulta.builder().id(10L).paciente(paciente).profissional(medico)
                .dataHora(LocalDateTime.now().plusDays(1)).status(StatusConsulta.AGENDADA).build();
        return ConsultaEventoPayload.from(consulta, tipoEvento);
    }

    @Test
    void publicaComARoutingKeyDoTipoDeEvento() {
        ConsultaEventoPayload payload = payload(TipoEvento.CONSULTA_CRIADA);

        publisher.aoConfirmarTransacao(new ConsultaEvent(payload));

        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq("consulta.criada"), eq(payload));
    }

    @Test
    void naoRelancaExcecaoQuandoRabbitTemplateFalha() {
        ConsultaEventoPayload payload = payload(TipoEvento.CONSULTA_EDITADA);
        doThrow(new RuntimeException("broker indisponivel"))
                .when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), eq(payload));

        publisher.aoConfirmarTransacao(new ConsultaEvent(payload));

        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq("consulta.editada"), eq(payload));
    }
}
