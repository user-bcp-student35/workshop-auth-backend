# Proposal

Referencia: Issue [#11](https://github.com/user-bcp-student35/workshop-auth-backend/issues/11) (HU01). El cuerpo del Issue solo indica que el trabajo se genere a partir de HU-01, por lo que el alcance se toma de [HU-01.md](../../../HU-01.md).

## Why

El token que devuelve `POST /auth/login` no cumple lo que exige HU-01: no tiene claims `sub`, `client`, `iat` ni `exp`, no está protegido contra alteraciones (hoy es `usuario|rol|expiracion|secreto` codificado en Base64) y el secreto está fijado en el código. Sin estas garantías no se puede verificar de forma fiable la identidad, la expiración ni la integridad del token. REGLAS.md exige además pruebas de caracterización antes de tocar el token, por lo que el cambio debe llegar con cobertura que fije el comportamiento actual.

## What Changes

- El token emitido por el login incluye los claims `sub`, `client`, `iat` y `exp`.
- `client` se toma de la solicitud, se valida y se refleja en el token. **BREAKING**: el campo pasa a ser obligatorio; hoy `LoginRequest` lo recibe pero se ignora.
- `expiresIn` coincide con `exp - now` dentro de una tolerancia de 5 segundos.
- El token es íntegro: cualquier modificación de sus claims se detecta al verificarlo. **BREAKING**: cambia el formato del token.
- Fuera de los perfiles Quarkus `%dev` y `%test` no existe secreto por defecto; el secreto procede de `AUTH_TOKEN_SECRET`.
- Los logs no contienen token, secreto ni contraseña.

## Capabilities

### New Capabilities
- `login-token`: claims, expiración, integridad y gestión del secreto del token devuelto por el login, junto con la ausencia de datos sensibles en logs.

### Modified Capabilities
- Ninguna. `openspec/specs/` no contiene specs todavía.

## Alcance

- Token devuelto por `POST /auth/login`.
- Validación del campo `client` de la solicitud de login.
- Rechazo de tokens expirados e íntegros verificados por el propio servicio.
- Configuración del secreto (`AUTH_TOKEN_SECRET`) y redacción de logs en el flujo de login.

## Fuera de alcance

- Elegir entre HMAC propio y una librería JWT (decisión técnica para `design.md`, no para esta propuesta).
- Persistencia de usuarios (`LegacyDatabase`) y refactor de capas API, servicio y repositorio.
- Nuevos endpoints de verificación o renovación de tokens.
- Endurecer la respuesta 500, que hoy expone el nombre de la excepción y su mensaje.

## Comportamiento que debe preservarse

Contrato de referencia: `docs/ENDPOINTS.md` existe en el repositorio y documenta el contrato actual; las pruebas y `AuthResource.java` completan su evidencia:

- `POST /auth/login` consume y produce JSON.
- Errores: 400 `Request is null`, 400 `Username is required`, 400 `Password is required`, 401 `Invalid user {username} or password`, 500 con `message` y `exception`.
- Respuesta 200 con `token`, `expiresIn` (3600), `user` (`username`, `displayName` escapado en HTML, `role`) y `debug` (`authenticated-with-legacy-flow`).

## Decisiones

1. Baseline del contrato: `docs/ENDPOINTS.md` y el comportamiento comprobado de `AuthResource.java` y sus pruebas.
2. Mecanismo de integridad: neutral en la spec (HMAC o librería JWT se decide en `design.md`). HU-01 menciona HMAC y `AUTH_TOKEN_SECRET`, que el código actual no usa.
3. `client` obligatorio: ausente o en blanco devuelve 400. Cambio incompatible para clientes que no lo envían.
4. Tolerancia entre `expiresIn` y `exp - now`: 5 segundos.
5. Rechazo de tokens expirados: incluido como verificación interna, sin endpoint nuevo.
6. Campo `debug`: se conserva sin cambios.
7. Perfil de laboratorio: perfiles Quarkus `%dev` y `%test`.

## Impact

- `AuthResource.login`: validación de `client`, forma de la respuesta.
- `LegacyTokenUtil`: emisión del token (formato, claims, secreto).
- Configuración: `AUTH_TOKEN_SECRET` y perfiles.
- Logs del flujo de login.
- Pruebas existentes de `AuthResource`, `LegacyTokenUtil` y del login, más pruebas nuevas de claims, expiración y errores.
