package com.udea.demo.pedidos.application.anomalias;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RelojAnomaliasConfig {
    @Bean("relojAnomalias")
    public Clock relojAnomalias() { return Clock.systemUTC(); }
}
