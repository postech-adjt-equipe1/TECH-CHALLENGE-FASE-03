package br.com.fiap.historico.messaging;

import br.com.fiap.historico.service.HistoricoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * Consome a mensagem como bytes crus (em vez de deixar o MessageConverter
 * resolver o tipo pelo header {@code __TypeId__}) porque o publisher grava ali
 * o nome da classe do Servico de Agendamento, que nao existe neste servico.
 *
 * <p>Tratamento de falha:
 * <ul>
 *   <li>parsing malformado = erro <b>permanente</b>: rejeita sem requeue
 *       ({@link AmqpRejectAndDontRequeueException}) e a mensagem vai direto
 *       para a DLQ — reprocessar nao adianta.</li>
 *   <li>qualquer outra falha (ex.: banco indisponivel) = erro <b>transitorio</b>:
 *       relanca; o interceptor de retry do Spring AMQP tenta de novo algumas
 *       vezes (ver {@code application.properties}) e, esgotadas as tentativas,
 *       a mensagem tambem cai na DLQ.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConsultaEventoListener {

    private final HistoricoService historicoService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${historico.messaging.queue}")
    public void aoReceberEventoConsulta(Message message) {
        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        ConsultaEventoPayload payload;
        try {
            payload = objectMapper.readValue(json, ConsultaEventoPayload.class);
        } catch (RuntimeException ex) {
            log.error("Evento de consulta malformado, enviando para a DLQ: {}", json, ex);
            throw new AmqpRejectAndDontRequeueException("Payload de evento invalido", ex);
        }
        historicoService.projetar(payload);
    }
}
