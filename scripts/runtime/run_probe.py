#!/usr/bin/env python3
"""Run a disposable native Paper proof with exact supplied plugin/server JARs.

Requires a previously bootstrapped disposable Paper server directory only for its
cached libraries, version/bootstrap caches and small test world. Never point the
output directory at an existing server or user data. No Maven or network fetches.
"""
import argparse
import hashlib
import json
import re
import shutil
import subprocess
import zipfile
from pathlib import Path


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--dank-jar", required=True, type=Path)
parser.add_argument("--slimefun-jar", required=True, type=Path)
parser.add_argument("--paper-jar", required=True, type=Path)
parser.add_argument("--cached-server", required=True, type=Path)
parser.add_argument("--java-home", required=True, type=Path)
parser.add_argument("--output", required=True, type=Path)
parser.add_argument("--gui-guards", action="store_true")
parser.add_argument("--legacy-alias-restart", action="store_true")
parser.add_argument("--unreadable-item-restart", action="store_true")
args = parser.parse_args()
if args.output.exists():
    raise SystemExit("Refusing to reuse an existing output directory; choose a fresh disposable path")
source = Path(__file__).parent / "src/audit/PackPreservationProbe.java"
args.output.mkdir(parents=True)
server = args.output / "server"
server.mkdir()
for name in ["libraries", "cache", "versions", "world", "config"]:
    shutil.copytree(args.cached_server / name, server / name)
for name in ["bukkit.yml", "spigot.yml"]:
    if (args.cached_server / name).exists():
        shutil.copy2(args.cached_server / name, server / name)
(server / "plugins").mkdir()
for target, given in [("plugins/DankTech2.jar", args.dank_jar), ("plugins/Slimefun.jar", args.slimefun_jar), ("server.jar", args.paper_jar)]:
    shutil.copy2(given, server / target)
for plugin in ["Slimefun", "DankTech2", "bStats"]:
    (server / "plugins" / plugin).mkdir()
slimefun_config = args.cached_server / "plugins/Slimefun/config.yml"
if slimefun_config.exists():
    shutil.copy2(slimefun_config, server / "plugins/Slimefun/config.yml")
else:
    (server / "plugins/Slimefun/config.yml").write_text("options:\n  auto-update: false\n")
(server / "plugins/DankTech2/config.yml").write_text("auto-update: false\n")
(server / "plugins/bStats/config.yml").write_text("enabled: false\n")
(server / "eula.txt").write_text("eula=true\n")
(server / "server.properties").write_text(
    "server-ip=127.0.0.1\nserver-port=0\nlevel-name=world\nonline-mode=false\n"
    "enforce-secure-profile=false\nview-distance=2\nsimulation-distance=2\n"
    "spawn-protection=0\nwhite-list=true\nmax-players=1\nmax-tick-time=60000\n"
    "pause-when-empty-seconds=-1\n"
)
classes = args.output / "probe-classes"
classes.mkdir()
shutil.copy2(source, args.output / "PackPreservationProbe.java")
classpath = ":".join(str(p) for p in [server / "plugins/DankTech2.jar", server / "plugins/Slimefun.jar"]
    + sorted((server / "libraries").rglob("*.jar")))
compile_result = subprocess.run([str(args.java_home / "bin/javac"), "--release", "25", "-cp", classpath,
    "-d", str(classes), str(source)], text=True, capture_output=True)
(args.output / "probe-compile.log").write_text(compile_result.stdout + compile_result.stderr)
compile_result.check_returncode()
(classes / "plugin.yml").write_text("name: PackPreservationProbe\nversion: '1.0'\nmain: audit.PackPreservationProbe\n"
    "api-version: '1.21'\ndepend: [Slimefun]\nsoftdepend: [DankTech2]\n")
subprocess.run([str(args.java_home / "bin/jar"), "--create", "--file", str(server / "plugins/PackPreservationProbe.jar"),
    "-C", str(classes), "."], check=True)
