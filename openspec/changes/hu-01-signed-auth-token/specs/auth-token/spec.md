# Spec Delta

## Purpose

Define el contrato observable del token emitido por el login, sus claims y vigencia, y las garantías de manejo del secreto y de los datos de autenticación.

## ADDED Requirements

### Requirement: Login emite un token firmado con identidad y cliente
El sistema MUST emitir, tras un login válido, un token cuya firma permita verificar su autenticidad e integridad y que incluya los claims `sub`, `client`, `iat` y `exp`.

#### Scenario: Login válido incluye los claims requeridos
- **WHEN** un usuario se autentica con credenciales válidas y un `client` válido
- **THEN** la respuesta es HTTP 200 y el token tiene una firma verificable, `sub` igual al username autenticado, `client` igual al valor recibido, `iat` como instante de emisión y `exp` como instante de expiración

### Requirement: Login valida el campo client
El sistema MUST requerir un `client` presente y no vacío ni compuesto solo por espacios para emitir un token.

#### Scenario: Client ausente o en blanco
- **WHEN** una solicitud de login válida en username y password omite `client` o lo envía nulo, vacío o en blanco
- **THEN** el sistema responde HTTP 400 y no emite un token

### Requirement: El token deja de ser válido al expirar
El sistema MUST considerar expirado un token cuando el instante actual sea igual o posterior al claim `exp`.

#### Scenario: Token en o después de exp
- **WHEN** un consumidor evalúa un token cuyo claim `exp` ya se alcanzó o pasó
- **THEN** el token no se considera válido

### Requirement: expiresIn refleja el tiempo restante del token
La respuesta exitosa MUST expresar `expiresIn` en segundos y su diferencia respecto de `exp - now` MUST permanecer dentro de la tolerancia documentada para este contrato.

#### Scenario: Duración reportada coincide con exp
- **WHEN** un usuario completa un login válido
- **THEN** `expiresIn` es un entero positivo y la diferencia respecto del tiempo restante hasta `exp` no supera la tolerancia documentada

### Requirement: El login preserva el contrato de respuesta existente
Salvo por el contenido del token, la validación nueva de `client` y el valor dinámico de `expiresIn`, el sistema MUST conservar las formas de respuesta y los comportamientos documentados para `POST /auth/login`.

#### Scenario: Respuesta de login válido conserva los datos de usuario
- **WHEN** un usuario completa un login válido
- **THEN** la respuesta contiene `token`, `expiresIn`, `user` y `debug`, y `user` conserva `username`, `displayName` escapado en HTML y `role`

#### Scenario: Request o credenciales requeridas inválidas
- **WHEN** el request es nulo, o `username` o `password` es nulo, vacío o en blanco
- **THEN** el sistema responde HTTP 400 con el mensaje documentado para esa validación

#### Scenario: Credenciales inválidas
- **WHEN** el username no existe o la password no coincide
- **THEN** el sistema responde HTTP 401 con el mensaje de credenciales inválidas documentado

#### Scenario: Excepción no controlada
- **WHEN** ocurre una excepción no controlada durante el login
- **THEN** el sistema responde HTTP 500 con los campos `message` y `exception` documentados

### Requirement: El secreto por defecto queda limitado al sandbox
El sistema MUST confinar cualquier secreto de firma por defecto al sandbox de laboratorio y MUST NOT usarlo en un entorno fuera de ese sandbox.

#### Scenario: Entorno fuera del sandbox sin secreto propio
- **WHEN** el servicio opera fuera del sandbox sin un secreto de firma configurado para ese entorno
- **THEN** el servicio no emite tokens usando el secreto por defecto del laboratorio

### Requirement: Los datos sensibles de autenticación no aparecen en logs
El sistema MUST NOT registrar el secreto de firma, passwords ni tokens emitidos en claro o en forma recuperable.

#### Scenario: Login exitoso y fallido no exponen secretos
- **WHEN** se procesan logins exitosos y fallidos
- **THEN** los logs no contienen passwords, secretos de firma ni tokens emitidos en claro o en forma recuperable

### Requirement: La documentación declara la decisión de firma
La documentación del servicio MUST declarar la decisión adoptada entre HMAC y una biblioteca JWT, junto con la configuración del secreto de firma aplicable.

#### Scenario: Decisión de firma documentada
- **WHEN** se revisa la documentación operativa del servicio
- **THEN** se identifica si la firma usa HMAC o una biblioteca JWT y cómo se configura el secreto correspondiente