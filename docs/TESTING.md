# Pruebas del contrato de endpoints

Ejecuciones reales del 2026-10-19. Referencia HTTP: [ENDPOINTS.md](ENDPOINTS.md). La entrega inicial de pruebas de endpoints, documentada primero abajo, no cambio produccion y completo 38 tests. La seccion H-02 posterior registra la implementacion aprobada de hashing/migracion y el resultado vigente de 62 tests. Los contadores iniciales son historicos, no el total actual. Se reutilizaron [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java) y [LegacyDatabaseTest.java](../src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java), y se mantuvo [HealthResourceTest.java](../src/test/java/com/bcp/workshop/auth/HealthResourceTest.java).

## Matriz de casos

Todos los casos ejecutables siguientes estan aprobados en la ejecucion registrada. Los nombres entre parentesis corresponden a metodos reales de las clases de prueba, no a pruebas propuestas.

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| POST /auth/login | Credenciales student y trainer (logsInWithWorkshopUser, logsInWithTrainer) | Camino feliz | 200 JSON; claves exactas token, expiresIn, user, debug; datos correctos de cada usuario; expiresIn entero 3600. |
| POST /auth/login | displayName con caracteres HTML (escapesDisplayNameWithoutChangingResponseFields) | Caso borde | 200; displayName escapado con escapeHtml4; sin nuevos campos. |
| POST /auth/login | client y propiedad desconocida (ignoresClientAndUnknownProperties) | Caso borde | 200; no modifican la identidad autenticada. |
| POST /auth/login | client ausente, null o vacio (acceptsMissingNullOrEmptyClient) | Caso borde | 200 para las tres variantes con credenciales validas. |
| POST /auth/login | Login repetido (repeatedLoginPreservesUserIdentity) | Caso borde | Ambas peticiones retornan 200 con la misma identidad; no se exige igualdad de tokens dinamicos. |
| POST /auth/login | Mayusculas o espacios adicionales (doesNotNormalizeCredentials) | Caso borde | 401; se preserva el username recibido en el mensaje y no se normalizan credenciales. |
| POST /auth/login | login(null) directo (validatesNullRequestBeforeAccessingDatabase) | Error | 400 con message Request is null, sin necesitar una base inyectada. |
| POST /auth/login | JSON null por HTTP (rejectsJsonNullRequestOverHttp) | Error | 400 JSON con solo message Request is null. |
| POST /auth/login | Cuerpo HTTP vacio (rejectsEmptyRequestOverHttp) | Error | 400 JSON con solo message Request is null. |
| POST /auth/login | Username ausente, null, vacio o espacios (validatesUsernameBeforePassword) | Error | 400 con Username is required, antes de validar password. |
| POST /auth/login | Password ausente, null, vacio o espacios (validatesMissingOrBlankPassword) | Error | 400 con Password is required. |
| POST /auth/login | JSON mal formado (rejectsMalformedJsonWithEmptyBody) | Error | 400, cuerpo de cero bytes y sin Content-Type; no se espera un JSON message. |
| POST /auth/login | Objeto en lugar de username string (rejectsObjectInsteadOfUsername) | Error | 400 JSON con objectName LoginRequest, attributeName username, line 1, column 13 y value null para el request exacto documentado. |
| POST /auth/login | Password incorrecto (rejectsIncorrectPassword) | Error | 401 con solo message Invalid user student or password. |
| POST /auth/login | Usuario inexistente (rejectsUnknownUser) | Error | 401 con solo message Invalid user unknown or password. |
| POST /auth/login | Fallo de consulta SQLite (queryFailurePreservesUnauthorizedResponse) | Error / caracterizacion | 401, conforme al contrato actual; el defecto de ocultar el fallo se reporta aparte abajo. |
| POST /auth/login | Excepcion controlada con mensaje (preservesInternalErrorResponse) | Error / caracterizacion | 500 con message controlled test failure y exception java.lang.IllegalStateException. Prueba directa del resource, no peticion HTTP. |
| POST /auth/login | Excepcion controlada sin mensaje (preservesUnknownErrorFallback) | Error / caracterizacion | 500 con message Unknown error y exception java.lang.IllegalStateException. Prueba directa del resource. |
| POST /auth/login | Content-Type text/plain (rejectsUnsupportedContentType) | Error | 415, cuerpo de cero bytes y sin Content-Type de respuesta. |
| POST /auth/login | Accept application/xml (rejectsUnsupportedAccept) | Error | 406, cuerpo de cero bytes y sin Content-Type de respuesta. |
| POST /auth/login | Credenciales validas con comillas (logsInWithQuotedCredentials) | Caso borde | 200; las comillas son datos, sin escapado manual. |
| POST /auth/login | SQL manipulado en username o password (rejectsSqlSyntaxInUsername, rejectsSqlSyntaxInPassword) | Error / regresion de seguridad | 401; no se acepta una coincidencia creada mediante sintaxis SQL. Ambos casos tambien existen en LegacyDatabaseTest. |
| GET /q/health | GET sin credenciales ni cuerpo (reportsHealthWithoutCredentialsOrRequestBody) | Camino feliz | 200 JSON exacto con status UP y checks vacio. |
| GET /q/health | Lista de checks vacia y sin headers opcionales (supportsAnEmptyCheckListWithoutOptionalHeaders) | Caso borde | 200 JSON exacto con checks []; no se inventa un campo vacio de request para un GET sin cuerpo. |
| GET /q/health | Accept application/xml (returnsJsonEvenWhenAcceptRequestsXml) | Caso borde | 200 JSON, conforme al contrato de salud; no se aplica el 406 de login a esta ruta. |

