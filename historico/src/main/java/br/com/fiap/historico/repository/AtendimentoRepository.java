package br.com.fiap.historico.repository;

import br.com.fiap.historico.domain.Atendimento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AtendimentoRepository extends JpaRepository<Atendimento, Long> {

    List<Atendimento> findByPacienteIdOrderByDataHoraAsc(Long pacienteId);

    List<Atendimento> findByPacienteIdAndDataHoraAfterOrderByDataHoraAsc(Long pacienteId, LocalDateTime referencia);

    Optional<Atendimento> findFirstByPacienteEmailIgnoreCase(String pacienteEmail);
}
