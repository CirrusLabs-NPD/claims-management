# CI

The CI pipeline is defined in `.github/workflows/ci.yml`.

It runs on every pull request targeting `main` and on every push to `main`,
reproducing what a reviewer does by hand for both services in the monorepo:

- **Backend (Java 21 / Maven):** JDK 21, `mvn -B -e compile` (deps + javac
  type-check), then `mvn -B -e test`. Maven cache (`~/.m2`) via setup-java.
- **Frontend (React / Vite):** `npm ci`, then `npm run build`
  (`tsc -b` type-check + `vite build`). npm cache via setup-node.

Any failing gate fails the build — no `continue-on-error`, no soft-fail.
