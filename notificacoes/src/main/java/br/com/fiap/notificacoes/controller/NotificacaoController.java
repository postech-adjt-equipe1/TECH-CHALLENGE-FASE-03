package br.com.fiap.notificacoes.controller;

import br.com.fiap.notificacoes.domain.Notificacao;
import br.com.fiap.notificacoes.dto.NotificacaoResponse;
import br.com.fiap.notificacoes.exception.ResourceNotFoundException;
import br.com.fiap.notificacoes.repository.NotificacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints de leitura para inspecionar os lembretes ja processados
 * (usados na demonstracao/QA ponta a ponta). Este servico consome eventos
 * de outro servico e nao possui usuarios proprios, entao nao ha modelagem
 * de autenticacao/perfil aqui — decisao de escopo, nao lacuna esquecida.
 */
@RestController
@RequestMapping("/notificacoes")
@RequiredArgsConstructor
public class NotificacaoController {

    private final NotificacaoRepository notificacaoRepository;

    @GetMapping
    public List<NotificacaoResponse> listarTodas() {
        return notificacaoRepository.findAllByOrderByCriadoEmDesc().stream()
                .map(NotificacaoResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public NotificacaoResponse buscarPorId(@PathVariable Long id) {
        Notificacao notificacao = notificacaoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notificacao nao encontrada: " + id));
        return NotificacaoResponse.from(notificacao);
    }

    @GetMapping("/paciente/{pacienteId}")
    public List<NotificacaoResponse> listarPorPaciente(@PathVariable Long pacienteId) {
        return notificacaoRepository.findByPacienteIdOrderByCriadoEmDesc(pacienteId).stream()
                .map(NotificacaoResponse::from)
                .toList();
    }
}
