package com.udea.demo.usuarios.application.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * HU-13 CA-3: marca un DTO de entrada que NO puede recibir datos de identidad (cédula, fecha de nacimiento ni sus
 * derivados). Si el JSON los incluye, aunque el DTO no los tenga, la petición se rechaza con 409 IDENTIDAD_INMUTABLE
 * en lugar de ignorarlos en silencio. Así se evita la asignación masiva por API directa.
 * Lo aplica {@link com.udea.demo.config.CamposIdentidadProtegidosAdvice}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SinCamposIdentidad {
}
