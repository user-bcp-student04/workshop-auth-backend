# Design

## Context

- El servicio usa Java 21 y Quarkus 3.27.4. El build ya incluye REST/Jackson, JUnit 5 y REST Assured, pero no una biblioteca JWT.
- `POST /auth/login` valida username/password, consulta credenciales y construye la respuesta en un mismo recurso. El request ya contiene `client`, pero hoy no se valida ni se usa.
- El token actual es Base64 reversible de username, role, expiracion y un secreto literal. `expiresIn` se fija por separado en 3600 segundos.
- Solo existe una prueba de integracion de login exitoso; comprueba status, username y presencia de token. No caracteriza errores, expiracion, claims ni logs.
- No hay un endpoint ni middleware que consuma o valide tokens. La motivacion y el contrato aprobado estan en `proposal.md` y `specs/auth-token/spec.md`.

## Goals / Non-Goals

**Goals:**

- Emitir un token firmado y verificable con los claims aprobados, derivando la expiracion de un TTL unico.
- Mantener el formato externo de la respuesta JSON y las respuestas existentes fuera de la nueva validacion de `client`.
- Usar un secreto configurable y evitar que secretos, passwords o tokens aparezcan en logs.
- Permitir pruebas deterministas de claims y tiempo sin agregar una ruta de validacion.

**Non-Goals:**

- Crear endpoints de verificacion, introspeccion o renovacion, ni agregar autenticacion a otras rutas.
- Cambiar la base de datos, las reglas de credenciales, el contrato de health o la forma de los errores 500.
- Implementar rotacion simultanea de claves, refresh tokens o aceptacion dual del token legacy.

## Decisions

### Biblioteca y formato de firma

Usar Nimbus JOSE + JWT para emitir un JWT compacto firmado como JWS con HS256. El formato estandar y la biblioteca evitan crear una serializacion, firma o verificador propios; la biblioteca debe comprobar firma y claims de tiempo en las pruebas.

Alternativas consideradas:

- Construir el token con APIs criptograficas del JDK: no requiere dependencia, pero obliga al proyecto a mantener serializacion, canonicalizacion y verificacion propias, con mayor riesgo de errores de seguridad.
- Usar otra integracion JWT de Quarkus: podria aportar mas integracion con autorizacion del framework, pero esta propuesta no agrega validacion de requests ni middleware y solo necesita emitir tokens. No aporta una ventaja suficiente para ampliar esa superficie.

### Secreto y configuracion por entorno

Leer el material de firma de `AUTH_TOKEN_SECRET`. En produccion el servicio debe fallar al iniciar si falta o no cumple el minimo de 256 bits requerido por HS256; nunca debe recurrir a un valor de laboratorio. Un valor de laboratorio solo puede estar habilitado de forma explicita en perfiles dev/test. El valor real se suministra por configuracion del entorno, no se versiona ni se imprime.

La alternativa de un literal compartido entre todos los entornos se descarta porque no permite separar el sandbox del despliegue real y mantiene el riesgo identificado en la propuesta.

### Claims y duracion

- Derivar `sub` del username persistido y `client` del valor recibido, sin normalizarlo; preservar `role` como claim para mantener la informacion incluida en el token legacy.
- Generar `iat` y `exp` en segundos Unix a partir de un unico instante de emision. Mantener el TTL nominal existente de 3600 segundos.
- Calcular `expiresIn` desde el mismo `exp` y el instante de respuesta. Documentar una tolerancia maxima de 1 segundo para la precision en segundos y el tiempo de procesamiento.
- Proveer el reloj al componente emisor para que las pruebas de limite de expiracion sean deterministas.

Alternativamente, se podria hacer configurable el TTL ahora; se conserva 3600 segundos para evitar introducir una nueva politica de producto no solicitada.

### Limite entre recurso y emision del token

Mantener el orden actual de validacion de request, username y password; validar `client` despues de esos campos y antes de consultar credenciales. El recurso debe delegar la creacion del token a un componente de emision dedicado y continuar construyendo la respuesta JSON existente. El rechazo nuevo de `client` usa HTTP 400 y el mismo formato JSON de errores de validacion.

No agregar allowlist, formato, longitud ni transformacion de `client`: el Issue no define esas reglas. Un valor no nulo, no vacio y no compuesto solo por espacios es suficiente para esta entrega.

### Proteccion de logs

No registrar cuerpos de solicitud, password, secreto ni token en los caminos de exito o error. Conservar la respuesta 500 documentada sin agregar logging de valores sensibles. La verificacion se hace con captura de logs en pruebas, buscando valores unicos de fixture y comprobando que no aparecen.

## Contratos protegidos

- Preservar `/auth/login`, JSON, la respuesta exitosa con `token`, `expiresIn`, `user` y `debug`, y los campos `username`, `displayName` escapado y `role` dentro de `user`.
- Preservar las validaciones y respuestas 400 existentes para request, username y password, el 401 para credenciales invalidas y el 500 documentado.
- Cambiar intencionalmente la obligatoriedad de `client`, el contenido del token y el valor dinamico de `expiresIn`, tal como permiten la propuesta y la spec.
- No modificar `GET /q/health`.

