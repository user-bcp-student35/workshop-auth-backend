# Tasks

## 1. Decisiones confirmadas y spike previo

- [x] 1.1 Registrar en design.md §12 las respuestas confirmadas: `client` obligatorio, mecanismo B (quarkus-smallrye-jwt con HS256), `role` fuera del token, 500 sin token si falta el secreto y corrección de proposal.md. Verificar que §12 no tiene preguntas abiertas.
  - Evidencia: §12 de `design.md` registra las cinco decisiones; `proposal.md` referencia `docs/ENDPOINTS.md` existente como contrato.
- [x] 1.2 Spike fuera del historial: comprobar que quarkus-smallrye-jwt firma y verifica HS256 con `AUTH_TOKEN_SECRET`, rechaza `alg=none` y firmas alteradas, y no cambia el comportamiento de `/auth/login`. Verificar con una prueba temporal descartada antes del primer commit; si falla, cambiar a la opción A y documentarlo en design.md.
  - Comando temporal: `mvn -B -f target/hu01-jwt-spike/pom.xml test` con `AUTH_TOKEN_SECRET=HU01-spike-secret-32-bytes-minimum`. Resultado: 3 tests, 0 fallos, 0 errores, BUILD SUCCESS; cubrió firma/verificación HS256, `alg=none` y firma alterada.
  - Compatibilidad: con `quarkus-smallrye-jwt` añadida temporalmente al POM, `mvn -B "-Dtest=AuthResourceTest,AuthResourceInternalErrorTest" test` dio 26 y 1 tests, 0 fallos ni errores. POM restaurado sin la dependencia temporal y pruebas repetidas con exit code 0. El spike temporal se eliminó y el resultado se registró en `design.md` §7 D1.

## 2. Caracterización antes de cambios funcionales

- [x] 2.1 Añadir prueba C1: `debug` vale exactamente `authenticated-with-legacy-flow` y `user` contiene exactamente `username`, `displayName` y `role`. Verificar que pasa sobre el código actual con `mvn -B test -Dtest=AuthResourceTest`.
  - Cubierta por pruebas existentes, sin prueba nueva: `AuthResourceTest.logsInWithWorkshopUser` y `logsInWithTrainer` (vía `assertSuccessfulLogin`) comprueban el conjunto exacto de claves, `debug` y el mapa `user`.
  - Comando: `mvn -B "-Dtest=AuthResourceTest" test`. Resultado: 26 tests, 0 fallos, 0 errores, 0 omitidos, BUILD SUCCESS.
- [x] 2.2 Añadir prueba C2: con username vacío y sin `client`, la respuesta es 400 `Username is required`. Verificar que pasa sobre el código actual.
  - Cubierta por `AuthResourceTest.validatesUsernameBeforePassword`; ninguna petición envía `client`.
  - Mismo comando y resultado que 2.1.
- [x] 2.3 Añadir prueba C3: un fallo del servicio de autenticación (doble de prueba) produce 500 con `message` y `exception` a través de HTTP. Verificar que pasa sobre el código actual.
  - Prueba nueva: `AuthResourceInternalErrorTest.authenticationFailureReturnsInternalErrorOverHttp` (`@InjectMock AuthenticationService`, en clase aparte para no afectar a `AuthResourceTest`). Dependencia añadida al `pom.xml`: `quarkus-junit5-mockito` (scope test, versión gestionada por el BOM).
  - Comando: `mvn -B "-Dtest=AuthResourceInternalErrorTest" test`. Resultado: 1 test, 0 fallos, 0 errores, BUILD SUCCESS.
  - `preservesInternalErrorResponse` y `preservesUnknownErrorFallback` siguen siendo pruebas directas del resource, no HTTP.
- [x] 2.4 Ejecutar la suite completa antes de tocar código funcional. Verificar `BUILD SUCCESS` con `mvn -B test`.
  - Comando: `mvn -B test`. Resultado: 63 tests, 0 fallos, 0 errores, 0 omitidos, BUILD SUCCESS.

## 3. Configuración del secreto y reloj

- [x] 3.1 Añadir `auth.token.secret=${AUTH_TOKEN_SECRET:}` en `application.properties` con valores de laboratorio solo en `%dev` y `%test`, marcados como no productivos. Verificar que no existe un valor predeterminado para `%prod`.
  - Evidencia: `application.properties` define valor base vacío y valores `HU01-*-only-secret-not-for-production` solo en `%dev` y `%test`; no existe override `%prod`.
- [x] 3.2 Verificar que el secreto de laboratorio está limitado a `%dev` y `%test` y que el valor base sigue vacío fuera de esos perfiles.
  - Comando: `mvn -B test`. Resultado: 63 tests, 0 fallos, 0 errores, 0 omitidos, BUILD SUCCESS. Inspección de configuración: `grep_search` encontró solo las tres declaraciones anteriores.
- [x] 3.3 Escribir prueba que falle si no existe un productor CDI de `java.time.Clock` en UTC. Verificar que falla antes de implementar; el uso del reloj fijo sin `Thread.sleep` se verificará en `AuthTokenServiceTest`.
  - Evidencia roja: `mvn -B "-Dtest=ClockProducerTest" test` falló por `UnsatisfiedResolutionException` para `java.time.Clock`.
