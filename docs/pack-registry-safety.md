# Pack registry preservation

A failed dank_packs.yml load previously printed an exception and returned an empty or partial registry. Normal pickup/open/unloader code treats an absent registry entry as a deleted pack. Autosave/shutdown could subsequently replace the original file with that incomplete registry.

This change strictly loads the existing YAML before registering any Slimefun item, listener, Doctor or scheduled task. A read/parse failure stops startup and does not expose a usable ConfigManager singleton. The original invalid file is retained for operator recovery; no automatic repair, empty fallback or deletion-map reset is performed. Valid empty files remain supported for fresh installations.

Normal saves serialize before opening output, stage a complete sibling temporary file, flush its bytes and replace the destination atomically where supported. Only AtomicMoveNotSupportedException permits the ordinary replacement fallback. Temporary files are cleaned up; existing file symlinks and POSIX mode bits are preserved. This is not a full power-loss durability guarantee or an external concurrent-writer protocol.

Pack IDs, PDC names/types, inventory arrays, amounts, owners, tiers, YAML keys, item/research definitions, recipes, rates and deletion rules for a successfully loaded registry are unchanged. Test libraries are test-only. The final baseline build now executes the 12 YAML/file regressions instead of skipping all tests.

Validation must cover normal startup/restart with old-format items and deliberate malformed-registry startup on disposable Paper instances. A stopped plugin is the intended outcome for unreadable registry data, not a reason to remove the gate. This development change is not a stable release or blanket historical-world certification.

## 1.1.03 compatibility reader — 2026-10-03

The earlier sections record the original registry safety change and its twelve-test validation. Version 1.1.03 retains those startup and staged-write boundaries and adds compatibility for the known `io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack` YAML alias. Crafted template subclasses could save that alias even though Bukkit could not deserialize it as an item after restart.

The compatibility reader recognizes that alias in registry item records and reads the existing payload using Bukkit's `ItemStack` representation. Loading does not rewrite the original file bytes. Future saves use a native item copy that preserves item components and PDC. Other unreadable item records stop startup and leave the original file available for operator repair; a missing decoded item is never silently accepted as a successful load.

The [1.1.03 validation record](validation/1.1.03/README.md) documents the expanded regression suite, exact native runtime checks, artifact digests, and the failing restart case that motivated this addition.
