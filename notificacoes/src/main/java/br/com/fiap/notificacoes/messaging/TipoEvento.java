package br.com.fiap.notificacoes.messaging;

/**
 * Espelha br.com.fiap.agendamento.messaging.TipoEvento (contrato do evento
 * publicado pelo Servico de Agendamento) — os dois servicos evoluem em
 * repositorios/branches independentes, sem biblioteca compartilhada.
 */
public enum TipoEvento {
    CONSULTA_CRIADA,
    CONSULTA_EDITADA
}
