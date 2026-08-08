package br.com.fiap.agendamento.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConsultaEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${agendamento.messaging.exchange}")
    private String exchange;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoConfirmarTransacao(ConsultaEvent event) {
        ConsultaEventoPayload payload = event.payload();
        try {
            rabbitTemplate.convertAndSend(exchange, payload.tipoEvento().routingKey(), payload);
        } catch (Exception ex) {
            log.error("Falha ao publicar evento {} para consulta {}", payload.tipoEvento(), payload.consultaId(), ex);
        }
    }
}
