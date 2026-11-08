package com.udea.demo.usuarios.interfaces.services;

import com.udea.demo.usuarios.application.dto.CompletarIdentidadRequestDTO;
import com.udea.demo.usuarios.application.dto.IdentidadPerfilResponseDTO;

/** HU-13: identidad del perfil del usuario autenticado. Siempre opera sobre el propio usuario, nunca por id. */
public interface IdentidadPerfilServiceI {
    IdentidadPerfilResponseDTO consultarPropia();

    IdentidadPerfilResponseDTO completarPropia(CompletarIdentidadRequestDTO dto, String origen);
}
