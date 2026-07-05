# iflowlab

A local run/debug **workbench for SAP CPI Groovy scripts**: author a script, feed
it an input Message, run it against a faithful clean-room mock of the CPI Message
API, and inspect the output — offline, in your browser.

Status: pre-alpha, under active development. Part of the
[iFlowMonitor](https://github.com/iflowmonitor) organization.

## Slice 1 (v0.1) — interactive run-and-inspect

- Monaco editor with Groovy syntax highlighting.
- Input panel: body, content-type, headers, properties.
- **Run** → type-aware output (XML/JSON pretty-printed, text raw, binary hex),
  header/property before→after diff, `println` + `messageLog` capture, and
  uncaught exceptions mapped to the script line.
- In-process execution on **Groovy 4.0.29** (the current CPI tenant runtime),
  local-trust / no sandbox, with a configurable timeout + hard watchdog.
- File-based workspace: scripts and `messages/<name>/` fixtures live as files.

Debugging (breakpoints/step/inspect) is slice 2; assertions/saved suites are later.
See `prd/web-workbench-slice-1.md` and `grill-me-sessions/01-web-workbench.md`.

## Build & run

Requires JDK 21+ (built/tested on GraalVM JDK 25), Maven 3.9+, and Node 20+.

```bash
mvn package                                   # builds all modules + bundles the SPA into the jar
java -jar app/target/quarkus-app/quarkus-run.jar --workspace /path/to/iflow-project
```

Then open http://127.0.0.1:8080 (binds to localhost only). `--workspace` defaults
to the current directory; `--port` overrides the port.

Dev mode (hot reload for both backend and UI):

```bash
mvn -pl app quarkus:dev
```

## Modules

| Module | What |
|---|---|
| `cpi-mock` | Clean-room `com.sap.gateway.ip.core.customdev.util.Message` + `MessageLog` |
| `engine` | Engine-agnostic run contract + `GroovyRunEngine` + output classifier |
| `app` | Quarkus + Quinoa host: `POST /run`, workspace file API, serves the SPA |
| `legacy/routing` | Archived Kotlin/Gradle routing-XSLT engine (future second engine) |
