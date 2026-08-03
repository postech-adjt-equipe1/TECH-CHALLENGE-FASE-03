package br.com.fiap.agendamento.config;

import br.com.fiap.agendamento.security.JsonAccessDeniedHandler;
import br.com.fiap.agendamento.security.JsonAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        // Paciente ve apenas a propria lista de consultas
                        .requestMatchers(HttpMethod.GET, "/consultas/me").hasRole("PACIENTE")
                        // Medico e Enfermeiro consultam o historico completo; Paciente pode ver
                        // uma consulta especifica, mas a posse e validada no controller
                        .requestMatchers(HttpMethod.GET, "/consultas/{id}").hasAnyRole("MEDICO", "ENFERMEIRO", "PACIENTE")
                        .requestMatchers(HttpMethod.GET, "/consultas").hasAnyRole("MEDICO", "ENFERMEIRO")
                        // Enfermeiro registra novas consultas
                        .requestMatchers(HttpMethod.POST, "/consultas").hasRole("ENFERMEIRO")
                        // Medico edita o historico de consultas
                        .requestMatchers(HttpMethod.PUT, "/consultas/{id}").hasRole("MEDICO")
                        .anyRequest().authenticated()
                )
                .httpBasic(basic -> basic.authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                );
        return http.build();
    }
}
