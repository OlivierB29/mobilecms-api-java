# mobilecms-api-java

Java rewrite of [mobilecms-api-slim](https://github.com/OlivierB29/mobilecms-api-slim). REST API for managing JSON files and images, implemented with Spring Boot 3.

The HTTP contract is `openapi.yaml` (MobileCMS API v50). Paths are served under `/mobilecmsapi/v50`.

## Features

- JSON-file storage (no database)
- JWT authentication (per-user secret, HS512)
- BCrypt password hashing compatible with the PHP `password_hash` hashes
- Public web read API, editor CMS API, admin user API
- Media upload and thumbnail generation
- iCalendar export at `/mobilecmsapi/v50/agenda/events.ics`

## Configuration

Copy and edit `conf/development/conf.json`. Paths are resolved from `mobilecms.root-dir`:

| Property | Default | Meaning |
| --- | --- | --- |
| `MOBILECMS_ROOT` / `mobilecms.root-dir` | current directory | Working root (`DOCUMENT_ROOT` equivalent) |
| `MOBILECMS_CONF` / `mobilecms.conf-file` | `conf/development/conf.json` | JSON configuration |

Layout expected by default:

```
<root>/public/          content types, types.json, theme
<root>/media/           uploaded files
<root>/../private/      users/ and admin types (relative privatedir)
```

`jwt` may be `php-jwt` (standard HS512 JWT, default) or the legacy Slim custom HMAC token.

## Run

Java 21+.

```bash
./mvnw spring-boot:run
```

Or:

```bash
./mvnw -DskipTests package
java -jar target/mobilecms-api-java-50.0.0-SNAPSHOT.jar
```

Server port is `8080`. Example login:

`POST /mobilecmsapi/v50/authapi/authenticate` with `{ "user": "editor@example.com", "password": "..." }`.

## Tests

```bash
./mvnw test
```
