"""Validate the production Fabric JAR before publishing it as an artifact."""
import json
import os
from pathlib import Path
import re
import shutil
import zipfile


def main():
    properties = {}
    for line in Path("gradle.properties").read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.lstrip().startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            properties[key.strip()] = value.strip()

    version = properties["mod_version"]
    if not re.fullmatch(r"[0-9A-Za-z][0-9A-Za-z.+_-]*", version):
        raise ValueError(f"Invalid mod_version: {version!r}")
    if os.environ.get("GITHUB_REF_TYPE") == "tag":
        tag = os.environ["GITHUB_REF_NAME"]
        if tag != f"v{version}":
            raise ValueError(f"Release tag {tag} does not match mod_version {version}")

    jar = Path("build/libs") / f"megacobble-{version}.jar"
    with zipfile.ZipFile(jar) as archive:
        bad_entry = archive.testzip()
        if bad_entry:
            raise ValueError(f"Corrupted JAR entry: {bad_entry}")
        metadata = json.loads(archive.read("fabric.mod.json"))
        if metadata["id"] != "megacobble" or metadata["version"] != version:
            raise ValueError("JAR metadata does not match MegaCobble's version")
        if "com/aaroncraft/megacobble/MegaCobble.class" not in archive.namelist():
            raise ValueError("JAR is missing the compiled mod entrypoint")

    destination = Path("dist")
    destination.mkdir(exist_ok=True)
    shutil.copy2(jar, destination / jar.name)
    print(f"Verified {jar.name} ({jar.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
