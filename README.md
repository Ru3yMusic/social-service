# social-service

Microservicio de funcionalidades sociales de **RUBY MUSIC**. Gestiona amistades entre usuarios, seguimiento de artistas y reportes de contenido. Es **productor Kafka** para notificaciones en tiempo real de solicitudes y aceptaciones de amistad, y para actualizar contadores de seguidores en `catalog-service`.

---

## Responsabilidad

- Solicitudes de amistad con flujo de estados `PENDING → ACCEPTED | REJECTED | REMOVED`
- Soft delete de amistades (soporta "Deshacer eliminar amigo")
- Seguir/dejar de seguir artistas (idempotente)
- Reportes de comentarios, canciones y usuarios con flujo admin `PENDING → REVIEWED | DISMISSED`
- Publicación de eventos Kafka para notificaciones realtime y contadores en catalog-service

---

## Stack

| Componente | Versión |
|---|---|
| Java | 21 |
| Spring Boot | 3.2.5 |
| Spring Cloud | 2023.0.1 |
| Spring Data JPA | — |
| Spring Kafka | — (productor) |
| MapStruct | 1.5.5.Final |
| Lombok | — |
| SpringDoc OpenAPI | 2.5.0 |
| OpenAPI Generator (Maven plugin) | 7.4.0 |

---

## Puerto

| Servicio | Puerto |
|---|---|
| social-service | **8085** |
| Acceso vía gateway | `http://localhost:8080/api/v1/social` |

---

## Base de datos

| Parámetro | Valor |
|---|---|
| Engine | PostgreSQL |
| Database | `social_db` |
| Host | `localhost:5432` |
| DDL | `update` (Hibernate auto-schema) |

### Entidades

| Tabla | Clave primaria | Descripción |
|---|---|---|
| `friendships` | UUID | Relación bidireccional requester ↔ addressee, soft delete |
| `artist_follows` | Composite `(user_id, artist_id)` | Seguimiento de artistas |
| `reports` | UUID | Reportes con `target_id` como String (cross-service) |

---

## Endpoints

Las interfaces de controller se generan desde `src/main/resources/openapi.yml`. Todos los endpoints requieren el header `X-User-Id` propagado por el api-gateway.

### Amistades

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/friends/request` | Enviar solicitud de amistad |
| `GET` | `/friends` | Listar amigos confirmados |
| `GET` | `/friends/requests/pending` | Solicitudes pendientes recibidas |
| `POST` | `/friends/{friendshipId}/accept` | Aceptar solicitud |
| `POST` | `/friends/{friendshipId}/reject` | Rechazar solicitud |
| `DELETE` | `/friends/{friendshipId}` | Eliminar amistad (soft delete) |

### Seguir artistas

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/artists/{artistId}/follow` | Seguir artista (idempotente) |
| `DELETE` | `/artists/{artistId}/follow` | Dejar de seguir |
| `GET` | `/artists/{artistId}/follow/status` | Verificar si sigue al artista |
| `GET` | `/artists/following` | Lista de IDs de artistas seguidos |

### Reportes

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/reports` | Crear reporte de comentario/canción/usuario |
| `GET` | `/reports?status=PENDING` | Listar reportes por estado (admin) |
| `PATCH` | `/reports/{reportId}/status` | Actualizar estado del reporte (admin) |

---

## Kafka — Productor

| Topic | Cuándo se emite | Consumidor | Efecto |
|---|---|---|---|
| `user.friend.request` | Al enviar solicitud de amistad | realtime-service | Notificación `FRIEND_REQUEST` al destinatario |
| `user.friend.accepted` | Al aceptar solicitud | realtime-service | Notificación `FRIEND_ACCEPTED` al solicitante |
| `artist.followed` | Al seguir un artista | catalog-service | `artist.followers_count + 1` |
| `artist.unfollowed` | Al dejar de seguir | catalog-service | `artist.followers_count - 1` |

### Payloads de eventos

```json
// user.friend.request
{ "requesterId": "uuid", "addresseeId": "uuid", "friendshipId": "uuid",
  "requesterUsername": "string", "requesterPhotoUrl": "string" }

// user.friend.accepted
{ "requesterId": "uuid", "addresseeId": "uuid", "friendshipId": "uuid",
  "addresseeUsername": "string", "addresseePhotoUrl": "string" }

