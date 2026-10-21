# Proposal

## Fuente

- GitHub Issue [#13 - HU-01](https://github.com/user-bcp-student04/workshop-auth-backend/issues/13).
- Historia de usuario comprometida: [HU-01.md](../../../HU-01.md).
- Contrato vigente que sirve de baseline: [docs/ENDPOINTS.md](../../../docs/ENDPOINTS.md).

## Why

`POST /auth/login` entrega hoy un token Base64 reversible que contiene un secreto embebido, mientras que HU-01 requiere un token firmado con identidad, cliente y tiempos verificables. Este cambio importa para que el consumidor pueda confiar en los claims y en la expiración sin exponer material de autenticación en logs.

## What Changes

- Cambia el contrato del token emitido por login: debe estar firmado e incluir `sub`, `client`, `iat` y `exp`.
- `client`, actualmente aceptado pero ignorado, pasa a ser un dato validado de la solicitud y queda reflejado en el token.
- `expiresIn` debe corresponder al tiempo restante hasta `exp`, dentro de una tolerancia documentada.
- Se exige que tokens expirados no se consideren válidos y que haya pruebas para login válido, credenciales inválidas y expiración.
- Se protege el secreto y los datos sensibles de autenticación frente a los logs; el secreto por defecto no debe usarse fuera del sandbox de laboratorio.
- No se elige aquí entre HMAC y una biblioteca JWT. HU-01 deja esa decisión técnica abierta y pide que se documente.

## Comportamiento a preservar

- Se mantienen `POST /auth/login`, su autenticación previa no requerida y el formato JSON.
- Se conservan las validaciones existentes de request, `username` y `password`, y sus respuestas 400; credenciales inválidas continúan respondiendo 401 y las excepciones no controladas mantienen la respuesta 500 documentada.
- La respuesta exitosa conserva `token`, `expiresIn` y `user` con `username`, `displayName` y `role`, además de `debug`; solo cambia el contenido del token y `expiresIn` deja de ser necesariamente fijo.
- `displayName` continúa escapado en HTML.
- `GET /q/health` y su contrato no forman parte del cambio.

## Alcance

- Requisitos observables de emisión y expiración del token en el flujo de login.
- Validación de `client` para poder incluirlo en los claims.
- Protección de secretos, tokens y credenciales en logs y uso/documentación del secreto de laboratorio.
- Criterios de aceptación para los flujos de login cubiertos por HU-01.

## Fuera de alcance

- Añadir endpoints de login, introspección o verificación de tokens.
- Cambiar el contrato de health, el modelo de usuario o las reglas de autenticación por credenciales.
- Elegir o diseñar el algoritmo, formato del token o biblioteca concreta; esa decisión queda para una etapa posterior.
- Definir una lista de clientes permitidos o restricciones de formato/longitud que no aparecen en el Issue.

## Capabilities

### New Capabilities

- `auth-token`: contrato del token emitido por login, claims, cliente, expiración y protección de datos de autenticación.

### Modified Capabilities

- Ninguna. El inventario actual no contiene specs; el contrato de endpoints existente se toma de `docs/ENDPOINTS.md` como baseline.

## Impact

- API: respuesta exitosa y validación de entrada de `POST /auth/login`, con los cambios de token y `client` descritos arriba.
- Seguridad: manejo del secreto de firma, expiración y registros de autenticación.
- Documentación y pruebas del backend: deberán reflejar y verificar los requisitos de HU-01.

## Supuestos y preguntas abiertas

- Para que `client` pueda integrar el claim requerido, se asume como mínimo que debe estar presente y no ser nulo, vacío o solo espacios. El Issue no define allowlist, longitud ni caracteres permitidos.
- HU-01 afirma que ya se usa HMAC y `AUTH_TOKEN_SECRET`; el código actual y `docs/ENDPOINTS.md` muestran en cambio Base64 reversible con secreto embebido. Esta propuesta sigue el baseline observado y trata el token firmado como cambio intencional del Issue.
- HU-01 requiere una tolerancia documentada para `expiresIn` frente a `exp - now`, pero no fija su valor; no se inventa uno en esta propuesta.
- HU-01 pide pruebas de expiración, pero el contrato actual no expone un endpoint consumidor/verificador de tokens. El requisito cubre la validez temporal del token; no añade una ruta HTTP de verificación.