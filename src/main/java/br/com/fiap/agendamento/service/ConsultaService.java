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
import br.com.fiap.agendamento.messaging.ConsultaEventoPayload;
import br.com.fiap.agendamento.messaging.TipoEvento;
import br.com.fiap.agendamento.repository.ConsultaRepository;
import br.com.fiap.agendamento.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ConsultaService {

    private final ConsultaRepository consultaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ApplicationEventPublisher eventPublisher;

    public Consulta registrar(ConsultaRequest request) {
        Usuario paciente = buscarUsuarioComPerfil(request.pacienteId(), Perfil.PACIENTE);
        Usuario profissional = buscarProfissional(request.profissionalId());
        LocalDateTime dataHora = truncarParaMinuto(request.dataHora());
        validarConflito(profissional.getId(), dataHora, StatusConsulta.AGENDADA, null);

        Consulta consulta = Consulta.builder()
                .paciente(paciente)
                .profissional(profissional)
                .dataHora(dataHora)
                .status(StatusConsulta.AGENDADA)
                .observacoes(request.observacoes())
                .build();

        consulta = consultaRepository.save(consulta);
        publicarEvento(consulta, TipoEvento.CONSULTA_CRIADA);
        return consulta;
    }

    public Consulta editar(Long id, ConsultaUpdateRequest request) {
        Consulta consulta = buscarPorId(id);
        LocalDateTime dataHora = truncarParaMinuto(request.dataHora());
        validarConflito(consulta.getProfissional().getId(), dataHora, request.status(), consulta.getId());

        consulta.setDataHora(dataHora);
        consulta.setStatus(request.status());
        consulta.setObservacoes(request.observacoes());
        consulta = consultaRepository.save(consulta);
        publicarEvento(consulta, TipoEvento.CONSULTA_EDITADA);
        return consulta;
    }

    @Transactional(readOnly = true)
    public List<Consulta> listarTodas() {
        return consultaRepository.findAllByOrderByDataHoraAsc();
    }

    @Transactional(readOnly = true)
    public List<Consulta> listarMinhas(String emailPaciente) {
        return consultaRepository.findByPacienteEmailOrderByDataHoraAsc(emailPaciente);
    }

    @Transactional(readOnly = true)
    public Consulta buscarPorId(Long id) {
        return consultaRepository.findByIdFetched(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consulta nao encontrada: " + id));
    }

    @Transactional(readOnly = true)
    public Consulta buscarParaPaciente(Long id, String emailPaciente) {
        Consulta consulta = buscarPorId(id);
        if (!consulta.getPaciente().getEmail().equalsIgnoreCase(emailPaciente)) {
            throw new AccessDeniedException("Paciente so pode visualizar as proprias consultas");
        }
        return consulta;
    }

    private Usuario buscarUsuarioComPerfil(Long id, Perfil perfilEsperado) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Usuario nao encontrado: " + id));
        if (usuario.getPerfil() != perfilEsperado) {
            throw new BusinessException("Usuario " + id + " nao possui o perfil " + perfilEsperado);
        }
        return usuario;
    }

    private Usuario buscarProfissional(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Usuario nao encontrado: " + id));
        if (usuario.getPerfil() != Perfil.MEDICO && usuario.getPerfil() != Perfil.ENFERMEIRO) {
            throw new BusinessException("Usuario " + id + " nao e um profissional (medico ou enfermeiro)");
        }
        return usuario;
    }

    private LocalDateTime truncarParaMinuto(LocalDateTime dataHora) {
        return dataHora.truncatedTo(ChronoUnit.MINUTES);
    }

    private void validarConflito(Long profissionalId, LocalDateTime dataHora, StatusConsulta status, Long idExcluir) {
        if (status != StatusConsulta.AGENDADA) {
            return;
        }
        boolean conflito = (idExcluir == null)
                ? consultaRepository.existsConflitoAgendado(profissionalId, dataHora, StatusConsulta.AGENDADA)
                : consultaRepository.existsConflitoAgendadoExcluindo(profissionalId, dataHora, StatusConsulta.AGENDADA, idExcluir);
        if (conflito) {
            throw new BusinessException("Profissional " + profissionalId + " ja possui uma consulta agendada em " + dataHora);
        }
    }

    private void publicarEvento(Consulta consulta, TipoEvento tipoEvento) {
        eventPublisher.publishEvent(new ConsultaEvent(ConsultaEventoPayload.from(consulta, tipoEvento)));
    }
}
