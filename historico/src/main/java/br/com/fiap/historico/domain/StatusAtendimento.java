package br.com.fiap.historico.domain;

/**
 * Espelha o enum StatusConsulta do Servico de Agendamento (chega como String
 * dentro do evento). Mantido local porque este servico nao compartilha
 * biblioteca com o dominio do Agendamento.
 */
public enum StatusAtendimento {
    AGENDADA,
    REALIZADA,
    CANCELADA
}
