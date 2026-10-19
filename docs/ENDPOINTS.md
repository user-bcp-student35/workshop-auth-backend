# Contrato actual de endpoints

Actualizado el 2026-10-19 a partir del codigo vigente y de peticiones HTTP locales contra Quarkus 3.27.4. Solo H-01 esta corregido: la consulta de login es parametrizada; el token, las contrasenas y los errores conservan los defectos indicados abajo. El seguimiento permanece en [HALLAZGOS.md](../HALLAZGOS.md).

## Conexion y ejecucion local

- URL base por defecto: `http://127.0.0.1:8080`. Puerto y host: [application.properties:1](../src/main/resources/application.properties#L1). El perfil prod cambia el host a 0.0.0.0; esto no constituye una recomendacion de despliegue.
- Requisitos de README.md: JDK 21 y Maven 3.9+. Ejecutar los comandos desde la raiz del backend.
- Con el POM actual, `mvn quarkus:dev` omite el arranque por no tener el goal build configurado. Para ejecutar sin cambiar el POM se verifico `mvn -Dquarkus.enforceBuildGoal=false quarkus:dev`. Este argumento es del plugin; el runtime puede advertir que no es una propiedad de configuracion reconocida.
- SQLite usa `AUTH_DB_URL` o `jdbc:sqlite:./data/workshop.db`: [LegacyDatabase.java:17](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L17). Inicializa la tabla e inserta, solo si no existen, student / Workshop123! y trainer / Trainer123!: [LegacyDatabase.java:28](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L28), [LegacyDatabase.java:29](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L29), [LegacyDatabase.java:30](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L30). Si la base ya contenia usuarios con esos nombres, sus datos no se sobrescriben.
- Los ejemplos HTTP se comprobaron en 127.0.0.1:8082 con `AUTH_DB_URL=jdbc:sqlite:./target/endpoints-doc.db`, sin credenciales reales. Se conservaron las respuestas en [observaciones HTTP](../target/endpoints-http-observations.json); ese archivo generado esta ignorado por Git. Los ejemplos JSON de esta guia son completos, no plantillas con placeholders.

## POST /auth/login

### 1. Que hace

Comprueba las credenciales de un usuario del laboratorio y entrega su identidad, rol y token para continuar la sesion en los sistemas consumidores.

Ruta implementada por [AuthResource.java:15](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L15) y [AuthResource.java:23](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L23).

### 2. Autenticacion y headers

No requiere autenticacion previa: no se necesita header Authorization ni un token para iniciar sesion. Las credenciales se envian en el JSON, no mediante Basic ni Bearer. El metodo no lee Authorization: [AuthResource.java:24](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L24). El ejemplo se verifico sin ese header.

| Header | Obligatorio para integrar | Valor |
|---|---|---|
| Content-Type | Si, al enviar JSON | application/json |
| Accept | No; recomendado | application/json |
| Authorization | No | No enviar para este endpoint |

Consume y produce JSON: [AuthResource.java:16](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L16), [AuthResource.java:17](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L17). Las respuestas JSON observadas incluyen `Content-Type: application/json; charset=UTF-8`; algunos errores del framework no incluyen Content-Type ni cuerpo.

### 3. Request

| campo | tipo | obligatorio | descripcion |
|---|---|---|---|
| username | string | Si | Nombre exacto del usuario. No admite ausencia, null ni vacio despues de trim. Es sensible a mayusculas; no se recorta antes de consultar. |
| password | string | Si | Contrasena exacta. No admite ausencia, null ni vacio despues de trim. No se recorta antes de consultar. |
| client | string o null | No | Identificador del cliente; se acepta pero no interviene en autenticacion, token ni respuesta. |

Campos declarados: [LoginRequest.java:4](../src/main/java/com/bcp/workshop/auth/LoginRequest.java#L4), [LoginRequest.java:5](../src/main/java/com/bcp/workshop/auth/LoginRequest.java#L5), [LoginRequest.java:6](../src/main/java/com/bcp/workshop/auth/LoginRequest.java#L6). Validaciones, en orden: objeto, username y password ([AuthResource.java:26](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L26), [AuthResource.java:29](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L29), [AuthResource.java:32](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L32)). Se ignoran propiedades desconocidas: [application.properties:8](../src/main/resources/application.properties#L8). No hay parametros de ruta o query definidos para login.

Request completo verificado:

```json
{
  "username": "student",
  "password": "Workshop123!",
  "client": "angular-workshop"
}
```

```http
POST /auth/login HTTP/1.1
Host: 127.0.0.1:8080
Content-Type: application/json
Accept: application/json

{"username":"student","password":"Workshop123!","client":"angular-workshop"}
```

### 4. Response exitosa

**HTTP 200 OK**, JSON.

| campo | tipo | descripcion |
|---|---|---|
| token | string | Token dinamico Base64 del usuario, rol, vencimiento y secreto legado. No es un JWT firmado. |
| expiresIn | integer | Duracion anunciada en segundos, siempre 3600. No demuestra que un consumidor compruebe el vencimiento. |
| user | object | Datos publicos del usuario autenticado; no incluye id ni password. |
| user.username | string | Nombre obtenido de la base, sin normalizacion adicional. |
| user.displayName | string | Nombre visible escapado mediante escapeHtml4; caracteres HTML pueden aparecer como entidades, por ejemplo &lt;b&gt;. |
| user.role | string | Rol almacenado. Las semillas usan STUDENT y TRAINER; el codigo no restringe la columna a esos dos valores. |
| debug | string | Valor fijo authenticated-with-legacy-flow. |

Construccion de todos los campos: [AuthResource.java:39](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L39), [AuthResource.java:41](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L41), [AuthResource.java:42](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L42), [AuthResource.java:43](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L43), [AuthResource.java:45](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L45), [AuthResource.java:46](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L46), [AuthResource.java:48](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L48).

Respuesta real capturada con el request anterior:

```json
{
  "token": "c3R1ZGVudHxTVFVERU5UfDE3OTI0NTAwNDJ8V09SS1NIT1BfU0VDUkVUXzIwMjY=",
  "expiresIn": 3600,
  "user": {
    "username": "student",
    "displayName": "Workshop Student",
    "role": "STUDENT"
  },
  "debug": "authenticated-with-legacy-flow"
}
```

El token anterior es una muestra historica con vencimiento epoch 1792450042; no debe reutilizarse como credencial vigente. Cada login recalcula el vencimiento. El orden de las claves JSON no es contractual.

### 5. Respuestas de error

Los errores del resource son JSON; los del framework pueden tener otro esquema o cuerpo vacio. El cliente debe comprobar status y Content-Type antes de deserializar y no asumir que todo error tiene message.

**400, request null o cuerpo vacio.** Se observo con el cuerpo JSON `null` y tambien con cuerpo HTTP vacio usando Content-Type application/json. Evidencia: [AuthResource.java:27](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L27).

```json
{"message":"Request is null"}
```

**400, username ausente, null, vacio o solo espacios.** Ejemplo de request: `{}`. Se valida antes de password. Evidencia: [AuthResource.java:30](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L30).

```json
{"message":"Username is required"}
```

**400, password ausente, null, vacio o solo espacios con username valido para la validacion.** Request verificado: `{"username":"student"}`. Evidencia: [AuthResource.java:33](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L33).

```json
{"message":"Password is required"}
```

**401, credenciales incorrectas o usuario inexistente.** Tambien ocurre si el repositorio captura un fallo de consulta y devuelve null; un 401 no permite distinguir indisponibilidad de SQLite de credenciales incorrectas. El mensaje incluye el username recibido. Ejemplo verificado con student / incorrect. Evidencia: [AuthResource.java:37](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L37), [LegacyDatabase.java:45](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L45), [LegacyDatabase.java:48](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L48).

```json
{"message":"Invalid user student or password"}
```

**500, excepcion que alcanza el catch de login.** Devuelve el mensaje de la excepcion, o Unknown error si es null, y el nombre completo de su clase. No se incluye stacktrace en este cuerpo. Evidencia: [AuthResource.java:54](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L54), [AuthResource.java:56](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L56), [AuthResource.java:57](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L57).

Ejemplos completos respaldados por las pruebas con una excepcion controlada; no son respuestas provocadas en el servidor HTTP de esta comprobacion: [AuthResourceTest.java:115](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L115), [AuthResourceTest.java:120](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L120), [AuthResourceTest.java:177](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L177).

```json
{"message":"controlled test failure","exception":"java.lang.IllegalStateException"}
```

```json
{"message":"Unknown error","exception":"java.lang.IllegalStateException"}
```

Esquema de los errores propios del resource:

| campo | tipo | obligatorio | descripcion |
|---|---|---|---|
| message | string | Si, en sus respuestas 400, 401 y 500 | Mensaje de validacion, rechazo de credenciales o detalle de la excepcion, segun los ejemplos anteriores. |
| exception | string | Solo en su respuesta 500 | Nombre completo de la clase de excepcion. No aparece en sus respuestas 400 ni 401. |

Los valores del 500 son variables, no un catalogo cerrado de clases o mensajes. Este esquema no describe los errores que Quarkus genera antes de entrar a login.

**400, JSON mal formado antes de entrar al resource.** Request verificado: un cuerpo formado solamente por `{`. El cuerpo de respuesta observado tiene cero bytes y no tiene Content-Type. Ejemplo completo de status y cuerpo vacio, no JSON:

```http
HTTP/1.1 400 Bad Request

```

**400, estructura de campo no deserializable por Quarkus.** Request verificado: `{"username":{},"password":"Workshop123!"}`. Respuesta real del framework, no de los Map del resource:

```json
{
  "objectName": "LoginRequest",
  "attributeName": "username",
  "line": 1,
  "column": 13,
  "value": null
}
```

Campos de la respuesta capturada para ese request:

| campo | tipo observado | descripcion |
|---|---|---|
| objectName | string | LoginRequest, objeto cuya deserializacion fallo. |
| attributeName | string | username, campo afectado en este ejemplo. |
| line | integer | Linea del JSON donde se detecto el problema; 1 en este ejemplo. |
| column | integer | Columna del JSON donde se detecto el problema; 13 en este ejemplo. |
| value | null | Valor reportado por el framework en este caso; no se asumen sus tipos para otros errores. |

La posicion depende del JSON enviado. No se garantiza que todos los errores de deserializacion compartan este esquema; su implementacion pertenece a la extension Quarkus REST Jackson declarada en [pom.xml:36](../pom.xml#L36), no al resource.

**415, Content-Type no soportado.** Se verifico POST con Content-Type text/plain y cuerpo student. Respuesta sin Content-Type y con cero bytes de cuerpo:

```http
HTTP/1.1 415 Unsupported Media Type

```

**406, Accept no compatible con JSON.** Se verifico el request de login valido con Accept application/xml. Respuesta sin Content-Type y con cero bytes de cuerpo:

```http
HTTP/1.1 406 Not Acceptable

```

Preguntas abiertas: no se ha determinado un esquema universal para otros errores de conversion de tipos, ni se han caracterizado limites de tamano, timeouts, fallos de transporte o respuestas agregadas por un proxy. No se inventan sus codigos ni cuerpos y no se promete exhaustividad de errores ajenos al resource.

### 6. Notas de seguridad

- La consulta actual usa PreparedStatement y setString para ambas credenciales: [LegacyDatabase.java:39](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L39), [LegacyDatabase.java:40](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L40), [LegacyDatabase.java:41](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L41). Las comillas se tratan como datos; no hace falta escaparlas manualmente en el cliente.
- Las contrasenas siguen almacenadas en texto plano. Este refactor no incorpora BCrypt ni otro hash: [LegacyDatabase.java:29](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L29).
- El token sigue siendo Base64 reversible, no firmado ni cifrado; incluye un secreto fijo y un timestamp calculado como ahora mas 3600 segundos: [LegacyTokenUtil.java:8](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L8), [LegacyTokenUtil.java:13](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L13), [LegacyTokenUtil.java:14](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L14), [LegacyTokenUtil.java:15](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L15). No se puede afirmar que expire de verdad en un consumidor: este backend no implementa su verificacion.
- Pregunta abierta: que header y formato exigen los sistemas consumidores para presentar el token. Este repositorio no define ese contrato; no asumir Authorization: Bearer para sus APIs.
- El 500 expone detalles internos y el 401 refleja el username. No registrar ni mostrar tokens o contrasenas en interfaces y logs del cliente.
- CORS esta habilitado para cualquier origen, con metodos GET, POST, PUT, DELETE, OPTIONS y cabeceras accept, authorization, content-type, x-requested-with: [application.properties:4](../src/main/resources/application.properties#L4), [application.properties:5](../src/main/resources/application.properties#L5), [application.properties:6](../src/main/resources/application.properties#L6), [application.properties:7](../src/main/resources/application.properties#L7). CORS no sustituye autenticacion ni autoriza otras rutas.
- Solo laboratorio local aislado. README.md prohibe desplegar en Internet, CERT o PROD; esta guia no declara el backend seguro para produccion.

## GET /q/health

### 1. Que hace

Permite a un operador o sistema de monitoreo comprobar si el servicio publica una respuesta de salud antes de depender de el.

Lo proporciona quarkus-smallrye-health, no AuthResource: [pom.xml:40](../pom.xml#L40), con version del BOM [pom.xml:13](../pom.xml#L13). Ruta anunciada en README.md y verificada mediante HTTP local.

### 2. Autenticacion y headers

No requiere autenticacion en la configuracion actual: el GET se verifico sin Authorization y devolvio 200. No hay un header de credenciales requerido para este endpoint.

| Header | Obligatorio | Valor |
|---|---|---|
| Accept | No; recomendado | application/json |
| Content-Type | No | No enviar: la peticion no tiene cuerpo |
| Authorization | No | No requerido |

Se observo `Content-Type: application/json; charset=UTF-8` en la respuesta. Un GET con Accept application/xml tambien devolvio JSON y 200; no reutilizar para salud la regla 406 documentada para login.

### 3. Request

| campo | tipo | obligatorio | descripcion |
|---|---|---|---|
| Ninguno | No aplica | No aplica | No hay parametros de ruta, query o campos JSON necesarios para consultar la salud actual. |

El ejemplo JSON de request **no aplica**: GET /q/health se consulta sin cuerpo y no define campos de entrada. No enviar `{}` ni `null` como requisito de integracion. El ejemplo completo de la peticion es HTTP, porque no contiene JSON:

```http
GET /q/health HTTP/1.1
Host: 127.0.0.1:8080
Accept: application/json

```

En la comprobacion local, POST /q/health tambien devolvio la misma respuesta 200 por el handler del framework. Para integrar este endpoint documentado, utilizar GET; no se afirma que otros verbos reciban 405.

### 4. Response exitosa

**HTTP 200 OK**, JSON observado en la configuracion actual.

| campo | tipo | descripcion |
|---|---|---|
| status | string | Estado agregado de salud publicado por la extension; el valor observado es UP. |
| checks | array | Lista de comprobaciones publicadas; actualmente esta vacia. No hay elementos cuyo esquema pueda documentarse como propio de este backend. |

Respuesta real completa:

```json
{
  "status": "UP",
  "checks": []
}
```

Ambos campos y sus valores se verificaron mediante HTTP contra la extension declarada en [pom.xml:40](../pom.xml#L40). Pregunta abierta: cual seria el esquema de elementos de checks si se incorporan comprobaciones en una entrega futura; no se inventan name, data u otros campos que no aparecieron en la respuesta actual.

### 5. Respuestas de error

El codigo de la aplicacion no define respuestas de error para esta ruta. La comprobacion actual devuelve 200, UP y checks vacio; tampoco se observo un error al cambiar Accept o enviar POST. Por tanto, **no existe un ejemplo de error de salud verificado que pueda presentarse como respuesta actual**.

Pregunta abierta: puede esta configuracion concreta de la extension producir DOWN y HTTP 503, bajo que condicion, y con que cuerpo completo? No se ha reproducido un check fallido y este componente no implementa un escenario de salud negativa. No se publica un JSON supuesto de 503 ni de 500, porque no hay evidencia local que lo respalde.

Si no hay conexion, no existe un cuerpo HTTP del backend que mostrar: el cliente debe manejar el error de transporte por separado. Respuestas de proxies, timeouts y autenticacion agregada por infraestructura tambien quedan abiertas; no estan definidas aqui.

### 6. Notas de seguridad

- Un 200 UP con checks vacio no acredita que SQLite permita autenticar, que las credenciales sean correctas ni que los tokens sean seguros. Los fallos de inicializacion de SQLite se capturan y no se relanzan: [LegacyDatabase.java:32](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L32), [LegacyDatabase.java:33](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L33).
- No enviar username, password ni token a salud. La respuesta actual solo publica status y checks.
- El endpoint es accesible sin credenciales en local y comparte la configuracion HTTP/CORS actual. No inferir que una futura infraestructura lo exponga o proteja del mismo modo.
- El laboratorio no debe desplegarse en ambientes productivos ni publicos, segun README.md.

## Documentos y evidencias consultados

[README.md](../README.md), [HALLAZGOS.md](../HALLAZGOS.md) y la version previa de este documento.

Codigo, configuracion y pruebas utilizados para verificar el contrato: [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java), [LoginRequest.java](../src/main/java/com/bcp/workshop/auth/LoginRequest.java), [LegacyDatabase.java](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java), [LegacyTokenUtil.java](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java), [application.properties](../src/main/resources/application.properties), [pom.xml](../pom.xml) y [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java). Los enlaces de evidencia aparecen tambien junto a los campos y ejemplos. Las respuestas de Quarkus ajenas al codigo propio se respaldan con [observaciones HTTP locales](../target/endpoints-http-observations.json), no con suposiciones sobre el framework.