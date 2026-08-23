# Security Policy

## Supported versions

Radixor supports the latest published release of each runtime:

| Runtime | Supported release |
|---|---|
| Java | Latest published release |
| Python (PyO3) | Latest published release |
| Python-C | Latest published release |

Older releases are supported only under a separate support agreement.

## Report a vulnerability privately

Do not disclose a suspected vulnerability in a public GitHub issue. Use
[GitHub private vulnerability reporting](https://github.com/leogalambos/Radixor/security/advisories/new)
and include:

- the affected runtime, package and version;
- the conditions required to reproduce the issue;
- the potential impact;
- a minimal reproduction, if it can be shared safely;
- any known mitigation.

Ordinary defects, integration questions and documentation problems belong in
[GitHub Issues](https://github.com/leogalambos/Radixor/issues).

## Relevant security boundaries

Radixor is an in-process library and does not provide authentication, network
services or authorization. Its relevant security boundaries still include
native code, parsers, compressed dictionaries and compiled model files,
resource consumption, published packages, and the release supply chain.

Treat application-supplied dictionaries and compiled models as controlled
inputs. Checksums detect accidental corruption; they do not make a model from
an untrusted provider safe or authentic.
