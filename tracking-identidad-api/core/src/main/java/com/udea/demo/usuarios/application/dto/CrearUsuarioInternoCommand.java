package com.udea.demo.usuarios.application.dto;

import com.udea.demo.usuarios.domain.model.Rol;
import jakarta.validation.constraints.*;

/**
 * Creación administrativa de usuarios internos (HU-01B). HU-13 CA-1 agrega la cédula y la fecha de nacimiento
 * como obligatorias. La forma se valida aquí y la semántica en ProteccionDatosIdentidadService.
 */
public record CrearUsuarioInternoCommand(

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    String nombre,

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no tiene un formato válido")
    @Size(max = 150, message = "El correo no puede superar 150 caracteres")
    String email,

    @Pattern(regexp = "^[0-9+\\- ]{7,20}$",
             message = "El teléfono no tiene un formato válido")
    String telefono,

    @NotNull(message = "El rol es obligatorio")
    Rol rol,

    @Size(max = 200, message = "La dirección no puede superar 200 caracteres")
    String direccion,

    @Size(max = 50, message = "La licencia no puede superar 50 caracteres")
    String licencia,

    @Size(max = 50, message = "El código de empleado no puede superar 50 caracteres")
    String codigoEmpleado,

    @NotBlank(message = "El número de cédula es obligatorio")
    @Size(max = 20, message = "El número de cédula no tiene un formato válido")
    String numeroCedula,

    @NotBlank(message = "La fecha de nacimiento es obligatoria")
    @Pattern(regexp = "^$|^[0-9]{4}-[0-9]{2}-[0-9]{2}$", message = "La fecha de nacimiento debe tener el formato AAAA-MM-DD")
    String fechaNacimiento
) {
    /** Compatibilidad con llamadas anteriores a HU-13. Sin identidad, la creación falla con un mensaje por campo. */
    public CrearUsuarioInternoCommand(String nombre, String email, String telefono, Rol rol, String direccion,
                                      String licencia, String codigoEmpleado) {
        this(nombre, email, telefono, rol, direccion, licencia, codigoEmpleado, null, null);
    }

    @Override
    public String toString() {
        return "CrearUsuarioInternoCommand[rol=" + rol + ", nombre=***, email=***, numeroCedula=***, fechaNacimiento=***]";
    }
}
