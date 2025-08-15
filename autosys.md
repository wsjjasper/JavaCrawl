Gotcha — if your **Autosys** version doesn’t support `-J`, then for finding all jobs in **`INACTIVE/ON_ICE` (IO)** status, you can filter the `autorep -q` output.

Here’s a one-liner:

```bash
autorep -j ALL -q | awk '$4=="INACTIVE/ON_ICE" {print $1}'
```

**Explanation:**

* `autorep -j ALL -q` → lists all jobs and their statuses.
* `awk '$4=="INACTIVE/ON_ICE" {print $1}'` → picks only jobs where the **status column** equals `INACTIVE/ON_ICE` (prints the job name).
* `$4` is the status column in `-q` output (job name is `$1`).

---

If you want **job name + status** for verification:

```bash
autorep -j ALL -q | awk '$4=="INACTIVE/ON_ICE" {print $1, $4}'
```

---

If you want to **search only jobs containing `abc`** that are in IO status:

```bash
autorep -j "abc%" -q | awk '$4=="INACTIVE/ON_ICE" {print $1}'
```

---

If you want, I can also make you a **version that gets the job names in IO and then dumps their JIL definitions** automatically.
That would solve your earlier problem of status + job details.
Do you want me to prepare that?
