package br.com.fiap.notificacoes.service;

import br.com.fiap.notificacoes.domain.Notificacao;
import br.com.fiap.notificacoes.repository.NotificacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Alem do lembrete disparado no momento em que a consulta e criada/editada
 * (event-driven, no {@link NotificacaoService}), este job varre
 * periodicamente as consultas que vao acontecer dentro de uma janela
 * (default: proximas 24h) e dispara um lembrete extra de proximidade ao
 * paciente — atendendo ao requisito "lembretes automaticos sobre consultas
 * futuras". Idempotente: cada consulta so recebe o lembrete de proximidade
 * uma vez (flag {@code lembreteProximidadeEnviado}).
 */
@Component
@ConditionalOnProperty(name = "lembrete.proximidade.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class LembreteProximidadeScheduler {

    private static final DateTimeFormatter FORMATO_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final NotificacaoRepository notificacaoRepository;

    @Value("${lembrete.proximidade.janela-horas:24}")
    private long janelaHoras;

    @Scheduled(
            fixedDelayString = "${lembrete.proximidade.intervalo-ms:3600000}",
            initialDelayString = "${lembrete.proximidade.intervalo-ms:3600000}")
    @Transactional
    public void dispararLembretesDeProximidade() {
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime limite = agora.plusHours(janelaHoras);

        List<Notificacao> pendentes = notificacaoRepository
                .findByLembreteProximidadeEnviadoFalseAndDataHoraConsultaBetweenOrderByDataHoraConsultaAsc(agora, limite);
        if (pendentes.isEmpty()) {
            return;
        }

        Set<Long> consultasAvisadas = new HashSet<>();
        for (Notificacao notificacao : pendentes) {
            if (consultasAvisadas.add(notificacao.getConsultaId())) {
                log.info("Lembrete de proximidade para {} <{}>: sua consulta {} esta marcada para {} (menos de {}h).",
                        notificacao.getPacienteNome(), notificacao.getPacienteEmail(), notificacao.getConsultaId(),
                        notificacao.getDataHoraConsulta().format(FORMATO_DATA_HORA), janelaHoras);
            }
            notificacao.marcarLembreteProximidadeEnviado();
        }
        notificacaoRepository.saveAll(pendentes);
    }
}
