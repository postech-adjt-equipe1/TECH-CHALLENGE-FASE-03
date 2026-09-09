package br.com.fiap.agendamento.dto;

import br.com.fiap.agendamento.domain.Consulta;
import br.com.fiap.agendamento.domain.StatusConsulta;

import java.time.LocalDateTime;

public record ConsultaResponse(
        Long id,
        Long pacienteId,
        String pacienteNome,
        Long profissionalId,
        String profissionalNome,
        LocalDateTime dataHora,
        StatusConsulta status,
        String observacoes
) {
    public static ConsultaResponse from(Consulta consulta) {
        return new ConsultaResponse(
                consulta.getId(),
                consulta.getPaciente().getId(),
                consulta.getPaciente().getNome(),
                consulta.getProfissional().getId(),
                consulta.getProfissional().getNome(),
                consulta.getDataHora(),
                consulta.getStatus(),
                consulta.getObservacoes()
        );
    }
}
