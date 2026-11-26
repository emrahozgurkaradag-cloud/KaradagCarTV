#!/usr/bin/env python3
"""uiautomator dump XML'inde metin/açıklamaya göre düğüm bulur ve
merkez koordinatını yazar. Kullanım: aa-ui.py dump.xml "regex1|regex2" """
import re, sys, xml.etree.ElementTree as ET
path, pattern = sys.argv[1], re.compile(sys.argv[2], re.I)
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(1)
for node in root.iter("node"):
    attrs = [node.get("text"), node.get("content-desc"), node.get("resource-id")]
    label = " ".join(filter(None, attrs))
    if any(v and pattern.search(v.strip()) for v in attrs):
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2, label.replace("\n", " ")[:80])
            sys.exit(0)
sys.exit(1)
