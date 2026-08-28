package br.com.fiap.agendamento.repository;

import br.com.fiap.agendamento.domain.Perfil;
import br.com.fiap.agendamento.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByPerfil(Perfil perfil);
}
