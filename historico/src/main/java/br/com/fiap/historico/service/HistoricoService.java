package br.com.fiap.historico.service;

import br.com.fiap.historico.domain.Atendimento;
import br.com.fiap.historico.domain.StatusAtendimento;
import br.com.fiap.historico.messaging.ConsultaEventoPayload;
import br.com.fiap.historico.repository.AtendimentoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class HistoricoService {

    private final AtendimentoRepository atendimentoRepository;

    /**
     * Aplica um evento de consulta a projecao. {@code consulta.criada} insere;
     * {@code consulta.editada} atualiza a linha existente (ou insere, se o
     * evento de criacao ainda nao chegou). Idempotente: reentrega do mesmo
     * evento, ou um evento mais antigo que o ultimo aplicado, e ignorada.
     */
    public void projetar(ConsultaEventoPayload payload) {
        Atendimento existente = atendimentoRepository.findById(payload.consultaId()).orElse(null);

        if (existente != null) {
            if (existente.getUltimoEventoId().equals(payload.eventoId())
                    || existente.getOcorreEm().isAfter(payload.ocorreEm())) {
                log.info("Evento {} ignorado (reentrega ou fora de ordem) para consulta {}",
                        payload.eventoId(), payload.consultaId());
                return;
            }
            existente.aplicar(payload.pacienteId(), payload.pacienteNome(), payload.pacienteEmail(),
                    payload.profissionalId(), payload.profissionalNome(), payload.dataHora(),
                    parseStatus(payload.status()), payload.observacoes(), payload.eventoId(), payload.ocorreEm());
            atendimentoRepository.save(existente);
            log.info("Projecao atualizada: consulta {} (status {})", payload.consultaId(), payload.status());
            return;
        }

        Atendimento novo = new Atendimento(payload.consultaId(), payload.pacienteId(), payload.pacienteNome(),
                payload.pacienteEmail(), payload.profissionalId(), payload.profissionalNome(), payload.dataHora(),
                parseStatus(payload.status()), payload.observacoes(), payload.eventoId(), payload.ocorreEm());
        atendimentoRepository.save(novo);
        log.info("Projecao criada: consulta {} para paciente {}", payload.consultaId(), payload.pacienteId());
    }

    @Transactional(readOnly = true)
    public List<Atendimento> historicoDoPaciente(Long pacienteId) {
        return atendimentoRepository.findByPacienteIdOrderByDataHoraAsc(pacienteId);
    }

    @Transactional(readOnly = true)
    public List<Atendimento> consultasFuturasDoPaciente(Long pacienteId, LocalDateTime referencia) {
        return atendimentoRepository.findByPacienteIdAndDataHoraAfterOrderByDataHoraAsc(pacienteId, referencia);
    }

    /**
     * Resolve o {@code pacienteId} do paciente autenticado a partir do e-mail
     * (que e o username). Usado para impedir que um PACIENTE consulte o
     * historico de outro (regra do enunciado: "paciente visualiza apenas as
     * suas consultas").
     */
    @Transactional(readOnly = true)
    public Long pacienteIdPorEmail(String email) {
        return atendimentoRepository.findFirstByPacienteEmailIgnoreCase(email)
                .map(Atendimento::getPacienteId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Paciente autenticado ainda nao possui atendimentos no historico"));
    }

    private StatusAtendimento parseStatus(String status) {
        return StatusAtendimento.valueOf(status);
    }
}
