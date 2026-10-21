# Testing del Backend

## Verificacion de HU-01 implementada (2026-10-21)

- Comando desde el backend: `mvn -Dquarkus.http.test-port=0 verify`, con
    `--add-opens=java.base/java.lang=ALL-UNNAMED` temporal en JAVA_TOOL_OPTIONS,
    restaurado al terminar. Resultado: BUILD SUCCESS; 23 tests, 0 failures,
    0 errors, 0 skipped.
- Se reutiliza la caracterizacion HTTP y se agregan pruebas de client,
    firma/claims/expiracion determinista con Nimbus, configuracion de clave,
    captura real de logs con fixtures y smoke del contrato GET /q/health.
- Cobertura INSTRUCTION observada en target/site/jacoco/jacoco.xml:
    AuthResource 100%, SignedTokenIssuer 89.32%, TokenConfiguration 100% e
    IssuedToken 100%. Las clases modificadas superan 85% en esta metrica.
- El artefacto de produccion construido con `mvn -Dquarkus.http.test-port=0
    package quarkus:build` rechazo arranque sin AUTH_TOKEN_SECRET y con fixture
    corto (exit=1 en ambos casos). No se imprimieron valores de secretos.
- Java 25 produjo errores de instrumentacion de JaCoCo para clases Java
    24/25. Aunque las pruebas pasan y se genera reporte del proyecto, queda
    pendiente repetir con Java 21; no se desactivo JaCoCo. CodeQL y SCA no
    se ejecutaron localmente y quedan pendientes de CI.
- Los bloques siguientes conservan la historia de caracterizacion y una
    referencia previa de otro checkout; no reemplazan estos resultados.

## Baseline aprobado para HU-01 (2026-10-21)

Se preserva HTTP 500 con `message` y `exception`, de acuerdo con
`docs/ENDPOINTS.md` y el cambio `hu-01-signed-auth-token`. `message` conserva
el mensaje de la excepcion, o `Unknown error` si es nulo; `exception`
conserva el nombre completo de su clase. Esta compatibilidad no implica
que exponer detalles internos sea recomendable; cambiarla requiere otro
alcance aprobado.

La caracterizacion actual reutiliza `AuthResourceTest` y agrega una
alternativa CDI en `src/test/java/com/bcp/workshop/auth/CharacterizationDatabase.java`.
La ejecucion de `mvn -Dtest=AuthResourceTest -Dquarkus.http.test-port=0 test`,
con apertura modular temporal de JVM registrada en `tasks.md`, paso 8 pruebas
sin fallos, errores ni omisiones. Java 25 provoco errores de instrumentacion
de JaCoCo: la cobertura queda pendiente de validacion con Java 21.

Los ejemplos restantes y los resultados de 18 pruebas y cobertura mas abajo
son una referencia previa de otro checkout, no evidencia de esta ejecucion.
No se implementan ni se validan pruebas adicionales de health en este paso.

## Matriz de cobertura de endpoints

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| POST /auth/login | Credenciales validas (student/Workshop123!) | Camino feliz | 200, token no nulo, user.username=student |
| POST /auth/login | Login sin campo opcional client | Caso borde | 200, mismo contrato de respuesta que camino feliz |
| POST /auth/login | Request null | Error documentado (400) | 400, body.message="Request is null" |
| POST /auth/login | username ausente | Error documentado (400) | 400, body.message="Username is required" |
| POST /auth/login | username en blanco | Caso borde | 400, body.message="Username is required" |
| POST /auth/login | password ausente | Error documentado (400) | 400, body.message="Password is required" |
| POST /auth/login | password en blanco | Caso borde | 400, body.message="Password is required" |
| POST /auth/login | credenciales invalidas | Error documentado (401) | 401, body.message="Invalid user <username> or password" |
| POST /auth/login | excepcion no controlada durante el login | Error documentado (500) | 500 con message de la excepcion (o Unknown error) y exception con el nombre de su clase |
| GET /q/health | Estado saludable por defecto | Camino feliz | 200, status="UP", checks presente |
| GET /q/health | Query param inesperado | Caso borde | 200, status="UP" |
| GET /q/health | Readiness forzada a DOWN | Error documentado (503) | 503, status="DOWN", checks presente |

## Codigo de tests

### Archivos

- src/test/java/com/bcp/workshop/auth/AuthResourceTest.java
- src/test/java/com/bcp/workshop/auth/HealthEndpointTest.java
- src/test/java/com/bcp/workshop/auth/TestHealthToggle.java

