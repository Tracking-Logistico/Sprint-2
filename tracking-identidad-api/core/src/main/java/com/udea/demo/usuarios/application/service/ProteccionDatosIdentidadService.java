package com.udea.demo.usuarios.application.service;

import com.udea.demo.usuarios.domain.exception.CifradoIdentidadException;
import com.udea.demo.usuarios.domain.exception.DatoIdentidadInvalidoException;
import com.udea.demo.usuarios.domain.model.CampoIdentidad;
import com.udea.demo.usuarios.domain.model.DatosIdentidadProtegidos;
import com.udea.demo.usuarios.domain.model.NumeroCedula;
import com.udea.demo.usuarios.domain.model.Usuario;
import com.udea.demo.usuarios.interfaces.services.CifradorDatosIdentidadI;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * Valida y protege la cédula y la fecha de nacimiento antes de persistirlas (HU-13, CA-1 y CA-8). Las
 * tareas de registro y perfil (HU-01A/B) deben usar este servicio en lugar de llamar al cifrador directamente.
 */
@Service
public class ProteccionDatosIdentidadService {
    public static final String CAMPO_FECHA_NACIMIENTO = "fechaNacimiento";
    static final LocalDate FECHA_NACIMIENTO_MINIMA = LocalDate.of(1900, 1, 1);

    private final CifradorDatosIdentidadI cifrador;

    public ProteccionDatosIdentidadService(CifradorDatosIdentidadI cifrador) {
        this.cifrador = cifrador;
    }

    /**
     * Valida, normaliza y cifra los datos de identidad.
     *
     * @param hoy fecha actual en la zona operativa. La recibe del llamador para que sea determinista en pruebas.
     */
    public DatosIdentidadProtegidos proteger(String numeroCedula, LocalDate fechaNacimiento, LocalDate hoy) {
        Objects.requireNonNull(hoy, "hoy");
        NumeroCedula cedula = NumeroCedula.de(numeroCedula);
        validarFechaNacimiento(fechaNacimiento, hoy);
        return new DatosIdentidadProtegidos(
                cifrador.cifrar(cedula.valor(), CampoIdentidad.NUMERO_CEDULA),
                cifrador.indiceCiegoCedula(cedula.valor()),
                cifrador.cifrar(fechaNacimiento.toString(), CampoIdentidad.FECHA_NACIMIENTO));
    }

    /** Índice ciego de una cédula recibida, para comprobar la unicidad antes de insertar. */
    public String indiceCiego(String numeroCedula) {
        return cifrador.indiceCiegoCedula(NumeroCedula.de(numeroCedula).valor());
    }

    public NumeroCedula cedulaDe(Usuario usuario) {
        exigirDatos(usuario);
        return NumeroCedula.de(cifrador.descifrar(usuario.getNumeroCedulaCifrado(), CampoIdentidad.NUMERO_CEDULA));
    }

    public LocalDate fechaNacimientoDe(Usuario usuario) {
        exigirDatos(usuario);
        String fecha = cifrador.descifrar(usuario.getFechaNacimientoCifrada(), CampoIdentidad.FECHA_NACIMIENTO);
        try {
            return LocalDate.parse(fecha);
        } catch (DateTimeParseException ex) {
            throw new CifradoIdentidadException("La fecha de nacimiento protegida no es válida");
        }
    }

    static void validarFechaNacimiento(LocalDate fechaNacimiento, LocalDate hoy) {
        if (fechaNacimiento == null) {
            throw new DatoIdentidadInvalidoException(CAMPO_FECHA_NACIMIENTO, "La fecha de nacimiento es obligatoria");
        }
        if (fechaNacimiento.isAfter(hoy)) {
            throw new DatoIdentidadInvalidoException(CAMPO_FECHA_NACIMIENTO,
                    "La fecha de nacimiento no puede ser futura");
        }
        if (fechaNacimiento.isBefore(FECHA_NACIMIENTO_MINIMA)) {
            throw new DatoIdentidadInvalidoException(CAMPO_FECHA_NACIMIENTO,
                    "La fecha de nacimiento no es una fecha válida");
        }
    }

    private static void exigirDatos(Usuario usuario) {
        if (usuario == null || !usuario.tieneDatosIdentidad()) {
            throw new CifradoIdentidadException("El usuario no tiene datos de identidad registrados");
        }
    }
}