java_version = subprocess.run([str(args.java_home / "bin/java"), "-version"], text=True, capture_output=True)
with zipfile.ZipFile(args.dank_jar) as plugin_archive:
    version_match = re.search(r"(?m)^version:\s*['\"]?([A-Za-z0-9_.+-]+)", plugin_archive.read("plugin.yml").decode())
    if version_match is None:
        raise RuntimeError("Could not identify supplied DankTech2 plugin version")
    expected_version = version_match.group(1)
manifest = {"files": {str(p.relative_to(args.output)): digest(p) for p in [server / "plugins/DankTech2.jar",
    server / "plugins/Slimefun.jar", server / "server.jar", server / "plugins/PackPreservationProbe.jar",
    args.output / "PackPreservationProbe.java"]}, "java": java_version.stderr, "gui_guards": args.gui_guards,
    "expected_dank_version": expected_version,
    "scope": "Real Paper inventory, PDC and registry; GUI refusal checks use a tracked Player-interface proxy, no connected players.",
    "results": []}
(args.output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")


def run_phase(phase, expect_refusal=False):
    command = [str(args.java_home / "bin/java"), "-Xms256M", "-Xmx1024M", "-Dterminal.jline=false",
        "-Dterminal.ansi=false", "-Dpaper.disablePluginRemapping=true", f"-Dprobe.phase={phase}",
        f"-Dprobe.expected-dank-version={expected_version}"]
    if expect_refusal:
        command.append("-Dprobe.expect-registry-refusal=true")
    registry_before_sha = digest(server / "plugins/DankTech2/dank_packs.yml") if expect_refusal else None
    if args.gui_guards:
        command.append("-Dprobe.gui-guards=true")
    command += ["-jar", "server.jar", "--nogui"]
    with (server / f"native-{phase}.log").open("wb") as output:
        process = subprocess.Popen(command, cwd=server, stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT)
        try:
            code = process.wait(timeout=180)
        except subprocess.TimeoutExpired:
            try:
                process.stdin.write(b"stop\n")
                process.stdin.flush()
                process.wait(timeout=30)
            except (BrokenPipeError, subprocess.TimeoutExpired):
                process.kill()
                process.wait()
            raise RuntimeError(f"Timed out waiting for native phase {phase}; inspect native-{phase}.log")
    result = server / f"probe-result-{phase}.txt"
    content = result.read_text() if result.exists() else "No probe result file"
    passed = code == 0 and content.rstrip().endswith(f"PASS {phase}")
    registry_after_sha = digest(server / "plugins/DankTech2/dank_packs.yml") if expect_refusal else None
    if expect_refusal:
        passed = passed and registry_before_sha == registry_after_sha
    manifest["results"].append({"phase": phase, "exit_code": code, "passed": passed,
        "checks": len(re.findall(r"^OK ", content, re.MULTILINE)), "result_sha256": digest(result) if result.exists() else None,
        "refused_registry_sha256_before": registry_before_sha, "refused_registry_sha256_after": registry_after_sha})
    (args.output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"{phase}: {'PASS' if passed else 'FAIL'} ({manifest['results'][-1]['checks']} checks)", flush=True)
    if not passed:
        print(content[-5000:], flush=True)
        raise SystemExit(1)


run_phase("first")
run_phase("second")
if args.legacy_alias_restart:
    registry = server / "plugins/DankTech2/dank_packs.yml"
    before = registry.read_text()
    replaced = before.replace("==: org.bukkit.inventory.ItemStack", "==: io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack")
    if replaced == before:
        raise RuntimeError("No ordinary YAML item aliases found for the legacy-alias recovery fixture")
    registry.write_text(replaced)
    (server / "probe-legacy-alias-input.yml").write_text(replaced)
    run_phase("legacy-alias")
if args.unreadable_item_restart:
    registry = server / "plugins/DankTech2/dank_packs.yml"
    before = registry.read_text()
    replaced = before.replace("==: org.bukkit.inventory.ItemStack", "==: missing.historical.UnreadableItem")
    if replaced == before:
        raise RuntimeError("No ordinary YAML item aliases found for the unreadable-item refusal fixture")
    registry.write_text(replaced)
    (server / "probe-unreadable-registry-input.yml").write_text(replaced)
    run_phase("unreadable-item", expect_refusal=True)
print(str(args.output / "manifest.json"), flush=True)
