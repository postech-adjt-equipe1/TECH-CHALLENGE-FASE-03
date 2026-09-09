package br.com.fiap.notificacoes.service;

import br.com.fiap.notificacoes.domain.Notificacao;
import br.com.fiap.notificacoes.domain.StatusNotificacao;
import br.com.fiap.notificacoes.messaging.TipoEvento;
import br.com.fiap.notificacoes.repository.NotificacaoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LembreteProximidadeSchedulerTest {

    @Mock
    NotificacaoRepository repository;

    private LembreteProximidadeScheduler scheduler() {
        LembreteProximidadeScheduler s = new LembreteProximidadeScheduler(repository);
        ReflectionTestUtils.setField(s, "janelaHoras", 24L);
        return s;
    }

    private Notificacao lembrete(Long consultaId, LocalDateTime dataHora) {
        return new Notificacao(UUID.randomUUID(), TipoEvento.CONSULTA_CRIADA, consultaId, 3L,
                "Carla Pereira", "paciente1@hospital.com", dataHora, "msg", StatusNotificacao.ENVIADA);
    }

    @Test
    void marcaComoEnviadoEPersisteApenasUmaVezPorConsulta() {
        LocalDateTime emBreve = LocalDateTime.now().plusHours(3);
        Notificacao criada = lembrete(10L, emBreve);
        Notificacao editada = lembrete(10L, emBreve);
        when(repository.findByLembreteProximidadeEnviadoFalseAndDataHoraConsultaBetweenOrderByDataHoraConsultaAsc(
                any(), any())).thenReturn(List.of(criada, editada));

        scheduler().dispararLembretesDeProximidade();

        assertThat(criada.isLembreteProximidadeEnviado()).isTrue();
        assertThat(editada.isLembreteProximidadeEnviado()).isTrue();
        ArgumentCaptor<List<Notificacao>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void semConsultasNaJanela_naoPersisteNada() {
        when(repository.findByLembreteProximidadeEnviadoFalseAndDataHoraConsultaBetweenOrderByDataHoraConsultaAsc(
                any(), any())).thenReturn(List.of());

        scheduler().dispararLembretesDeProximidade();

        verify(repository, never()).saveAll(any());
    }
}
