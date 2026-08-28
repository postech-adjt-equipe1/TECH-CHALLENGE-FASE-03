package br.com.fiap.notificacoes.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${notificacoes.messaging.exchange}")
    private String exchange;

    @Value("${notificacoes.messaging.queue}")
    private String queue;

    @Value("${notificacoes.messaging.routing-key-pattern}")
    private String routingKeyPattern;

    /**
     * Mesmo nome/durabilidade do TopicExchange declarado pelo Servico de
     * Agendamento (RabbitMQConfig daquele servico) — os dois lados precisam
     * declarar o exchange de forma identica, senao o broker rejeita a
     * declaracao com PRECONDITION_FAILED.
     */
    @Bean
    public TopicExchange consultasExchange() {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public Queue lembretesQueue() {
        return new Queue(queue, true);
    }

    @Bean
    public Binding lembretesBinding(Queue lembretesQueue, TopicExchange consultasExchange) {
        return BindingBuilder.bind(lembretesQueue).to(consultasExchange).with(routingKeyPattern);
    }
}
