# Fix `MESSAGES_PATH` con `/` inicial — bump `2.1.1 → 2.1.2`

**Fecha:** 2026-05-27 · **ADO:** AB#42116

## Cambio

`Values.Constants.MESSAGES_PATH`: `"/py/com/semp/lib/socket/"` → `"py.com.semp.lib.socket."`.

Mismo root cause documentado en el walkthrough central: el `/` inicial rompe `ResourceBundle.getBundle()` cuando el JAR viaja por classpath (Spring Boot `JarLauncher` del `samr2-adq`).

## Archivos

- `lib_socket/src/main/java/py/com/semp/lib/socket/configuration/Values.java`: constante a dotted.
- `lib_socket/src/test/java/py/com/semp/lib/socket/internal/MessageUtilTest.java`:
  - `testResource`: ajuste análogo al de utilidades.
  - `testResourceBundle`: bundle name pasa a `py.com.semp.lib.socket.messages`.
  - `testResourceBundle2`: eliminado (duplicaba con la forma vieja leading-slash, que era precisamente el bug).
- `lib_socket/pom.xml`: `<version>` `2.1.1 → 2.1.2`, `<lib.utilidades.version>` `2.1.1 → 2.1.2`.

## Verificación

- `mvn clean install`: 10 tests OK, `lib.socket-2.1.2.jar` instalado.
- Reproducción E2E con `samr.driver.clou-2.1.2.jar` + `lib.socket-2.1.2.jar` cargando desde classpath: `MessageUtil.getMessage(...)` con locale `en_US` ahora devuelve el string traducido en lugar de tirar `MissingResourceException`.

## Walkthrough central

`samr2-tareas/walkthroughs/20260527-1345_fix-i18n-messages-path-leading-slash.md`.
