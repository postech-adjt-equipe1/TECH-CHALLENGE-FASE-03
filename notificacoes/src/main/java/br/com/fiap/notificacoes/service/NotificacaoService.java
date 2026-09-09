package br.com.fiap.notificacoes.service;

import br.com.fiap.notificacoes.domain.Notificacao;
import br.com.fiap.notificacoes.domain.StatusNotificacao;
import br.com.fiap.notificacoes.messaging.ConsultaEventoPayload;
import br.com.fiap.notificacoes.messaging.TipoEvento;
import br.com.fiap.notificacoes.repository.NotificacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificacaoService {

    private static final DateTimeFormatter FORMATO_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final NotificacaoRepository notificacaoRepository;

    public void processar(ConsultaEventoPayload payload) {
        if (notificacaoRepository.existsByEventoId(payload.eventoId())) {
            log.info("Evento {} ja processado anteriormente, ignorando (redelivery)", payload.eventoId());
            return;
        }

        String mensagem = montarMensagem(payload);

        Notificacao notificacao = new Notificacao(
                payload.eventoId(),
                payload.tipoEvento(),
                payload.consultaId(),
                payload.pacienteId(),
                payload.pacienteNome(),
                payload.pacienteEmail(),
                payload.dataHora(),
                mensagem,
                StatusNotificacao.ENVIADA
        );
        notificacaoRepository.save(notificacao);

        log.info("Lembrete enviado para {} <{}>: {}", payload.pacienteNome(), payload.pacienteEmail(), mensagem);
    }

    private String montarMensagem(ConsultaEventoPayload payload) {
        String dataHoraFormatada = payload.dataHora().format(FORMATO_DATA_HORA);
        if (payload.tipoEvento() == TipoEvento.CONSULTA_CRIADA) {
            return "Ola %s, sua consulta com %s foi agendada para %s. Por favor compareca no horario."
                    .formatted(payload.pacienteNome(), payload.profissionalNome(), dataHoraFormatada);
        }
        return "Ola %s, sua consulta com %s foi atualizada. Novo horario: %s (status: %s)."
                .formatted(payload.pacienteNome(), payload.profissionalNome(), dataHoraFormatada, payload.status());
    }
}
