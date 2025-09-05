In FEEL (Camunda DMN), you can’t directly treat an integer as a date — you first need to **convert the integer into a string** in the right format, then use the `date()` function to parse it into a FEEL `date`.

---

### Example 1: Integer in `yyyyMMdd` format

Suppose the input is:

```
20250904
```

✅ FEEL expression:

```feel
date(string(input))
```

* `string(input)` → converts `20250904` (number) into `"20250904"`.
* `date("2025-09-04")` is required, so the string must have dashes.
  If your integer is `20250904`, you must **reformat** it.

To reformat:

```feel
date(concat(substring(string(input),1,4), "-", substring(string(input),5,2), "-", substring(string(input),7,2)))
```

Result: `date("2025-09-04")`.

---

### Example 2: Compare with another date

Suppose you want to check if `input` date is **after today**:

```feel
date(concat(substring(string(input),1,4), "-", substring(string(input),5,2), "-", substring(string(input),7,2))) > today()
```

---

### Example 3: Check if integer date is before `2025-12-31`

```feel
date(concat(substring(string(input),1,4), "-", substring(string(input),5,2), "-", substring(string(input),7,2))) < date("2025-12-31")
```

---

⚡ Tip: If your integer is already in **epoch days** (days since 1970-01-01), then you’d need a different conversion using arithmetic, but most DMN cases use `yyyyMMdd`.

---

👉 Do you want me to give you a **reusable FEEL function** you can drop in DMN to convert `yyyyMMdd` integer into a `date`, so you don’t repeat the long `concat(substring(...))` each time?
