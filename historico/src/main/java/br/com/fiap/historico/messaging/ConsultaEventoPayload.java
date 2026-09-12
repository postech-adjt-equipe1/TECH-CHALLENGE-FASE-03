package br.com.fiap.historico.messaging;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Copia local do contrato publicado pelo Servico de Agendamento em
 * br.com.fiap.agendamento.messaging.ConsultaEventoPayload (ver
 * {@code agendamento/README.md}, secao "Eventos publicados (RabbitMQ)").
 * O campo {@code status} chega como String (nome do enum StatusConsulta do
 * Agendamento) porque este servico nao depende daquela classe de dominio.
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
