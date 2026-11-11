package com.udea.demo.pedidos.application.anomalias;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;


@EnabledIfEnvironmentVariable(named="HU18_TEST_POSTGRES_URL",matches=".+")
class AnomaliasPostgresTest {
    Connection conexion;
    String esquema;

    Connection conectar() throws Exception {
        return DriverManager.getConnection(System.getenv("HU18_TEST_POSTGRES_URL"),
                System.getenv("HU18_TEST_POSTGRES_USER"), System.getenv("HU18_TEST_POSTGRES_PASSWORD"));
    }

    @BeforeEach
    void migrar() throws Exception {
        conexion=conectar();
        esquema="hu18_"+UUID.randomUUID().toString().replace("-","");
        try (Statement s=conexion.createStatement()) {

            s.execute("CREATE SCHEMA "+esquema);
            s.execute("SET search_path TO "+esquema);
            s.execute("CREATE TABLE usuarios(id BIGINT PRIMARY KEY); CREATE TABLE pedidos(id_pedido BIGINT PRIMARY KEY);"
                    +"CREATE TABLE oficinas_logisticas(id BIGINT PRIMARY KEY); CREATE TABLE eventos_ubicacion_pedido(id BIGINT PRIMARY KEY);"
                    +"CREATE TABLE anomalias(id_anomalia BIGINT PRIMARY KEY); INSERT INTO anomalias VALUES (1)");
            s.execute(Files.readString(Path.of("../../tracking-identidad-api/core/src/main/resources/db/migration/V28__anomalias_y_recepciones.sql")));
            s.execute("INSERT INTO usuarios VALUES (1); INSERT INTO pedidos VALUES (1)");
        }
    }

    @AfterEach
    void limpiar() throws Exception {
        if (conexion!=null) {
            try (Statement s=conexion.createStatement()) { s.execute("DROP SCHEMA "+esquema+" CASCADE"); }
            conexion.close();
        }
    }

    void insertar(String evento) throws Exception {
        try (PreparedStatement p=conexion.prepareStatement("""
                INSERT INTO anomalias(envio_id,tipo,evento_origen,inicio_utc,estado,clave_activa,creada_en)
                VALUES (1,'PERMANENCIA_EXCESIVA',?,CURRENT_TIMESTAMP,'ACTIVA',1,CURRENT_TIMESTAMP)
                """)) { p.setString(1,evento); p.executeUpdate(); }
    }

    @Test
    void conservaLaTablaHistorica() throws Exception {
        try (Statement s=conexion.createStatement(); ResultSet r=s.executeQuery("SELECT count(*) FROM anomalias_legacy_hu18")) {
            assertThat(r.next()).isTrue();
            assertThat(r.getLong(1)).isEqualTo(1);
        }
    }

    @Test
    void deduplicaEventoInclusoCerradoYSoloUnaActivaPorTipo() throws Exception {
        insertar("OFICINA-1");
        assertThatThrownBy(()->insertar("OFICINA-2")).isInstanceOf(SQLException.class)
                .satisfies(e->assertThat(((SQLException)e).getSQLState()).isEqualTo("23505"));
        try (Statement s=conexion.createStatement()) {
            s.execute("UPDATE anomalias SET estado='RESUELTA',clave_activa=NULL,cerrada_en=CURRENT_TIMESTAMP,cerrada_por=1,origen_cierre='WEB'");
        }
        assertThatThrownBy(()->insertar("OFICINA-1")).isInstanceOf(SQLException.class);
        insertar("OFICINA-2");
    }

    @Test
    void restriccionesDeEstadoYParametrosSeAplicanEnLaBase() throws Exception {
        try (Statement s=conexion.createStatement()) {
            assertThatThrownBy(()->s.execute("UPDATE parametros_anomalia SET inactividad_horas=0")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("UPDATE parametros_anomalia SET recepcion_horas=169")).isInstanceOf(SQLException.class);
            insertar("OFICINA-1");
            assertThatThrownBy(()->s.execute("UPDATE anomalias SET clave_activa=NULL")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("UPDATE anomalias SET estado='RESUELTA'")).isInstanceOf(SQLException.class);
        }
    }

    @Test
    void bloqueoDistribuidoExcluyeOtraConexionHastaLiberarse() throws Exception {
        conexion.setAutoCommit(false);
        try (Statement s=conexion.createStatement(); Connection otra=conectar(); Statement segundo=otra.createStatement()) {
            segundo.execute("SET search_path TO "+esquema);
            s.executeQuery("SELECT id FROM bloqueo_motor_anomalias WHERE id=1 FOR UPDATE NOWAIT").close();
            assertThatThrownBy(()->segundo.executeQuery("SELECT id FROM bloqueo_motor_anomalias WHERE id=1 FOR UPDATE NOWAIT"))
                    .isInstanceOf(SQLException.class).satisfies(e->assertThat(((SQLException)e).getSQLState()).isEqualTo("55P03"));
            conexion.rollback();
            segundo.executeQuery("SELECT id FROM bloqueo_motor_anomalias WHERE id=1 FOR UPDATE NOWAIT").close();
        } finally { conexion.setAutoCommit(true); }
    }
}