Salud no tiene una respuesta de error confirmada en ENDPOINTS.md. DOWN/503 y otros errores figuran como preguntas abiertas, no como casos ejecutables con un cuerpo conocido. Por ello no hay un test de error de salud que pueda implementarse sin inventar un comportamiento. Esta limitacion se registra como pendiente, no como prueba aprobada ni omitida por anotacion.

## Codigo de los tests

Las fuentes enlazadas al principio son el codigo completo realmente compilado y ejecutado. A continuacion se reproduce el codigo agregado para cubrir las brechas. Los casos previos de camino feliz, validacion 400, rechazo 401, excepciones 500 y regresion SQL permanecen en AuthResourceTest y LegacyDatabaseTest; no se cambiaron sus expectativas para hacer pasar un defecto.

### Ampliacion de AuthResourceTest

Este fragmento esta integrado en la clase existente con @QuarkusTest. Reutiliza su @BeforeEach, el metodo login, assertSuccessfulLogin y los imports existentes de JUnit, RestAssured, Arrays, HashMap y Map; no es una clase adicional ni un archivo que deba crearse. El JSON literal del caso de tipo invalido mantiene la posicion documentada de la columna.

```java
@Test
void acceptsMissingNullOrEmptyClient() {
    Map<String, Object> request = new HashMap<>(Map.of("username", "student", "password", "Workshop123!"));
    login(request).statusCode(200).body("user.username", is("student"));
    for (String client : Arrays.asList(null, "")) {
        request.put("client", client);
        login(request).statusCode(200).body("user.username", is("student"));
    }
}

@Test
void repeatedLoginPreservesUserIdentity() {
    assertSuccessfulLogin("student", "Workshop123!", "Workshop Student", "STUDENT");
    assertSuccessfulLogin("student", "Workshop123!", "Workshop Student", "STUDENT");
}

@Test
void rejectsJsonNullRequestOverHttp() {
    given().contentType("application/json").body("null")
            .when().post("/auth/login").then()
            .statusCode(400).contentType("application/json")
            .body("$", is(Map.of("message", "Request is null")));
}

@Test
void rejectsEmptyRequestOverHttp() {
    given().contentType("application/json").body("")
            .when().post("/auth/login").then()
            .statusCode(400).contentType("application/json")
            .body("$", is(Map.of("message", "Request is null")));
}

@Test
void rejectsMalformedJsonWithEmptyBody() {
    assertEmptyError(given().contentType("application/json").body("{")
            .when().post("/auth/login").then(), 400);
}

@Test
void rejectsObjectInsteadOfUsername() {
    Map<String, Object> expected = new HashMap<>(Map.of(
            "objectName", "LoginRequest", "attributeName", "username", "line", 1, "column", 13));
    expected.put("value", null);

    Map<String, Object> response = given().contentType("application/json")
            .body("{\"username\":{},\"password\":\"Workshop123!\"}")
            .when().post("/auth/login").then()
            .statusCode(400).contentType("application/json")
            .extract().jsonPath().getMap("$");

    assertEquals(expected, response);
}

@Test
void rejectsUnsupportedContentType() {
    assertEmptyError(given().contentType("text/plain").body("student")
            .when().post("/auth/login").then(), 415);
}

@Test
void rejectsUnsupportedAccept() {
    assertEmptyError(given().contentType("application/json").accept("application/xml")
            .body(Map.of("username", "student", "password", "Workshop123!", "client", "angular-workshop"))
            .when().post("/auth/login").then(), 406);
}

private static void assertEmptyError(ValidatableResponse response, int status) {
    io.restassured.response.Response actual = response.statusCode(status).extract().response();
    assertEquals(0, actual.asByteArray().length);
    assertEquals(null, actual.getHeader("Content-Type"));
}
```

