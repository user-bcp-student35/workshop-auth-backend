# Diseño: claims, client y expiración del token de login (HU-01)

Referencias: [proposal.md](proposal.md), [specs/login-token/spec.md](specs/login-token/spec.md), Issue #11, [HU-01.md](../../../HU-01.md), [REGLAS.md](../../../REGLAS.md), [docs/ENDPOINTS.md](../../../docs/ENDPOINTS.md).

## 1. Contexto técnico

Estado verificado en la rama `feature/HU-01` (solo lectura, sin cambios):

- `AuthResource.login` valida en orden: request null, username, password; llama a `AuthenticationService.authenticate`; emite el token con `LegacyTokenUtil.create(user.username(), user.role())`; responde `expiresIn` como literal `3600` y `debug` fijo `authenticated-with-legacy-flow`. El `displayName` se escapa con `StringEscapeUtils.escapeHtml4`.
- `LegacyTokenUtil` genera `Base64(username|role|epochExpiry|WORKSHOP_SECRET_2026)`. El secreto es una constante en código, no hay firma ni verificación y la expiración no se comprueba en ningún punto.
- `LoginRequest` declara `client`, pero el login no lo usa.
- Persistencia: `AuthenticationService` consulta `LegacyDatabase.findByUsername` y verifica con `PasswordHasher` (Argon2id). El hashing ya está implementado (H-02) y queda fuera de HU-01.
- `application.properties` no contiene secretos. Dependencias: Quarkus 3.27.4 (BOM), `quarkus-rest-jackson`, `quarkus-arc`, `quarkus-smallrye-health`. No hay librería JWT.
- Pruebas: `AuthResourceTest` (24), `HealthResourceTest` (3), `LegacyDatabaseTest` (11), `PasswordHasherTest`, `AuthenticationServiceTest`. No existe prueba dedicada a `LegacyTokenUtil`.

## 2. Hallazgos que afectan al diseño

1. **`docs/ENDPOINTS.md` existe en esta rama.** [proposal.md](proposal.md) indica que no existe y usa `AuthResource` como base. Este diseño usa `docs/ENDPOINTS.md` como contrato de referencia. El proposal debe corregirse; no se ha modificado en esta fase.
2. **Conflicto en `client`.** `docs/ENDPOINTS.md` (sección Request) y las pruebas `acceptsMissingNullOrEmptyClient` e `ignoresClientAndUnknownProperties` fijan `client` como opcional. La spec aprobada exige `client` obligatorio con 400 `Client is required`. Es un cambio de contrato intencionado que debe reflejarse en `ENDPOINTS.md` y `TESTING.md` en el mismo cambio. Requiere confirmación explícita (ver sección 12).
3. **Pruebas que envían credenciales sin `client`.** Los tests que esperan 400 de username o password (`validatesUsernameBeforePassword`, `validatesMissingOrBlankPassword`) siguen válidos porque esas validaciones van antes. Los tests que esperan 401 deben enviar `client` válido; hay que revisar los helpers `login(...)` y `assertUnauthorized(...)` antes de cambiar código.
4. **Documentación del token.** `ENDPOINTS.md` y `TESTING.md` describen el token actual como Base64 sin firma. Cambian junto con el formato.

## 3. Goals

- Emitir un token con claims `sub`, `client`, `iat` y `exp`, firmado con HMAC-SHA256 y clave tomada de `AUTH_TOKEN_SECRET`.
- Hacer que `expiresIn` (3600) y `exp - iat` salgan de la misma constante, con diferencia de 0 s en emisión y tolerancia de 5 s en la respuesta.
- Verificar firma, expiración y claims obligatorios en un componente de seguridad separado, probado de forma aislada.
- Exigir `client` no vacío en `POST /auth/login`, validado antes de consultar la base.
- Eliminar el secreto fijo del código fuera de los perfiles `%dev` y `%test`.
- Mantener sin cambios el resto del contrato de login (ver sección 5).

## 4. Non-goals

- No modificar `LegacyDatabase`, `PasswordMigration`, `PasswordHasher` ni `AuthenticationService`.
- No añadir endpoints, refresh tokens, revocación, listas de bloqueo ni rotación de claves.
- No usar RS256/ES256 ni JWKS: requieren gestión de claves fuera del alcance del laboratorio.
- No proteger otros recursos con el token: el repositorio no tiene recursos protegidos.
- No cambiar el formato de errores de Quarkus (400 de JSON mal formado, 415, 406) ni `/q/health`.
- No refactorizar más allá del método `login` y sus validaciones.

## 5. Contratos protegidos

