package br.com.fiap.agendamento.repository;

import br.com.fiap.agendamento.domain.Consulta;
import br.com.fiap.agendamento.domain.StatusConsulta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ConsultaRepository extends JpaRepository<Consulta, Long> {

    @Query("select c from Consulta c join fetch c.paciente join fetch c.profissional " +
            "where c.paciente.email = :email order by c.dataHora asc")
    List<Consulta> findByPacienteEmailOrderByDataHoraAsc(String email);

    @Query("select c from Consulta c join fetch c.paciente join fetch c.profissional order by c.dataHora asc")
    List<Consulta> findAllByOrderByDataHoraAsc();

    @Query("select c from Consulta c join fetch c.paciente join fetch c.profissional where c.id = :id")
    Optional<Consulta> findByIdFetched(Long id);

    @Query("select count(c) > 0 from Consulta c where c.profissional.id = :profissionalId " +
            "and c.dataHora = :dataHora and c.status = :status")
    boolean existsConflitoAgendado(Long profissionalId, LocalDateTime dataHora, StatusConsulta status);

    @Query("select count(c) > 0 from Consulta c where c.profissional.id = :profissionalId " +
            "and c.dataHora = :dataHora and c.status = :status and c.id <> :idExcluir")
    boolean existsConflitoAgendadoExcluindo(Long profissionalId, LocalDateTime dataHora, StatusConsulta status, Long idExcluir);
}
