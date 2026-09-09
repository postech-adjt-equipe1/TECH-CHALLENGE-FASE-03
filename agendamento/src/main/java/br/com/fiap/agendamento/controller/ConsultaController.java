package br.com.fiap.agendamento.controller;

import br.com.fiap.agendamento.domain.Consulta;
import br.com.fiap.agendamento.dto.ConsultaRequest;
import br.com.fiap.agendamento.dto.ConsultaResponse;
import br.com.fiap.agendamento.dto.ConsultaUpdateRequest;
import br.com.fiap.agendamento.service.ConsultaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/consultas")
@RequiredArgsConstructor
public class ConsultaController {

    private static final String ROLE_PACIENTE = "ROLE_PACIENTE";

    private final ConsultaService consultaService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConsultaResponse registrar(@Valid @RequestBody ConsultaRequest request) {
        Consulta consulta = consultaService.registrar(request);
        return ConsultaResponse.from(consulta);
    }

    @PutMapping("/{id}")
    public ConsultaResponse editar(@PathVariable Long id, @Valid @RequestBody ConsultaUpdateRequest request) {
        Consulta consulta = consultaService.editar(id, request);
        return ConsultaResponse.from(consulta);
    }

    @GetMapping
    public List<ConsultaResponse> listarTodas() {
        return consultaService.listarTodas().stream()
                .map(ConsultaResponse::from)
                .toList();
    }

    @GetMapping("/me")
    public List<ConsultaResponse> listarMinhas(Authentication authentication) {
        return consultaService.listarMinhas(authentication.getName()).stream()
                .map(ConsultaResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ConsultaResponse buscarPorId(@PathVariable Long id, Authentication authentication) {
        Consulta consulta = isPaciente(authentication)
                ? consultaService.buscarParaPaciente(id, authentication.getName())
                : consultaService.buscarPorId(id);
        return ConsultaResponse.from(consulta);
    }

    private boolean isPaciente(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_PACIENTE::equals);
    }
}
