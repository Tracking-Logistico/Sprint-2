package com.udea.demo.seguridad;

import jakarta.persistence.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-13 CA-8: "no almacena imágenes del documento". No existe ningún endpoint que reciba archivos o binarios,
 * ni ninguna entidad con columnas binarias donde se pudiera guardar una foto. Si alguien lo agrega, este test falla
 * y obliga a revisar el caso con Ley 1581.
 */
@DisplayName("HU-13 Sin imágenes del documento: ni endpoints de archivos ni columnas binarias")
class SinImagenesDocumentoTest {
    private static final Set<Class<?>> TIPOS_BINARIOS = Set.of(MultipartFile.class, MultipartFile[].class,
            jakarta.servlet.http.Part.class, byte[].class, Byte[].class, InputStream.class,
            org.springframework.core.io.Resource.class, java.sql.Blob.class);

    private static List<Class<?>> clasesCon(Class<? extends java.lang.annotation.Annotation> anotacion) throws Exception {
        ClassPathScanningCandidateComponentProvider escaner = new ClassPathScanningCandidateComponentProvider(false);
        escaner.addIncludeFilter(new AnnotationTypeFilter(anotacion));
        List<Class<?>> clases = new ArrayList<>();
        for (BeanDefinition bd : escaner.findCandidateComponents("com.udea.demo")) clases.add(Class.forName(bd.getBeanClassName()));
        return clases;
    }

    @Test
    @DisplayName("Ningún endpoint REST recibe archivos, partes multipart ni binarios")
    void sinEndpointsDeArchivos() throws Exception {
        List<Class<?>> controladores = clasesCon(RestController.class);
        assertThat(controladores).isNotEmpty();
        List<String> violaciones = new ArrayList<>();
        for (Class<?> c : controladores) {
            for (Method m : c.getDeclaredMethods()) {
                for (Parameter p : m.getParameters()) {
                    if (TIPOS_BINARIOS.contains(p.getType())) violaciones.add(c.getSimpleName() + "#" + m.getName());
                }
            }
        }
        assertThat(violaciones).isEmpty();
    }

    @Test
    @DisplayName("Ninguna entidad JPA tiene campos binarios (byte[], Blob) donde guardar una imagen")
    void sinColumnasBinarias() throws Exception {
        List<Class<?>> entidades = clasesCon(Entity.class);
        assertThat(entidades).isNotEmpty();
        List<String> violaciones = new ArrayList<>();
        for (Class<?> e : entidades) {
            for (Field f : e.getDeclaredFields()) {
                if (TIPOS_BINARIOS.contains(f.getType()) || f.isAnnotationPresent(jakarta.persistence.Lob.class)) {
                    violaciones.add(e.getSimpleName() + "#" + f.getName());
                }
            }
        }
        assertThat(violaciones).isEmpty();
    }
}
