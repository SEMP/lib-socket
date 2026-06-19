# lib-socket — Documentación del proyecto

## 1. Rol en SAMR2

Librería de **manejo de socket** del sistema SAMR2 — capa de conexión sobre la que
los drivers IEC 62056-21 establecen comunicación con los medidores (servidores y
drivers de socket / `SocketChannel`, lectura de datos, configuración de cliente y
servidor). Es una reescritura SAMR2 de la utilidad equivalente de SAMR1.
Coordenada Maven: `py.com.semp:lib.socket`, consumida por `samr2-adq`.

## 2. Stack / tecnologías

- **Java 17**, proyecto **modular (JPMS)** — tiene `module-info.java`.
- **Maven.** Coordenada `py.com.semp:lib.socket:2.1.2` (packaging `jar`,
  `<name>lib-socket</name>`).
- Plugins: `maven-compiler-plugin` 3.13.0, `maven-surefire-plugin` 3.2.5.
- `module-info.java`: módulo `lib_socket`; `exports` los paquetes `…socket`,
  `.configuration`, `.readers`, `.drivers`; `requires transitive lib_utilidades`.

## 3. Estructura del repo

El `pom.xml` está **anidado** en `lib_socket/pom.xml` (no en la raíz del repo).
Código en `lib_socket/src/main/java/py/com/semp/lib/socket/`, recursos en
`lib_socket/src/main/resources/`, tests en `lib_socket/src/test/java/`.

Paquete base `py.com.semp.lib.socket`, con clases reales:

- raíz: `SocketChannelServer`, `SocketChannelServerEjemplo`, `SocketServerEjemplo`
- `.configuration`: `ClientSocketConfiguration`, `ServerSocketConfiguration`, `Values`
- `.drivers`: `SocketDriver`, `SocketChannelDriver`
- `.readers`: `SocketChannelDataReader`
- `.exceptions`: `DataReaderSetException`
- `.internal`: `MessageUtil`, `Messages`

Recursos i18n (base name `messages`) en
`lib_socket/src/main/resources/py/com/semp/lib/socket/`: `messages.properties`,
`messages_en.properties`, `messages_es.properties`.

## 4. Build, ejecución y tests

Es una **librería** (no se ejecuta).

- **Build:** `mvn -f lib_socket/pom.xml package`.
- **Tests:** JUnit 5 en `lib_socket/src/test/java/...` — `MessageUtilTest`,
  `ConnectionTest`, `SocketConfigurationTest` (corren con Surefire).

## 5. Dependencias e integraciones

- **Depende de** `py.com.semp:lib.utilidades` (`requires transitive`); usa
  `py.com.semp.lib.utilidades.messages.{MessageKey,MessageManager,MessageRetriever}`
  y `…configuration.Values`. (junit-jupiter es de `scope=test`.)
- **Quién la consume:** `samr2-adq` la declara como dependencia Maven
  (`py.com.semp:lib.socket`) resuelta desde su `local-maven-repo/` (jar versionado
  en git).
- **Origen:** reescritura SAMR2; remote en **GitHub público**
  (`git@github.com:SEMP/lib-socket.git`).

## 6. Ramas y versionado

- Rama principal: `main` — **ya contiene la migración a Maven** (la rama
  `feat/migracion-maven` fue fusionada). También existe una rama codex de mensajes.
- **No hay archivos `version-*.txt`.** La versión es única y vive en el `pom.xml`
  (`2.1.2`).

## 7. Gotchas / notas conocidas

- **ResourceBundle / path dotted (AB#42116) — ya corregido.**
  `Values.Constants.MESSAGES_PATH` usa la forma *dotted*
  `"py.com.semp.lib.socket."`. La forma anterior con `/` inicial
  (`"/py/com/semp/lib/socket/"`) funcionaba en module-path pero rompía en classpath
  (Spring Boot `JarLauncher` del `samr2-adq`). **Usar siempre la forma dotted.** Ver
  walkthrough `docs/walkthroughs/20260527-1345_fix-messages-path-dotted.md` y la
  memoria `samr2-agent/memorias/resourcebundle-jpms-classpath-gotcha.md`.
- Tras cambiar esta lib, refrescar el jar del consumidor con
  `samr2-adq/scripts/actualizar-deps-locales.sh`.

## 8. Referencias

- `lib_socket/pom.xml` — coordenadas y dependencias.
- `lib_socket/src/main/java/py/com/semp/lib/socket/internal/MessageUtil.java`
- `lib_socket/src/main/java/py/com/semp/lib/socket/configuration/Values.java`
- `lib_socket/src/main/resources/py/com/semp/lib/socket/messages*.properties`
- `docs/walkthroughs/20260527-1345_fix-messages-path-dotted.md`
- Canónico: `samr2-agent/CLAUDE.md`; memoria `resourcebundle-jpms-classpath-gotcha.md`.
