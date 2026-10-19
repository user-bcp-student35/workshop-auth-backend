# Plan de correccion del hallazgo 2

### Aprobacion de implementacion de H-02

El 2026-10-19 el usuario indico que este PLAN.md esta revisado y aprobado y solicito implementar sus items. En la consulta operativa posterior aprobo ensayar exclusivamente en target con 100 usuarios ficticios, Argon2id de 19 MiB, 2 iteraciones y 1 hilo, login de como maximo 1 segundo y migracion de como maximo 60 segundos. Los ensayos incluyen ventana sin escrituras y rollback/restauracion; no se migra data/workshop.db. La inicializacion conserva su manejo de errores actual, sin introducir un aborto de arranque nuevo. Las referencias siguientes a ausencia de aprobacion describen la redaccion inicial y quedan sustituidas por este registro.

### Aprobacion humana historica

El 2026-10-19 el usuario confirmo en esta conversacion: "Confirmo", en respuesta a la solicitud de aprobar PLAN.md para implementar unicamente H-01 y autorizar el registro de esa aprobacion. La autorizacion comprende pruebas antes/despues, parametrizacion SQL, verificacion de cobertura y actualizacion de H-01 en HALLAZGOS.md. No autoriza implementar H-02, H-03 ni H-04.

El mismo dia autorizo el ajuste minimo de Surefire en pom.xml para proporcionar AUTH_DB_URL con una base SQLite dentro de target durante las pruebas. Es una desviacion acotada respecto a los archivos inicialmente previstos para H-01, necesaria para que el comando existente de CI, mvn verify, ejecute las pruebas aisladas sin configuracion externa. No agrega dependencias ni reduce quality gates.

Estos antecedentes no extienden la autorizacion historica a nuevos cambios. Este encargo actualiza solo PLAN.md; no modifica codigo, contrato ni estados del backlog.

## Item: 2

### Item de HALLAZGOS.md y situacion actual

Corresponde a **H-02 - Datos: contrasenas en texto plano**, tipo Vulnerabilidad, severidad alta y estado **Pendiente** en [HALLAZGOS.md](HALLAZGOS.md). No se incluye otro item ni se cambia su estado al redactar esta propuesta.

Evidencia vigente: [LegacyDatabase.java:28](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L28) crea la columna password TEXT NOT NULL; [LegacyDatabase.java:29](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L29) y [LegacyDatabase.java:30](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L30) insertan contrasenas legibles de laboratorio. El SELECT de [LegacyDatabase.java:19](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L19) compara password directamente. Aunque ya utiliza PreparedStatement, eso evita SQL injection, no protege las credenciales almacenadas. Una lectura de la base revela contrasenas reutilizables.

La aprobacion historica de H-01 no se extiende a este item. La aprobacion posterior y los limites operativos propios de H-02 estan registrados en el encabezado de esta version; los resultados de implementacion se conservan en HALLAZGOS.md y docs/TESTING.md.

### Alcance propio

Eliminar el almacenamiento activo y la comparacion SQL de contrasenas en texto plano, conservando las cuentas, sus credenciales funcionales, sus datos y la interfaz HTTP actual. Incluye hash/verificacion, migracion del esquema, semillas y los ajustes minimos de coordinacion y pruebas indispensables para ese cambio.

Quedan fuera firma y validacion de tokens, secreto, CORS, saneamiento de errores, actualizacion general de dependencias, endpoints nuevos, politica nueva de passwords, rate limiting y refactor general del resource. La parametrizacion existente se conserva como regresion, no se presenta como una segunda remediacion incluida. GET /q/health tampoco cambia.

### Cambio propuesto

**Hash y verificacion.** Usar Argon2id con salt aleatorio independiente por contrasena y formato autocontenido que guarde algoritmo, parametros, salt y hash. No cifrar contrasenas de manera reversible, no usar SHA-256 solo y no implementar criptografia propia. Verificar con la biblioteca; no comparar hashes regenerados porque los salts seran distintos. No registrar ni devolver password, hash ni salt.