| Aspecto | Comportamiento que se preserva | Fuente |
|---|---|---|
| Ruta y tipos | `POST /auth/login`, `application/json` de entrada y salida | `AuthResource` |
| Éxito 200 | Claves exactas `token`, `expiresIn`, `user`, `debug`; `user` con `username`, `displayName`, `role` | `ENDPOINTS.md` §4 |
| `expiresIn` | Entero 3600 | Spec, escenario `expiresIn coincide con exp` |
| `displayName` | Escapado con `escapeHtml4` (`<b>` aparece como `&lt;b&gt;`) | Test `escapesDisplayNameWithoutChangingResponseFields` |
| `debug` | Valor fijo `authenticated-with-legacy-flow` | `ENDPOINTS.md` §4 |
| 400 | Mensajes `Request is null`, `Username is required`, `Password is required`, en ese orden | Tests `validates*` |
| 400 nuevo | `Client is required` tras las validaciones de password, antes de consultar la base | **Cambio intencionado (HU-01)** |
| 401 | `Invalid user {username} or password`, sin cambios | Tests `rejects*` y `doesNotNormalizeCredentials` |
| 500 | Forma `{message, exception}`, sin cambios | `preservesInternalErrorResponse` |
| Errores de framework | 400 de JSON mal formado, 415, 406 sin cuerpo | Tests `rejectsMalformedJsonWithEmptyBody`, `rejectsUnsupportedContentType`, `rejectsUnsupportedAccept` |
| Logs | Ni token ni valor de `AUTH_TOKEN_SECRET` en logs | Spec, escenario de logs |
| Credenciales | Sin normalización de username ni password | `doesNotNormalizeCredentials` |

Cambios de contrato intencionados, a documentar: formato del token (Base64 pasa a JWS compacto HS256), claims, `client` obligatorio y eliminación de `role` como claim (sigue en `user.role`).

## 6. Componentes afectados

- **`AuthResource.login`**: añade la validación de `client` tras la de password y antes de `authenticate`. Usa la constante de expiración. Mantiene la forma de la respuesta y el manejo de 500. Se extraen las validaciones a métodos con guard clauses solo dentro de este método, como exige [REGLAS.md](../../../REGLAS.md).
- **`LegacyTokenUtil`**: se sustituye por el servicio de token y se elimina al final, junto con la constante `WORKSHOP_SECRET_2026`.
- **Nuevo `AuthTokenService`** (paquete `com.bcp.workshop.auth`, `@ApplicationScoped`): `issue(username, client)` devuelve el token compacto; `verify(token)` devuelve los claims validados o lanza excepción. Es el único lugar que conoce el secreto y el algoritmo.
- **Nuevo productor de `java.time.Clock`**: permite fijar el tiempo en pruebas sin `Thread.sleep`.
- **`application.properties`**: `auth.token.secret=${AUTH_TOKEN_SECRET:}` por defecto vacío; valor de laboratorio explícito solo en `%dev` y `%test`.
- **`pom.xml`**: dependencia JWT según la decisión D1 (solo si se elige una librería).
- **Pruebas**: `AuthResourceTest` (dos casos cambian a propósito, ver sección 8); nueva `AuthTokenServiceTest`.
- **Documentación**: `docs/ENDPOINTS.md` (§3 client, §4 token, §6 notas de seguridad) y `docs/TESTING.md`.

Sin cambios: `LegacyDatabase`, `PasswordMigration`, `PasswordHasher`, `AuthenticationService`, `UserRecord`, `StoredCredentials`, `HealthResource`.

## 7. Decisiones técnicas y alternativas

### D1. Mecanismo de integridad

| Opción | Ventajas | Inconvenientes |
|---|---|---|
| A. HMAC-SHA256 propio con `javax.crypto.Mac` y formato compacto escrito a mano | Sin dependencia nueva | Codificación y verificación propias; más riesgo y más revisión de seguridad |
| B. `quarkus-smallrye-jwt` (versión gestionada por el BOM 3.27.4) con HS256 | Estándar; versión alineada con Quarkus | Nueva extensión; API de firma con clave simétrica a confirmar en un spike |
| C. `io.jsonwebtoken` (jjwt) con versión fijada | Librería estándar y madura | Versión fuera del BOM; mantenimiento aparte |
| D. RS256/ES256 | Verificación sin secreto compartido | Gestión de claves; fuera de alcance |

**Recomendación: B.** Antes de implementar, un spike debe confirmar: firma y verificación HS256 con `AUTH_TOKEN_SECRET`, rechazo de `alg=none` y de firmas alteradas, y que la extensión no añade seguridad automática a `/auth/login`. Si el spike falla o la extensión altera el comportamiento, se usa A, validado con el vector de prueba de RFC 7515, apéndice A.1 (HS256). Ambas opciones son HMAC; la spec se cumple con las dos.

