package br.com.fiap.historico.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Projecao (read model) de uma consulta, alimentada pelos eventos
 * {@code consulta.criada} / {@code consulta.editada} publicados pelo Servico
 * de Agendamento. A chave primaria e o proprio {@code consultaId} de origem —
 * assim {@code consulta.editada} apenas atualiza a linha existente e o
 * historico reflete sempre o estado corrente da consulta.
 */
@Entity
@Table(name = "atendimento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Atendimento {

    @Id
    @Column(name = "consulta_id")
    private Long consultaId;

    @Column(nullable = false)
    private Long pacienteId;

    @Column(nullable = false)
    private String pacienteNome;

    @Column(nullable = false)
    private String pacienteEmail;

    @Column(nullable = false)
    private Long profissionalId;

    @Column(nullable = false)
    private String profissionalNome;

    @Column(nullable = false)
    private LocalDateTime dataHora;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusAtendimento status;

    @Column(length = 1000)
    private String observacoes;

    /** Ultimo evento aplicado a esta projecao (idempotencia / ordenacao). */
    @Column(nullable = false)
    private UUID ultimoEventoId;

    /** Momento em que o ultimo evento aplicado ocorreu na origem. */
    @Column(nullable = false)
    private Instant ocorreEm;

    @Column(nullable = false)
    private Instant atualizadoEm;

    public Atendimento(Long consultaId, Long pacienteId, String pacienteNome, String pacienteEmail,
                       Long profissionalId, String profissionalNome, LocalDateTime dataHora,
                       StatusAtendimento status, String observacoes, UUID ultimoEventoId, Instant ocorreEm) {
        this.consultaId = consultaId;
        aplicar(pacienteId, pacienteNome, pacienteEmail, profissionalId, profissionalNome,
                dataHora, status, observacoes, ultimoEventoId, ocorreEm);
    }

    public void aplicar(Long pacienteId, String pacienteNome, String pacienteEmail,
                        Long profissionalId, String profissionalNome, LocalDateTime dataHora,
                        StatusAtendimento status, String observacoes, UUID ultimoEventoId, Instant ocorreEm) {
        this.pacienteId = pacienteId;
        this.pacienteNome = pacienteNome;
        this.pacienteEmail = pacienteEmail;
        this.profissionalId = profissionalId;
        this.profissionalNome = profissionalNome;
        this.dataHora = dataHora;
        this.status = status;
        this.observacoes = observacoes;
        this.ultimoEventoId = ultimoEventoId;
        this.ocorreEm = ocorreEm;
        this.atualizadoEm = Instant.now();
    }
}
