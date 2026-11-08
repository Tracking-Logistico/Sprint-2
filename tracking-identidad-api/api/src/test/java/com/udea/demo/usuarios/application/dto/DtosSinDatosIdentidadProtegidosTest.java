package com.udea.demo.usuarios.application.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Salvaguarda de CA-8 y de la minimización (Ley 1581): ningún DTO de la API puede exponer el texto cifrado, el
 * índice ciego ni la cédula completa. Solo se permite un campo con "Enmascarad" en el nombre. Si una tarea
 * posterior agrega un campo así a un DTO, este test falla.
 */
@DisplayName("HU-13 Los DTOs no exponen datos de identidad protegidos")
class DtosSinDatosIdentidadProtegidosTest {

    @Test
    void ningunDtoExponeCifradosHashesNiCedulaCompleta() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider escaner = new ClassPathScanningCandidateComponentProvider(false);
        escaner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));
        Set<BeanDefinition> candidatos = escaner.findCandidateComponents("com.udea.demo");

        List<String> dtos = new ArrayList<>();
        List<String> violaciones = new ArrayList<>();
        for (BeanDefinition candidato : candidatos) {
            String nombre = candidato.getBeanClassName();
            if (nombre == null || !nombre.contains(".application.dto.")) continue;
            dtos.add(nombre);
            // Los DTOs de ENTRADA (registro, creación interna) reciben la cédula que escribe el usuario. Ningún
            // DTO, ni de entrada ni de salida, puede llevar el cifrado o el índice ciego.
            String simple = nombre.substring(nombre.lastIndexOf('.') + 1);
            boolean entrada = simple.endsWith("RequestDTO") || simple.endsWith("Command");
            for (Field campo : Class.forName(nombre).getDeclaredFields()) {
                String n = campo.getName().toLowerCase(Locale.ROOT);
                boolean expuesto = n.contains("cifrad") || n.contains("cedulahash") || n.contains("blindindex")
                        || (!entrada && n.contains("cedula") && !n.contains("enmascarad"));
                if (expuesto) violaciones.add(nombre + "#" + campo.getName());
            }
        }

        assertThat(dtos).as("el escaneo debe encontrar DTOs").isNotEmpty();
        assertThat(violaciones).as("DTOs que exponen datos de identidad protegidos").isEmpty();
    }
}
