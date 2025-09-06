Perfect 👍 Let’s merge the **formula rewriting logic** into your `ReconcilerService`.
This way, after reconciling DB2 + Excel data and building the new sheet, the service will also update all formulas based on the new row positions.

Below is a consolidated version:

---

## 🔧 `ReconcilerService` (with formula updater merged)

```java
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;

@Service
public class ReconcilerService {

    private static final String STATUS_COL = "MatchStatus";
    private static final String CHANGED_COLS = "ChangedColumns";
    private static final Pattern CELL_REF_PATTERN = Pattern.compile("([A-Z]+)(\\d+)");

    public ReconcileResult reconcile(List<Map<String, Object>> db2Rows,
                                     Map<String, Map<String, String>> excelByKey,
                                     Set<String> excelAllColumns,
                                     String keyColumn,
                                     Workbook workbook,
                                     String sheetName) {

        LinkedHashSet<String> dbColumns = new LinkedHashSet<>();
        if (!db2Rows.isEmpty()) {
            dbColumns.addAll(db2Rows.get(0).keySet());
        }

        LinkedHashSet<String> excelOnlyColumns = new LinkedHashSet<>(excelAllColumns);
        excelOnlyColumns.removeAll(dbColumns);

        List<String> columnsOrdered = new ArrayList<>(dbColumns);
        columnsOrdered.addAll(excelOnlyColumns);

        if (!columnsOrdered.contains(STATUS_COL)) columnsOrdered.add(STATUS_COL);
        if (!columnsOrdered.contains(CHANGED_COLS)) columnsOrdered.add(CHANGED_COLS);

        Set<String> dbKeys = new LinkedHashSet<>();
        for (Map<String, Object> row : db2Rows) {
            Object keyObj = row.get(keyColumn);
            if (keyObj != null) dbKeys.add(String.valueOf(keyObj));
        }

        List<Map<String, String>> outputRows = new ArrayList<>();
        Map<String, Integer> newKeyToRowMap = new HashMap<>(); // for formula update

        int rowCounter = 2; // Excel rows start at 1, header is row 1
        // 1) DB2 baseline rows
        for (Map<String, Object> dbRow : db2Rows) {
            String keyVal = dbRow.get(keyColumn) == null ? "" : String.valueOf(dbRow.get(keyColumn));
            Map<String, String> out = new LinkedHashMap<>();

            for (String c : dbColumns) {
                Object v = dbRow.get(c);
                out.put(c, v == null ? "" : String.valueOf(v));
            }

            Map<String, String> excelRow = excelByKey.get(keyVal);
            if (excelRow != null) {
                for (String c : excelOnlyColumns) {
                    out.put(c, excelRow.getOrDefault(c, ""));
                }
                List<String> changedCols = new ArrayList<>();
                for (String c : dbColumns) {
                    if (excelRow.containsKey(c)) {
                        String dbVal = out.get(c) == null ? "" : out.get(c).trim();
                        String exVal = excelRow.getOrDefault(c, "").trim();
                        if (!dbVal.equals(exVal)) {
                            changedCols.add(c);
                        }
                    }
                }
                if (!changedCols.isEmpty()) {
                    out.put(STATUS_COL, "CHANGED");
                    out.put(CHANGED_COLS, String.join(",", changedCols));
                } else {
                    out.put(STATUS_COL, "MATCHED");
                    out.put(CHANGED_COLS, "");
                }
            } else {
                for (String c : excelOnlyColumns) {
                    out.putIfAbsent(c, "");
                }
                out.put(STATUS_COL, "DB_ONLY");
                out.put(CHANGED_COLS, "");
            }
            outputRows.add(out);

            // map key → new row number
            if (!keyVal.isEmpty()) {
                newKeyToRowMap.put(keyVal, rowCounter);
            }
            rowCounter++;
        }

        // 2) Excel NEW rows
        for (Map.Entry<String, Map<String, String>> e : excelByKey.entrySet()) {
            String key = e.getKey();
            if (!dbKeys.contains(key)) {
                Map<String, String> excelRow = e.getValue();
                Map<String, String> out = new LinkedHashMap<>();
                for (String c : dbColumns) {
                    out.put(c, "");
                }
                for (String c : excelOnlyColumns) {
                    out.put(c, excelRow.getOrDefault(c, ""));
                }
                if (dbColumns.contains(keyColumn) && out.get(keyColumn).isBlank()) {
                    out.put(keyColumn, key);
                }
                out.put(STATUS_COL, "NEW");
                out.put(CHANGED_COLS, "");
                outputRows.add(out);

                newKeyToRowMap.put(key, rowCounter);
                rowCounter++;
            }
        }

        // Update formulas in the workbook
        updateFormulas(workbook, sheetName, keyColumn, newKeyToRowMap);

        return new ReconcileResult(columnsOrdered, outputRows);
    }

    private void updateFormulas(Workbook workbook,
                                String dataSheetName,
                                String keyColumn,
                                Map<String, Integer> newKeyToRowMap) {

        Sheet sheet = workbook.getSheet(dataSheetName);

        // Build old row→key mapping
        Map<Integer, String> oldRowToKey = new HashMap<>();
        int keyColIndex = -1;

        Row headerRow = sheet.getRow(0);
        for (Cell cell : headerRow) {
            if (cell.getStringCellValue().equalsIgnoreCase(keyColumn)) {
                keyColIndex = cell.getColumnIndex();
                break;
            }
        }
        if (keyColIndex == -1) return;

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

        // Iterate formula cells
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

    private String rewriteFormula(String formula,
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
}
```

---

## 🔑 What Changed

* `newKeyToRowMap` built during reconciliation (key → new row number).
* After reconciliation, we call `updateFormulas(...)`.
* Formula cells referencing old rows get updated to new rows using `rewriteFormula`.
* All logic stays inside `ReconcilerService`.

---

⚠️ Currently, this supports **simple A1-style references**. If your formulas also use:

* `$A$1` (absolute refs),
* ranges like `A1:A10`,
  we need to extend the regex and handling.

👉 Do you want me to extend this version to handle **absolute references and ranges** as well, so it’s more robust?
