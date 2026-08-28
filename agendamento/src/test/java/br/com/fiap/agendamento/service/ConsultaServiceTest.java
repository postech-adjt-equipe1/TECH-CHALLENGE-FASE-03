package br.com.fiap.agendamento.service;

import br.com.fiap.agendamento.domain.Consulta;
import br.com.fiap.agendamento.domain.Perfil;
import br.com.fiap.agendamento.domain.StatusConsulta;
import br.com.fiap.agendamento.domain.Usuario;
import br.com.fiap.agendamento.dto.ConsultaRequest;
import br.com.fiap.agendamento.dto.ConsultaUpdateRequest;
import br.com.fiap.agendamento.exception.BusinessException;
import br.com.fiap.agendamento.exception.ResourceNotFoundException;
import br.com.fiap.agendamento.messaging.ConsultaEvent;
import br.com.fiap.agendamento.messaging.TipoEvento;
import br.com.fiap.agendamento.repository.ConsultaRepository;
import br.com.fiap.agendamento.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultaServiceTest {

    @Mock
    private ConsultaRepository consultaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ConsultaService consultaService;

    private final LocalDateTime dataHora = LocalDateTime.now().plusDays(1).truncatedTo(ChronoUnit.MINUTES);

    private Usuario paciente() {
        return Usuario.builder().id(1L).nome("Carla Pereira").email("paciente1@hospital.com")
                .senha("hash").perfil(Perfil.PACIENTE).ativo(true).build();
    }

    private Usuario medico() {
        return Usuario.builder().id(2L).nome("Dra. Ana Souza").email("medico@hospital.com")
                .senha("hash").perfil(Perfil.MEDICO).ativo(true).build();
    }

    private Consulta consultaExistente() {
        return Consulta.builder().id(10L).paciente(paciente()).profissional(medico())
                .dataHora(dataHora).status(StatusConsulta.AGENDADA).observacoes("rotina").build();
    }

    @Test
    void registrarComSucessoSalvaEPublicaEventoDeConsultaCriada() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(paciente()));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(medico()));
        when(consultaRepository.existsConflitoAgendado(2L, dataHora, StatusConsulta.AGENDADA)).thenReturn(false);
        when(consultaRepository.save(any(Consulta.class))).thenAnswer(invocation -> {
            Consulta c = invocation.getArgument(0);
            c.setId(10L);
            return c;
        });

        Consulta resultado = consultaService.registrar(new ConsultaRequest(1L, 2L, dataHora, "rotina"));

        assertThat(resultado.getId()).isEqualTo(10L);
        assertThat(resultado.getStatus()).isEqualTo(StatusConsulta.AGENDADA);

        ArgumentCaptor<ConsultaEvent> captor = ArgumentCaptor.forClass(ConsultaEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().payload().tipoEvento()).isEqualTo(TipoEvento.CONSULTA_CRIADA);
        assertThat(captor.getValue().payload().consultaId()).isEqualTo(10L);
        assertThat(captor.getValue().payload().pacienteEmail()).isEqualTo("paciente1@hospital.com");
    }

    @Test
    void registrarLancaBusinessExceptionQuandoPacienteNaoExiste() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> consultaService.registrar(new ConsultaRequest(1L, 2L, dataHora, null)))
                .isInstanceOf(BusinessException.class);

        verify(consultaRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void registrarLancaBusinessExceptionQuandoUsuarioPacienteTemPerfilErrado() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(medico()));

        assertThatThrownBy(() -> consultaService.registrar(new ConsultaRequest(1L, 2L, dataHora, null)))
                .isInstanceOf(BusinessException.class);

        verify(consultaRepository, never()).save(any());
    }

    @Test
    void registrarLancaBusinessExceptionQuandoProfissionalNaoExisteOuNaoEhProfissional() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(paciente()));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(paciente()));

        assertThatThrownBy(() -> consultaService.registrar(new ConsultaRequest(1L, 2L, dataHora, null)))
                .isInstanceOf(BusinessException.class);

        verify(consultaRepository, never()).save(any());
    }

    @Test
    void registrarLancaBusinessExceptionQuandoHorarioConflitaComOutraConsultaDoMesmoProfissional() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(paciente()));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(medico()));
        when(consultaRepository.existsConflitoAgendado(2L, dataHora, StatusConsulta.AGENDADA)).thenReturn(true);

        assertThatThrownBy(() -> consultaService.registrar(new ConsultaRequest(1L, 2L, dataHora, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ja possui uma consulta agendada");

        verify(consultaRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void editarComSucessoSalvaEPublicaEventoDeConsultaEditada() {
        Consulta existente = consultaExistente();
        LocalDateTime novaDataHora = dataHora.plusHours(1);
        when(consultaRepository.findByIdFetched(10L)).thenReturn(Optional.of(existente));
        when(consultaRepository.existsConflitoAgendadoExcluindo(2L, novaDataHora, StatusConsulta.AGENDADA, 10L)).thenReturn(false);
        when(consultaRepository.save(any(Consulta.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Consulta resultado = consultaService.editar(10L,
                new ConsultaUpdateRequest(novaDataHora, StatusConsulta.AGENDADA, "reagendado"));

        assertThat(resultado.getDataHora()).isEqualTo(novaDataHora);
        assertThat(resultado.getObservacoes()).isEqualTo("reagendado");

        ArgumentCaptor<ConsultaEvent> captor = ArgumentCaptor.forClass(ConsultaEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().payload().tipoEvento()).isEqualTo(TipoEvento.CONSULTA_EDITADA);
    }

    @Test
    void editarLancaResourceNotFoundExceptionQuandoConsultaNaoExiste() {
        when(consultaRepository.findByIdFetched(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> consultaService.editar(99L,
                new ConsultaUpdateRequest(dataHora, StatusConsulta.AGENDADA, null)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(consultaRepository, never()).save(any());
    }

    @Test
    void editarLancaBusinessExceptionQuandoNovoHorarioConflitaComOutraConsultaDoMesmoProfissional() {
        Consulta existente = consultaExistente();
        LocalDateTime novaDataHora = dataHora.plusHours(2);
        when(consultaRepository.findByIdFetched(10L)).thenReturn(Optional.of(existente));
        when(consultaRepository.existsConflitoAgendadoExcluindo(2L, novaDataHora, StatusConsulta.AGENDADA, 10L)).thenReturn(true);

        assertThatThrownBy(() -> consultaService.editar(10L,
                new ConsultaUpdateRequest(novaDataHora, StatusConsulta.AGENDADA, null)))
                .isInstanceOf(BusinessException.class);

        verify(consultaRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void editarNaoValidaConflitoQuandoNovoStatusNaoEhAgendada() {
        Consulta existente = consultaExistente();
        when(consultaRepository.findByIdFetched(10L)).thenReturn(Optional.of(existente));
        when(consultaRepository.save(any(Consulta.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Consulta resultado = consultaService.editar(10L,
                new ConsultaUpdateRequest(dataHora, StatusConsulta.REALIZADA, "atendido"));

        assertThat(resultado.getStatus()).isEqualTo(StatusConsulta.REALIZADA);
        verify(consultaRepository, never()).existsConflitoAgendadoExcluindo(any(), any(), any(), any());
        verify(consultaRepository, never()).existsConflitoAgendado(any(), any(), any());
    }
}
