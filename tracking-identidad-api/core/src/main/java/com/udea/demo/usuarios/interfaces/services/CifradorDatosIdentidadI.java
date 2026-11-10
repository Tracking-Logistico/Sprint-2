package com.udea.demo.usuarios.interfaces.services;

import com.udea.demo.usuarios.domain.model.CampoIdentidad;

/** Puerto de cifrado de datos de identidad (HU-13, CA-8). Las claves nunca salen de la implementación. */
public interface CifradorDatosIdentidadI {

    /** Cifrado autenticado y no determinista: el mismo dato produce un texto cifrado distinto cada vez. */
    String cifrar(String textoClaro, CampoIdentidad campo);

    /** Descifra y comprueba la integridad. Lanza CifradoIdentidadException si el texto fue manipulado. */
    String descifrar(String textoCifrado, CampoIdentidad campo);

    /** Índice ciego determinista (HMAC-SHA256, hex en minúsculas) para la unicidad y las búsquedas exactas. */
    String indiceCiegoCedula(String cedulaNormalizada);
}
