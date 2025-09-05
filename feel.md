In Camunda DMN, you can use **FEEL built-in functions** like `matches()`, `substring()` and `substring before/after` to extract text inside parentheses.

Here’s a practical example:

Suppose the input string is:

```
"Risk Rating (High)"
```

✅ **FEEL expression to extract text within `()`**:

```feel
if matches(input, ".*\\(.*\\).*") 
then substring before(substring after(input, "("), ")") 
else null
```

### Explanation:

1. `matches(input, ".*\\(.*\\).*")` → checks if the string contains parentheses.
2. `substring after(input, "(")` → returns everything after the first `(`.

   * `"High)"` in this example.
3. `substring before(..., ")")` → trims everything after the `)`.

   * `"High"` in this example.
4. If no parentheses exist, it returns `null`.

---

👉 If you want it to return the **original string when no parentheses exist**, just change the `else` part:

```feel
if matches(input, ".*\\(.*\\).*") 
then substring before(substring after(input, "("), ")") 
else input
```

That way:

* `"Risk Rating (High)"` → `"High"`
* `"Risk Rating"` → `"Risk Rating"`

---

Do you want me to also show you how to **handle multiple parentheses** in a string (e.g., `"ABC (X) DEF (Y)"` → pick `"X"` or `"Y"`)?
