#!/usr/bin/env python3
"""Keep the fail-closed load ahead of every registered handler; these are source guards."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / 'src/main/java/io/github/sefiraat/danktech2/DankTech2.java').read_text()
manager = (root / 'src/main/java/io/github/sefiraat/danktech2/managers/ConfigManager.java').read_text()
loader = (root / 'src/main/java/io/github/sefiraat/danktech2/managers/PackRegistryFile.java').read_text()
startup = main.split('public void onEnable() {', 1)[1].split('public void onDisable()', 1)[0]
load = startup.index('this.configManager = new ConfigManager();')
for registration in ('setupSlimefun();', 'LegacyDoctorBridge.register(this);', 'new ListenerManager()', 'new RunnableManager()'):
    assert load < startup.index(registration), registration
constructor = manager.split('public ConfigManager() {', 1)[1].split('\n    }', 1)[0]
assert constructor.index('instance = null;') < constructor.index('getConfig(') < constructor.index('instance = this;')
read = manager.split('private FileConfiguration getConfig(', 1)[1].split('private void updateConfig(', 1)[0]
assert 'PackRegistryFile.load(file.toPath())' in read and 'throw new IllegalStateException(' in read
assert 'e.printStackTrace()' not in read
assert 'PackRegistryFile.save(configuration, file.toPath())' in manager
assert loader.index('configuration.saveToString()') < loader.index('Files.createTempFile(')
assert 'AtomicMoveNotSupportedException' in loader and 'output.force(true)' in loader
assert 'if (this.configManager != null)' in main
print('Pack registry initialization and staged-save boundaries passed.')