### HealthResourceTest completo

```java
package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class HealthResourceTest {
    @Test
    void reportsHealthWithoutCredentialsOrRequestBody() {
        Map<String, Object> response = given().accept("application/json")
                .when().get("/q/health").then()
                .statusCode(200).contentType("application/json")
                .extract().jsonPath().getMap("$");

        assertEquals(Map.of("status", "UP", "checks", List.of()), response);
    }

    @Test
    void supportsAnEmptyCheckListWithoutOptionalHeaders() {
        Map<String, Object> response = given()
                .when().get("/q/health").then()
                .statusCode(200).contentType("application/json")
                .extract().jsonPath().getMap("$");

        assertEquals(Map.of("status", "UP", "checks", List.of()), response);
    }

    @Test
    void returnsJsonEvenWhenAcceptRequestsXml() {
        Map<String, Object> response = given().accept("application/xml")
                .when().get("/q/health").then()
                .statusCode(200).contentType("application/json")
                .extract().jsonPath().getMap("$");

        assertEquals(Map.of("status", "UP", "checks", List.of()), response);
    }
}
```

## Comandos y resultados ejecutados

Directorio: raiz de workshop-auth-backend, no docs ni la raiz general del workspace. En esta maquina: `C:/Users/Usuario/Documents/T50095-noelia-benavente/reqflow-workspace/workshop-auth-backend`. Entorno de ejecucion: Windows PowerShell, JDK 21, Maven y Quarkus 3.27.4. Surefire suministra AUTH_DB_URL con SQLite dentro de target segun [pom.xml](../pom.xml); las pruebas de repositorio validan esa ubicacion antes de borrar o insertar datos. No se usa la base normal data/workshop.db ni secretos reales.

Primero se ejecuto la validacion acotada inmediatamente despues de agregar los tests:

```text
mvn -B -Dtest=AuthResourceTest,HealthResourceTest test
```

Luego se ejecuto toda la suite y el reporte de cobertura sin datos acumulados:

```text
mvn -B -Djacoco.append=false verify
```

| comando | codigo de salida real | resultados realmente ejecutados | evidencia |
|---|---|---|---|
| mvn -B -Dtest=AuthResourceTest,HealthResourceTest test | 0 | 27 tests: AuthResourceTest 24 y HealthResourceTest 3; 0 fallos, 0 errores, 0 omitidos; BUILD SUCCESS | [testing-endpoints.log](../target/testing-endpoints.log) |
| mvn -B -Djacoco.append=false verify | 0 | 38 tests: AuthResourceTest 24, HealthResourceTest 3 y LegacyDatabaseTest 11; 0 fallos, 0 errores, 0 omitidos; BUILD SUCCESS | [testing-verify.log](../target/testing-verify.log) |

