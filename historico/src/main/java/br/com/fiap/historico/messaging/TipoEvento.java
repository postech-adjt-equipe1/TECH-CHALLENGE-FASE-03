package br.com.fiap.historico.messaging;

/**
 * Espelha br.com.fiap.agendamento.messaging.TipoEvento (contrato do evento
 * publicado pelo Servico de Agendamento) — servicos independentes, sem
 * biblioteca compartilhada.
 */
public enum TipoEvento {
    CONSULTA_CRIADA,
    CONSULTA_EDITADA
}
