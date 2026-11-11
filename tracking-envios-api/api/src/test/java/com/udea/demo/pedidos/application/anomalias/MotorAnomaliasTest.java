package com.udea.demo.pedidos.application.anomalias;

import com.udea.demo.pedidos.domain.anomalias.TipoAnomalia;
import com.udea.demo.pedidos.domain.model.Pedido;
import com.udea.demo.pedidos.interfaces.persistence.PedidoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MotorAnomaliasTest {
    @Test
    void unFalloEnUnaReglaNoCierraSuAlertaNiImpideLasOtras() {
        var repositorio=mock(RepositorioAnomalias.class);
        var pedidos=mock(PedidoRepository.class);
        var reglas=mock(ReglasAnomalias.class);
        var transacciones=mock(PlatformTransactionManager.class);
        when(transacciones.getTransaction(any())).thenAnswer(invocacion->new SimpleTransactionStatus());
        var pedido=mock(Pedido.class);
        when(pedidos.findByIdForUpdate(1L)).thenReturn(Optional.of(pedido));
        var parametros=new AnomaliasDTO.Parametros(24,4,2,0);
        when(repositorio.parametros()).thenReturn(parametros);
        Instant ahora=Instant.parse("2030-01-01T00:00:00Z");
        when(reglas.evaluar(pedido,TipoAnomalia.PERMANENCIA_EXCESIVA,parametros,ahora))
                .thenThrow(new IllegalStateException("Fallo sintético"));
        var motor=new MotorAnomalias(repositorio,pedidos,reglas,Clock.fixed(ahora,ZoneOffset.UTC),transacciones);
        motor.evaluar(1L);
        verify(repositorio,never()).reconciliar(eq(1L),eq(TipoAnomalia.PERMANENCIA_EXCESIVA),any(),any());
        verify(repositorio).reconciliar(1L,TipoAnomalia.INACTIVIDAD_REPARTO,null,ahora);
        verify(repositorio).reconciliar(1L,TipoAnomalia.REINTENTOS_ACUMULADOS,null,ahora);
        verify(repositorio).reconciliar(1L,TipoAnomalia.SIN_CONFIRMACION_RECEPCION,null,ahora);
        verify(transacciones,times(1)).rollback(any());
        verify(transacciones,times(3)).commit(any());
    }
}
