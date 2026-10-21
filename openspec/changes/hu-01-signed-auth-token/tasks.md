# Tasks

Scope: GitHub Issue [#13 - HU-01](https://github.com/user-bcp-student04/workshop-auth-backend/issues/13), bounded by `proposal.md`, `specs/auth-token/spec.md` and `design.md`.

## 1. Caracterizar el contrato actual antes del cambio

- [ ] 1.1 Extender la prueba de login válido para caracterizar HTTP 200, los campos actuales de respuesta, `debug`, `user` con `displayName` escapado, `expiresIn` actual y presencia del token; verificar con `mvn -Dtest=AuthResourceTest test`.
- [ ] 1.2 Agregar pruebas de request nulo, username/password nulos o en blanco y credenciales inválidas, comprobando status y mensajes actuales 400/401; verificar con `mvn -Dtest=AuthResourceTest test`.
- [ ] 1.3 Caracterizar el mapeo de una excepción del login a HTTP 500 y los campos `message`/`exception` usando un doble CDI solo de prueba para `LegacyDatabase`; verificar con `mvn -Dtest=AuthResourceTest test` sin agregar puntos de fallo a producción.

## 2. Preparar emision y configuracion segura del token

- [ ] 2.1 Confirmar y registrar que los componentes que verificarán tokens HS256 son servicios de confianza; si algún consumidor público necesita verificación independiente, detener la implementación y revisar la decisión de firma antes de continuar.
- [ ] 2.2 Incorporar una versión fijada de Nimbus JOSE + JWT y verificar que el proyecto compila y las pruebas de caracterización siguen pasando con `mvn test`.
- [ ] 2.3 Implementar el emisor JWT con reloj controlable y pruebas de firma válida, rechazo de token alterado, claims `sub`, `client`, `iat`, `exp`, preservación de `role` y expiración antes/en/después de `exp`; verificar con las pruebas unitarias del emisor.
- [ ] 2.4 Configurar `AUTH_TOKEN_SECRET` con mínimo de 256 bits, sin fallback de laboratorio fuera de dev/test, y probar que producción falla al iniciar si falta o es débil; documentar la configuración y verificar con las pruebas de perfiles/configuración.

## 3. Integrar el contrato firmado en login

- [ ] 3.1 Validar `client` como obligatorio y no blanco después de validar username/password y antes de consultar credenciales; probar los casos ausente, nulo, vacío y espacios con HTTP 400 y sin token usando `mvn -Dtest=AuthResourceTest test`.
- [ ] 3.2 Conectar el login al emisor, derivar `expiresIn` del mismo `exp` con tolerancia máxima de un segundo y preservar los campos de respuesta protegidos; probar login válido, claims, duración y errores 400/401/500 con `mvn -Dtest=AuthResourceTest test`.
- [ ] 3.3 Actualizar `docs/ENDPOINTS.md` con la obligatoriedad de `client`, los claims, la expiración, la decisión HS256 y la configuración del secreto sin publicar su valor; verificar que ejemplos y respuestas coinciden con `specs/auth-token/spec.md` y las pruebas del endpoint.

## 4. Verificar proteccion y regresion integrada

- [ ] 4.1 Agregar una prueba de captura de logs para login exitoso y fallido con valores de fixture únicos, y asegurar que no aparecen password, secreto ni token en claro o recuperables; verificar con la prueba de logs.
- [ ] 4.2 Ejecutar `mvn verify` y comprobar que pasan las pruebas de login, emisor, configuración y logs, y que `GET /q/health` conserva su respuesta de salud; registrar el resultado de la suite.