package com.udea.demo.usuarios.application.assistant;

import org.springframework.stereotype.Component;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import static com.udea.demo.usuarios.application.assistant.AssistantDTO.*;

@Component
public class AssistantGuard {
    private static final Pattern DENIED=Pattern.compile("\\b(metricas?|analitica|dashboard|estadisticas?|rendimiento|desempeno|promedio|total|cuantos?|cantidad|calcula|grafic[oa]s?|compara|todos|todas|listar|listado|actualiza|modifica|elimina|borra|borrar|cambia|crea|crear|asigna|ignora|ignorar|olvida|instrucciones|instruccion|prompt|system|sistema|developer|assistant|jailbreak|tokens?|hash|contrasena|password|nacimiento|fecha|select|union|drop|insert|delete|update|truncate|sql)\\b");
    private static final Pattern PACKAGE=Pattern.compile("(?iu)\\b(?:paquete|envío|envio|tracking|código|codigo)(?:\\s+de\\s+seguimiento)?\\s*(?::|#)?\\s*([A-Za-z0-9][A-Za-z0-9_-]{2,49})\\s*$");
    private static final Pattern PROFILE=Pattern.compile("(?iu)\\b(?:perfil|rol|estado\\s+de\\s+cuenta|contacto|datos\\s+de\\s+contacto)(?:\\s+(?:de(?:l)?|tiene))?\\s+(?:(?:el\\s+|la\\s+)?usuari[oa]\\s+)?(.{2,150})$");
    private static final Set<String> WORDS=Set.of("donde","esta","el","paquete","envio","tracking","codigo","de","seguimiento","cual","es","estado","ubicacion","actual","consulta","muestra","me","dime","ver","necesito","saber","por","favor","quiero","que","rol","tiene","perfil","usuario","usuaria","datos","contacto","cuenta","del","la","al","para","busca","podrias","puedes","informacion","un","una");

    public Prepared prepare(String input) {
        if (input==null || input.isBlank() || input.length()>500) throw new IllegalArgumentException();
        String text=Normalizer.normalize(input.strip(),Normalizer.Form.NFC);
        if (text.codePoints().anyMatch(c->Character.isISOControl(c) || Character.getType(c)==Character.FORMAT)) return outside();
        if (!text.matches("[\\p{L}\\p{M}\\p{N} \\t¿?¡!.,:@_#'’\\\"-]+")) return outside();
        text=text.replaceAll("^[¿¡]+|[?!.,]+$","").strip();
        String normalized=normalize(text);
        if (DENIED.matcher(normalized).find()) return outside();
        boolean packageWord=Pattern.compile("\\b(paquete|envio|tracking|codigo)\\b").matcher(normalized).find();
        boolean profileWord=Pattern.compile("\\b(perfil|rol|contacto|usuario|usuaria|cuenta)\\b").matcher(normalized).find();
        if (packageWord && profileWord) return outside();
        var matcher=(packageWord ? PACKAGE : PROFILE).matcher(text);
        if (!matcher.find()) return outside();
        String target=matcher.group(1).strip().replaceAll("^[\\\"']|[\\\"']$","");
        Intent intent=packageWord ? Intent.PAQUETE : Intent.PERFIL;
        if (intent==Intent.PERFIL && !target.matches("[\\p{L}\\p{M}][\\p{L}\\p{M} .’'-]{1,149}")
                && !target.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")) return outside();
        String prefix=text.substring(0,matcher.start(1));
        for (String word : normalize(prefix).split("[^a-z]+")) {
            if (!word.isEmpty() && !WORDS.contains(word)) return outside();
        }
        String anonymous=normalize(prefix).replaceAll("[^a-z ]","").replaceAll("\\s+"," ").strip()+" OBJETIVO";
        return new Prepared(intent,target,anonymous);
    }

    private Prepared outside() { return new Prepared(Intent.FUERA_DE_ALCANCE,"",""); }
    private static String normalize(String text) {
        return Normalizer.normalize(text,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);
    }
}
