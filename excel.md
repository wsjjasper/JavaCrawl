Got it 👍 You want **Option 3: keep existing VLOOKUP formulas, but adjust them programmatically in Java when row positions change**.

That means:

1. Read the original Excel file.
2. Keep a mapping of `key → old rowIndex` and `key → new rowIndex` (after DB2 reconciliation).
3. For each formula cell, detect if it references the key column (e.g., `A1`, `A2`), then rewrite it to the new row index.
4. Save the updated file.

---

## 🛠️ Implementation Sketch with Apache POI

```java
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class FormulaUpdater {

    private static final Pattern CELL_REF_PATTERN = Pattern.compile("([A-Z]+)(\\d+)");

    /**
     * Update formulas that reference old row indices to new ones, based on key mapping.
     */
    public static void updateFormulas(Workbook workbook,
                                      String dataSheetName,
                                      String keyColumn,
                                      Map<String, Integer> newKeyToRowMap) {

        Sheet sheet = workbook.getSheet(dataSheetName);

        // Step 1: Build old key→rowIndex map
        Map<Integer, String> oldRowToKey = new HashMap<>();
        int keyColIndex = -1;

        Row headerRow = sheet.getRow(0);
        for (Cell cell : headerRow) {
            if (cell.getStringCellValue().equalsIgnoreCase(keyColumn)) {
                keyColIndex = cell.getColumnIndex();
                break;
            }
        }
        if (keyColIndex == -1) {
            throw new RuntimeException("Key column not found: " + keyColumn);
        }

        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Cell keyCell = row.getCell(keyColIndex);
            if (keyCell != null) {
                String keyVal = keyCell.toString().trim();
                if (!keyVal.isEmpty()) {
                    oldRowToKey.put(r + 1, keyVal); // Excel rows are 1-based
                }
            }
        }

        // Step 2: Iterate all formula cells
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (cell != null && cell.getCellType() == CellType.FORMULA) {
                    String oldFormula = cell.getCellFormula();

                    String newFormula = rewriteFormula(oldFormula, oldRowToKey, newKeyToRowMap);
                    if (!oldFormula.equals(newFormula)) {
                        cell.setCellFormula(newFormula);
                    }
                }
            }
        }
    }

    private static String rewriteFormula(String formula,
                                         Map<Integer, String> oldRowToKey,
                                         Map<String, Integer> newKeyToRowMap) {

        Matcher matcher = CELL_REF_PATTERN.matcher(formula);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String col = matcher.group(1);
            int oldRow = Integer.parseInt(matcher.group(2));

            String key = oldRowToKey.get(oldRow);
            if (key != null && newKeyToRowMap.containsKey(key)) {
                int newRow = newKeyToRowMap.get(key);
                matcher.appendReplacement(sb, col + newRow);
            } else {
                matcher.appendReplacement(sb, matcher.group());
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // Example usage
    public static void main(String[] args) throws Exception {
        try (FileInputStream fis = new FileInputStream("input.xlsx");
             Workbook workbook = new XSSFWorkbook(fis)) {

            // Suppose this comes from your reconciler (key→new rowIndex)
            Map<String, Integer> newKeyToRowMap = new HashMap<>();
            newKeyToRowMap.put("1", 2); // key=1 now at row 2
            newKeyToRowMap.put("3", 4); // key=3 now at row 4

            updateFormulas(workbook, "sheet1", "ID", newKeyToRowMap);

            try (FileOutputStream fos = new FileOutputStream("output.xlsx")) {
                workbook.write(fos);
            }
        }
    }
}
```

---

## 🔑 Key Points

* `CELL_REF_PATTERN` catches references like `A1`, `B12`, etc.
* `oldRowToKey` maps **old row number → key value**.
* `newKeyToRowMap` comes from your reconciliation logic (key → new row number).
* For each formula:

  * Find references (`A1`, `C5`, etc.).
  * If that row had a key, and the key moved → rewrite to new row.
* Example:

  ```
  Old: =VLOOKUP(A1, Static!A:B,2,FALSE)
  New: =VLOOKUP(A5, Static!A:B,2,FALSE)   // if key from row 1 now at row 5
  ```

---

⚠️ Limitations

* Works best for **simple VLOOKUP/A1 references**.
* Doesn’t yet handle `$A$1` absolute refs, ranges like `A1:A10`, or cross-sheet formulas — you’d extend the regex.
* If Excel has merged cells or array formulas, extra care is needed.

---

👉 Do you want me to also extend this to handle **absolute refs (`$A$1`) and ranges (`A1:A10`)** so your formulas won’t silently break?
