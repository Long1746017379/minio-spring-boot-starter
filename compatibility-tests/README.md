# Compatibility tests

Integration tests that verify the starter's auto-configuration on Spring Boot 2.7.x (`boot2-it`) and Spring Boot 3.x (`boot3-it`).

Both projects are intentionally outside the root Maven reactor, so the starter must be installed to the local repository first. JDK 17 is used for both projects.

## Run

```bash
mvn clean install -DskipTests
mvn -f compatibility-tests/boot2-it test
mvn -f compatibility-tests/boot3-it test
```

- `boot2-it`: Spring Boot 2.7.18, covers the `spring.factories` registration path.
- `boot3-it`: Spring Boot 3.4.1, covers the `AutoConfiguration.imports` registration path.

The tests only exercise bean registration and configuration flags; they do not connect to a real MinIO server.