La eleccion de Argon2id es una propuesta de implementacion, no una convencion nueva atribuida al repositorio: GOOD_PRACTICES.md pide hash lento con salt y da BCrypt como ejemplo. El contrato no establece longitud maxima. Evitar truncamiento silencioso y validar entradas Unicode y passwords que difieren despues de 72 bytes UTF-8. No introducir un limite de 72 bytes ni normalizacion para facilitar el uso de BCrypt. Si la biblioteca candidata impone limites incompatibles, detener esa seleccion antes de implementar; no cambiar la API para adaptarla.

**Dependencia justificada.** Evaluar `com.password4j:password4j` como biblioteca de hashing mantenida, con Argon2id, generacion de salt y codificacion/verificacion encapsuladas. El [pom.xml](pom.xml) actual no declara una biblioteca de hash lento. Antes de agregarla, revisar el arbol resuelto por si ya hay una API adecuada y comprobar soporte real de Argon2id, formato, licencia, compatibilidad con JDK 21/Quarkus 3.27.4 y advisories de una version concreta. No se afirma que el BOM gestione esa version. Fijar una version verificada en la futura implementacion; no agregar la dependencia ni actualizar Quarkus en este encargo. Si la evaluacion falla, registrar el bloqueo y revisar la propuesta, sin reemplazarla por criptografia casera.

**Separacion minima de responsabilidades.** El repositorio busca por username mediante un SELECT parametrizado y entrega una proyeccion interna con hash y datos de usuario. Un servicio de autenticacion coordina esa busqueda con PasswordHasher y devuelve UserRecord solo cuando la verificacion es correcta. El resource sustituye la llamada directa de autenticacion por la delegacion al servicio, sin reescribir sus validaciones, token, mensajes ni armado de respuesta. No agregar hash a UserRecord ni a un DTO HTTP. Esta separacion sirve al cambio de passwords, no a una limpieza general de capas.

**Semillas.** Mantener student y trainer con sus credenciales publicas de laboratorio y datos actuales, sin duplicarlos ni sobrescribir cuentas existentes. Insertar hashes ficticios precomputados para esas semillas mediante parametros; no persistir primero la contrasena legible para hashearla despues. Esos hashes de bootstrap pueden configurarse como fixtures locales claramente identificados, no como secretos reales. Conservar el arranque local documentado sin exigir una variable nueva que no se haya acordado. No reutilizar estos usuarios de laboratorio en un despliegue real.

**Clean Code.** Separar hash, verificacion, consulta, mapeo, migracion y siembra en metodos de una responsabilidad. Reutilizar el mismo PasswordHasher en los caminos que calculan/verifican hashes; no duplicar reglas ni consultas. Usar nombres de dominio, guard clauses, try-with-resources y constantes o configuracion validada para algoritmo, costes y SQL. No trasladar comparacion criptografica ni persistencia al resource.

### Migracion y recuperacion

1. Reconocer el esquema legado por columnas y una version de esquema, no por prefijos de los valores almacenados. Una contrasena que se parezca a un hash sigue siendo texto legado que debe procesarse completo.
2. Ejecutar una migracion transaccional en una SQLite aislada primero: crear el nuevo esquema con password_hash NOT NULL, leer cada password, producir su hash y copiar id, username, display_name y role sin cambios.
3. Preservar unicidad, claves y comportamiento de AUTOINCREMENT, incluidos los identificadores siguientes. Validar recuentos y datos antes de sustituir la tabla. El esquema final no debe conservar la columna password ni otra copia activa de su texto plano.
4. Confirmar version y tabla solo al completar la migracion. Un segundo arranque no duplica usuarios ni recalcula hashes existentes. Una base nueva nace directamente con hashes, sin pasar por texto plano. Una base con version no soportada no se adivina ni se modifica parcialmente.
5. Ante fallo, rollback sin mezclar estados ni habilitar un fallback de autenticacion en texto plano. Mantener separadas la migracion y las consultas operativas. Mientras la aplicacion siga iniciada, una consulta que no pueda autenticarse conserva el rechazo/401 documentado; no introducir otro codigo HTTP. Si se considera necesario abortar el arranque ante un fallo de migracion, es una decision operativa pendiente de autorizacion porque hoy la inicializacion captura errores sin relanzarlos.
6. Acordar antes de implementar una ventana sin escrituras concurrentes, una copia de seguridad local protegida y un procedimiento de recuperacion ensayado. No restaurar automaticamente una copia antigua y anunciar H-02 como corregido. La copia puede contener plaintext y requiere acceso y retencion controlados.

