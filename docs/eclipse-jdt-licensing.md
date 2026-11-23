# Eclipse JDT licensing and distribution review

JD-GUI Duo is distributed under the GNU General Public License version 3 (GPLv3). The `services` module directly depends on the following Eclipse Java Development Tools (JDT) components:

| Maven coordinate | Declared version |
| --- | --- |
| `org.eclipse.jdt:org.eclipse.jdt.core` | `3.47.0` |
| `org.eclipse.jdt:org.eclipse.jdt.core.manipulation` | `1.25.0` |

These Eclipse components are distributed under the Eclipse Public License version 2.0 (EPL-2.0), and resolved Eclipse Platform libraries may also carry EPL-2.0 terms. The exclusions in `services/pom.xml` do not remove the obligations for components that remain in the runtime distribution.

## Compatibility issue

EPL-2.0 and GPLv3 are not automatically compatible for combined distribution merely because both licenses are open source. We must not assume that Eclipse JDT artifacts carry the optional GPL secondary-license designation. JD-GUI Duo is derived from the original JD-GUI, so a GPLv3 section 7 linking exception can apply to its GPL-covered contributions only when the relevant copyright holders authorize that additional permission. A project maintainer cannot grant it on behalf of unrelated copyright holders.

## Release review

Before claiming that the combined application can be distributed under a GPLv3 linking exception:

1. Identify the copyright holders and licensing terms for the original JD-GUI code and subsequent contributions. Obtain any permissions needed for a GPLv3 section 7 exception covering the linked Eclipse JDT and necessary EPL-2.0 Eclipse Platform components. Do not describe the exception as granted until the authorization is established.
2. Resolve the actual `services` runtime dependency tree and record the licenses of the JDT and Eclipse Platform artifacts included in the application. Review other bundled dependencies independently.
3. Inspect the Windows, Linux, and macOS release archives, including the libraries copied into each distribution, to ensure that applicable third-party copyright and license notices, source availability, and redistribution requirements are met.
4. If the necessary linking permission cannot be obtained, evaluate separating the EPL-2.0 functionality into an independently distributed component or another legally reviewed arrangement rather than claiming compatibility.

This document records a known licensing concern; it is **not** a license grant, a declaration of compliance, or a substitute for reviewing the actual release artifacts.
