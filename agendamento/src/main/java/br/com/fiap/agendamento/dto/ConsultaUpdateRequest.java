package br.com.fiap.agendamento.dto;

import br.com.fiap.agendamento.domain.StatusConsulta;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record ConsultaUpdateRequest(
        @NotNull LocalDateTime dataHora,
        @NotNull StatusConsulta status,
        String observacoes
) {
}
