package br.com.fiap.agendamento.service;

import br.com.fiap.agendamento.domain.Consulta;
import br.com.fiap.agendamento.domain.Perfil;
import br.com.fiap.agendamento.domain.Usuario;
import br.com.fiap.agendamento.dto.ConsultaRequest;
import br.com.fiap.agendamento.dto.ConsultaUpdateRequest;
import br.com.fiap.agendamento.exception.BusinessException;
import br.com.fiap.agendamento.exception.ResourceNotFoundException;
import br.com.fiap.agendamento.repository.ConsultaRepository;
import br.com.fiap.agendamento.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ConsultaService {

    private final ConsultaRepository consultaRepository;
    private final UsuarioRepository usuarioRepository;

    public Consulta registrar(ConsultaRequest request) {
        Usuario paciente = buscarUsuarioComPerfil(request.pacienteId(), Perfil.PACIENTE);
        Usuario profissional = buscarProfissional(request.profissionalId());

        Consulta consulta = Consulta.builder()
                .paciente(paciente)
                .profissional(profissional)
                .dataHora(request.dataHora())
                .status(br.com.fiap.agendamento.domain.StatusConsulta.AGENDADA)
                .observacoes(request.observacoes())
                .build();

        return consultaRepository.save(consulta);
    }

    public Consulta editar(Long id, ConsultaUpdateRequest request) {
        Consulta consulta = buscarPorId(id);
        consulta.setDataHora(request.dataHora());
        consulta.setStatus(request.status());
        consulta.setObservacoes(request.observacoes());
        return consultaRepository.save(consulta);
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
}
