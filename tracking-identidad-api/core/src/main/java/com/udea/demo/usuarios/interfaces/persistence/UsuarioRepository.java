package com.udea.demo.usuarios.interfaces.persistence;

import com.udea.demo.usuarios.domain.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    boolean existsByEmail(String email);

    Optional<Usuario> findByEmail(String email);

    /** HU-13: unicidad de la cédula por su índice ciego. La constraint uk_usuarios_numero_cedula_hash es la garantía final. */
    boolean existsByNumeroCedulaHash(String numeroCedulaHash);
}