- [x] 3.4 Implementar el productor de `Clock` y hacer que la prueba dedicada pase; las pruebas de emisión con reloj fijo se verifican en `AuthTokenServiceTest` tras implementar el servicio.
  - Archivo: `ClockProducer.java`. Mismo comando tras implementar: 1 test, 0 fallos, 0 errores, BUILD SUCCESS.
- [x] 3.5 Documentar en docs/ENDPOINTS.md (§6) la variable `AUTH_TOKEN_SECRET` y el comportamiento sin ella. Verificar que la sección existe y coincide con D5.
  - Evidencia: §6 documenta `auth.token.secret=${AUTH_TOKEN_SECRET:}`, valores solo de laboratorio en `%dev`/`%test`, requisito mínimo de 32 bytes y 500 sin token si falta o es demasiado corto; búsqueda de esos términos encontró la nota en la sección.

## 4. Servicio de token (AuthTokenService)

- [ ] 4.1 Escribir pruebas que fallen para `issue`: el token tiene tres segmentos, `sub` es el username, `client` es el valor recibido, `exp - iat` vale 3600, no contiene `role`, y con secreto ausente o menor a 32 bytes lanza `IllegalStateException` sin revelar el valor. Verificar que fallan antes de implementar.
- [ ] 4.2 Añadir `quarkus-smallrye-jwt` en pom.xml sin versión fuera del BOM e implementar `AuthTokenService.issue` y la validación del secreto con HS256. Verificar que 4.1 pasa y que `mvn -B dependency:tree` no muestra versiones fijadas a mano.
- [ ] 4.3 Escribir pruebas que fallen para `verify`: token íntegro aceptado con sus claims; `sub` alterado rechazado; `alg=none` rechazado; token Base64 anterior rechazado; token expirado según el `Clock` rechazado. Verificar que fallan.
- [ ] 4.4 Implementar `AuthTokenService.verify` con formato, algoritmo, firma, claims obligatorios y comprobación de `exp`. Verificar que 4.3 pasa.
- [ ] 4.5 Documentar en docs/TESTING.md las pruebas nuevas de `AuthTokenServiceTest`. Verificar que la matriz de pruebas las incluye.

## 5. Conexión en el login

- [ ] 5.1 Escribir pruebas que fallen: `client` ausente, null, vacío o solo espacios devuelve 400 `Client is required` sin consultar la base. Verificar que fallan.
- [ ] 5.2 Escribir pruebas que fallen: `client` válido devuelve 200, el token se verifica con `AuthTokenService`, `expiresIn` vale 3600 y `exp` difiere de `expiresIn` como máximo 5 s. Verificar que fallan.
- [ ] 5.3 Escribir prueba que falle: con un perfil de prueba sin secreto, el login devuelve 500 sin token. Verificar que falla.
- [ ] 5.4 Cambiar a propósito `acceptsMissingNullOrEmptyClient` (400 `Client is required`), enviar un `client` válido en `ignoresClientAndUnknownProperties` y en los helpers `login(...)` y `assertUnauthorized(...)`. Verificar que C1 a C3 siguen en verde.
- [ ] 5.5 Implementar la validación de `client` en `AuthResource.login` tras la de password y antes de `authenticate`, y emitir con `AuthTokenService`. Verificar que 5.1 a 5.3 pasan.
- [ ] 5.6 Actualizar docs/ENDPOINTS.md (§3 `client` obligatorio, §4 token firmado) y docs/TESTING.md. Verificar que ambos documentos coinciden con el comportamiento.

## 6. Logs sin datos sensibles

- [ ] 6.1 Escribir prueba que capture los logs de un login correcto y de uno fallido y compruebe que no aparecen token, `AUTH_TOKEN_SECRET` ni contraseña. Verificar el resultado; si ya pasa, se mantiene como caracterización.
- [ ] 6.2 Solo si 6.1 detecta una fuga, eliminar el dato sensible de los registros del flujo de login. Verificar que 6.1 pasa.

## 7. Retirada del formato anterior

- [ ] 7.1 Buscar referencias a `LegacyTokenUtil` y `WORKSHOP_SECRET_2026` en src/main y src/test. Verificar que solo aparecen en los archivos que se van a eliminar.
- [ ] 7.2 Eliminar `LegacyTokenUtil` y la constante `WORKSHOP_SECRET_2026`. Verificar con `mvn -B test` en verde y con una búsqueda que no quede ninguna referencia en src/main.
- [ ] 7.3 Corregir proposal.md: `docs/ENDPOINTS.md` sí existe en la rama `feature/HU-01`. Verificar que el texto "no existe" ya no aparece.

## 8. Verificación final de integración

- [ ] 8.1 Ejecutar `mvn -B verify` con JaCoCo y comprobar cobertura de al menos 85% en el código modificado. Verificar en el informe de JaCoCo.
- [ ] 8.2 Ejecutar `openspec validate hu01-token-claims-expiracion --strict`. Verificar que el resultado es valid.
- [ ] 8.3 Analizar vulnerabilidades de la nueva dependencia (OSV u otra herramienta). Verificar que no quedan hallazgos altos sin resolver.
- [ ] 8.4 Buscar en el repositorio valores de secreto reales. Verificar que solo aparecen valores de laboratorio en `%dev` y `%test`.

## Workflow follow-up

- Archivar el cambio con openspec-archive-change tras completar todas las tareas.
- Revisar con el equipo los clientes que dejan de funcionar al exigir `client`.