## Componentes afectados

- Dependencias Maven: incorporar la biblioteca Nimbus JOSE + JWT con una version compatible y fijada.
- Configuracion Quarkus: mapear `AUTH_TOKEN_SECRET`, limitar cualquier fallback a dev/test y rechazar configuracion de produccion ausente o debil.
- Request y recurso de login: validar `client` y pasar los datos de usuario/cliente al componente emisor, conservando las respuestas protegidas.
- Emision de tokens: reemplazar el formato legacy, producir claims y firma, y calcular `expiresIn` desde `exp`.
- Pruebas y documentacion del contrato: caracterizar la conducta actual antes del cambio y actualizar los ejemplos de token, configuracion y expiracion.

## Estrategia incremental

1. Antes de cambiar la emision o las validaciones, ampliar las pruebas de caracterizacion del endpoint y fijar el comportamiento que no debe variar. Registrar por separado las aserciones sobre token Base64 y `expiresIn` fijo como conducta legacy que sera reemplazada, no como criterio final.
2. Agregar pruebas del contrato nuevo contra el emisor: firma verificable, claims, role preservado, `client`, y limites exactos de `exp` con reloj controlado. Agregar casos para `client` ausente/blanco, coherencia de `expiresIn` y ausencia de datos sensibles en logs.
3. Incorporar la dependencia y configuracion de secreto; verificar que produccion no inicia sin un secreto valido y que dev/test usan solamente el valor aislado para laboratorio.
4. Sustituir la emision legacy y conectar la validacion de `client`, preservando el orden de validaciones, la respuesta y los campos de usuario.
5. Actualizar `docs/ENDPOINTS.md` y ejecutar la suite del backend, incluidas las pruebas de caracterizacion y las nuevas pruebas del token.

## Pruebas de caracterizacion antes de cambios criticos

Antes de modificar la validacion del login o reemplazar el token, extender `AuthResourceTest` para cubrir:

- Login valido: HTTP 200; presencia de todos los campos actuales; `user.username`, `role`, `displayName` escapado y `debug`; `expiresIn` actual; existencia de `token`.
- Request nulo, username y password nulos/vacios/en blanco: HTTP 400 y mensajes actuales.
- Credenciales invalidas: HTTP 401 y forma/mensaje actual.
- Excepcion no controlada: HTTP 500 y campos `message`/`exception`, si puede provocarse sin introducir acoplamiento de prueba a detalles internos.
- La forma legacy del token solo se documenta como baseline transitorio; no mantener una asercion que impida el cambio intencional a un token firmado.

Despues de esa linea base, probar el JWT mediante la biblioteca: firma valida, claims esperados, token alterado con firma invalida, expiracion justo antes de `exp` y rechazo en `exp` o despues. Probar tambien la politica de configuracion por entorno y capturar logs para asegurar que no contienen password, secreto ni token.

## Riesgos / Trade-offs

- [Cambio incompatible del token] -> Coordinar consumidores para aceptar el nuevo JWT y exigir un nuevo login durante el despliegue; no aceptar ambos formatos porque eso prolongaria el formato inseguro.
- [HS256 requiere compartir el secreto con cualquier consumidor que verifique el token] -> Limitar la distribucion del secreto a servicios de confianza y nunca entregarlo a clientes publicos. Si el modelo real requiere verificacion publica, ese cambio necesita una decision de claves asimetricas y una revisita de este diseno antes de implementar.
- [Secreto ausente, corto o filtrado] -> Fallar cerrado fuera de dev/test, aprovisionar material aleatorio de al menos 256 bits y excluir su valor de logs y control de versiones.
- [Desfase de reloj entre emisor y consumidores] -> Sincronizar relojes de los entornos y mantener la tolerancia documentada en `expiresIn`; no aceptar tokens en o despues de `exp`.
- [Exposicion preexistente en respuestas 500] -> Se conserva por compatibilidad de acuerdo con el baseline; reducir esos detalles es un cambio separado y no forma parte de esta implementacion.

## Migracion y rollback

1. Desplegar la configuracion del secreto de produccion antes de activar la version que emite JWT; verificar que el servicio rechaza el arranque cuando falta.
2. Publicar la actualizacion del contrato y coordinar a consumidores para que interpreten el nuevo token. El endpoint sigue en la misma ruta y requiere login nuevo para obtenerlo.
3. Tras el despliegue, validar login, claims, expiracion y errores con la suite y un smoke test; comprobar que los logs no contienen valores sensibles.

Rollback: volver a desplegar la version previa del backend y solicitar nuevos logins a los usuarios afectados. Los JWT emitidos por la version nueva no deben asumirse compatibles con consumidores legacy. No se necesita migracion de datos; mantener el secreto configurado hasta confirmar el rollback y no reintroducirlo como literal de produccion.

## Open Questions

Ninguna para comenzar la implementacion. Si se descubre un consumidor no confiable que deba verificar tokens sin compartir el secreto HS256, detenerse y revisar la decision de firma antes de implementar.