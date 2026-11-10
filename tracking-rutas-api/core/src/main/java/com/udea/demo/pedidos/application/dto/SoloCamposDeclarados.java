package com.udea.demo.pedidos.application.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * HU-13 13-BE-03, whitelist de campos: el JSON de un DTO marcado solo puede traer las propiedades que el record
 * declara (nombres exactos). Cualquier otra propiedad (clienteId, estado, remitenteEmail, etc.) se rechaza con un
 * 400 genérico en lugar de ignorarse en silencio. Lo aplica com.udea.demo.config.VerificacionEdadSoloRemitenteAdvice.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SoloCamposDeclarados {
}
