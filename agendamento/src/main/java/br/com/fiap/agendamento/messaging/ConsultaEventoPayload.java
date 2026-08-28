package br.com.fiap.agendamento.messaging;

import br.com.fiap.agendamento.domain.Consulta;
import br.com.fiap.agendamento.domain.StatusConsulta;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record ConsultaEventoPayload(
        UUID eventoId,
        TipoEvento tipoEvento,
        Instant ocorreEm,
        Long consultaId,
        LocalDateTime dataHora,
        StatusConsulta status,
        Long pacienteId,
        String pacienteNome,
        String pacienteEmail,
        Long profissionalId,
        String profissionalNome,
        String observacoes
) {

    public static ConsultaEventoPayload from(Consulta consulta, TipoEvento tipoEvento) {
        return new ConsultaEventoPayload(
                UUID.randomUUID(),
                tipoEvento,
                Instant.now(),
                consulta.getId(),
                consulta.getDataHora(),
                consulta.getStatus(),
                consulta.getPaciente().getId(),
                consulta.getPaciente().getNome(),
                consulta.getPaciente().getEmail(),
                consulta.getProfissional().getId(),
                consulta.getProfissional().getNome(),
                consulta.getObservacoes()
        );
    }
}
