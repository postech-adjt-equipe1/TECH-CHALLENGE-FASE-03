package br.com.fiap.notificacoes.repository;

import br.com.fiap.notificacoes.domain.Notificacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    boolean existsByEventoId(UUID eventoId);

    List<Notificacao> findAllByOrderByCriadoEmDesc();

    List<Notificacao> findByPacienteIdOrderByCriadoEmDesc(Long pacienteId);

    /**
     * Lembretes ainda nao "avisados" pelo job de proximidade cuja consulta
     * acontece na janela informada.
     */
    List<Notificacao> findByLembreteProximidadeEnviadoFalseAndDataHoraConsultaBetweenOrderByDataHoraConsultaAsc(
            LocalDateTime inicio, LocalDateTime fim);
}
