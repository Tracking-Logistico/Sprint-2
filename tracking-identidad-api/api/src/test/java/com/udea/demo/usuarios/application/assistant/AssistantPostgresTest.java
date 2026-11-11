package com.udea.demo.usuarios.application.assistant;

import com.udea.demo.usuarios.domain.model.CampoIdentidad;
import com.udea.demo.usuarios.infrastructure.cifrado.CifradorAesGcmDatosIdentidad;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.env.MockEnvironment;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.udea.demo.usuarios.application.assistant.AssistantDTO.*;

@EnabledIfEnvironmentVariable(named="HU12_TEST_POSTGRES_URL",matches=".+")
class AssistantPostgresTest {
    String url,user,password,schema,role,readerPassword;
    Connection admin;
    AssistantReadStore reader;
    long userId,parcelId;
    CifradorAesGcmDatosIdentidad cipher;

    @BeforeEach void setup() throws Exception {
        url=System.getenv("HU12_TEST_POSTGRES_URL");user=System.getenv("HU12_TEST_POSTGRES_USER");password=System.getenv("HU12_TEST_POSTGRES_PASSWORD");
        String suffix=UUID.randomUUID().toString().replace("-","");schema="hu12_"+suffix;role="reader_"+suffix;
        readerPassword=UUID.randomUUID().toString();
        admin=DriverManager.getConnection(url,user,password);
        execute("CREATE SCHEMA "+schema);
        Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate();
        execute("SET search_path TO "+schema);
        execute("CREATE ROLE "+role+" LOGIN PASSWORD '"+readerPassword+"'");
        execute("GRANT USAGE ON SCHEMA "+schema+" TO "+role);
        execute("GRANT SELECT ON assistant_user_profile,assistant_package,assistant_location TO "+role);
        cipher=new CifradorAesGcmDatosIdentidad(UUID.randomUUID().toString()+UUID.randomUUID(),true);
        try(var s=admin.prepareStatement("INSERT INTO usuarios(nombre,email,password,rol,estado,acepto_terminos,numero_cedula_cifrado,numero_cedula_hash,fecha_nacimiento_cifrada,datos_identidad_bloqueados,fecha_bloqueo_identidad) VALUES (?,?,?,?,?,false,?,?,?,true,CURRENT_TIMESTAMP) RETURNING id")) {
            s.setString(1,"Persona Sintética");s.setString(2,"persona@example.invalid");s.setString(3,"valor-interno-no-publicable");s.setString(4,"CLIENTE");s.setString(5,"ACTIVO");
            s.setString(6,cipher.cifrar("1234567890",CampoIdentidad.NUMERO_CEDULA));s.setString(7,cipher.indiceCiegoCedula("1234567890"));s.setString(8,cipher.cifrar("2000-01-01",CampoIdentidad.FECHA_NACIMIENTO));
            try(var r=s.executeQuery()){r.next();userId=r.getLong(1);}
        }
        execute("INSERT INTO clientes(id_usuario) VALUES ("+userId+")");
        try(var s=admin.createStatement();var r=s.executeQuery("INSERT INTO pedidos(numero_pedido,numero_tracking,id_cliente,direccion_origen,direccion_destino,descripcion_paquete,peso_kg,largo_cm,ancho_cm,alto_cm,tipo_servicio,prioridad_sugerida,estado) SELECT 'PED-PRUEBA','NCA-PRUEBA',id_cliente,'Origen sintético','Destino sintético','Prueba',1,1,1,1,'ESTANDAR','MEDIA','RECIBIDO_EN_ORIGEN' FROM clientes WHERE id_usuario="+userId+" RETURNING id_pedido")) {r.next();parcelId=r.getLong(1);}
        var environment=new MockEnvironment();environment.setActiveProfiles("test");
        reader=new AssistantReadStore(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,role,readerPassword,false,environment,cipher);
    }
    void execute(String sql) throws SQLException {try(var s=admin.createStatement()){s.execute(sql);}}
    @AfterEach void cleanup() throws Exception {
        if(reader!=null)reader.close();
        if(admin!=null){execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");execute("DROP ROLE IF EXISTS "+role);admin.close();}
    }
    @Test void perfilMinimoYDatosInexistentes() {
        var answer=reader.lookup(new Prepared(Intent.PERFIL,"Persona Sintética",""));
        assertThat(answer.result()).isEqualTo(Result.RESUELTA);
        assertThat(answer.text()).contains("****7890","persona@example.invalid").doesNotContain("1234567890","2000-01-01","valor-interno-no-publicable","v1:");
        assertThat(reader.lookup(new Prepared(Intent.PERFIL,"No Existe","")).result()).isEqualTo(Result.SIN_DATOS);
        assertThat(reader.lookup(new Prepared(Intent.PAQUETE,"NCA-INEXISTENTE","")).result()).isEqualTo(Result.SIN_DATOS);
        assertThat(reader.lookup(new Prepared(Intent.PERFIL,"' OR 1=1 --","")).result()).isEqualTo(Result.SIN_DATOS);
    }
    @Test void nombreAmbiguoNoEscogePersona() throws Exception {
        execute("INSERT INTO usuarios(nombre,email,password,rol,estado,acepto_terminos) VALUES ('Persona Sintética','otra@example.invalid','sin-acceso','CLIENTE','ACTIVO',false)");
        assertThat(reader.lookup(new Prepared(Intent.PERFIL,"Persona Sintética","")).result()).isEqualTo(Result.SIN_DATOS);
        assertThat(reader.lookup(new Prepared(Intent.PERFIL,"persona@example.invalid","")).result()).isEqualTo(Result.RESUELTA);
    }
    @Test void ultimaUbicacionRespetaCorreccionesHu19() throws Exception {
        location("EN_BODEGA_ORIGEN","Oficina de prueba",null,null,null,1);
        long wrong=location("EN_TRANSITO_VEHICULO",null,"Vehículo anterior",null,null,3);
        location("EN_PUNTO_ENTREGA",null,null,"Punto corregido",wrong,2);
        assertThat(reader.lookup(new Prepared(Intent.PAQUETE,"NCA-PRUEBA","")).text()).contains("recibido","Punto corregido").doesNotContain("Vehículo anterior");
    }
    long location(String stage,String office,String vehicle,String point,Long correction,int hour) throws SQLException {
        try(var s=admin.prepareStatement("INSERT INTO eventos_ubicacion_pedido(id_evento,id_pedido,numero_tracking,etapa,estado_pedido,ciudad_nombre,oficina_nombre,vehiculo_id,punto_entrega_nombre,usuario_id,origen,fecha_evento,corrige_evento_id) VALUES (?,?,?,?,?,'Ciudad sintética',?,?,?,?,'WEB',?,?) RETURNING id")) {
            s.setString(1,UUID.randomUUID().toString());s.setLong(2,parcelId);s.setString(3,"NCA-PRUEBA");s.setString(4,stage);s.setString(5,"RECIBIDO_EN_ORIGEN");s.setString(6,office);s.setString(7,vehicle);s.setString(8,point);s.setLong(9,userId);s.setTimestamp(10,Timestamp.valueOf("2026-01-01 0"+hour+":00:00"));s.setObject(11,correction);
            try(var r=s.executeQuery()){r.next();return r.getLong(1);}
        }
    }
    @Test void cuentaDeLecturaNoPuedeEscribirNiLeerSecretos() throws Exception {
        try(var c=DriverManager.getConnection(url,role,readerPassword);var s=c.createStatement()) {
            s.execute("SET search_path TO "+schema);
            for(String sql:List.of("UPDATE usuarios SET nombre='Otro'","DELETE FROM pedidos","INSERT INTO assistant_session DEFAULT VALUES","SELECT password FROM usuarios","SELECT fecha_nacimiento_cifrada FROM usuarios"))
                assertThatThrownBy(()->s.execute(sql)).isInstanceOf(SQLException.class);
        }
        execute("GRANT UPDATE ON usuarios TO "+role);
        assertThatThrownBy(()->reader.lookup(new Prepared(Intent.PERFIL,"Persona Sintética",""))).isInstanceOf(AssistantUnavailableException.class);
    }
}