Cambiar la tabla no garantiza borrado forense de paginas libres, journal, WAL o backups de SQLite. Identificar esos artefactos y acordar su tratamiento dentro del backend. No afirmar que una migracion logica borra todas las copias historicas del secreto.

**Puerta operativa previa:** medir tiempo y memoria de un hash/verificacion y de la migracion con un volumen representativo. Acordar limites aceptables de arranque, bloqueo de SQLite y latencia de login antes de elegir los costes; no inventar una cifra aprobada ni medir solo una contrasena. Si no se cumplen, ajustar la propuesta y repetir el ensayo antes de tocar datos existentes.

### Contrato a preservar

[docs/ENDPOINTS.md](docs/ENDPOINTS.md) se consulta y no se edita en este encargo. H-02 pide cambiar la representacion interna de las contrasenas; no pide cambiar la interfaz HTTP ni agregar una politica de credenciales.

- Conservar ruta, ausencia de Authorization obligatorio, Content-Type, campos username/password/client, tolerancia de propiedades desconocidas y validaciones en el orden actual. No introducir trim, cambio de mayusculas ni normalizacion Unicode antes de verificar password.
- Mantener 200 con los mismos campos, tipos y valores fijos de expiresIn/debug, identidad y escape de displayName. El token sigue teniendo el formato actual y su vencimiento dinamico; no modificarlo como parte de este item.
- Mantener mensajes y condiciones documentadas de 400/401/500, incluidos los errores de framework 406/415. El fallo de consulta capturado conserva el 401 actual; hash corrupto o verificacion negativa no debe filtrar material de credenciales ni dar acceso.
- Las cuentas existentes deben seguir autenticando con su misma contrasena; una contrasena distinta debe rechazarse aun si comparte un prefijo largo. Mantener las pruebas de comillas y SQL manipulado.
- No cambiar el endpoint de salud ni convertir su UP actual en una comprobacion de passwords o SQLite.

La nota de seguridad que actualmente describe plaintext quedaria historicamente desactualizada tras implementar H-02; su futura actualizacion descriptiva debe basarse en evidencia de la remediacion, sin alterar requests o responses. No se realiza esa actualizacion ni se afirma que los hashes ya existan ahora.

### Archivos que se van a tocar al implementar

| archivo existente | cambio limitado a H-02 |
|---|---|
| [src/main/java/com/bcp/workshop/auth/LegacyDatabase.java](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java) | Lookup parametrizado por username, lectura de proyeccion interna con hash y delegacion de inicializacion/migracion. |
| [src/main/java/com/bcp/workshop/auth/AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java) | Solo delegar la autenticacion al servicio; conservar el contrato y el resto del flujo. |
| [src/main/resources/application.properties](src/main/resources/application.properties) | Parametros de hash con nombres expresivos y, si se alojan aqui, hashes ficticios de bootstrap; no variables nuevas obligatorias sin acuerdo. |
| [pom.xml](pom.xml) | Una dependencia de hashing justificada y verificada, solo si no existe API utilizable; no reducir gates ni cambiar otras versiones. |
| [src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java](src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java) | Separar fixtures de esquema legado de fixtures con hashes; probar lookup, preservacion de datos y migracion con SQLite en target. |
| [src/test/java/com/bcp/workshop/auth/AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java) | Caracterizacion HTTP, casos de passwords largos/Unicode y ajustes del doble de autenticacion sin debilitar expectativas. |
| [README.md](README.md) | Procedimiento local de migracion, recuperacion y configuracion elegida; conservar las credenciales publicas de laboratorio. |
| [docs/TESTING.md](docs/TESTING.md) | Registrar casos agregados y ejecuciones reales, no resultados anticipados. |

