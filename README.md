# Workshop Auth Backend — Java Quarkus

> **Laboratorio intencionalmente vulnerable.** Ejecutar únicamente en local, repositorios privados y ambientes aislados. No desplegar en Internet, CERT o PROD.

Backend Quarkus que autentica usuarios de laboratorio almacenados en SQLite y entrega un token consumido por el frontend Angular y la API Python.

## Ejecución

Requisitos: JDK 21 y Maven 3.9+.

```bash
mvn quarkus:dev
```

Servicio: `http://localhost:8080`

Credenciales locales:

- `student` / `Workshop123!`
- `trainer` / `Trainer123!`

## Endpoints

- `POST /auth/login`
- `GET /q/health`

## Contrasenas y migracion local (H-02)

En la rama de trabajo, login busca por username con SQL parametrizado y verifica un hash Argon2id mediante Password4j 1.8.4. Cada contrasena procesada recibe un salt aleatorio de 16 bytes; el formato almacenado incluye algoritmo, parametros, salt y hash. No se agrega el hash al JSON ni a UserRecord. Las credenciales publicas de laboratorio de arriba se conservan.

La configuracion por defecto es `auth.password.memory-kib=19456`, `auth.password.iterations=2` y `auth.password.parallelism=1`: 19 MiB de memoria de trabajo del algoritmo, 2 iteraciones y 1 hilo. Eso no limita el heap total de Java. Se validan costes y hashes soportados antes de verificarlos. Los valores actuales se ensayaron localmente; cambiar costes exige medir de nuevo latencia, memoria y migracion. Las semillas usan hashes ficticios precomputados en codigo, no nuevas contrasenas reales ni variables obligatorias. Se pueden suministrar hashes de laboratorio mediante `auth.seed.student-hash` y `auth.seed.trainer-hash`; no registrar esos valores.

Al inicializar, una base nueva crea directamente `legacy_users.password_hash`; una base legada de version 0 con columna password se migra dentro de una transaccion a version 1 (`PRAGMA user_version`). Se preservan ids, datos, unicidad y secuencia AUTOINCREMENT. Los reinicios no recalculan hashes ni sobrescriben usuarios existentes. El lookup operativo nunca vuelve a comparar passwords en texto plano.

Antes de arrancar esta version contra una base existente:

1. Detener la aplicacion y cualquier otro escritor. No hacer migraciones concurrentes. Confirmar AUTH_DB_URL y que se trata de una base de este laboratorio con esquema soportado.
2. Crear una copia SQLite consistente, dentro del ambito local y con acceso restringido. Si hay WAL o conexiones activas, no asumir que copiar solo el archivo .db constituye una copia valida; cerrar las conexiones y utilizar un mecanismo de backup consistente de SQLite. La copia legada puede contener contrasenas legibles y requiere una politica de retencion y eliminacion.
3. Ensayar primero sobre una copia aislada dentro de target. No usar datos reales para el ensayo de rendimiento. Revisar los resultados de [docs/TESTING.md](docs/TESTING.md) y volver a medir si el volumen o el hardware cambian.
4. Arrancar la version nueva siguiendo la guia de ejecucion de [docs/ENDPOINTS.md](docs/ENDPOINTS.md); comprobar version 1, columna password_hash sin columna password, cuentas y pruebas de login antes de admitir uso del laboratorio.
5. Ante fallo, la transaccion hace rollback y la inicializacion conserva el manejo legado de errores: registra el fallo sin abortar el proceso. No interpretar que el proceso siga activo como una migracion correcta. No hay fallback de autenticacion plaintext; si el lookup falla, login conserva el 401 documentado. Un esquema desconocido no se migra a ciegas.
6. Si hace falta recuperar, detener escritores, conservar la evidencia del fallo y restaurar la copia consistente completa. Volver a ensayar y ejecutar la migracion con esta version corregida. No arrancar la version anterior sobre el esquema nuevo; restaurar una base legada tampoco permite afirmar que H-02 este corregido hasta completar y validar la migracion.

La prueba operativa aprobada uso 100 usuarios ficticios, login de como maximo 1 s y migracion de como maximo 60 s; incluyo fallo a mitad, rollback y restauracion de una copia cerrada sin WAL activo. No se ha migrado automaticamente la base habitual del usuario durante el desarrollo.

Eliminar la columna no garantiza borrar contenidos historicos en paginas libres, journal, WAL o backups. Su tratamiento debe acordarse y ejecutarse de forma controlada; no borrar artefactos de una base abierta. Este cambio no firma tokens, no restringe CORS y no sanea errores: el laboratorio sigue sin ser apto para produccion.

## Propósito de la rama `main`

La rama contiene deuda técnica y de seguridad deliberada: SQL concatenado, contraseñas en texto plano, token reversible, secreto hardcodeado, CORS permisivo, método largo, manejo genérico de excepciones y dependencias con CVEs.
