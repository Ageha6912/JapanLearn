import re
from pathlib import Path

p = Path("app/build/test-results/testDebugUnitTest")
total = failures = errors = 0
for x in p.glob("*.xml"):
    t = x.read_text(encoding="utf-8", errors="ignore")
    total += int(re.search(r'tests="(\d+)"', t).group(1))
    failures += int(re.search(r'failures="(\d+)"', t).group(1))
    errors += int(re.search(r'errors="(\d+)"', t).group(1))
print(f"tests={total} failures={failures} errors={errors}")