Son resultados de esta entrega, no los 27 tests de la suite previa ni las capturas manuales de ENDPOINTS.md. Los bucles de variantes cuentan como un metodo JUnit cada uno; el total no representa el numero de peticiones HTTP. El comando completo no excluye el tag correction ni utiliza skipTests. No hace falta iniciar un servidor manual: @QuarkusTest inicia y detiene la aplicacion de prueba.

Reportes por clase: [AuthResourceTest](../target/surefire-reports/com.bcp.workshop.auth.AuthResourceTest.txt), [HealthResourceTest](../target/surefire-reports/com.bcp.workshop.auth.HealthResourceTest.txt), [LegacyDatabaseTest](../target/surefire-reports/com.bcp.workshop.auth.LegacyDatabaseTest.txt). Los artefactos target estan ignorados por Git y se regeneran al ejecutar Maven; este documento conserva los resultados y el metodo de reproduccion.

## Cobertura y metodo de medicion

La meta de [REGLAS.md](../REGLAS.md) es al menos 85% del codigo modificado. Esta entrega modifica solo tests y documentacion; no hay nuevas lineas de produccion. JaCoCo mide las clases de produccion compiladas en target/classes, no la cobertura de las propias pruebas en target/test-classes. No se presenta la cobertura de produccion como una medicion de test code.

Se empleo el agente JaCoCo 0.8.12 ya configurado en el POM y su goal report durante verify. Con `-Djacoco.append=false` se evita mezclar ejecuciones anteriores. Se leyeron los contadores LINE y BRANCH del XML generado: porcentaje = 100 * covered / (covered + missed). Para clases sin ramas, la metrica BRANCH no aplica; no se inventa un contador.

| alcance de produccion medido | lineas cubiertas / total | cobertura de lineas | ramas cubiertas / total | cobertura de ramas |
|---|---|---|---|---|
| AuthResource | 25/25 | 100% | 14/14 | 100% |
| LegacyDatabase, clase cambiada previamente por H-01 | 22/22 | 100% | 2/2 | 100% |
| LegacyTokenUtil | 3/3 | 100% | No aplica | No aplica |
| LoginRequest | 1/1 | 100% | No aplica | No aplica |
| UserRecord | 1/1 | 100% | No aplica | No aplica |
| Total de clases propias | 52/52 | 100% | 16/16 | 100% |

Reportes: [HTML JaCoCo](../target/site/jacoco/index.html) y [XML JaCoCo](../target/site/jacoco/jacoco.xml). Salud la implementa una extension, no una clase propia: sus tres pruebas acreditan el contrato HTTP observado, pero este reporte no mide la cobertura interna de SmallRye/Quarkus. El 100% no demuestra ausencia de defectos o exhaustividad de entradas.

No se redujeron CodeQL, Dependency Review ni JaCoCo. El POM conserva instrumentacion y reporte; el umbral se comprobo con los contadores, no se afirma que exista un jacoco:check automatico. No se ejecutaron los workflows de GitHub localmente.

## Fallos de la ejecucion

Ninguno: ambos comandos finalizaron con codigo 0. No hubo tests fallidos, errores de infraestructura ni tests omitidos. Las trazas de las excepciones controladas de persistencia pertenecen a los casos de caracterizacion ya existentes; los reportes JUnit confirman que esos casos aprobaron.

## Pendientes y preguntas abiertas

