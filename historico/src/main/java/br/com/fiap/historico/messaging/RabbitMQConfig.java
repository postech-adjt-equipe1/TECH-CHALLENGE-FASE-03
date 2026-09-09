package br.com.fiap.historico.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Liga a fila deste servico ao exchange {@code agendamento.consultas} do
 * Servico de Agendamento e configura o par dead-letter: mensagens que o
 * listener rejeitar (falha transitoria apos esgotar as tentativas, ou falha
 * permanente de parsing) caem em {@code historico.consultas.dlq} em vez de
 * ficarem em loop de redelivery.
 */
@Configuration
public class RabbitMQConfig {

    @Value("${historico.messaging.exchange}")
    private String exchange;

    @Value("${historico.messaging.queue}")
    private String queue;

    @Value("${historico.messaging.routing-key-pattern}")
    private String routingKeyPattern;

    @Value("${historico.messaging.dead-letter-exchange}")
    private String deadLetterExchange;

    @Value("${historico.messaging.dead-letter-queue}")
    private String deadLetterQueue;

    /**
     * Mesmo nome/durabilidade do TopicExchange declarado pelo Servico de
     * Agendamento — os dois lados precisam declarar identico, senao o broker
     * rejeita com PRECONDITION_FAILED.
     */
    @Bean
    public TopicExchange consultasExchange() {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public Queue atendimentosQueue() {
        return QueueBuilder.durable(queue)
                .deadLetterExchange(deadLetterExchange)
                .build();
    }

    @Bean
    public Binding atendimentosBinding(Queue atendimentosQueue, TopicExchange consultasExchange) {
        return BindingBuilder.bind(atendimentosQueue).to(consultasExchange).with(routingKeyPattern);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(deadLetterExchange, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(deadLetterQueue).build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with("#");
    }
}
