package br.com.fiap.notificacoes.messaging;

import br.com.fiap.notificacoes.service.NotificacaoService;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Consome a mensagem como bytes crus (em vez de deixar o
 * MessageConverter resolver o tipo pelo header __TypeId__) porque o
 * publisher grava ali o nome da classe do Servico de Agendamento
 * (br.com.fiap.agendamento...), que nao existe neste servico. Assim o
 * parsing fica isolado do detalhe de serializacao do lado publicador.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificacaoListener {

    private final NotificacaoService notificacaoService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${notificacoes.messaging.queue}")
    public void aoReceberEventoConsulta(Message message) {
        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            ConsultaEventoPayload payload = objectMapper.readValue(json, ConsultaEventoPayload.class);
            notificacaoService.processar(payload);
        } catch (Exception ex) {
            // best-effort, no mesmo espirito da publicacao no Servico de Agendamento:
            // loga e descarta em vez de re-lancar (evita loop infinito de redelivery
            // para uma mensagem que nunca vai processar). Uma DLQ resolveria isso,
            // mas fica fora do escopo deste bloco.
            log.error("Falha ao processar evento de consulta recebido: {}", json, ex);
        }
    }
}
