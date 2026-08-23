# Trust, Security and Support

This page collects the policies and evidence needed to evaluate Radixor as a
production dependency. It distinguishes published project commitments from
assurances that require a separate support agreement.

## Current supported releases

The latest published release of each runtime is supported:

| Runtime | Package or coordinate | Supported release |
|---|---|---|
| Java | `org.egothor:radixor` | Latest published release |
| Python (PyO3) | `radixor` | Latest published release |
| Python-C | `radixor-c` | Latest published release |

Older releases are supported only under a separate contract. The public
project does not promise an extended-support period or response-time SLA for
superseded releases.

## Versioning across runtimes

Radixor runtimes share a project versioning model without requiring identical
patch numbers:

- the **major** number identifies major change affecting the project as a whole,
  including architectural change;
- the **minor** number identifies a shared fix or improvement affecting all
  runtimes;
- the **patch** number identifies fixes and improvements local to one runtime.

Java, Python/PyO3 and Python-C therefore maintain independent patch streams.
Releases with the same major and minor components belong to the same shared
project line even when their runtime-specific patch components differ. PyO3
and Python-C patch numbers may also diverge. Model artifacts and the
model catalog are versioned independently from runtime software.

## Support and ordinary defects

[GitHub Issues](https://github.com/leogalambos/Radixor/issues) is the public
support channel for usage questions, integration problems, documentation
problems and ordinary defects. Include the runtime, exact version, operating
system, reproduction steps, expected result and observed result when relevant.

Support for older releases, private operational assistance, or contractual
service levels requires a separate agreement.

## Report security issues privately

Do **not** put vulnerability details in a public issue. Submit suspected
security problems through
[GitHub private vulnerability reporting](https://github.com/leogalambos/Radixor/security/advisories/new).
Include the affected runtime and version, reproduction conditions, potential
impact and any known mitigation. Public disclosure should follow coordinated
assessment and remediation.

Radixor has no network service, authentication layer or authorization model,
but it can still have security-relevant defects. Its trust boundaries include:

- malformed, hostile or excessively large dictionaries and compiled models;
- decompression and resource-exhaustion behavior;
- memory safety in native Python runtimes;
- parser, serialization and binary-format validation;
- package, model and build-pipeline supply-chain integrity.

Treat dictionaries and compiled models as controlled inputs. Package manifests
and SHA-256 checks detect accidental corruption; they do not authenticate a
malicious model provider.

## Published engineering evidence

The [Reports and Published Build Artifacts](reports.md) page links to the latest
test, static-analysis, coverage, mutation-testing, dependency-vulnerability and
SBOM reports. Python releases additionally publish checksums and GitHub artifact
attestations; installation guidance explains how to verify them.

These reports are evidence tied to a particular build. They do not replace an
application-specific threat model, dependency review or acceptance test.

## Compatibility and upgrades

The [Compatibility and Guarantees](compatibility-and-guarantees.md) page defines
the supported Java API and behavioral boundaries. Because model releases may
change stemming outcomes independently of runtime releases, production upgrades
should pin both runtime and model versions and run application vocabulary and
search-relevance regression tests.

## Accessibility and documentation feedback

The documentation aims to provide text or tabular evidence for material claims
shown in charts, keyboard-accessible navigation, visible focus indication and
responsive layouts. Accessibility defects are documentation defects and should
be reported through [GitHub Issues](https://github.com/leogalambos/Radixor/issues).
The project does not currently claim certification against a particular WCAG
conformance level.
