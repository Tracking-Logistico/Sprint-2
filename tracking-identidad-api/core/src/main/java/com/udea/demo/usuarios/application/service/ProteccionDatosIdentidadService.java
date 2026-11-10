package com.udea.demo.usuarios.application.service;

import com.udea.demo.usuarios.domain.exception.CifradoIdentidadException;
import com.udea.demo.usuarios.domain.exception.DatoIdentidadInvalidoException;
import com.udea.demo.usuarios.domain.model.CampoIdentidad;
import com.udea.demo.usuarios.domain.model.DatosIdentidadProtegidos;
import com.udea.demo.usuarios.domain.model.NumeroCedula;
import com.udea.demo.usuarios.domain.model.Usuario;
import com.udea.demo.usuarios.interfaces.services.CifradorDatosIdentidadI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Objects;

/**
 * Valida y protege la cédula y la fecha de nacimiento antes de persistirlas (HU-13, CA-1 y CA-8). Las
 * tareas de registro y perfil (HU-01A/B) deben usar este servicio en lugar de llamar al cifrador directamente.
 */
@Service
public class ProteccionDatosIdentidadService {
    public static final String CAMPO_FECHA_NACIMIENTO = "fechaNacimiento";
    static final LocalDate FECHA_NACIMIENTO_MINIMA = LocalDate.of(1900, 1, 1);
    private static final String ZONA_POR_DEFECTO = "America/Bogota";
    /** ISO estricto: año de 4 dígitos, sin signo y sin ajustar días inexistentes (30 de febrero). */
    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private final CifradorDatosIdentidadI cifrador;

    @Value("${app.operations.time-zone:America/Bogota}")
    private String zonaOperativa;

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

    /**
     * Variante para las entradas HTTP y CLI (13-BE-01). La fecha llega como texto ISO (AAAA-MM-DD) y se
     * interpreta de forma estricta, así que una fecha inexistente como 2023-02-30 se rechaza con un mensaje
     * del campo. "Hoy" es la fecha en la zona operativa (app.operations.time-zone).
     */
    public DatosIdentidadProtegidos protegerHoy(String numeroCedula, String fechaNacimientoIso) {
        NumeroCedula.de(numeroCedula); // los errores se informan en el orden de los campos: primero la cédula
        return proteger(numeroCedula, interpretarFecha(fechaNacimientoIso), hoy());
    }

    /** Mayoría de edad en Colombia (verificación 18+). */
    public static final int MAYORIA_DE_EDAD = 18;

    /**
     * HU-13 CA-2: edad del usuario calculada en el servidor a la fecha actual de la zona operativa, a partir de la
     * fecha de nacimiento cifrada. Es la única fuente de edad del sistema: nunca se acepta una edad del cliente.
     */
    public int edadDe(Usuario usuario) {
        return edadEn(fechaNacimientoDe(usuario), hoy());
    }

    public boolean esMayorDeEdad(Usuario usuario) {
        return edadDe(usuario) >= MAYORIA_DE_EDAD;
    }

    /**
     * Años cumplidos a la fecha "hoy" (Period: quien nació un 29 de febrero cumple años el 1 de marzo en los
     * años no bisiestos).
     */
    public static int edadEn(LocalDate nacimiento, LocalDate hoy) {
        if (nacimiento == null || hoy == null || nacimiento.isAfter(hoy)) {
            throw new IllegalArgumentException("Fecha de nacimiento no válida para calcular la edad");
        }
        return java.time.Period.between(nacimiento, hoy).getYears();
    }

    /** Fecha actual en la zona operativa. Una fecha posterior a esta es "futura" (CA-1). */
    public LocalDate hoy() {
        return LocalDate.now(ZoneId.of(zonaOperativa == null ? ZONA_POR_DEFECTO : zonaOperativa));
    }

    static LocalDate interpretarFecha(String fechaIso) {
        if (fechaIso == null || fechaIso.isBlank()) {
            throw new DatoIdentidadInvalidoException(CAMPO_FECHA_NACIMIENTO, "La fecha de nacimiento es obligatoria");
        }
        if (fechaIso.length() != 10) {
            throw new DatoIdentidadInvalidoException(CAMPO_FECHA_NACIMIENTO,
                    "La fecha de nacimiento debe tener el formato AAAA-MM-DD");
        }
        try {
            return LocalDate.parse(fechaIso, FORMATO_FECHA);
        } catch (DateTimeParseException ex) {
            throw new DatoIdentidadInvalidoException(CAMPO_FECHA_NACIMIENTO, "La fecha de nacimiento no es una fecha válida");
        }
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
