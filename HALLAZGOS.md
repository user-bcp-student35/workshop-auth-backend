# Hallazgos - Backend Quarkus

Matriz unica de seguimiento de los siete casos de RETO.md. Diagnostico inicial por analisis estatico; H-01 y H-02 se corrigieron y verificaron el 2026-10-19 con aprobacion humana registrada en PLAN.md. Los demas hallazgos conservan su diagnostico y estado inicial. Todos los casos tienen evidencia; ninguno corresponde a "No encontrado". Las correcciones internas deben conservar el contrato de [docs/ENDPOINTS.md](docs/ENDPOINTS.md).

| id | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
|---|---|---|---|---|---|---|
| H-01 | SQL: concatenacion de credenciales | Vulnerabilidad | La consulta concatenada permitia alterar el filtro de autenticacion. Resuelto con PreparedStatement y setString para ambas credenciales; mapeo separado y contrato de respuesta conservado. | src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:18; src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:39; src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:40; src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:41; src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:51 | alta | Resuelto |
| H-02 | Datos: contrasenas en texto plano | Vulnerabilidad | Resuelto con Argon2id, salt independiente y migracion transaccional sin plaintext activo. Respaldan PasswordHasherTest, migratesPlaintextWithoutLosingCredentials y el diff local target/h02.diff. | src/main/java/com/bcp/workshop/auth/PasswordHasher.java:34; src/main/java/com/bcp/workshop/auth/PasswordHasher.java:41; src/main/java/com/bcp/workshop/auth/PasswordMigration.java:42; src/main/java/com/bcp/workshop/auth/PasswordMigration.java:22; src/main/java/com/bcp/workshop/auth/AuthenticationService.java:17 | alta | Resuelto |
| H-03 | Token: Base64 con secreto embebido | Vulnerabilidad | El token codifica usuario, rol, vencimiento y secreto sin firma. Base64 es reversible y no protege la integridad; expone el secreto y permite alterar los campos sin garantia criptografica. Se incluye un vencimiento, pero aqui no hay verificacion del token; no se afirma como lo validan consumidores externos. | src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:8; src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:13; src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:14; src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:15 | alta | Pendiente |
| H-04 | Configuracion: secreto y CORS hardcodeados | Vulnerabilidad | El secreto fijo en Java queda expuesto con el codigo y no puede rotarse mediante configuracion. CORS esta habilitado con una expresion que admite cualquier origen, ampliando los origenes web autorizados; no se afirma que esto por si solo permita robar credenciales. | src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java:8; src/main/resources/application.properties:4; src/main/resources/application.properties:5; src/main/resources/application.properties:6; src/main/resources/application.properties:7 | alta | Pendiente |
| H-05 | SCA: Quarkus 3.27.4 y Commons Text 1.9 | Sugerencia | El BOM de Quarkus y la dependencia Commons Text tienen las versiones indicadas en RETO.md. Requieren investigar advisories y su aplicabilidad antes de actualizar o remover: las versiones por si solas no prueban un CVE explotable. El uso observado es escapeHtml4, no interpolacion; no se ejecuto un analisis SCA. Severidad preventiva, no puntuacion de un CVE confirmado. | pom.xml:13; pom.xml:22; pom.xml:49; pom.xml:50; src/main/java/com/bcp/workshop/auth/AuthResource.java:45 | media | Pendiente |
| H-06 | Errores: respuesta expone detalles internos | Vulnerabilidad | El catch generico del resource devuelve el mensaje y el nombre de clase de la excepcion al cliente; una excepcion que llegue a ese bloque puede revelar detalles de implementacion. Las excepciones capturadas en findByCredentials se imprimen y retornan null, por lo que terminan en 401, no en este 500. | src/main/java/com/bcp/workshop/auth/AuthResource.java:54; src/main/java/com/bcp/workshop/auth/AuthResource.java:56; src/main/java/com/bcp/workshop/auth/AuthResource.java:57; src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:39; src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:42 | media | Pendiente |
| H-07 | Clean code: resource concentra responsabilidades | Refactor | login mezcla validacion anidada, coordinacion de autenticacion, llamada directa a persistencia, generacion de token y armado de respuesta. Este acoplamiento dificulta pruebas aisladas y aumenta el riesgo de regresiones al corregir seguridad; el SQL reside en LegacyDatabase, no en el resource. | src/main/java/com/bcp/workshop/auth/AuthResource.java:24; src/main/java/com/bcp/workshop/auth/AuthResource.java:29; src/main/java/com/bcp/workshop/auth/AuthResource.java:35; src/main/java/com/bcp/workshop/auth/AuthResource.java:39; src/main/java/com/bcp/workshop/auth/AuthResource.java:40; src/main/java/com/bcp/workshop/auth/AuthResource.java:49 | baja | Pendiente |

## Evidencia de cierre de H-01

Se escribieron y ejecutaron las pruebas antes de modificar LegacyDatabase. Se reutilizan [AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java) y la nueva [LegacyDatabaseTest.java](src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java). El tag correction separa las pruebas de correccion de las de caracterizacion; mvn verify ejecuta ambas, sin excluir ninguna.

| Verificacion | Antes del cambio | Despues del cambio |
|---|---|---|
| Caracterizacion de repositorio y contrato HTTP | 21 pruebas aprobadas, 0 fallos, 0 errores | Las mismas 21 aprobadas, 0 fallos, 0 errores |
| Correccion SQL en repositorio y HTTP | 6 pruebas fallidas: SQL en username/password autentica indebidamente y credenciales con comillas no autentican | Las mismas 6 aprobadas: rechazo de entradas manipuladas y autenticacion literal con comillas |
| Suite completa, mvn -B verify | No usada como linea base verde: las pruebas nuevas de correccion fallaban | 27 pruebas aprobadas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS |

