# DankTech2 maintenance contract

Target Minecraft/Paper 1.21.11+ and Java 21 bytecode. Validate the same distributable JAR on newer supported servers; a newer API compilation alone is not native/runtime proof.

Preserve all item IDs, PDC keys and types, long pack IDs, tier/count semantics, inventory arrays, owners, YAML paths, recipes and energy/transfer rates. Keep legacy data readers. Never classify a pack as deleted because its registry failed to load. Never initialize listeners or machines with an unreadable registry.

Run `python3 scripts/verify_pack_registry_safety.py` and the complete Maven tests/package. Do not skip new regression suites, hide warnings or weaken data checks to make CI green. Atomic replacement is best effort where supported, not a guarantee against every disk or power failure. Test serialization/write failures without touching live server data.

Use scoped development branches; preserve concurrent changes. Keep test-only dependencies out of the plugin JAR. Record source revisions, actual test results and remaining limits. Publish raw plugin JARs only through the coordinated release process; retain existing bundle inclusions and keep United reverse conversion out of the release gate.
