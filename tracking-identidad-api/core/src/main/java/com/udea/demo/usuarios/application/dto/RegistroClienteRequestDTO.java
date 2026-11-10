package com.udea.demo.usuarios.application.dto;

import jakarta.validation.constraints.*;

/**
 * Registro público de cliente (HU-01A). HU-13 CA-1 agrega la cédula y la fecha de nacimiento como obligatorias.
 * Aquí se valida la forma: presencia, longitud y patrón. La semántica (formato de cédula, fecha real y no
 * futura) la valida ProteccionDatosIdentidadService, también con mensajes por campo. Si el cliente envía
 * una "edad", se ignora: la edad siempre se calcula en el servidor.
 */
public record RegistroClienteRequestDTO(
    @NotBlank(message = "El nombre es obligatorio") @Size(max = 100) String nombre,
    @NotBlank(message = "El email es obligatorio") @Email(message = "El formato de email no es válido") String email,
    @NotBlank(message = "La contraseña es obligatoria") @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[^A-Za-z0-9]).*$",
             message = "La contraseña debe contener al menos una mayúscula, una minúscula, un número y un carácter especial")
    String password,
    @NotBlank(message = "La confirmación de contraseña es obligatoria") String confirmarPassword,
    @Pattern(regexp = "^$|^\\+?[1-9][0-9]{7,14}$", message = "El teléfono debe usar un formato internacional válido") String telefono,
    @Size(max = 200, message = "La dirección no puede superar 200 caracteres") String direccion,
    @Size(max = 100, message = "La ciudad no puede superar 100 caracteres") String ciudad,
    @NotNull @AssertTrue(message = "Debe aceptar los términos y condiciones") Boolean aceptoTerminos,
    @NotBlank(message = "La versión de términos es obligatoria") String versionTerminos,
    @NotNull @AssertTrue(message = "Debe aceptar la política de tratamiento de datos") Boolean aceptoPoliticaDatos,
    @NotBlank(message = "La versión de la política de datos es obligatoria") String versionPoliticaDatos,
    @NotBlank(message = "El número de cédula es obligatorio")
    @Size(max = 20, message = "El número de cédula no tiene un formato válido")
    String numeroCedula,
    @NotBlank(message = "La fecha de nacimiento es obligatoria")
    @Pattern(regexp = "^$|^[0-9]{4}-[0-9]{2}-[0-9]{2}$", message = "La fecha de nacimiento debe tener el formato AAAA-MM-DD")
    String fechaNacimiento
) {
    public RegistroClienteRequestDTO(String nombre, String email, String password, String confirmarPassword,
                                     String telefono, String direccion, String ciudad,
                                     Boolean aceptoTerminos, String versionTerminos) {
        this(nombre, email, password, confirmarPassword, telefono, direccion, ciudad,
                aceptoTerminos, versionTerminos, true, versionTerminos, null, null);
    }

    /** Firma anterior a HU-13. Sin identidad, el registro falla con mensajes por campo. */
    public RegistroClienteRequestDTO(String nombre, String email, String password, String confirmarPassword,
                                     String telefono, String direccion, String ciudad,
                                     Boolean aceptoTerminos, String versionTerminos,
                                     Boolean aceptoPoliticaDatos, String versionPoliticaDatos) {
        this(nombre, email, password, confirmarPassword, telefono, direccion, ciudad,
                aceptoTerminos, versionTerminos, aceptoPoliticaDatos, versionPoliticaDatos, null, null);
    }

    public RegistroClienteRequestDTO(String nombre, String email, String password, String confirmarPassword,
                                     String telefono, String direccion, String ciudad,
                                     Boolean aceptoTerminos, String versionTerminos,
                                     String numeroCedula, String fechaNacimiento) {
        this(nombre, email, password, confirmarPassword, telefono, direccion, ciudad,
                aceptoTerminos, versionTerminos, true, versionTerminos, numeroCedula, fechaNacimiento);
    }

    /** Sin contraseñas, cédula ni fecha de nacimiento: un log accidental del DTO no expone datos sensibles. */
    @Override
    public String toString() {
        return "RegistroClienteRequestDTO[nombre=***, email=***, password=***, numeroCedula=***, fechaNacimiento=***]";
    }
}
