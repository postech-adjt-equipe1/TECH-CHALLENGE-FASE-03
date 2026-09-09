package br.com.fiap.notificacoes.repository;

import br.com.fiap.notificacoes.domain.Notificacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    boolean existsByEventoId(UUID eventoId);

    List<Notificacao> findAllByOrderByCriadoEmDesc();

    List<Notificacao> findByPacienteIdOrderByCriadoEmDesc(Long pacienteId);
}
