# Eclipse JDT licensing: decision record

## Verified facts

JD-GUI Duo is a fork of the original JD-GUI, which is licensed under GNU General Public License version 3 or later (GPLv3-or-later). Its upstream license identifies Emmanuel Dupuy as a copyright holder. JD-GUI Duo's `services` module directly includes:

| Dependency | Version | License identified upstream |
| --- | --- | --- |
| `org.eclipse.jdt:org.eclipse.jdt.core` | `3.47.0` | Eclipse Public License 2.0 (EPL-2.0) |
| `org.eclipse.jdt:org.eclipse.jdt.core.manipulation` | `1.25.0` | EPL-2.0 |

The dependency is functional, not incidental: source files in `services` use Eclipse JDT abstract syntax trees, indexing, parser infrastructure, and cleanup-related code. `app` also imports Eclipse JDT abstract syntax tree APIs. Removing the dependencies would require replacing these functions, not just changing Maven metadata.

The `assembler` module copies runtime dependencies into the distributed application and creates Windows, Linux, and macOS archives. Consequently, this question concerns actual combined redistribution, not merely compiling against a library.

## Licensing determination

EPL-2.0 is **not GPL-compatible by default**. Its optional GPL secondary-license mechanism applies only when the initial contributor explicitly designates it; the presence of EPL-2.0 text alone is not such a designation. The Eclipse JDT upstream notice identifies EPL-2.0, and no applicable secondary-license grant has been established for the exact distributed JDT artifacts.

A GPLv3 section 7 linking permission for the application may offer a route, but only copyright holders can authorize additional permissions for their respective GPL-covered contributions. The original JD-GUI copyright notice makes it unsafe to assert that this project's maintainer alone can grant such an exception to all inherited code. An exception written into this repository without that authorization would not settle the problem.

Primary references:

- [Original JD-GUI license](https://github.com/java-decompiler/jd-gui/blob/master/LICENSE)
- [Eclipse JDT upstream notice](https://github.com/eclipse-jdt/eclipse.jdt.core/blob/master/NOTICE)
- [EPL-2.0 section 3.2 and Exhibit A](https://www.eclipse.org/legal/epl/epl-v20.html)
- [Free Software Foundation license compatibility analysis](https://www.gnu.org/licenses/license-list.html)

## Paths to a durable resolution

**Preferred if we retain the current Eclipse-powered features:** Obtain written permission from the relevant copyright holders of inherited and subsequently contributed GPL-covered code to distribute JD-GUI Duo linked with the specified EPL-2.0 Eclipse components under an appropriately scoped GPLv3 section 7 additional permission. Identify the contributions covered by each authorization, preserve evidence of consent, and obtain legal review of the exact text before publishing it. Independently verify the license of each transitive dependency and its redistribution obligations. This is not a step that can be completed by a source-code change alone.

**Alternative without copyright-holder permissions:** Replace Eclipse JDT integration with independently implemented or suitably GPL-compatible parser, indexing, reference-navigation, and cleanup functionality. Remove the Eclipse dependencies and their transitive artifacts from all distributed archives, and verify feature parity with integration tests. Simply loading the same EPL libraries through a separate class loader, executable, or downloadable plugin does not by itself establish license compatibility; any separation architecture requires its own review.

**Another possible route:** Request a qualifying GPL secondary-license designation from the relevant Eclipse JDT copyright holders for the exact components and versions. This is not currently evidenced and should not be assumed.

## Release acceptance criteria

1. Record the copyright ownership and permission basis for GPL-covered original and fork contributions, **or** remove the incompatible combined dependency from the distributed product.
2. Generate the resolved runtime dependency tree for `services` and `app`; identify every shipped Eclipse artifact and its actual license and notices. Verify the exact published artifact versions rather than relying solely on upstream default-branch documentation.
3. Inspect the final Windows, Linux, and macOS distributions and include the required third-party licenses, attribution, source-availability information, and applicable corresponding source materials.
4. Verify the packaged output and functionality in continuous integration. Do not mark a release compliant solely because Maven resolves successfully or a notice file exists.

**Current disposition:** unresolved pending copyright-holder authorization or a validated replacement of the EPL dependency. This document neither grants an exception nor certifies redistribution compliance.