- Error de GET /q/health: DOWN/503 no tiene condicion ni cuerpo confirmados en el contrato actual. No se agrego un check de salud artificial para forzar ese estado, porque cambiaria el comportamiento que se pretende probar. No se marco un test inexistente como aprobado.
- Los errores de conversion distintos del request exacto documentado, limites de tamano, timeouts, proxies y fallos de transporte siguen abiertos en ENDPOINTS.md; no se inventaron codigos o schemas para ellos.
- El 500 esta probado directamente sobre login con un doble Java de LegacyDatabase, incluyendo message null. No se presenta ese caso como una ejecucion del 500 por HTTP real. Una inyeccion de fallos por HTTP seria una ampliacion de infraestructura de prueba a justificar por separado.

## Pruebas propuestas, no ejecutadas

No hay pruebas propuestas que se contabilicen entre las 38 aprobadas. Cuando el contrato de salud defina un escenario DOWN y un cuerpo de error real, proponer su test HTTP con esa condicion y ese cuerpo exactos. Esta propuesta no autoriza agregar endpoints, campos, una politica de salud nueva o expectativas de 503 sin evidencia.

## Defectos conocidos, separados de los resultados

No se detecto un nuevo incumplimiento del contrato durante esta ejecucion. Los defectos documentados no se corrigieron ni se ocultaron mediante cambios en expectativas:

- El 500 sigue exponiendo mensaje y clase internos: [AuthResource.java:56](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L56). Los tests existentes son caracterizacion del contrato, no prueba de remediacion de H-06.
- Una excepcion capturada en la consulta se imprime y retorna null, y login responde 401: [LegacyDatabase.java:45](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L45), [LegacyDatabase.java:48](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L48), [AuthResource.java:37](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L37). Esto oculta al cliente un fallo de persistencia. Se reporta explicitamente; no se cambio una expectativa de 500 a 401 para hacer pasar un test nuevo.
- El token no tiene firma y contiene un secreto fijo: [LegacyTokenUtil.java:8](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L8), [LegacyTokenUtil.java:14](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L14). Comprobar sus campos y timestamp no demuestra verificacion criptografica ni expiracion efectiva en consumidores.
- Las contrasenas siguen legibles: [LegacyDatabase.java:29](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L29). Las pruebas no presentan ese almacenamiento como seguro.

El seguimiento de remediaciones permanece exclusivamente en [HALLAZGOS.md](../HALLAZGOS.md); esta entrega no modifica sus estados.

## Documentos consultados

[ENDPOINTS.md](ENDPOINTS.md), [REGLAS.md](../REGLAS.md), [GOOD_PRACTICES.md](GOOD_PRACTICES.md) y [HALLAZGOS.md](../HALLAZGOS.md). Tambien se consultaron los tests existentes y [pom.xml](../pom.xml) para verificar fixtures, aislamiento, dependencias de prueba y configuracion de Surefire/JaCoCo.

## H-02: pruebas antes y despues

Implementacion del unico Item: 2 de [PLAN.md](../PLAN.md). La guia conserva aqui la evidencia historica de endpoints; este apartado sustituye las afirmaciones de plaintext de la entrega anterior para el almacenamiento activo. No modifica el contrato HTTP ni declara remediados token, CORS o errores verbosos.

| endpoint o responsabilidad | caso | tipo | resultado esperado y observado |
|---|---|---|---|
| POST /auth/login | Password largo con Unicode y diferencia despues de 72 bytes | Caracterizacion antes/despues | Credencial completa valida: 200; sufijo incorrecto: 401, sin truncar. |
| POST /auth/login | Unicode compuesto frente a descompuesto | Caracterizacion antes/despues | Solo el valor exacto autentica; no se normaliza la contrasena. |
| PasswordHasher | Salt independiente, PHC autocontenido e interoperabilidad | Correccion | Hashes distintos para igual entrada; ambos verifican con Password4j. |
| PasswordHasher / AuthenticationService | Password incorrecto, hash corrupto, parametros invalidos y ausencia | Error / correccion | No autentican; no se usa plaintext como fallback ni se filtra el hash al DTO. |
| PasswordMigration | Base nueva y base legada con password que parece un hash | Correccion | Esquema version 1, solo password_hash; la credencial legada completa sigue funcionando. |
| PasswordMigration | Segundo arranque, cuentas existentes, ids, unicidad y secuencia con ids borrados | Caso borde | No rehash, duplicados ni sobrescritura de cuentas; no se reutilizan ids historicos. |
| PasswordMigration | Fallo en el segundo hash, version desconocida, esquema incorrecto, transaccion previa y semilla invalida | Error / recuperacion | Rollback o rechazo sin estado parcial, conservando datos y version; sin fallback plaintext. |
| PasswordMigration | 100 usuarios ficticios y restauracion de backup cerrado | Operativo | Migracion <=60 s, autenticacion <=1 s, memoria de Argon2 configurada a 19 MiB; recuperacion aprobada. |

