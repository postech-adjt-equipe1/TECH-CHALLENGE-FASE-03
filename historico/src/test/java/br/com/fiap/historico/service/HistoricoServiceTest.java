package br.com.fiap.historico.service;

import br.com.fiap.historico.domain.Atendimento;
import br.com.fiap.historico.domain.StatusAtendimento;
import br.com.fiap.historico.messaging.ConsultaEventoPayload;
import br.com.fiap.historico.messaging.TipoEvento;
import br.com.fiap.historico.repository.AtendimentoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoricoServiceTest {

    @Mock
    AtendimentoRepository repository;

    @InjectMocks
    HistoricoService service;

    private ConsultaEventoPayload evento(UUID eventoId, TipoEvento tipo, Instant ocorreEm, String status) {
        return new ConsultaEventoPayload(eventoId, tipo, ocorreEm, 10L,
                LocalDateTime.of(2026, 9, 1, 14, 30), status,
                3L, "Carla Pereira", "paciente1@hospital.com",
                1L, "Dra. Ana Souza", "Consulta de rotina");
    }

    @Test
    void projetar_consultaCriada_insereNovaProjecao() {
        when(repository.findById(10L)).thenReturn(Optional.empty());

        service.projetar(evento(UUID.randomUUID(), TipoEvento.CONSULTA_CRIADA, Instant.now(), "AGENDADA"));

        ArgumentCaptor<Atendimento> captor = ArgumentCaptor.forClass(Atendimento.class);
        verify(repository).save(captor.capture());
        Atendimento salvo = captor.getValue();
        assertThat(salvo.getConsultaId()).isEqualTo(10L);
        assertThat(salvo.getPacienteId()).isEqualTo(3L);
        assertThat(salvo.getStatus()).isEqualTo(StatusAtendimento.AGENDADA);
    }

    @Test
    void projetar_consultaEditada_atualizaProjecaoExistente() {
        Instant criado = Instant.parse("2026-09-01T10:00:00Z");
        Atendimento existente = new Atendimento(10L, 3L, "Carla Pereira", "paciente1@hospital.com",
                1L, "Dra. Ana Souza", LocalDateTime.of(2026, 9, 1, 14, 30),
                StatusAtendimento.AGENDADA, "Consulta de rotina", UUID.randomUUID(), criado);
        when(repository.findById(10L)).thenReturn(Optional.of(existente));

        service.projetar(evento(UUID.randomUUID(), TipoEvento.CONSULTA_EDITADA,
                criado.plusSeconds(3600), "REALIZADA"));

        verify(repository).save(existente);
        assertThat(existente.getStatus()).isEqualTo(StatusAtendimento.REALIZADA);
    }

    @Test
    void projetar_reentregaDoMesmoEvento_eIgnorada() {
        UUID eventoId = UUID.randomUUID();
        Instant ocorreEm = Instant.parse("2026-09-01T10:00:00Z");
        Atendimento existente = new Atendimento(10L, 3L, "Carla Pereira", "paciente1@hospital.com",
                1L, "Dra. Ana Souza", LocalDateTime.of(2026, 9, 1, 14, 30),
                StatusAtendimento.AGENDADA, null, eventoId, ocorreEm);
        when(repository.findById(10L)).thenReturn(Optional.of(existente));

        service.projetar(evento(eventoId, TipoEvento.CONSULTA_EDITADA, ocorreEm, "CANCELADA"));

        verify(repository, never()).save(any());
    }

    @Test
    void projetar_eventoForaDeOrdem_eIgnorado() {
        Instant maisNovo = Instant.parse("2026-09-01T12:00:00Z");
        Atendimento existente = new Atendimento(10L, 3L, "Carla Pereira", "paciente1@hospital.com",
                1L, "Dra. Ana Souza", LocalDateTime.of(2026, 9, 1, 14, 30),
                StatusAtendimento.REALIZADA, null, UUID.randomUUID(), maisNovo);
        when(repository.findById(10L)).thenReturn(Optional.of(existente));

        service.projetar(evento(UUID.randomUUID(), TipoEvento.CONSULTA_EDITADA,
                maisNovo.minusSeconds(3600), "AGENDADA"));

        verify(repository, never()).save(any());
    }

    @Test
    void pacienteIdPorEmail_semAtendimentos_negaAcesso() {
        when(repository.findFirstByPacienteEmailIgnoreCase("novo@hospital.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pacienteIdPorEmail("novo@hospital.com"))
                .hasMessageContaining("historico");
    }
}
