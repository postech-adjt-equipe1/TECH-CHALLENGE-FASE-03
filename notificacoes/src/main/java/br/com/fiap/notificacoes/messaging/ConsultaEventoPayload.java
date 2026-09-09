package br.com.fiap.notificacoes.messaging;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Copia local do contrato publicado pelo Servico de Agendamento em
 * br.com.fiap.agendamento.messaging.ConsultaEventoPayload (ver README do
 * bloco de Agendamento, secao "Eventos publicados (RabbitMQ)"). O campo
 * "status" chega como String (nome do enum StatusConsulta do Agendamento)
 * pois este servico nao depende da classe daquele dominio.
 */
public record ConsultaEventoPayload(
        UUID eventoId,
        TipoEvento tipoEvento,
        Instant ocorreEm,
        Long consultaId,
        LocalDateTime dataHora,
        String status,
        Long pacienteId,
        String pacienteNome,
        String pacienteEmail,
        Long profissionalId,
        String profissionalNome,
        String observacoes
) {
}