Codigo completo ejecutado: [PasswordHasherTest.java](../src/test/java/com/bcp/workshop/auth/PasswordHasherTest.java), [AuthenticationServiceTest.java](../src/test/java/com/bcp/workshop/auth/AuthenticationServiceTest.java), [LegacyDatabaseTest.java](../src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java), [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java). Los tests heredados conservan sus expectativas observables; el doble del resource y los fixtures operativos se adaptaron al servicio y a password_hash, respectivamente. El fixture legado existe solo para ensayar migracion en target.

### Directorio, comandos y codigos reales

Directorio de todos los comandos: raiz de workshop-auth-backend, con JDK 21. Surefire suministra la base SQLite dentro de target. No se arranco el servicio contra data/workshop.db. Las pruebas de migracion se ejecutan en serie, con conexiones de ensayo cerradas al copiar/restaurar. No se excluyeron pruebas en verify.

```text
mvn -B -Dtest=LegacyDatabaseTest,AuthResourceTest,HealthResourceTest -DexcludedGroups=h02-correction test
mvn -B -Dtest=LegacyDatabaseTest -Dgroups=h02-correction test
mvn -B -Dtest=PasswordHasherTest,AuthenticationServiceTest test
mvn -B -Dtest=PasswordHasherTest,AuthenticationServiceTest,LegacyDatabaseTest test
mvn -B -Dtest=LegacyDatabaseTest#freshDatabaseContainsOnlyPasswordHashes+migratesPlaintextWithoutLosingCredentials test
mvn -B -Djacoco.append=false verify
```

En PowerShell, encerrar entre comillas cada argumento -D completo, especialmente el selector que contiene #. El tag h02-correction contiene dos casos en la version previa y once al completar la entrega; la comparacion exacta antes/despues usa los dos metodos originales identificados en la quinta orden.

| fase | salida real | resultado | evidencia |
|---|---|---|---|
| Caracterizacion ampliada antes | 0 | 40 aprobadas | [h02-characterization-before.log](../target/h02-characterization-before.log) |
| Correccion de schema/migracion antes | 1 | 2 fallidas: ausencia de password_hash | [h02-correction-before.log](../target/h02-correction-before.log) |
| Unitarias nuevas antes de produccion | 1 | Compilacion roja por clases inexistentes; no se contabiliza como tests ejecutados | [h02-unit-before.log](../target/h02-unit-before.log) |
| Primer control de core despues | 0 | 24 aprobadas: servicio 4, hasher 7, repositorio 13 en esa fase | [h02-core-after.log](../target/h02-core-after.log) |
| Misma caracterizacion despues | 0 | 40 aprobadas | [h02-characterization-after.log](../target/h02-characterization-after.log) |
| Mismos dos casos originales despues | 0 | 2 aprobadas | [h02-correction-after.log](../target/h02-correction-after.log) |
| Migracion y recuperacion ampliadas | 0 | 11 ensayos aprobados | [h02-migration-after.log](../target/h02-migration-after.log) |
| Suite completa final | 0 | 62 aprobadas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS | [h02-verify.log](../target/h02-verify.log) |

