package br.com.fiap.agendamento.config;

import br.com.fiap.agendamento.domain.Perfil;
import br.com.fiap.agendamento.domain.Usuario;
import br.com.fiap.agendamento.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Popula usuarios de demonstracao no primeiro start, apenas se a base estiver vazia.
 * Nenhuma senha e fixada em codigo: os valores abaixo sao apenas defaults de
 * desenvolvimento, sobrescritos em outros ambientes pelas variaveis de ambiente indicadas.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DemoUsersSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${SEED_MEDICO_EMAIL:medico@hospital.com}")
    private String medicoEmail;
    @Value("${SEED_MEDICO_SENHA:medico123}")
    private String medicoSenha;

    @Value("${SEED_ENFERMEIRO_EMAIL:enfermeiro@hospital.com}")
    private String enfermeiroEmail;
    @Value("${SEED_ENFERMEIRO_SENHA:enfermeiro123}")
    private String enfermeiroSenha;

    @Value("${SEED_PACIENTE1_EMAIL:paciente1@hospital.com}")
    private String paciente1Email;
    @Value("${SEED_PACIENTE1_SENHA:paciente123}")
    private String paciente1Senha;

    @Value("${SEED_PACIENTE2_EMAIL:paciente2@hospital.com}")
    private String paciente2Email;
    @Value("${SEED_PACIENTE2_SENHA:paciente123}")
    private String paciente2Senha;

    @Override
    public void run(String... args) {
        if (usuarioRepository.count() > 0) {
            return;
        }

        seed("Dra. Ana Souza", medicoEmail, medicoSenha, Perfil.MEDICO);
        seed("Enf. Bruno Lima", enfermeiroEmail, enfermeiroSenha, Perfil.ENFERMEIRO);
        seed("Carla Pereira", paciente1Email, paciente1Senha, Perfil.PACIENTE);
        seed("Diego Santos", paciente2Email, paciente2Senha, Perfil.PACIENTE);

        log.info("Usuarios de demonstracao criados (ver README para as credenciais).");
    }

    private void seed(String nome, String email, String senhaPlana, Perfil perfil) {
        Usuario usuario = Usuario.builder()
                .nome(nome)
                .email(email)
                .senha(passwordEncoder.encode(senhaPlana))
                .perfil(perfil)
                .ativo(true)
                .build();
        usuarioRepository.save(usuario);
    }
}
