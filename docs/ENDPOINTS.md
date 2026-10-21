# Contrato de Endpoints

## Configuracion de firma HU-01

La emision firmada usa Nimbus JOSE + JWT 10.5 y HS256. Configurar
`AUTH_TOKEN_SECRET` con material aleatorio de al menos 32 bytes (256 bits),
interpretado como UTF-8; la longitud minima no sustituye la entropia.
No versionar ni imprimir el valor. En produccion, su ausencia o una longitud
insuficiente impide el arranque. Solo dev/test tienen fixtures explicitos
de laboratorio; nunca usar esos valores en un despliegue real.
La configuracion interna `auth.token.secret` no debe sobrescribirse con un
valor debil. El secreto solo se comparte entre servicios backend de confianza;
los clientes publicos transportan el token, pero no verifican HS256 ni reciben
el secreto. No se agrega endpoint de verificacion ni renovacion.

## Endpoint: POST /auth/login

### 1) Que hace

Autentica a un usuario de laboratorio y devuelve un token junto con datos basicos del usuario autenticado.

### 2) Autenticacion requerida

No requiere autenticacion previa.

Header esperado:

- Content-Type: application/json

### 3) Request

Campos del body JSON:

| campo | tipo | obligatorio | descripcion |
|---|---|---|---|
| username | string | Si | Usuario de login. Si viene nulo, vacio o en blanco, responde 400. |
| password | string | Si | Contrasena del usuario. Si viene nula, vacia o en blanco, responde 400. |
| client | string | Si | Presente, no nulo, no vacio ni compuesto solo por espacios. Se conserva sin normalizar en el claim client. |

Ejemplo JSON real y completo:

```json
{
  "username": "student",
  "password": "Workshop123!",
  "client": "angular-workshop"
}
```

### 4) Response exitosa

Codigo de estado: 200 OK

Campos del body JSON:

| campo | tipo | descripcion |
|---|---|---|
| token | string | JWT compacto firmado HS256, con claims sub, client, role, iat y exp. No contiene el secreto ni la password. |
| expiresIn | integer | Segundos restantes hasta exp, calculados desde el mismo exp; tolerancia maxima de 1 segundo respecto de exp - now. |
| user | object | Datos del usuario autenticado. |
| user.username | string | Username persistido del usuario autenticado. |
| user.displayName | string | Nombre de despliegue del usuario (escapado en HTML). |
| user.role | string | Rol persistido del usuario (ejemplo: STUDENT o TRAINER). |
| debug | string | Marca de flujo legacy actual. |

Ejemplo ilustrativo de respuesta (token sustituido; no es un token utilizable):

```json
{
  "token": "<JWT compacto HS256 emitido por login>",
  "expiresIn": 3600,
  "user": {
    "username": "student",
    "displayName": "Workshop Student",
    "role": "STUDENT"
  },
  "debug": "authenticated-with-legacy-flow"
}
```

El TTL nominal es 3600 segundos: `exp = iat + 3600`. `sub` y `role`
proceden del usuario persistido, `client` conserva exactamente la solicitud,
y `iat`/`exp` son segundos Unix. El consumidor de confianza debe verificar
HS256 con su biblioteca y rechazar firmas alteradas y tokens cuando
`now >= exp`, sin tolerancia de validez temporal. La tolerancia de un segundo
solo aplica al reporte de `expiresIn`, no extiende la vida del token.
El cambio del token y la obligatoriedad de client son incompatibilidades
intencionales de HU-01: coordinar consumidores y solicitar un nuevo login;
no se aceptan tokens legacy ni se agrega una ruta para verificarlos.

### 5) Respuestas de error posibles

Codigo: 400 Bad Request

Cuando ocurre:

- Request nulo.
- username nulo, vacio o en blanco.
- password nula, vacia o en blanco.
- client ausente, nulo, vacio o en blanco; se valida despues de username/password y antes de consultar credenciales.

Ejemplos de cuerpo:

```json
{"message":"Request is null"}
```

```json
{"message":"Username is required"}
```

```json
{"message":"Password is required"}
```

```json
{"message":"Client is required"}
```

Codigo: 401 Unauthorized

Cuando ocurre:

- Credenciales invalidas (usuario no encontrado o password no coincide).

Ejemplo de cuerpo:

```json
{"message":"Invalid user student or password"}
```

Codigo: 500 Internal Server Error

Cuando ocurre:

- Se lanza una excepcion no controlada durante el flujo de login.

Ejemplo de cuerpo:

```json
{
  "message": "Unknown error",
  "exception": "java.lang.RuntimeException"
}
```

Pregunta abierta:

- El comportamiento exacto para JSON malformado o Content-Type distinto de application/json depende del runtime REST de Quarkus y no pudo verificarse por ejecucion en este entorno.

### 6) Notas de seguridad relevantes

- El token es JWT HS256; no registrar el token, passwords ni secretos. La configuracion y el limite de confianza se describen al inicio de este documento.
- La respuesta de error 500 expone detalles internos (mensaje y clase de excepcion).
- CORS esta habilitado de forma permisiva en configuracion.

## Endpoint: GET /q/health

### 1) Que hace

Expone el estado de salud del servicio para monitoreo y orquestacion.

### 2) Autenticacion requerida

No requiere autenticacion previa.

Header esperado:

- No requiere header de autenticacion.

### 3) Request

Este endpoint no recibe body.

| campo | tipo | obligatorio | descripcion |
|---|---|---|---|
| Sin campos | N/A | N/A | Request GET sin cuerpo. |

Ejemplo de request completo:

```json
{}
```

### 4) Response exitosa

Codigo de estado esperado: 200 OK

Campos del body JSON (segun SmallRye Health):

| campo | tipo | descripcion |
|---|---|---|
| status | string | Estado global de salud del servicio (por ejemplo UP). |
| checks | array | Lista de checks reportados por el runtime. |

Ejemplo JSON real y completo:

```json
{
  "status": "UP",
  "checks": []
}
```

Pregunta abierta:

- El shape exacto de checks depende de los health checks activos en runtime y no pudo verificarse por ejecucion en este entorno.

### 5) Respuestas de error posibles

Codigo potencial: 503 Service Unavailable

Cuando ocurre:

- Al menos un health check reporta estado no saludable.

Ejemplo de cuerpo:

```json
{
  "status": "DOWN",
  "checks": []
}
```

Pregunta abierta:

- No se pudo verificar por ejecucion local el contenido exacto del body para estados DOWN.

### 6) Notas de seguridad relevantes

- Este endpoint suele exponerse para observabilidad; conviene limitar su exposicion por red/perimetro segun el entorno.
- No se observaron controles de autenticacion especificos en este codigo para /q/health.

## Documentos y fuentes consultadas

- README.md
- REGLAS.md
- docs/GOOD_PRACTICES.md
- HALLAZGOS.md
- pom.xml
- src/main/resources/application.properties
- src/main/java/com/bcp/workshop/auth/AuthResource.java
- src/main/java/com/bcp/workshop/auth/LoginRequest.java
- src/main/java/com/bcp/workshop/auth/LegacyDatabase.java
- src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java
