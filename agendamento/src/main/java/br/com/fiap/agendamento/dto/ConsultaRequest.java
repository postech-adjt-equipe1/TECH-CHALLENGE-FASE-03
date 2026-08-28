package br.com.fiap.agendamento.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record ConsultaRequest(
        @NotNull Long pacienteId,
        @NotNull Long profissionalId,
        @NotNull @Future LocalDateTime dataHora,
        String observacoes
) {
}
