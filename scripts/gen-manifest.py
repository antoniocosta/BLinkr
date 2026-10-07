#!/usr/bin/env python3
"""Write the hosts from Sites.kt into the RouterActivity intent filter in AndroidManifest.xml.

usage: scripts/gen-manifest.py
"""
import os
import re

root = os.path.join(os.path.dirname(__file__), "..")
sites = open(os.path.join(root, "app/src/main/java/net/uncorp/blinkr/Sites.kt")).read()
block = sites[sites.index("val ALL: List<Site>"):sites.index("val ALL_HOSTS")]
hosts = []
for site in re.findall(r'Site\("[^"]+", "[^"]+", listOf\((.*?)\)\)', block, re.S):
    hosts += re.findall(r'"([^"]+)"', site)

path = os.path.join(root, "app/src/main/AndroidManifest.xml")
manifest = open(path).read()
start = "<!-- hosts: generated from Sites.kt by scripts/gen-manifest.py -->"
end = "<!-- /hosts -->"
indent = " " * 16
lines = "".join(f'{indent}<data android:host="{h}" />\n' for h in hosts)
manifest = re.sub(re.escape(start) + r".*?" + re.escape(end), f"{start}\n{lines}{indent}{end}", manifest, flags=re.S)
open(path, "w").write(manifest)
print(f"{len(hosts)} hosts")