Comandos ejecutados desde este repositorio, con JDK 21:

```text
mvn -Dtest=LegacyDatabaseTest,AuthResourceTest -DexcludedGroups=correction test
mvn -Dtest=LegacyDatabaseTest,AuthResourceTest -Dgroups=correction test
mvn -B verify
```

Los primeros dos comandos se ejecutaron antes y despues de parametrizar la consulta. Evidencias locales generadas en target (ignoradas por Git): [caracterizacion antes](target/h01-characterization-before.log), [correccion antes](target/h01-correction-before.log), [caracterizacion despues](target/h01-characterization-after.log), [correccion despues](target/h01-correction-after.log) y [verificacion completa](target/h01-verify.log).

JaCoCo para LegacyDatabase, unica clase de produccion modificada: 22/22 lineas (100%), 2/2 ramas (100%), 90/90 instrucciones (100%) y 5/5 metodos (100%). Supera el 85% requerido. Reporte: [JaCoCo](target/site/jacoco/index.html). Los workflows de CodeQL y Dependency Review permanecen intactos; sus ejecuciones en GitHub no se han realizado localmente.

Desviacion aprobada: Surefire configura AUTH_DB_URL con SQLite dentro de target para que el comando de CI funcione sin variables externas; no se agregaron dependencias ni se modificaron gates. Durante la preparacion se corrigio un problema de registro del driver JDBC entre classloaders de pruebas; despues se repitio y aprobo la caracterizacion antes de tocar produccion. No se modifico docs/ENDPOINTS.md ni se implementaron otros hallazgos. Las referencias iniciales de los hallazgos pendientes corresponden al diagnostico previo a H-01.

## Evidencia de cierre de H-02

Implementacion exclusiva del Item: 2 aprobado en PLAN.md. Se conserva el almacenamiento de hashes autocontenidos Argon2id con salt de 16 bytes, salida de 32 bytes y parametros por defecto de 19456 KiB, 2 iteraciones y 1 hilo. La migracion elimina la columna activa password, conserva datos e identificadores, y no habilita un fallback de autenticacion contra plaintext. No se migro data/workshop.db durante este trabajo: solo bases ficticias dentro de target.

Diff propio de H-02 contra la instantanea tomada antes de implementarlo, no contra main ni mezclado con H-01: [target/h02.diff](target/h02.diff).

| verificacion | antes | despues |
|---|---|---|
| Caracterizacion ampliada del contrato y credenciales largas/Unicode | 40 aprobadas, salida 0 | Las mismas 40 aprobadas, salida 0 |
| Dos pruebas originales de correccion de almacenamiento/migracion | 2 fallidas porque no existia password_hash, salida 1 | Las mismas 2 aprobadas, salida 0 |
| Nuevas responsabilidades de hash y servicio | Las pruebas se escribieron antes: compilacion roja por clases aun inexistentes, salida 1; no se contabiliza como tests ejecutados | 7 pruebas PasswordHasherTest y 4 AuthenticationServiceTest aprobadas |
| Migracion, rollback, versiones, secuencia, semillas, reinicio y restauracion | Casos adicionales definidos para la remediacion | 11 ensayos h02-correction aprobados, incluida recuperacion de backup ficticio |
| Suite completa | Linea base ampliada de 40 pruebas antes de implementar | 62 aprobadas, 0 fallos, 0 errores, 0 omitidas; mvn verify, salida 0 |

Evidencias locales: [caracterizacion antes](target/h02-characterization-before.log), [correccion antes](target/h02-correction-before.log), [unitarias antes](target/h02-unit-before.log), [caracterizacion despues](target/h02-characterization-after.log), [correccion despues](target/h02-correction-after.log), [migracion](target/h02-migration-after.log), [suite completa](target/h02-verify.log). Detalle y comandos: [docs/TESTING.md](docs/TESTING.md).

JaCoCo fresco: AuthResource, LegacyDatabase, AuthenticationService y StoredCredentials con 100% de lineas; PasswordHasher 26/28 (92,86%) y PasswordMigration 85/86 (98,84%). Todas las clases modificadas superan 85% de lineas y ramas donde aplica. Global: 176/179 lineas (98,32%) y 74/76 ramas (97,37%).

Ensayo final con 100 usuarios ficticios: hash 35,900 ms, verificacion 36,273 ms, migracion 3721,585 ms; login dentro de 1 s y migracion dentro de 60 s, con restauracion aprobada. La memoria de Argon2 esta configurada en 19 MiB; el heap JVM antes/despues se registro, no se afirma que sea su pico ni que toda la JVM consuma solo 19 MiB.

Dependencia agregada: Password4j 1.8.4 (Apache 2.0), con SLF4J API 2.0.17 resuelto. OSV no devolvio advisories para esas dos versiones en la consulta del 2026-10-19: [Password4j](target/h02-password4j-osv.json), [SLF4J](target/h02-slf4j-osv.json). Esto no certifica las demas dependencias ni resuelve H-05. Los workflows siguen intactos; CodeQL y Dependency Review se ejecutaran en GitHub, no localmente.

Limites del cierre: elimina plaintext en el esquema activo, no promete borrado forense de paginas, WAL, journal ni backups historicos. El procedimiento de proteccion y recuperacion se documenta en README.md. El contrato HTTP se conserva; docs/ENDPOINTS.md no se edito conforme al plan y su nota historica de almacenamiento plaintext requiere una futura actualizacion documental. Token, CORS y errores verbosos no se remediaron ni se marcaron resueltos.