Archivos nuevos propuestos, todos dentro del mismo paquete del backend y solo si no existe un hogar adecuado para esa responsabilidad:

```text
src/main/java/com/bcp/workshop/auth/AuthenticationService.java
src/main/java/com/bcp/workshop/auth/PasswordHasher.java
src/main/java/com/bcp/workshop/auth/PasswordMigration.java
src/main/java/com/bcp/workshop/auth/StoredCredentials.java
src/test/java/com/bcp/workshop/auth/PasswordHasherTest.java
src/test/java/com/bcp/workshop/auth/AuthenticationServiceTest.java
```

StoredCredentials es una proyeccion interna, no un DTO HTTP. Reutilizar LegacyDatabaseTest para migracion en vez de crear otra infraestructura de fixtures duplicada. UserRecord y LoginRequest no necesitan incorporar hashes. Las clases propuestas no existen todavia y no se crean en este encargo.

### Pruebas que deben escribirse antes de tocar la implementacion

Primero ejecutar y conservar las pruebas existentes de caracterizacion y regresion SQL. Despues escribir los casos nuevos contra bases ficticias y el contrato de hashing propuesto, antes de modificar produccion. Las pruebas de correccion pueden fallar o no compilar hasta introducir la responsabilidad nueva; deben registrarse como tales, no como una caracterizacion verde ni como resultados ya ejecutados.

| prueba previa | comportamiento a fijar antes del cambio | verificacion posterior de H-02 |
|---|---|---|
| Contrato HTTP completo | student/trainer, campos y tipos del 200, escape de displayName, client ignorado, validaciones, 401, 500 controlado y errores de framework. | Misma interfaz despues del cambio; los hashes no aparecen en ninguna respuesta. Reutilizar AuthResourceTest. |
| Credenciales exactas | Mayusculas, espacios, comillas y Unicode; passwords largos que solo difieren despues de 72 bytes UTF-8. | Se verifica el valor completo sin normalizacion, truncamiento o coincidencias por prefijo; mantener rechazo de SQL manipulado. |
| Hash con salt | Especificar hash/verificacion como responsabilidad de seguridad, sin fijar un hash literal para una entrada. | Misma contrasena produce hashes diferentes por el salt; ambos verifican; password incorrecto no verifica. Formato completo interpretable por la biblioteca. |
| Migracion de base existente | Fixture explicito del esquema legado con ids y todos los datos, incluido un password que se parezca a un hash. | Misma identidad y credencial funcional tras migrar; ninguna columna password ni valor legible activo; conteos, unicidad e identificadores preservados. |
| Base nueva y reinicio | Semillas actuales y regla de no sobrescribir cuentas existentes. | Base nueva solo con hashes, segundo arranque idempotente, sin rehash ni duplicados y sin modificar contrasenas previamente migradas. |
| Rollback y version de esquema | Fixture con fallo controlado a mitad de migracion; version desconocida. | No quedan tablas/filas/versiones parcialmente confirmadas; no se autentica con plaintext como fallback. No se destruyen datos de la base del usuario. |
| Repositorio y servicio | Usuario inexistente y fallo de consulta se distinguen internamente pero producen el rechazo documentado. | Consulta solo por username, siempre parametrizada; verificacion en PasswordHasher, sin exponer hash. Hash corrupto no autentica ni filtra errores de la biblioteca. |
| Rendimiento y recuperacion | Dataset representativo local, memoria disponible y mecanismo de fallo/restore reproducible. | Limites de tiempo/memoria y ventana de migracion previamente acordados se cumplen; recuperacion ensayada sin aceptar una migracion incompleta. |

