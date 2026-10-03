# DankTech2 1.1.03 native pack-preservation evidence

Test date: 2026-10-03. Native runtime: Paper `26.3-143-ff3655a` (Minecraft 26.3),
Java `25.0.4.1`, published Slimefun Legacy `4.1.67`.

**The final candidate passed 134 native checks across four separate server
processes.** The same candidate JAR and helper JAR were used in every phase. The
two failed intermediate candidates are retained below to show the actual defects
the native gate found before this result.

## Final candidate result

Candidate JAR: `SF_DankTech21.1.03.jar`, 188,694 bytes, SHA-256
`2921c540dae542d2932a7b4d44e93b160cb4a9fbd6739f17b413c9eed5e42407`.

| Native phase | Result | Evidence |
| --- | --- | --- |
| Upgrade/clone safety, real registry, all nine stale-menu refusal cases | PASS: 86 checks | [Assertions](candidate-first.txt), [server log excerpt](candidate-first-log.txt) |
| Clean restart: exact chest items and two registry items | PASS: 20 checks | [Assertions](candidate-second.txt), [server log excerpt](candidate-second-log.txt) |
| Restart from historical `SlimefunItemStack` registry aliases | PASS: 20 checks | [Assertions](candidate-legacy-alias.txt), [server log excerpt](candidate-legacy-alias-log.txt) |
| Unreadable-item startup: disabled addon, no handlers and unchanged file | PASS: 8 checks | [Assertions](candidate-unreadable-item.txt), [server log excerpt](candidate-unreadable-item-log.txt) |

All four processes exited with code 0 after normal server shutdown. The last phase
intentionally triggers a DankTech2 startup error and verifies that it fails safely.
Its registry SHA-256 was identical before startup and after shutdown:
`c3d554bc1907e58bfd44433e6eae3468a61044053b62ff0ba6736d4cc08fccc4`.

[`candidate-native-manifest.json`](candidate-native-manifest.json) binds every
phase to the exact inputs and helper. The single helper JAR SHA-256 is
`b355b8d33cef07091783df680afdeea9f2ceb28b02171e322340762b5066c722`,
and the helper source SHA-256 is
`45f942975a4cc4671623fd39347372c39b8e597d356c3a6c7b8ded9e11fbd001`.
The actual invocation is preserved in
[`candidate-invocation.txt`](candidate-invocation.txt), and the tested production
source hashes in [`candidate-source-sha256.json`](candidate-source-sha256.json).

The separate [`build-validation.json`](build-validation.json) records all 54
Maven tests passing with no failures, errors or skips on the restored 1.21.11 API
floor, successful clean API compatibility packages for Paper 26.2 and 26.3, and
Java 21 bytecode/provided-dependency checks. Those API packages are compilation
evidence; the four native processes above are actual Paper 26.3 runtime evidence.
An independently built CI or published JAR needs the same native check if its
bytes differ from this candidate.

## Reproducible native regression

The helper and runner are in [`scripts/runtime`](../../../scripts/runtime/README.md).
They run supplied binary JARs without rebuilding them. The probe checks actual
Paper inventories, item serialization/PDC and DankTech2 registry persistence.
The optional menu checks use tracked Player-interface proxies; no connected-player
or client-rendering result is claimed.

Exact shared runtime inputs:

| Input | SHA-256 |
| --- | --- |
| Paper 26.3 build 143 JAR | `32cf4a93545e218525bc4536b017c6b5d5085d27d449d64266a6b23ba4d0cbb9` |
| Published Slimefun Legacy 4.1.67 JAR | `011d1181c5684d048425078d519100d46fb0867f053ff4b3312fd69c9cedf816` |

## Failed intermediate candidate: preserved evidence

The first intermediate 1.1.03 candidate was compiled from base
`d9fa86457651c03d1494c1bd111671e7be93815a` plus the initial pack-preservation changes,
before the subsequent registry alias and stale-view fixes. Its JAR SHA-256 was
`bd1e2f7879a000866f65b643e89a0b67ab78cc09525d334df47aba75a3c5170a`.
It was not published by this test.

| Phase | Observed result |
| --- | --- |
| Initial native server process | PASS: 68 checks |
| Clean restart | FAIL after 13 checks: real registry reread did not retain both exact saved items |

