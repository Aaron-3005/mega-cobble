"""Run the JS regression suite against a fresh copy of Cobblemon's bundled sim.

Usage: python tools/test_mega_abilities.py [path/to/Cobblemon.jar]
Requires Node.js on PATH; no npm packages or running Minecraft server needed.
"""
import io
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile

root = Path(__file__).resolve().parents[1]
version = next(line.split('=', 1)[1].strip() for line in
               (root / 'gradle.properties').read_text().splitlines() if line.startswith('cobblemon_version='))
if len(sys.argv) > 1:
    jar_path = Path(sys.argv[1])
else:
    jar_path = root / f'Cobblemon-fabric-{version}.jar'
    if not jar_path.exists():
        gradle_cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle'))
        matches = list((gradle_cache / 'caches/modules-2/files-2.1/com.cobblemon/fabric' / version)
                       .glob(f'*/fabric-{version}.jar'))
        if len(matches) != 1:
            sys.exit(f'Build once to download Cobblemon {version}, or pass its JAR path explicitly.')
        jar_path = matches[0]
print(f'Testing the simulator bundled in {jar_path.name}', flush=True)
build = root / 'build'
build.mkdir(exist_ok=True)
# Only this temporary directory, created by us inside build/, is cleaned up.
with tempfile.TemporaryDirectory(prefix='mega-abilities-', dir=build) as temporary:
    destination = Path(temporary).resolve()
    with zipfile.ZipFile(jar_path) as jar:
        with zipfile.ZipFile(io.BytesIO(jar.read('data/cobblemon/showdown.zip'))) as simulator:
            for entry in simulator.infolist():
                if not (destination / entry.filename).resolve().is_relative_to(destination):
                    raise ValueError(f'Archive path escapes temporary directory: {entry.filename}')
            simulator.extractall(destination)
    result = subprocess.run(['node', str(root / 'tools/test_mega_abilities.cjs'), str(destination)], cwd=root)
sys.exit(result.returncode)
