package br.com.fiap.agendamento.security;

import br.com.fiap.agendamento.domain.Perfil;
import br.com.fiap.agendamento.domain.Usuario;
import br.com.fiap.agendamento.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    @Test
    void carregaUsuarioExistenteComAAuthorityCorrespondenteAoPerfil() {
        Usuario usuario = Usuario.builder()
                .id(1L)
                .nome("Dra. Ana Souza")
                .email("medico@hospital.com")
                .senha("hash")
                .perfil(Perfil.MEDICO)
                .ativo(true)
                .build();
        when(usuarioRepository.findByEmail("medico@hospital.com")).thenReturn(Optional.of(usuario));

        UserDetails userDetails = userDetailsService.loadUserByUsername("medico@hospital.com");

        assertThat(userDetails.getUsername()).isEqualTo("medico@hospital.com");
        assertThat(userDetails.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_MEDICO");
    }

    @Test
    void lancaExcecaoQuandoUsuarioNaoExiste() {
        when(usuarioRepository.findByEmail("inexistente@hospital.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("inexistente@hospital.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
