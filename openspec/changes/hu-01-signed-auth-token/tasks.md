# Tasks

Scope: GitHub Issue [#13 - HU-01](https://github.com/user-bcp-student04/workshop-auth-backend/issues/13), bounded by `proposal.md`, `specs/auth-token/spec.md` and `design.md`.

## 1. Caracterizar el contrato actual antes del cambio

- [x] 1.1 Extender la prueba de login válido para caracterizar HTTP 200, los campos actuales de respuesta, `debug`, `user` con `displayName` escapado, `expiresIn` actual y presencia del token; verificar con `mvn -Dtest=AuthResourceTest test`.
- [x] 1.2 Agregar pruebas de request nulo, username/password nulos o en blanco y credenciales inválidas, comprobando status y mensajes actuales 400/401; verificar con `mvn -Dtest=AuthResourceTest test`.
- [x] 1.3 Caracterizar el mapeo de una excepción del login a HTTP 500 y los campos `message`/`exception` usando un doble CDI solo de prueba para `LegacyDatabase`; verificar con `mvn -Dtest=AuthResourceTest test` sin agregar puntos de fallo a producción.

### Evidencia de caracterizacion (2026-10-21)

- Rama conservada: `mcp-lab-evidence`; Issue #13. Sin cambios de produccion ni de contratos HTTP.
- `docs/TESTING.md` no estaba presente en la revision inicial. Aparecio como archivo no rastreado durante la comprobacion final, sin haber sido creado ni modificado por este trabajo, y se leyo entonces. Se reutilizo y amplio `AuthResourceTest`, contrastando el contrato con `docs/ENDPOINTS.md`.
- `logsInWithWorkshopUser`: protege HTTP 200, JSON, los cuatro campos actuales, datos del usuario, debug, token no vacio y `expiresIn=3600` como baseline transitorio, no como requisito final del token firmado.
- `escapesDisplayNameInSuccessfulResponse`: protege el escape HTML con un nombre de fixture que contiene etiquetas, ampersand y comillas.
- `rejectsNullRequest`: protege HTTP 400 y `Request is null` para JSON null.
- `rejectsNullEmptyOrBlankUsernameBeforePassword`: protege HTTP 400 y la precedencia de username ante password tambien invalida; incluye null, vacio, espacio y tabulacion/saltos de linea.
- `rejectsNullEmptyOrBlankPassword`: protege HTTP 400 y su mensaje con username valido y los mismos casos de password.
- `rejectsUnknownUserAndIncorrectPassword`: protege HTTP 401 y su mensaje para usuario inexistente y password incorrecta.
- `mapsDatabaseExceptionToExistingErrorResponse` y `usesUnknownErrorWhenExceptionHasNoMessage`: protegen HTTP 500, los campos message/exception y el fallback `Unknown error`.
- `CharacterizationDatabase` es una alternativa CDI exclusiva de `src/test`: delega las credenciales normales a SQLite y reserva fixtures aislados para HTML y excepciones. No se agregaron dependencias ni puntos de fallo a produccion.
- Comando inicial: `mvn -f <ruta-del-backend>/pom.xml -Dtest=AuthResourceTest test`; se reintento desde la raiz del backend con `mvn -Dtest=AuthResourceTest test`. El reintento fallo al arrancar Quarkus: puerto 8081 ocupado, 1 error y 7 pruebas omitidas; ademas hubo un error de acceso modular de la JVM.
- Comando validado desde la raiz del backend: `mvn -Dtest=AuthResourceTest -Dquarkus.http.test-port=0 test`, agregando temporalmente `--add-opens=java.base/java.lang=ALL-UNNAMED` a `JAVA_TOOL_OPTIONS` y restaurando su valor al terminar. Resultado: `BUILD SUCCESS`; 8 tests, 0 failures, 0 errors, 0 skipped. Puerto dinamico, sin detener servicios existentes.
- Entorno comprobado con `mvn -v`: Maven 3.10.0 y Java 25.0.4.1; el proyecto declara Java 21. JaCoCo 0.8.12 emitio errores de instrumentacion para clases de Java 24/25 aunque los tests pasaron. No se desactivo ni modifico JaCoCo; cobertura/gates quedan pendientes de ejecucion en un JDK compatible. No se ejecuto ni se marca completada la tarea 4.2.
- Revision resuelta por confirmacion humana (2026-10-21): preservar `message` y `exception` en HTTP 500 para HU-01, como establecen el codigo actual, `docs/ENDPOINTS.md`, proposal, spec y design. Se alinearon la matriz y el ejemplo 500 de `docs/TESTING.md` con las pruebas de caracterizacion; sus cifras previas de 18 tests y cobertura se distinguen de esta ejecucion y no se adoptan como evidencia. No se implementa un error generico ni se amplian las tareas funcionales; el riesgo de exposicion de detalles internos queda fuera del alcance actual.

### Revalidacion de caracterizacion (2026-10-21)

- Se releyeron el Issue #13, proposal, la spec auth-token completa, design, tasks y `docs/TESTING.md` antes de agregar pruebas. Se conservaron las ocho pruebas existentes y el doble CDI, sin duplicarlas.
- Para completar 1.2 se agregaron solamente `rejectsMissingUsername` y `rejectsMissingPassword`: protegen la deserializacion HTTP de campos omitidos (distinta de un JSON con null explicito), HTTP 400, el mensaje correspondiente y el cuerpo de error de un solo campo.
- Desde la raiz de `workshop-auth-backend` se ejecuto `mvn -Dtest=AuthResourceTest -Dquarkus.http.test-port=0 test`, agregando temporalmente `--add-opens=java.base/java.lang=ALL-UNNAMED` a `JAVA_TOOL_OPTIONS` y restaurando su valor previo con `finally`.
- Resultado de esta revalidacion: `BUILD SUCCESS`; 10 tests, 0 failures, 0 errors, 0 skipped. Evidencia local: `target/surefire-reports/com.bcp.workshop.auth.AuthResourceTest.txt`.
- No se detectaron contradicciones entre las aserciones de caracterizacion y el baseline aprobado de OpenSpec. Se preservan HTTP 200/400/401/500, incluido `message`/`exception` en 500; no se valida ni implementa aun el contrato nuevo de JWT/client.
- Persisten los errores de instrumentacion de JaCoCo 0.8.12 con clases de Java 24/25. El exito de las pruebas funcionales no acredita cobertura: repetir con Java 21 queda pendiente, sin modificar dependencias ni desactivar gates. Las tareas 2, 3 y 4 permanecen sin completar; no se ejecuta `mvn verify` ni se amplia el alcance a health.

## 2. Preparar emision y configuracion segura del token

- [x] 2.1 Confirmar y registrar que los componentes que verificarán tokens HS256 son servicios de confianza; si algún consumidor público necesita verificación independiente, detener la implementación y revisar la decisión de firma antes de continuar.

Confirmacion humana (2026-10-21): solo servicios backend de confianza verificaran firmas HS256; los clientes publicos solo transportaran el token y nunca recibiran `AUTH_TOKEN_SECRET`.

- [x] 2.2 Incorporar una versión fijada de Nimbus JOSE + JWT y verificar que el proyecto compila y las pruebas de caracterización siguen pasando con `mvn test`.

Evidencia 2.2 (2026-10-21): Nimbus JOSE + JWT 10.5 fijado en pom.xml; `mvn -Dquarkus.http.test-port=0 test`, con apertura modular temporal de JVM ya descrita, termino en BUILD SUCCESS: 10 tests, 0 failures, 0 errors, 0 skipped. Persisten errores de instrumentacion de JaCoCo con Java 25; no se declara cobertura validada.

- [x] 2.3 Implementar el emisor JWT con reloj controlable y pruebas de firma válida, rechazo de token alterado, claims `sub`, `client`, `iat`, `exp`, preservación de `role` y expiración antes/en/después de `exp`; verificar con las pruebas unitarias del emisor.

Evidencia 2.3: `mvn -Dtest=SignedTokenIssuerTest test` termino en BUILD SUCCESS: 4 tests sin fallos, errores ni omisiones. Nimbus verifica firma y tiempo con reloj fijo y tolerancia cero, incluidos rechazo de alteracion, clave distinta y expiracion en/tras exp. Persisten las limitaciones de JaCoCo/Java 25 registradas arriba.

- [x] 2.4 Configurar `AUTH_TOKEN_SECRET` con mínimo de 256 bits, sin fallback de laboratorio fuera de dev/test, y probar que producción falla al iniciar si falta o es débil; documentar la configuración y verificar con las pruebas de perfiles/configuración.

Evidencia 2.4: `mvn -Dquarkus.http.test-port=0 test` y `mvn -Dquarkus.http.test-port=0 package quarkus:build`, con apertura modular temporal, pasaron 17 tests sin fallos. `java --add-opens=java.base/java.lang=ALL-UNNAMED -Dquarkus.profile=prod -Dquarkus.http.port=0 -jar target/quarkus-app/quarkus-run.jar`, primero sin AUTH_TOKEN_SECRET y luego con fixture corto, rechazo ambos arranques con exit=1 y el mensaje esperado, sin imprimir secretos. Se restauro la variable de entorno al terminar. Configuracion documentada en docs/ENDPOINTS.md; cobertura sigue pendiente por incompatibilidad de JaCoCo/Java 25.

## 3. Integrar el contrato firmado en login

- [x] 3.1 Validar `client` como obligatorio y no blanco después de validar username/password y antes de consultar credenciales; probar los casos ausente, nulo, vacío y espacios con HTTP 400 y sin token usando `mvn -Dtest=AuthResourceTest test`.

Evidencia 3.1: `mvn -Dtest=AuthResourceTest -Dquarkus.http.test-port=0 test`, con apertura modular temporal, BUILD SUCCESS; 13 tests sin fallos, errores ni omisiones. Incluye client ausente/null/vacio/blanco, cuerpo 400 de un solo campo y precedencia de validaciones antes de credenciales.

- [x] 3.2 Conectar el login al emisor, derivar `expiresIn` del mismo `exp` con tolerancia máxima de un segundo y preservar los campos de respuesta protegidos; probar login válido, claims, duración y errores 400/401/500 con `mvn -Dtest=AuthResourceTest test`.

Evidencia 3.2: `mvn -Dtest=AuthResourceTest,SignedTokenIssuerTest,TokenConfigurationTest -Dquarkus.http.test-port=0 test`, con apertura modular temporal, BUILD SUCCESS; 21 tests sin fallos, errores ni omisiones. Verifica firma HS256, claims exactos, TTL nominal 3600, client sin normalizar, expiresIn con tolerancia maxima de un segundo y baseline 200/400/401/500. La asercion transitoria de expiresIn fijo se reemplazo por la del contrato aprobado.

- [x] 3.3 Actualizar `docs/ENDPOINTS.md` con la obligatoriedad de `client`, los claims, la expiración, la decisión HS256 y la configuración del secreto sin publicar su valor; verificar que ejemplos y respuestas coinciden con `specs/auth-token/spec.md` y las pruebas del endpoint.

Evidencia 3.3: docs/ENDPOINTS.md contrastado con spec y pruebas HTTP (21 tests de la validacion anterior): client obligatorio, JWT HS256, claims sub/client/role/iat/exp, TTL 3600, tolerancia de reporte 1 segundo y rechazo temporal sin tolerancia en exp. Se documentan entorno, confianza y migracion sin exponer tokens ni claves reales. `git diff --check` paso, con aviso de normalizacion LF/CRLF.

## 4. Verificar proteccion y regresion integrada

- [x] 4.1 Agregar una prueba de captura de logs para login exitoso y fallido con valores de fixture únicos, y asegurar que no aparecen password, secreto ni token en claro o recuperables; verificar con la prueba de logs.

Evidencia 4.1: `mvn -Dtest=AuthResourceTest -Dquarkus.http.test-port=0 test`, con apertura modular temporal, BUILD SUCCESS; 16 tests sin fallos, errores ni omisiones. Handler JUL temporal con marcador de captura activo, fixtures de password de exito/fallo y comprobacion del secreto configurado y token emitido, tanto literales como codificados Base64 estandar/URL. No se imprimen datos sensibles y el handler se retira en finally. Incluye smoke de GET /q/health: 200, UP y checks presente.

- [x] 4.2 Ejecutar `mvn verify` y comprobar que pasan las pruebas de login, emisor, configuración y logs, y que `GET /q/health` conserva su respuesta de salud; registrar el resultado de la suite.

### Verificacion de implementacion (2026-10-21)

- `mvn -Dquarkus.http.test-port=0 verify`, desde la raiz del backend y con `--add-opens=java.base/java.lang=ALL-UNNAMED` agregado temporalmente a JAVA_TOOL_OPTIONS (restaurado en finally): BUILD SUCCESS, 23 tests, 0 failures, 0 errors, 0 skipped.
- Cobertura observada en el XML real `target/site/jacoco/jacoco.xml`, metrica INSTRUCTION: AuthResource 147/147 (100%), SignedTokenIssuer 92/103 (89.32%), TokenConfiguration 20/20 (100%), IssuedToken 9/9 (100%). BRANCH: AuthResource 18/18; SignedTokenIssuer 5/6. No se atribuyen a esta ejecucion las cifras historicas de otro checkout.
- Cumplido: emision HS256 con Nimbus, claims y tiempos, client obligatorio sin normalizar, secreto validado en arranque, baseline HTTP preservado, logs de login sin fixtures sensibles y smoke GET /q/health 200/UP/checks.
- Pendiente de otro gate: repetir en Java 21 porque JaCoCo 0.8.12 emitio errores de instrumentacion de clases Java 24/25 aun generando el reporte de clases del proyecto; ejecutar CodeQL/SCA/Dependency Review en CI. No se desactivo ni rebajo ningun control, y BUILD SUCCESS no significa aprobacion de esos gates.
- No aplicable: nuevos endpoints, validacion de tokens en requests, refresh, rotacion de claves y cambios de persistencia, todos fuera del alcance aprobado.
- Riesgo residual aprobado: el 500 conserva message y exception por compatibilidad; la reduccion de detalles internos requiere otro cambio. HS256 solo se comparte entre servicios de confianza conforme a la confirmacion humana de 2.1.
- No se cambiaron ramas ni repositorios; no se realizo commit, push, PR, merge ni archivo del cambio OpenSpec.

### Reejecucion solicitada con rutas explicitas (2026-10-21)

- Se releyeron Issue #13, proposal, specs/auth-token/spec.md, design y tasks. `openspec instructions apply --change hu-01-signed-auth-token --json` confirmo `all_done`, 12/12 tareas; no se duplico implementacion ni se cambiaron checkboxes.
- `mvn -Dquarkus.http.test-port=0 verify`, desde workshop-auth-backend, termino a las 17:49:06 UTC-05:00 en BUILD SUCCESS: 23 tests, 0 failures, 0 errors, 0 skipped. Se uso apertura modular temporal en JAVA_TOOL_OPTIONS y se restauro su valor en finally.
- JaCoCo genero nuevamente reporte de las ocho clases del proyecto, pero persisten errores de instrumentacion de clases Java 24/25. La repeticion en Java 21 y los gates de seguridad CI siguen pendientes; no se desactivaron controles ni se declara el cambio listo para merge. No hubo cambios funcionales en esta reejecucion.