Los cambios de firmas internas pueden exigir trasladar pruebas de autenticacion del repositorio al servicio; conservar los mismos casos y sus resultados observables. Las utilidades actuales insertan password literalmente ([LegacyDatabaseTest.java:127](src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java#L127)): actualizar fixtures operativos para hashes y mantener plaintext solo en el fixture aislado destinado a probar la migracion, nunca en la base normal.

Conservar tambien la caracterizacion de salud existente, sin agregar checks nuevos ni esperar un 503 no definido. No cambiar pruebas para hacer pasar un bug; reportar cualquier incompatibilidad y detener la ampliacion de alcance.

### Secuencia y verificacion

1. Obtener aprobacion explicita de esta propuesta de H-02, incluyendo biblioteca, estrategia de migracion y decisiones operativas abiertas. La aprobacion historica de otro trabajo no basta.
2. Ejecutar la linea base actual y escribir primero caracterizacion ampliada y pruebas de correccion. Todas las bases de ensayo deben estar dentro de target y usar datos ficticios.
3. Validar la biblioteca y medir costes con datos representativos. Acordar ventana y recuperacion antes de migrar una base existente.
4. Implementar en cambios revisables dentro de H-02: hashing, migracion/repositorio, servicio minimo y cableado del resource. No incorporar remediaciones de otros items.
5. Repetir caracterizacion, pruebas nuevas y suite completa. Comparar las respuestas HTTP y revisar que no quede lectura/comparacion/almacenamiento activo de plaintext ni fallback legado.
6. Medir al menos 85% del codigo de produccion modificado con JaCoCo, incluyendo ramas de fallo de migracion y verificacion. No reducir ni silenciar CodeQL, Dependency Review o JaCoCo. Registrar resultados reales en docs/TESTING.md.
7. Solo con evidencia de correccion completa actualizar la fila H-02 en HALLAZGOS.md; no cambiar otros estados. Esta propuesta no marca el item Resuelto.

Comandos previstos desde la raiz de workshop-auth-backend con JDK 21, primero contra el codigo actual y despues de cada cambio:

```text
mvn -B -Dtest=LegacyDatabaseTest,AuthResourceTest,HealthResourceTest test
```

Cuando se creen las clases nuevas de prueba:

```text
mvn -B -Dtest=PasswordHasherTest,AuthenticationServiceTest,LegacyDatabaseTest,AuthResourceTest test
mvn -B -Djacoco.append=false verify
```

verify debe ejecutar todas las pruebas, sin exclusiones ni skipTests. Reportes esperados de futuras ejecuciones: Surefire para resultados y JaCoCo HTML/XML para cobertura de produccion; medir covered / (covered + missed) por clases modificadas y ramas, sin presentar cobertura de produccion como cobertura del propio codigo de tests. Guardar comandos y codigos de salida reales.

No se ejecutaron estos comandos, benchmarks ni migraciones al redactar este plan. No se verificaron aun una version concreta de Password4j ni sus advisories. Las cuestiones operativas y de dependencia son puertas previas, no resultados aprobados.

### Referencias consultadas

[docs/ENDPOINTS.md](docs/ENDPOINTS.md), [docs/GOOD_PRACTICES.md](docs/GOOD_PRACTICES.md), [HALLAZGOS.md](HALLAZGOS.md), [README.md](README.md) y la version anterior de este plan. Se contrastaron LegacyDatabase, LegacyDatabaseTest y pom.xml para identificar el esquema vigente, fixtures y dependencias declaradas. Las reglas de cobertura y Clean Code de [REGLAS.md](REGLAS.md) siguen siendo condiciones de aceptacion, no una autorizacion para ampliar el item.