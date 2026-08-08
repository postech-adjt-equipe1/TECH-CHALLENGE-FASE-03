package br.com.fiap.agendamento.messaging;

public enum TipoEvento {

    CONSULTA_CRIADA("consulta.criada"),
    CONSULTA_EDITADA("consulta.editada");

    private final String routingKey;

    TipoEvento(String routingKey) {
        this.routingKey = routingKey;
    }

    public String routingKey() {
        return routingKey;
    }
}
