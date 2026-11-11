package com.udea.demo.usuarios.infrastructure.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import tools.jackson.databind.ObjectMapper;
import java.io.*;
import java.lang.reflect.Type;
import java.util.Set;

@ControllerAdvice(assignableTypes=AssistantController.class)
public class AssistantInputAdvice extends RequestBodyAdviceAdapter {
    private final ObjectMapper mapper;
    public AssistantInputAdvice(ObjectMapper mapper) { this.mapper=mapper; }
    @Override public boolean supports(MethodParameter parameter,Type type,Class<? extends HttpMessageConverter<?>> converter) { return true; }
    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage input,MethodParameter parameter,Type type,
            Class<? extends HttpMessageConverter<?>> converter) throws IOException {
        byte[] bytes=input.getBody().readNBytes(4097);
        if (bytes.length>4096) throw new IllegalArgumentException();
        try {
            var json=mapper.readTree(bytes);
            var allowed=Set.of("texto","sessionId","solicitudId","origen");
            if (json==null || !json.isObject() || !json.path("texto").isTextual() || json.propertyNames().stream().anyMatch(key->!allowed.contains(key))) throw new IllegalArgumentException();
        } catch (tools.jackson.core.JacksonException ex) { throw new IllegalArgumentException(); }
        return new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(bytes); }
            @Override public HttpHeaders getHeaders() { return input.getHeaders(); }
        };
    }
}
