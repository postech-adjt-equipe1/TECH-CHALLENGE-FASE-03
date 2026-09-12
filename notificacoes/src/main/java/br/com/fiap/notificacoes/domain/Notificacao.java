package br.com.fiap.notificacoes.domain;

import br.com.fiap.notificacoes.messaging.TipoEvento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notificacao", uniqueConstraints = @UniqueConstraint(columnNames = "evento_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evento_id", nullable = false)
    private UUID eventoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoEvento tipoEvento;

    @Column(nullable = false)
    private Long consultaId;

    @Column(nullable = false)
    private Long pacienteId;

    @Column(nullable = false)
    private String pacienteNome;

    @Column(nullable = false)
    private String pacienteEmail;

    @Column(nullable = false)
    private LocalDateTime dataHoraConsulta;

    @Column(nullable = false, length = 500)
    private String mensagem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusNotificacao status;

    @Column(nullable = false)
    private Instant criadoEm;

    /** Marca se o lembrete de proximidade (job agendado) ja foi disparado para esta consulta. */
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean lembreteProximidadeEnviado;

    public Notificacao(UUID eventoId, TipoEvento tipoEvento, Long consultaId, Long pacienteId,
                        String pacienteNome, String pacienteEmail, LocalDateTime dataHoraConsulta,
                        String mensagem, StatusNotificacao status) {
        this.eventoId = eventoId;
        this.tipoEvento = tipoEvento;
        this.consultaId = consultaId;
        this.pacienteId = pacienteId;
        this.pacienteNome = pacienteNome;
        this.pacienteEmail = pacienteEmail;
        this.dataHoraConsulta = dataHoraConsulta;
        this.mensagem = mensagem;
        this.status = status;
        this.criadoEm = Instant.now();
        this.lembreteProximidadeEnviado = false;
    }

    public void marcarLembreteProximidadeEnviado() {
        this.lembreteProximidadeEnviado = true;
    }
}