### D2. Claims
**Resultado del spike (2026-10-21):** `quarkus-smallrye-jwt` firma con un `SecretKey` derivado de `AUTH_TOKEN_SECRET` y `JWTParser` verifica con la misma clave HS256. Las pruebas temporales confirmaron rechazo de `alg=none` y de una firma alterada (3 tests, 0 fallos). Con la extensión añadida temporalmente al POM del backend, las pruebas de login caracterizadas pasaron (26 en `AuthResourceTest` y 1 en `AuthResourceInternalErrorTest`); la dependencia temporal se retiró. El código del spike quedó en `target/`, ignorado por Git, y se eliminó tras registrar el resultado.

### D2. Claims

- `sub` = username autenticado; `client` = valor recibido; `iat` y `exp` = `iat + TOKEN_LIFETIME_SECONDS`.
- `TOKEN_LIFETIME_SECONDS = 3600` es constante con nombre; se reutiliza en `expiresIn`. Ambos valores salen de un mismo `Instant` obtenido del `Clock`.
- `role` no se incluye en el token: la spec no lo exige y el rol sigue en `user.role`. Alternativa: incluirlo. Se recomienda omitirlo por ser un claim mínimo.
- `client` se valida con `trim()` (null o vacío tras recortar, 400), pero se guarda tal cual se recibió, como en username y password.

### D3. Verificación y expiración

`verify(token)` comprueba en este orden: formato de tres segmentos, `alg` = HS256, firma, presencia de `sub`, `client`, `iat` y `exp`, y `exp` posterior al instante del `Clock`. Si falla algo, lanza excepción. Ningún endpoint lo usa todavía: su prueba es unitaria, y `ENDPOINTS.md` debe decir que la expiración efectiva depende de los consumidores.

### D4. Tiempo testeable

Se inyecta `java.time.Clock` mediante productor. Las pruebas de expiración fijan el reloj. Alternativa rechazada: esperar tiempo real con `Thread.sleep`, lenta e inestable.

### D5. Configuración del secreto

- `auth.token.secret=${AUTH_TOKEN_SECRET:}` por defecto vacío.
- `%dev` y `%test` definen un valor de laboratorio explícito, marcado como no productivo.
- Fuera de esos perfiles, si el secreto está vacío o tiene menos de 32 bytes (requisito de HS256 de 256 bits), `issue` lanza `IllegalStateException`. El login lo convierte en el 500 existente `{message, exception}`, sin token. El mensaje no contiene el valor del secreto.
- Alternativa: fallo al arrancar (fail-fast). Es más estricto, pero cambia la prueba de la spec de "login" a "arranque" y necesita una configuración por perfil más compleja. Se deja como mejora posterior.

### D6. Sin aceptación de tokens legacy

El formato Base64 no se acepta en `verify`. Con el secreto fijo era falsificable, y aceptarlo mantendría esa vía abierta. Alternativa rechazada: periodo de transición con doble formato.

### D7. Orden de validación en login

`request null` → `username` → `password` → `client` → `authenticate` → `issue`. Un `client` inválido no consulta la base.

## 8. Estrategia incremental y pruebas de caracterización

### 8.1 Caracterización antes de modificar comportamiento

Ya cubiertas por la suite actual (38 tests registrados en `docs/TESTING.md`): claves exactas del 200, `expiresIn` 3600, `displayName` escapado, orden de validación 400, 401 sin normalizar, 500 con `{message, exception}`, errores de framework 400/415/406, `debug`.

Se añaden antes de cualquier cambio de código (pasan sobre el código actual):

- C1: `debug` tiene el valor exacto `authenticated-with-legacy-flow` y `user` contiene exactamente `username`, `displayName`, `role`.
- C2: la validación de username sigue antes que la de password también cuando falta `client`.
- C3: un fallo del servicio de autenticación produce 500 con `message` y `exception`, por la vía HTTP y no solo por llamada directa.

### 8.2 Pruebas nuevas de comportamiento (escritas antes de la implementación de cada parte)

- `client` ausente, null, vacío o solo espacios → 400 `Client is required`, sin consultar la base.
- `client` válido → 200 y `sub`, `client`, `iat`, `exp` correctos en el token.
- Token de tres segmentos verificable con `AuthTokenService.verify`.
- `exp - iat` = 3600 y `expiresIn` = 3600.
- Token expirado (reloj fijado después de `exp`) → rechazado.
- Firma alterada, `alg=none` o token legacy Base64 → rechazado.
- Secreto ausente o corto fuera de `%dev`/`%test` → 500 sin token.
- Secreto ausente o corto en `%dev`/`%test` no aplica: el perfil define su valor.

### 8.3 Pruebas que cambian a propósito

