package br.com.fiap.notificacoes.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
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

    @Value("${notificacoes.messaging.dead-letter-exchange}")
    private String deadLetterExchange;

    @Value("${notificacoes.messaging.dead-letter-queue}")
    private String deadLetterQueue;

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

    /**
     * Fila principal com dead-letter configurado: mensagem que o listener
     * rejeitar sem requeue (parsing invalido, ou falha transitoria depois de
     * esgotar as tentativas de retry) e roteada para a DLQ em vez de ficar
     * em loop de redelivery.
     */
    @Bean
    public Queue lembretesQueue() {
        return QueueBuilder.durable(queue)
                .deadLetterExchange(deadLetterExchange)
                .build();
    }

    @Bean
    public Binding lembretesBinding(Queue lembretesQueue, TopicExchange consultasExchange) {
        return BindingBuilder.bind(lembretesQueue).to(consultasExchange).with(routingKeyPattern);
    }

    @Bean
    public TopicExchange lembretesDeadLetterExchange() {
        return new TopicExchange(deadLetterExchange, true, false);
    }

    @Bean
    public Queue lembretesDeadLetterQueue() {
        return QueueBuilder.durable(deadLetterQueue).build();
    }

    @Bean
    public Binding lembretesDeadLetterBinding(Queue lembretesDeadLetterQueue, TopicExchange lembretesDeadLetterExchange) {
        return BindingBuilder.bind(lembretesDeadLetterQueue).to(lembretesDeadLetterExchange).with("#");
    }
}
