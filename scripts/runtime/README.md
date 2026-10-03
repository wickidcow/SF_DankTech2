# Native Paper pack-preservation probe

This disposable integration probe runs the supplied DankTech2 and Slimefun JARs on
a real Paper server. It complements the Maven regression suites; it does not use
MockBukkit. It requires Java 25 and an already bootstrapped disposable Paper 26.3
server directory containing `libraries`, `cache`, `versions`, `world`, and `config`.
The runner copies these into a **new** output directory, binds only to loopback
with an ephemeral port, and does not download dependencies or touch the source
server. Use an existing test server as the cache, never a production server.

```bash
python3 scripts/runtime/run_probe.py \
  --dank-jar /absolute/path/SF_DankTech21.1.03.jar \
  --slimefun-jar /absolute/path/Slimefun-Legacy4.1.67.jar \
  --paper-jar /absolute/path/paper-26.3-143.jar \
  --cached-server /absolute/path/disposable-paper-cache \
  --java-home /absolute/path/jdk-25 \
  --output /absolute/path/new-disposable-proof \
  --gui-guards \
  --legacy-alias-restart \
  --unreadable-item-restart
```

The output directory must not already exist. The runner compiles only its helper
plugin against the supplied runtime libraries; it does not rebuild DankTech2.
`manifest.json` records the exact candidate, Slimefun, Paper and helper JAR/source
digests, Java version, phase results and assertion counts. Each phase has a server
log and a separate `probe-result-*.txt`; failures keep all fixtures and logs.

## Checks and limits

1. The first server process invokes the actual upgrade and admin-clone methods.
   It checks all Trash tiers, full 64-bit IDs, raw filter payloads, rich nested PDC,
   malformed-item refusal, full/partial native inventory refusal, rollback on
   failed registration, and delivery/save/deletion order with the real registry.
   It saves a clone, upgraded Trash Pack and maximum-ID pack in a real chest.
2. A clean restart checks exact chest items, the unchanged historical Trash
   filter byte array, Slimefun item resolution, and both full items from the
   reread DankTech2 registry. Expected items are canonicalized through native
   byte serialization, with all serialized components and PDC equality checked;
   registry writes deliberately receive template subclasses to cover crafting.
3. The optional legacy-alias restart replaces ordinary item aliases in the test
   registry with the historical `SlimefunItemStack` alias, then requires the same
   exact registry and world-item assertions after a real restart.
4. The optional unreadable-item startup injects an unknown item alias. It requires
   DankTech2 to remain disabled, with no initialized registry singleton, listeners,
   scheduled tasks, packs or machines. The original registry must remain byte-for-
   byte unchanged through server shutdown; the manifest records both digests.

With `--gui-guards`, actual menu instances created before replacement are exercised
after the old pack has been invalidated. These checks use a **tracked Player
interface proxy** that permits only the refusal message and view closure and
fails on any cursor or inventory access. They cover stale admin opening,
empty/existing-slot deposits, withdrawals, inventory transfer and the last-item
clear path. They do not claim a connected-player or client-rendering test. The
Maven suites separately cover player inventories and building from empty slots.

Optional external services may log connection failures in an offline test
environment. The unreadable-item phase intentionally produces an addon startup
error; its success condition is safe refusal, not an error-free log. A passing
probe is evidence for these paths on the exact supplied runtime, not a blanket
compatibility or crash-consistency guarantee.