- `acceptsMissingNullOrEmptyClient`: pasa a esperar 400 `Client is required`.
- `ignoresClientAndUnknownProperties`: envía un `client` válido y propiedades desconocidas; sigue esperando 200.
- Helpers `login(...)` y `assertUnauthorized(...)`: deben enviar `client` válido para que los casos de 401 sigan probando credenciales y no `client`.

Cada cambio de contrato queda justificado en el PR con referencia a HU-01 y a la spec.

### 8.4 Secuencia de commits

1. Spike de la librería o del HMAC propio (no se incluye en el historial).
2. Caracterización C1–C3 sobre el código actual.
3. Configuración de secreto, productor de `Clock` y `AuthTokenService` con sus pruebas unitarias (la parte de servicio no está conectada aún).
4. Conexión en `AuthResource`: validación de `client`, emisión con la nueva clase, constante de expiración y casos cambiados en la sección 8.3.
5. Eliminación de `LegacyTokenUtil` y comprobación de que `WORKSHOP_SECRET_2026` no queda en `src/main`.
6. Documentación: `ENDPOINTS.md`, `TESTING.md` y corrección del proposal sobre la existencia de `ENDPOINTS.md`.
7. Verificación final: `mvn -B verify` con cobertura JaCoCo de al menos 85% en el código modificado; `openspec validate hu01-token-claims-expiracion --strict`; análisis de dependencias OSV si se añade librería.

Cada commit debe dejar la suite en verde.

## 9. Riesgos y trade-offs

| ID | Riesgo | Mitigación |
|---|---|---|
| R1 | Clientes que parsean el Base64 dejan de funcionar | Cambio marcado como BREAKING; el token caduca en 1 h |
| R2 | Clientes que no envían `client` reciben 400 | Confirmar con consumidores; ejemplos de `ENDPOINTS.md` ya lo envían |
| R3 | Dependencia nueva (opción B) | Análisis OSV; no silenciar reglas de seguridad de [REGLAS.md](../../../REGLAS.md) |
| R4 | Cambiar `AUTH_TOKEN_SECRET` invalida todos los tokens emitidos | Esperado; documentar en `ENDPOINTS.md` |
| R5 | Secreto débil configurado por el operador | Longitud mínima de 32 bytes; documentación |
| R6 | `verify` no tiene consumidores en el repositorio | La expiración efectiva se prueba solo en el componente; se documenta |
| R7 | El valor de laboratorio en `%dev`/`%test` se copia a producción | Valor marcado como no productivo y limitado a perfiles |
| R8 | Desfase entre `expiresIn` y `exp - now` | Mismo `Instant` para ambos; tolerancia de 5 s de la spec |
| R9 | Consumidores que leían `role` del token | `role` sigue en `user.role`; se documenta |
| R10 | Mensaje del 500 revela que falta `AUTH_TOKEN_SECRET` | Aceptado por el contrato 500 actual; alternativa fail-fast en D5 |
| R11 | Dos pruebas cambian de expectativa | Justificadas en el PR con HU-01 |

## 10. Rollback

- Revertir los commits de `feature/HU-01` en orden inverso. No hay migración de datos ni tokens persistidos: el token es stateless.
- Tras revertir, los tokens emitidos por la nueva versión dejan de ser válidos y viceversa; el impacto máximo es de 1 hora.
- No se usa feature flag: mantendría el camino legacy inseguro activo.
- En producción, quitar `AUTH_TOKEN_SECRET` no sirve como rollback: provoca 500 fuera del laboratorio. El rollback es revertir y redesplegar.

## 11. Verificación de este documento

- Diseño sin implementación de código funcional ni `tasks.md`.
- Validar con `openspec validate hu01-token-claims-expiracion --strict` antes de cerrar la fase.

## 12. Decisiones confirmadas

Estas decisiones cierran las preguntas de diseño para implementar HU-01:

1. `client` es obligatorio. Aunque el contrato actual lo acepta ausente, la spec aprobada exige 400 `Client is required`; se actualizarán las pruebas y `ENDPOINTS.md` en este cambio.
2. Se elige la opción B, `quarkus-smallrye-jwt` con HS256 y versión gestionada por el BOM. Antes de incorporarla al código funcional, el spike de 1.2 debe confirmar firma y verificación, rechazo de `alg=none` y firmas alteradas, y ausencia de cambios en el comportamiento de `/auth/login`. Si falla, se aplica la alternativa A conforme a D1 y se registra el resultado.
3. El token no incluye `role`: la spec no lo requiere y el rol continúa en `user.role` de la respuesta.
4. Si falta el secreto o tiene menos de 32 bytes fuera de `%dev` y `%test`, emitir el token lanza `IllegalStateException`; el login conserva el 500 actual y no devuelve token. No se adopta fail-fast al arrancar.
5. Se corrige `proposal.md`: `docs/ENDPOINTS.md` existe y es parte de la evidencia del contrato actual.
