#!/usr/bin/env python3
"""Run only under build_seguro.sh. Never uses production configs or player data."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import time
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server-home", type=Path, required=True,
                        help="Read-only source of the Paper jar, libraries and cache")
    parser.add_argument("--server-jar", default="paper-26.2-133.jar")
    parser.add_argument("--plugin", type=Path, required=True)
    parser.add_argument("--slimefun", type=Path, required=True)
    parser.add_argument("--work-dir", type=Path, required=True,
                        help="Must not exist; disposable server and evidence destination")
    args = parser.parse_args()
    source = args.server_home.resolve(strict=True)
    plugin = args.plugin.resolve(strict=True)
    slimefun = args.slimefun.resolve(strict=True)
    work = args.work_dir.resolve()
    if work == source or source in work.parents or work.exists():
        parser.error("--work-dir must be a new directory outside --server-home")
    server_jar = (source / args.server_jar).resolve(strict=True)
    if server_jar.parent != source:
        parser.error("--server-jar must be a file inside --server-home")
    java_home = Path(os.environ["JAVA_HOME"])
    work.mkdir(parents=True)
    plugins = work / "plugins"
    plugins.mkdir()
    for name in ("libraries", "cache", "versions"):
        if (source / name).is_dir():
            shutil.copytree(source / name, work / name)
    shutil.copy2(server_jar, work / "server.jar")
    shutil.copy2(plugin, plugins / "SensibleToolbox.jar")
    shutil.copy2(slimefun, plugins / "Slimefun.jar")
    # Use local cached libraries for compiling the probe; runtime dependencies
    # are supplied by Paper and the two real plugins through Bukkit's loader.
    classpath = os.pathsep.join(map(str, [plugin, *sorted((work / "libraries").rglob("*.jar"))]))
    classes = work / "probe-classes"
    classes.mkdir()
    subprocess.run([str(java_home / "bin/javac"), "--release", "25", "-classpath", classpath,
                    "-d", str(classes), str(Path(__file__).with_name("RecipeReloadProbe.java"))], check=True)
    with zipfile.ZipFile(plugins / "RecipeReloadProbe.jar", "w") as jar:
        jar.writestr("plugin.yml", "name: RecipeReloadProbe\nversion: '1'\n"
                     "main: integration.RecipeReloadProbe\napi-version: '1.21'\n"
                     "depend: [SensibleToolbox, Slimefun]\n")
        for file in classes.rglob("*.class"):
            jar.write(file, str(file.relative_to(classes)))
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    (work / "eula.txt").write_text("eula=true\n")
    (work / "isolated-recipe-reload-test").touch()
    (work / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\n"
        "enable-query=false\nenable-rcon=false\nmax-players=1\n"
        "view-distance=2\nsimulation-distance=2\nlevel-type=minecraft:flat\n"
        'generator-settings={"biome":"minecraft:plains","layers":'
        '[{"block":"minecraft:bedrock","height":1},'
        '{"block":"minecraft:dirt","height":2},'
        '{"block":"minecraft:grass_block","height":1}]}\n'
        "generate-structures=false\nspawn-protection=0\n")
    start = time.monotonic()
    with (work / "console.log").open("w") as log:
        process = subprocess.Popen([str(java_home / "bin/java"), "-Xms512M", "-Xmx3G",
                                    "-XX:ActiveProcessorCount=2", "-jar", "server.jar", "--nogui"],
                                   cwd=work, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT)
        try:
            code = process.wait(timeout=300)
        except BaseException:
            # Graceful console stop first; terminate only this disposable process.
            if process.poll() is None:
                try:
                    process.stdin.write(b"stop\n")
                    process.stdin.flush()
                    process.wait(timeout=60)
                except (BrokenPipeError, subprocess.TimeoutExpired):
                    process.terminate()
                    try:
                        process.wait(timeout=30)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait()
            raise
        finally:
            process.stdin.close()
    result = work / "result.txt"
    summary = {"exit_code": code, "seconds": round(time.monotonic() - start, 2),
               "plugin_sha256": hashlib.sha256(plugin.read_bytes()).hexdigest(),
               "server_sha256": hashlib.sha256(server_jar.read_bytes()).hexdigest(),
               "result": result.read_text().strip() if result.is_file() else "FAIL: no result"}
    (work / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary, indent=2), flush=True)
    return 0 if code == 0 and summary["result"].startswith("PASS ") else 1


if __name__ == "__main__":
    raise SystemExit(main())
