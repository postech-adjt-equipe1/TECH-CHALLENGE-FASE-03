package br.com.fiap.notificacoes.service;

import br.com.fiap.notificacoes.domain.Notificacao;
import br.com.fiap.notificacoes.domain.StatusNotificacao;
import br.com.fiap.notificacoes.messaging.ConsultaEventoPayload;
import br.com.fiap.notificacoes.messaging.TipoEvento;
import br.com.fiap.notificacoes.repository.NotificacaoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacaoServiceTest {

    @Mock
    private NotificacaoRepository notificacaoRepository;

    private NotificacaoService service;

    private ConsultaEventoPayload payload(TipoEvento tipoEvento, UUID eventoId) {
        return new ConsultaEventoPayload(
                eventoId,
                tipoEvento,
                Instant.now(),
                10L,
                LocalDateTime.of(2026, 9, 1, 14, 30),
                "AGENDADA",
                3L,
                "Carla Pereira",
                "paciente1@hospital.com",
                1L,
                "Dra. Ana Souza",
                "Consulta de rotina"
        );
    }

    @Test
    void persisteNotificacaoParaConsultaCriada() {
        service = new NotificacaoService(notificacaoRepository);
        UUID eventoId = UUID.randomUUID();
        ConsultaEventoPayload payload = payload(TipoEvento.CONSULTA_CRIADA, eventoId);
        when(notificacaoRepository.existsByEventoId(eventoId)).thenReturn(false);

        service.processar(payload);

        ArgumentCaptor<Notificacao> captor = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoRepository).save(captor.capture());
        Notificacao salva = captor.getValue();
        assertThat(salva.getEventoId()).isEqualTo(eventoId);
        assertThat(salva.getStatus()).isEqualTo(StatusNotificacao.ENVIADA);
        assertThat(salva.getPacienteId()).isEqualTo(3L);
        assertThat(salva.getMensagem()).contains("Carla Pereira").contains("Dra. Ana Souza").contains("agendada");
    }

    @Test
    void mensagemDeEdicaoMencionaAtualizacaoENovoStatus() {
        service = new NotificacaoService(notificacaoRepository);
        UUID eventoId = UUID.randomUUID();
        ConsultaEventoPayload payload = payload(TipoEvento.CONSULTA_EDITADA, eventoId);
        when(notificacaoRepository.existsByEventoId(eventoId)).thenReturn(false);

        service.processar(payload);

        ArgumentCaptor<Notificacao> captor = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoRepository).save(captor.capture());
        assertThat(captor.getValue().getMensagem()).contains("atualizada").contains("AGENDADA");
    }

    @Test
    void ignoraEventoJaProcessadoAoInvesDeDuplicarNotificacao() {
        service = new NotificacaoService(notificacaoRepository);
        UUID eventoId = UUID.randomUUID();
        ConsultaEventoPayload payload = payload(TipoEvento.CONSULTA_CRIADA, eventoId);
        when(notificacaoRepository.existsByEventoId(eventoId)).thenReturn(true);

        service.processar(payload);

        verify(notificacaoRepository, never()).save(any());
    }
}
