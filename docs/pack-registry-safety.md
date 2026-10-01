# Pack registry preservation

A failed dank_packs.yml load previously printed an exception and returned an empty or partial registry. Normal pickup/open/unloader code treats an absent registry entry as a deleted pack. Autosave/shutdown could subsequently replace the original file with that incomplete registry.

This change strictly loads the existing YAML before registering any Slimefun item, listener, Doctor or scheduled task. A read/parse failure stops startup and does not expose a usable ConfigManager singleton. The original invalid file is retained for operator recovery; no automatic repair, empty fallback or deletion-map reset is performed. Valid empty files remain supported for fresh installations.

Normal saves serialize before opening output, stage a complete sibling temporary file, flush its bytes and replace the destination atomically where supported. Only AtomicMoveNotSupportedException permits the ordinary replacement fallback. Temporary files are cleaned up; existing file symlinks and POSIX mode bits are preserved. This is not a full power-loss durability guarantee or an external concurrent-writer protocol.

Pack IDs, PDC names/types, inventory arrays, amounts, owners, tiers, YAML keys, item/research definitions, recipes, rates and deletion rules for a successfully loaded registry are unchanged. Test libraries are test-only. The final baseline build now executes the 12 YAML/file regressions instead of skipping all tests.

Validation must cover normal startup/restart with old-format items and deliberate malformed-registry startup on disposable Paper instances. A stopped plugin is the intended outcome for unreadable registry data, not a reason to remove the gate. This development change is not a stable release or blanket historical-world certification.
