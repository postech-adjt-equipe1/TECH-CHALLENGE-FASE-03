package br.com.fiap.historico.graphql;

import br.com.fiap.historico.service.HistoricoService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Consultas GraphQL sobre o historico medico. Regras de acesso (enunciado):
 * <ul>
 *   <li>MEDICO / ENFERMEIRO: consultam o historico de qualquer paciente
 *       (argumento {@code pacienteId} obrigatorio);</li>
 *   <li>PACIENTE: consulta apenas o proprio historico — o {@code pacienteId}
 *       e resolvido pelo usuario autenticado; informar o id de outro paciente
 *       resulta em acesso negado.</li>
 * </ul>
 */
@Controller
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class HistoricoGraphQlController {

    private static final String ROLE_PACIENTE = "ROLE_PACIENTE";

    private final HistoricoService historicoService;

    @QueryMapping
    public List<AtendimentoView> historicoPaciente(@Argument Long pacienteId, Authentication authentication) {
        Long alvo = resolverPacienteId(pacienteId, authentication);
        return historicoService.historicoDoPaciente(alvo).stream()
                .map(AtendimentoView::from)
                .toList();
    }

    @QueryMapping
    public List<AtendimentoView> consultasFuturas(@Argument Long pacienteId, Authentication authentication) {
        Long alvo = resolverPacienteId(pacienteId, authentication);
        return historicoService.consultasFuturasDoPaciente(alvo, LocalDateTime.now()).stream()
                .map(AtendimentoView::from)
                .toList();
    }

    private Long resolverPacienteId(Long solicitado, Authentication authentication) {
        if (isPaciente(authentication)) {
            Long proprio = historicoService.pacienteIdPorEmail(authentication.getName());
            if (solicitado != null && !solicitado.equals(proprio)) {
                throw new AccessDeniedException("Paciente so pode consultar o proprio historico");
            }
            return proprio;
        }
        if (solicitado == null) {
            throw new IllegalArgumentException("pacienteId e obrigatorio para o perfil informado");
        }
        return solicitado;
    }

    private boolean isPaciente(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_PACIENTE::equals);
    }
}
