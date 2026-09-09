package br.com.fiap.historico.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Autenticacao HTTP Basic para o endpoint GraphQL. Este servico nao tem
 * tabela de usuarios propria: reutiliza as mesmas credenciais semente do
 * Servico de Agendamento, injetadas por variavel de ambiente (as mesmas
 * {@code SEED_*} usadas la), de modo que o docker-compose define uma vez so.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService(
            PasswordEncoder encoder,
            @Value("${SEED_MEDICO_EMAIL:medico@hospital.com}") String medicoEmail,
            @Value("${SEED_MEDICO_SENHA:medico123}") String medicoSenha,
            @Value("${SEED_ENFERMEIRO_EMAIL:enfermeiro@hospital.com}") String enfermeiroEmail,
            @Value("${SEED_ENFERMEIRO_SENHA:enfermeiro123}") String enfermeiroSenha,
            @Value("${SEED_PACIENTE1_EMAIL:paciente1@hospital.com}") String paciente1Email,
            @Value("${SEED_PACIENTE1_SENHA:paciente123}") String paciente1Senha,
            @Value("${SEED_PACIENTE2_EMAIL:paciente2@hospital.com}") String paciente2Email,
            @Value("${SEED_PACIENTE2_SENHA:paciente123}") String paciente2Senha) {

        UserDetails medico = User.withUsername(medicoEmail).password(encoder.encode(medicoSenha)).roles("MEDICO").build();
        UserDetails enfermeiro = User.withUsername(enfermeiroEmail).password(encoder.encode(enfermeiroSenha)).roles("ENFERMEIRO").build();
        UserDetails paciente1 = User.withUsername(paciente1Email).password(encoder.encode(paciente1Senha)).roles("PACIENTE").build();
        UserDetails paciente2 = User.withUsername(paciente2Email).password(encoder.encode(paciente2Senha)).roles("PACIENTE").build();
        return new InMemoryUserDetailsManager(medico, enfermeiro, paciente1, paciente2);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/graphiql/**").permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(basic -> {});
        return http.build();
    }
}
