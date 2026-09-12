package br.com.fiap.historico.graphql;

import br.com.fiap.historico.domain.Atendimento;
import br.com.fiap.historico.domain.StatusAtendimento;

import java.time.format.DateTimeFormatter;

/**
 * Projecao exposta pelo GraphQL — desacopla o schema da entidade JPA
 * (esconde campos internos de controle como {@code ultimoEventoId}).
 */
public record AtendimentoView(
        String consultaId,
        String pacienteId,
        String pacienteNome,
        String pacienteEmail,
        String profissionalId,
        String profissionalNome,
        String dataHora,
        StatusAtendimento status,
        String observacoes
) {

    public static AtendimentoView from(Atendimento a) {
        return new AtendimentoView(
                String.valueOf(a.getConsultaId()),
                String.valueOf(a.getPacienteId()),
                a.getPacienteNome(),
                a.getPacienteEmail(),
                String.valueOf(a.getProfissionalId()),
                a.getProfissionalNome(),
                a.getDataHora().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                a.getStatus(),
                a.getObservacoes()
        );
    }
}
