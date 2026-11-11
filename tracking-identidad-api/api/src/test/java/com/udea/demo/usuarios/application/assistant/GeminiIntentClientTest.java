package com.udea.demo.usuarios.application.assistant;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static com.udea.demo.usuarios.application.assistant.AssistantDTO.*;

class GeminiIntentClientTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private String body(String text) {
        return mapper.writeValueAsString(Map.of("status","completed","steps",List.of(Map.of("type","model_output","content",List.of(Map.of("type","text","text",text))))));
    }
    @Test void clienteRealSeConstruyeConLaConfiguracionSpring() {
        try(var context=new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("assistant-test",Map.of("app.assistant.enabled","true")));
            context.registerBean(ObjectMapper.class,()->new ObjectMapper());
            context.register(GeminiIntentClient.class);context.refresh();
            assertThat(context.getBean(GeminiIntentClient.class)).isNotNull();
        }
    }
    @Test void contratoHttpSinDatosPersonalesNiHerramientas() throws Exception {
        var received=new AtomicReference<String>();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/interactions",exchange->{
            received.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] response=body("{\"intencion\":\"PERFIL\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
        });server.start();
        try {
            var client=new GeminiIntentClient(mapper,UUID.randomUUID().toString(),"gemini-3.5-flash-lite",URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/interactions"),HttpClient.newHttpClient());
            assertThat(client.classify(new AssistantGuard().prepare("¿Qué rol tiene Juan Pérez?"))).isEqualTo(Intent.PERFIL);
            var sent=mapper.readTree(received.get());
            assertThat(received.get()).doesNotContain("Juan","Pérez");
            assertThat(sent.get("store").asBoolean()).isFalse();
            assertThat(sent.get("tools").size()).isZero();
            assertThat(sent.get("response_format").get("mime_type").asText()).isEqualTo("application/json");
        } finally { server.stop(0); }
    }
    @Test void respuestasManipuladasFallanCerradas() throws Exception {
        var output=new AtomicReference<>("");
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{byte[] bytes=output.get().getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
        try {
            var client=new GeminiIntentClient(mapper,UUID.randomUUID().toString(),"gemini-3.5-flash-lite",URI.create("http://127.0.0.1:"+server.getAddress().getPort()),HttpClient.newHttpClient());
            var query=new AssistantGuard().prepare("Perfil de Juan Pérez");
            for (String malicious : new String[]{body("{\"intencion\":\"PERFIL\",\"sql\":\"DELETE FROM usuarios\"}"),body("{\"intencion\":\"PAQUETE\"}"),body("{\"intencion\":\"ESCRIBIR\"}"),body("<script>"),"{}","x".repeat(20000),"{\"status\":\"requires_action\",\"steps\":[]}"}) {
                output.set(malicious);assertThat(client.classify(query)).isEqualTo(Intent.FUERA_DE_ALCANCE);
            }
        } finally { server.stop(0); }
    }
    @Test void claveAusenteFallaSinRespuestaSimulada() {
        var client=new GeminiIntentClient(mapper,"","gemini-3.5-flash-lite");
        assertThatThrownBy(()->client.classify(new AssistantGuard().prepare("Perfil de Juan Pérez"))).isInstanceOf(AssistantUnavailableException.class);
    }
    @Test void proveedorLentoTieneTimeout() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{try { Thread.sleep(6000); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); } exchange.close();});server.start();
        try {
            var client=new GeminiIntentClient(mapper,UUID.randomUUID().toString(),"gemini-3.5-flash-lite",URI.create("http://127.0.0.1:"+server.getAddress().getPort()),HttpClient.newHttpClient());
            long start=System.nanoTime();
            assertThatThrownBy(()->client.classify(new AssistantGuard().prepare("Perfil de Juan Pérez"))).isInstanceOf(AssistantUnavailableException.class);
            assertThat((System.nanoTime()-start)/1_000_000).isLessThan(8000);
        } finally { server.stop(0); }
    }
}
