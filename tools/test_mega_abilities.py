"""Run the JS regression suite against a fresh copy of Cobblemon's bundled sim.

Usage: python tools/test_mega_abilities.py [path/to/Cobblemon-1.7.3.jar]
Requires Node.js on PATH; no npm packages or running Minecraft server needed.
"""
import io
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile

root = Path(__file__).resolve().parents[1]
jar_path = Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'Cobblemon-fabric-1.7.3+1.21.1.jar'
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