// artist.followed / artist.unfollowed
"uuid-del-artista"
```

> Los datos de usuario (`username`, `photoUrl`) se obtienen de los headers `X-Display-Name` y `X-Profile-Photo-Url` propagados por el api-gateway desde el JWT.

---

## Reglas de negocio

### Amistades
- Relación **bidireccional** — la tabla guarda quién envió la solicitud (`requester_id`) y quién la recibe (`addressee_id`)
- Estado inicial: `PENDING` → solo puede pasar a `ACCEPTED`, `REJECTED` o `REMOVED`
- Solo el **addressee** puede aceptar o rechazar una solicitud
- Eliminar amistad es **soft delete** (`deleted_at` + status `REMOVED`) — soporta "Deshacer"
- No se puede enviar solicitud a uno mismo
- Constraint único `(requester_id, addressee_id)` — no se permiten solicitudes duplicadas

### Artistas
- `followArtist()` es **idempotente** — no lanza error si ya sigue al artista
- La clave primaria compuesta `(user_id, artist_id)` previene duplicados a nivel de BD

### Reportes
- `target_id` es `String` (no UUID) — puede ser un `ObjectId` de MongoDB (comentarios de `realtime-service`) o un UUID (canciones/usuarios)
- Flujo admin: `PENDING → REVIEWED | DISMISSED`
- No hay restricción de un solo reporte por usuario/contenido a nivel de servicio

---

## Estructura del proyecto

```
social-service/
├── src/
│   ├── main/
│   │   ├── java/com/rubymusic/social/
│   │   │   ├── SocialServiceApplication.java
│   │   │   ├── controller/
│   │   │   │   ├── FriendshipsController.java      ← implements FriendshipsApi
│   │   │   │   ├── ArtistFollowsController.java    ← implements ArtistFollowsApi
│   │   │   │   └── ReportsController.java          ← implements ReportsApi
│   │   │   ├── exception/
│   │   │   │   └── GlobalExceptionHandler.java
│   │   │   ├── mapper/
│   │   │   │   ├── FriendshipMapper.java            ← MapStruct
│   │   │   │   └── ReportMapper.java                ← MapStruct
│   │   │   ├── model/
│   │   │   │   ├── Friendship.java                  ← soft delete, unique(requester, addressee)
│   │   │   │   ├── ArtistFollow.java                ← @EmbeddedId (userId, artistId)
│   │   │   │   ├── Report.java                      ← targetId como String
│   │   │   │   ├── enums/
│   │   │   │   │   ├── FriendshipStatus.java        ← PENDING|ACCEPTED|REJECTED|REMOVED
│   │   │   │   │   ├── ReportStatus.java             ← PENDING|REVIEWED|DISMISSED
│   │   │   │   │   └── ReportTargetType.java        ← COMMENT|SONG|USER
│   │   │   │   └── id/
│   │   │   │       └── ArtistFollowId.java           ← @Embeddable (userId, artistId)
│   │   │   ├── repository/
│   │   │   │   ├── FriendshipRepository.java
│   │   │   │   ├── ArtistFollowRepository.java
│   │   │   │   └── ReportRepository.java
│   │   │   └── service/
│   │   │       ├── FriendshipService.java
│   │   │       ├── ArtistFollowService.java
│   │   │       ├── ReportService.java
│   │   │       └── impl/
│   │   │           ├── FriendshipServiceImpl.java   ← publica Kafka events
│   │   │           ├── ArtistFollowServiceImpl.java ← publica Kafka events
│   │   │           └── ReportServiceImpl.java
│   │   └── resources/
│   │       ├── application.yml                      ← nombre + import config-server
│   │       └── openapi.yml                          ← contrato OpenAPI 3.0.3 completo
│   └── test/
│       └── java/com/rubymusic/social/
│           └── SocialServiceApplicationTests.java
└── pom.xml
```

---

## Manejo de errores

| Excepción | HTTP | Causa típica |
|---|---|---|
| `NoSuchElementException` | `404 Not Found` | Friendship o report no encontrado |
| `IllegalArgumentException` | `400 Bad Request` | Usuario no autorizado para la operación |
| `IllegalStateException` | `400 Bad Request` | Estado de transición inválido |
| `DataIntegrityViolationException` | `409 Conflict` | Solicitud de amistad ya existe |
| `Exception` (genérico) | `500 Internal Server Error` | Error inesperado |

---

## Variables de entorno

Inyectadas desde `config-server` (`config/social-service.yml`):

| Variable | Descripción | Default |
|---|---|---|
| `DB_USERNAME` | Usuario PostgreSQL | `postgres` |
| `DB_PASSWORD` | Contraseña PostgreSQL | `password` |

---

## Build & Run

```bash
# Build (genera interfaces y DTOs desde openapi.yml)
mvn clean package -DskipTests

# Run
mvn spring-boot:run

# Test
mvn test -Dtest=SocialServiceApplicationTests
```

> Requiere `discovery-service`, `config-server`, PostgreSQL en `localhost:5432` con `social_db` creada, y Kafka en `localhost:9092`.