The restart kept all three chest items exactly, including the clone, upgraded
Trash Pack and maximum-ID pack. Raw historical Trash filter bytes and all three
Slimefun item IDs also survived exactly. The failure was specific to the YAML
registry: a `SlimefunItemStack` template clone was emitted with its Java subclass
alias, and native Paper deserialized that unsupported alias as a null item. The
registry retained the two `last_user` sections but `getAllPacks()` returned zero.
This matches the real crafter's template-clone-to-`saveDankPack` path.

The first diagnostic also used subclass aliases in its expected-value YAML. The
probe now creates expected values through native `serializeAsBytes()` /
`deserializeBytes()`, and asserts that every serialized component and the full
typed PDC remain equal. This fixes only the expected-value fixture. The real
registry still receives template subclasses deliberately, and the preserved
clean run continues to fail specifically on registry item loss.

Evidence files:

- [`baseline-manifest.json`](baseline-manifest.json): exact supplied and helper digests.
- [`baseline-first.txt`](baseline-first.txt): complete successful first-phase assertions.
- [`baseline-restart.txt`](baseline-restart.txt): exact failing restart assertions and stack trace.
- [`baseline-restart-log.txt`](baseline-restart-log.txt): relevant real startup, failure and clean-shutdown log lines.
- [`baseline-registry.yml`](baseline-registry.yml): exact synthetic registry before the failing restart; contains no server-owner data.

## Native copy-constructor failure in a second intermediate candidate

The next candidate passed all 54 Maven tests and the three API compilation gates,
but native testing found a separate Paper 26.3 wrapper problem before restart.
Its JAR SHA-256 was
`08a19faf81fa7b67dfbe6ee4cba8b9499296bb6827475ef8e07112ff36d451bd`.
After 63 successful native checks, the stale Admin opening was correctly refused,
but comparison of the registry's copied item to the delivered native item threw
`ClassCastException` inside `CraftItemStack.getCraftStack`.

The exact runtime bytecode explains the failure: `new ItemStack(source)` puts
`source.clone()` directly into its native delegate field. A `SlimefunItemStack`
clone retains its subclass, while the native comparison requires that delegate
to be a `CraftItemStack`. Therefore a plain copy constructor is insufficient for
this registry writer on Paper 26.3. Native byte serialization/deserialization had
already passed the exact component and typed-PDC assertions for these fixtures.

This candidate also remains unpublished by the probe. Its evidence is retained:

- [`delegate-candidate-manifest.json`](delegate-candidate-manifest.json) and
  [`delegate-candidate-build-validation.json`](delegate-candidate-build-validation.json).
- [`delegate-candidate-first.txt`](delegate-candidate-first.txt) and
  [`delegate-candidate-first-log.txt`](delegate-candidate-first-log.txt).
- [`delegate-candidate-source-sha256.json`](delegate-candidate-source-sha256.json).
- [`paper-copy-constructor-bytecode.txt`](paper-copy-constructor-bytecode.txt).

## Scope and retained probe diagnostic

Both successful restarts retained the exact two registry items and all chest
items, including rich nested metadata, full 64-bit IDs, maximum counts and raw
Trash filter bytes. The refusal process left the addon disabled before pack,
machine, listener or task registration and kept the original registry bytes
unchanged through shutdown.

Native stale-menu checks rejected opening, deposits, withdrawals, inventory
transfer and the last-item clear path before any Player cursor or inventory API
access, keeping the original identity deleted and the replacement intact.
Maven tests separately cover mocked player inventories and empty-slot building.
These scoped results do not imply all-plugin interoperability, a connected-client
test, or protection against every disk/power failure.

An earlier helper attempt verified safe addon refusal but then could not resolve
`ConfigManager` through a disabled plugin's dependency exports. The helper was
corrected to inspect the already-loaded class using that plugin's own classloader;
no assertion was removed. The diagnostic is retained in
[`refusal-probe-diagnostic.txt`](refusal-probe-diagnostic.txt) and its
[`manifest`](refusal-probe-diagnostic-manifest.json). All four final phases were
then rerun from a fresh directory with the single corrected helper above.