Desglose final: AuthResourceTest 26, HealthResourceTest 3, AuthenticationServiceTest 4, LegacyDatabaseTest 22 y PasswordHasherTest 7. Los bucles de variantes cuentan como un test JUnit, no como una prueba por iteracion. El diff propio esta en [h02.diff](../target/h02.diff) y compara esta entrega con la instantanea inmediatamente anterior, no con main.

### Cobertura vigente de H-02

Metodo: agente JaCoCo 0.8.12 y report durante verify, con append=false para no acumular ejecuciones. Se leyeron contadores LINE y BRANCH de [jacoco.xml](../target/site/jacoco/jacoco.xml), usando 100 * covered / (covered + missed). Los tests no se incluyen como clases de produccion.

| clase modificada | lineas | porcentaje | ramas | porcentaje |
|---|---|---|---|---|
| AuthResource | 25/25 | 100% | 14/14 | 100% |
| LegacyDatabase | 23/23 | 100% | 2/2 | 100% |
| AuthenticationService | 10/10 | 100% | 4/4 | 100% |
| StoredCredentials | 2/2 | 100% | No aplica | No aplica |
| PasswordHasher | 26/28 | 92,86% | 28/28 | 100% |
| PasswordMigration | 85/86 | 98,84% | 26/28 | 92,86% |

Todas las clases modificadas superan 85% de lineas y ramas donde aplica. Global, incluyendo clases no modificadas: 176/179 lineas (98,32%) y 74/76 ramas (97,37%). [Reporte HTML](../target/site/jacoco/index.html). Los caminos no cubiertos no se ocultaron con exclusiones. CodeQL, Dependency Review y JaCoCo no se redujeron; los workflows de GitHub no se ejecutaron localmente.

### Ensayo operativo final

Datos impresos por measuresMigrationAndRestoresTheIsolatedBackup en la suite final: 100 usuarios ficticios, Argon2id memoryKiB=19456, iterations=2, parallelism=1; hash=35,900 ms, verify=36,273 ms, migration=3721,585 ms, recovery=PASS. Los asserts de tiempo comprueban autenticacion <=1 s y migracion/restauracion <=60 s cada una.

Heap JVM observado antes=275832592 bytes, despues=248359184 bytes. Son instantaneas sujetas al GC, no un pico de memoria ni un limite total de 19 MiB para Java; 19 MiB es el parametro de trabajo de Argon2. La copia ficticia recuperada reside en target/h02-recovery-fixture.db. Su plaintext es exclusivamente de ensayo; no contiene credenciales reales. Los limites fueron aprobados para este ensayo local, no son un SLA para otro hardware o volumen.

Password4j 1.8.4 se verifico con JDK 21/Quarkus JVM, licencia Apache 2.0 y API Argon2id. No se verifico imagen nativa. El arbol resuelto esta en [h02-dependency-after.log](../target/h02-dependency-after.log). Consultas OSV del 2026-10-19 sin advisories devueltos para [Password4j](../target/h02-password4j-osv.json) y [SLF4J API 2.0.17](../target/h02-slf4j-osv.json); no equivalen a certificar todo el proyecto ni resuelven H-05.

### Fallos, pendientes y desviaciones

- Fallos finales: ninguno. Los dos rojos funcionales y la compilacion roja previa quedan preservados en los logs; no se sustituyeron por expectativas mas debiles.
- Pendientes fuera de H-02: error DOWN/503 de salud sin contrato confirmado; token no firmado, CORS y errores verbosos; no se agregaron checks o politicas para esconderlos. La nota de plaintext de ENDPOINTS.md quedo historica al implementar H-02; no se edito ese documento conforme a PLAN.md.
- No se propone otra remediacion en esta entrega. Se implementaron las clases y la dependencia previstas, con limites operativos aprobados y sin abortar el arranque de un modo nuevo. No se cambiaron otras filas del backlog.
- El cierre protege el esquema activo, no garantiza borrado forense de paginas, WAL, journal o copias antiguas. Procedimiento de respaldo y recuperacion: [README.md](../README.md).