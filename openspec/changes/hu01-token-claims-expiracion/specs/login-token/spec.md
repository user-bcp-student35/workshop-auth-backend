# Spec Delta

## Purpose
Define el comportamiento observable del token devuelto por `POST /auth/login`: claims mínimos, expiración coherente, integridad verificable, gestión del secreto y ausencia de datos sensibles en logs. El perfil de laboratorio son los perfiles Quarkus `%dev` y `%test`.

## ADDED Requirements

### Requirement: Claims mínimos del token
El token devuelto tras un login válido SHALL contener los claims `sub`, `client`, `iat` y `exp`.

#### Scenario: Login válido incluye claims mínimos
- **WHEN** el cliente envía un username y una contraseña válidos junto con `client` "app-web"
- **THEN** el token contiene `sub` igual al username, `client` igual a "app-web", `iat` con el instante de emisión y `exp` posterior a `iat`

### Requirement: Campo client obligatorio y validado
El login SHALL exigir el campo `client` en la solicitud y SHALL reflejar su valor en el claim `client` del token.

#### Scenario: Client ausente
- **WHEN** el cliente envía un username y una contraseña válidos sin el campo `client`
- **THEN** la respuesta es 400 con el mensaje `Client is required` y no se devuelve token

#### Scenario: Client en blanco
- **WHEN** el cliente envía `client` con solo espacios en blanco
- **THEN** la respuesta es 400 con el mensaje `Client is required` y no se devuelve token

#### Scenario: Validaciones previas sin cambios
- **WHEN** el cliente envía un username vacío y no envía `client`
- **THEN** la respuesta es 400 con el mensaje `Username is required`

### Requirement: Expiración coherente con expiresIn
La respuesta de login exitosa SHALL informar `expiresIn` en 3600 segundos y SHALL cumplir que `exp` menos el instante de respuesta difiere de `expiresIn` en no más de 5 segundos.

#### Scenario: expiresIn coincide con exp
- **WHEN** el cliente recibe una respuesta de login exitosa
- **THEN** `expiresIn` es 3600 y la diferencia entre `exp` y el instante de recepción, en segundos, está dentro de 5 segundos de `expiresIn`

### Requirement: Integridad verificable del token
El token SHALL estar protegido de forma que cualquier modificación de sus claims sea detectada al verificarlo. Este requisito no fija el mecanismo.

#### Scenario: Claim alterado se rechaza
- **WHEN** se modifica el claim `sub` de un token emitido por el servicio sin volver a firmarlo
- **THEN** la verificación del token falla

#### Scenario: Token íntegro se acepta
- **WHEN** se verifica un token emitido por el servicio sin modificaciones y dentro de su expiración
- **THEN** la verificación es correcta y devuelve sus claims

### Requirement: Rechazo de tokens expirados
La verificación de un token SHALL fallar cuando el claim `exp` es anterior al instante actual.

#### Scenario: Token expirado
- **WHEN** se verifica un token cuyo `exp` ya ha pasado
- **THEN** la verificación falla indicando que el token ha expirado

### Requirement: Secreto de firma configurado fuera del laboratorio
Fuera de los perfiles `%dev` y `%test` el servicio SHALL NOT emitir tokens con un secreto por defecto incluido en el código y SHALL tomar el secreto de firma de `AUTH_TOKEN_SECRET`.

#### Scenario: Sin AUTH_TOKEN_SECRET fuera del laboratorio
- **WHEN** un login válido se procesa fuera de `%dev` y `%test` sin `AUTH_TOKEN_SECRET` definido
- **THEN** la respuesta no contiene token

#### Scenario: Token firmado con AUTH_TOKEN_SECRET
- **WHEN** un login válido se procesa con `AUTH_TOKEN_SECRET` definido
- **THEN** el token emitido se verifica con ese mismo valor de secreto

### Requirement: Sin datos sensibles en logs
El flujo de login SHALL NOT escribir en los logs el token, el secreto de firma ni la contraseña, tanto en login correcto como en login fallido.

#### Scenario: Login correcto no registra token ni secreto
- **WHEN** se completa un login válido
- **THEN** ninguna línea de log contiene el token emitido ni el valor de `AUTH_TOKEN_SECRET`

#### Scenario: Login fallido no registra contraseña
- **WHEN** se intenta un login con una contraseña incorrecta
- **THEN** ninguna línea de log contiene la contraseña enviada

### Requirement: Respuesta y errores de login preservados
El login SHALL mantener la forma de la respuesta exitosa con `token`, `expiresIn`, `user` (`username`, `displayName`, `role`) y `debug`, y los errores 400, 401 y 500 con sus mensajes actuales.

#### Scenario: Respuesta exitosa conserva el objeto user
- **WHEN** se completa un login válido
- **THEN** la respuesta contiene `user` con `username`, `displayName` y `role`

#### Scenario: Campo debug conservado
- **WHEN** se completa un login válido
- **THEN** la respuesta contiene `debug` con el valor `authenticated-with-legacy-flow`

#### Scenario: displayName escapado en HTML
- **WHEN** el `displayName` del usuario contiene `<b>`
- **THEN** la respuesta devuelve `&lt;b&gt;` en `displayName`

#### Scenario: Credenciales inválidas
- **WHEN** el username o la contraseña no coinciden con un usuario
- **THEN** la respuesta es 401 con el mensaje `Invalid user {username} or password`
