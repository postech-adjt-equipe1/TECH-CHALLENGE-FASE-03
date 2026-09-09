package br.com.fiap.notificacoes.dto;

import br.com.fiap.notificacoes.domain.Notificacao;
import br.com.fiap.notificacoes.domain.StatusNotificacao;
import br.com.fiap.notificacoes.messaging.TipoEvento;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record NotificacaoResponse(
        Long id,
        UUID eventoId,
        TipoEvento tipoEvento,
        Long consultaId,
        Long pacienteId,
        String pacienteNome,
        LocalDateTime dataHoraConsulta,
        String mensagem,
        StatusNotificacao status,
        Instant criadoEm
) {
    public static NotificacaoResponse from(Notificacao notificacao) {
        return new NotificacaoResponse(
                notificacao.getId(),
                notificacao.getEventoId(),
                notificacao.getTipoEvento(),
                notificacao.getConsultaId(),
                notificacao.getPacienteId(),
                notificacao.getPacienteNome(),
                notificacao.getDataHoraConsulta(),
                notificacao.getMensagem(),
                notificacao.getStatus(),
                notificacao.getCriadoEm()
        );
    }
}