### Test de login (fragmentos agregados)

```java
@Test
void logsInWithoutOptionalClientField() {
    given()
            .contentType("application/json")
            .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
            .when().post("/auth/login")
            .then()
            .statusCode(200)
            .body("user.username", is("student"))
            .body("token", notNullValue());
}

@Test
void returns400WhenUsernameIsBlank() {
    given()
            .contentType("application/json")
            .body("{\"username\":\"   \",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
            .when().post("/auth/login")
            .then()
            .statusCode(400)
            .body("message", is("Username is required"));
}

@Test
void returns400WhenPasswordIsBlank() {
    given()
            .contentType("application/json")
            .body("{\"username\":\"student\",\"password\":\" \",\"client\":\"angular-workshop\"}")
            .when().post("/auth/login")
            .then()
            .statusCode(400)
            .body("message", is("Password is required"));
}

@Test
void mapsDatabaseExceptionToExistingErrorResponse() {
    given()
            .contentType("application/json")
            .body(loginRequest("characterization-failure", "characterization-fixture"))
            .when().post("/auth/login")
            .then()
            .statusCode(500)
            .contentType("application/json")
            .body("$", aMapWithSize(2))
            .body("message", is("Characterization database failure"))
            .body("exception", is("java.lang.IllegalStateException"));
}
```

### Test de health (archivo completo)

```java
@QuarkusTest
class HealthEndpointTest {
    @AfterEach
    void clearHealthOverride() {
        TestHealthToggle.forceDown(false);
    }

    @Test
    void healthEndpointReturnsUpInHappyPath() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"))
                .body("checks", notNullValue());
    }

    @Test
    void healthEndpointIgnoresUnexpectedQueryParamAsEdgeCase() {
        given()
                .queryParam("source", "integration-test")
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"));
    }

    @Test
    void healthEndpointReturns503WhenAReadinessCheckIsDown() {
        TestHealthToggle.forceDown(true);

        given()
                .when().get("/q/health")
                .then()
                .statusCode(503)
                .body("status", is("DOWN"))
                .body("checks", notNullValue());
    }
}
```

```java
@Readiness
@ApplicationScoped
public class TestHealthToggle implements HealthCheck {
    private static final AtomicBoolean FORCE_DOWN = new AtomicBoolean(false);

    static void forceDown(boolean value) {
        FORCE_DOWN.set(value);
    }

    @Override
    public HealthCheckResponse call() {
        if (FORCE_DOWN.get()) {
            return HealthCheckResponse.down("test-readiness-toggle");
        }
        return HealthCheckResponse.up("test-readiness-toggle");
    }
}
```

## Ejecucion real

### Comando de validacion principal

- Directorio: C:/Users/Usuario/Documents/T60146-jonatan-moreno-leyva/reqflow-workspace/workshop-auth-backend
- Comando: .tools/maven/bin/mvn.cmd verify
- Codigo de salida: 0

### Resultados de tests (surefire)

- Tests run: 18
- Failures: 0
- Errors: 0
- Skipped: 0

### Cobertura (JaCoCo)

- Metodo de medicion: reporte XML generado por jacoco-maven-plugin en target/site/jacoco/jacoco.xml, agregando contadores de instruction y branch.
- Cobertura global (instruction): 94.66% (372/393)
- Cobertura global (branch): 79.41% (27/34)
- Cobertura paquete com.bcp.workshop.auth (instruction): 94.66% (372/393)
- Cobertura paquete com.bcp.workshop.auth (branch): 79.41% (27/34)

Validacion de criterio REGLAS.md (>=85% en codigo modificado): Cumplido por instruction coverage (94.66%).

## Fallos

- Ningun test en rojo en la ejecucion de verify.

## Pendientes

- Ninguno bloqueante para los endpoints documentados.

## Pruebas propuestas

- Agregar test HTTP de login con JSON malformado para cerrar la pregunta abierta de docs/ENDPOINTS.md sobre comportamiento de parser/Content-Type en runtime.
- Agregar aserciones de estructura minima de checks en /q/health para validar nombre/estado del check cuando hay DOWN.

## Documentos consultados

- docs/ENDPOINTS.md
- REGLAS.md
- docs/GOOD_PRACTICES.md
- PLAN.md
- HALLAZGOS.md
- src/main/java/com/bcp/workshop/auth/AuthResource.java
- src/main/java/com/bcp/workshop/auth/AuthService.java
- src/main/resources/application.properties
- pom.xml
