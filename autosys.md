Got it. Since your `autorep -q` shows a 4-column table and the **status is the last field** (e.g., `OI`), the most robust filter is to key off the **last column** and skip the 2 header lines.

**All jobs currently OI (or IO just in case):**

```bash
autorep -j ALL -q | awk 'NR>2 && ($NF=="OI" || $NF=="IO") {print $1}'
```

**Only jobs whose name matches `abc%` and are OI/IO:**

```bash
autorep -j "abc%" -q | awk 'NR>2 && ($NF=="OI" || $NF=="IO") {print $1}'
```

**If you also want to see name + status for verification:**

```bash
autorep -j ALL -q | awk 'NR>2 {print $1, $NF}' | awk '$2=="OI" || $2=="IO" {print}'
```

> Why this works: `NR>2` skips the header; `$NF` is the last field (Status), so it’s stable even if spacing shifts.
