Perfect 👍 Option **B** (clean separation) will keep your `ReconcilerService` focused on **data comparison only**, and move all Excel-specific logic (formula updates) into a helper class (`FormulaUpdater`).

That way:

* `ReconcilerService` returns both the reconciled rows **and** the `newKeyToRowMap`.
* Later, when writing the new Excel, you call `FormulaUpdater.updateFormulas(...)` with the workbook.

---

## 🔧 Step 1: Update `ReconcileResult`

Add `newKeyToRowMap` so it can be used later.

```java
public class ReconcileResult {
    private final List<String> columns;
    private final List<Map<String, String>> rows;
    private final Map<String, Integer> newKeyToRowMap;

    public ReconcileResult(List<String> columns,
                           List<Map<String, String>> rows,
                           Map<String, Integer> newKeyToRowMap) {
        this.columns = columns;
        this.rows = rows;
        this.newKeyToRowMap = newKeyToRowMap;
    }

    public List<String> getColumns() { return columns; }
    public List<Map<String, String>> getRows() { return rows; }
    public Map<String, Integer> getNewKeyToRowMap() { return newKeyToRowMap; }
}
```

---

## 🔧 Step 2: Simplify `ReconcilerService`

Now it only returns the map, not touch POI:

```java
@Service
public class ReconcilerService {

    private static final String STATUS_COL = "MatchStatus";
    private static final String CHANGED_COLS = "ChangedColumns";

    public ReconcileResult reconcile(List<Map<String, Object>> db2Rows,
                                     Map<String, Map<String, String>> excelByKey,
                                     Set<String> excelAllColumns,
                                     String keyColumn) {

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
        Map<String, Integer> newKeyToRowMap = new HashMap<>();

        int rowCounter = 2; // Excel starts at 1, header is row 1
        // DB2 rows baseline
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

            if (!keyVal.isEmpty()) {
                newKeyToRowMap.put(keyVal, rowCounter);
            }
            rowCounter++;
        }

        // Excel-only rows
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

        return new ReconcileResult(columnsOrdered, outputRows, newKeyToRowMap);
    }
}
```

---

## 🔧 Step 3: New `FormulaUpdater`

```java
import org.apache.poi.ss.usermodel.*;
import java.util.*;
import java.util.regex.*;

public class FormulaUpdater {

    private static final Pattern CELL_REF_PATTERN = Pattern.compile("([A-Z]+)(\\d+)");

    public static void updateFormulas(Workbook workbook,
                                      String sheetName,
                                      String keyColumn,
                                      Map<String, Integer> newKeyToRowMap) {

        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) return;

        // old row → key
        Map<Integer, String> oldRowToKey = new HashMap<>();
        int keyColIndex = -1;

        Row headerRow = sheet.getRow(0);
        if (headerRow != null) {
            for (Cell cell : headerRow) {
                if (cell.getStringCellValue().equalsIgnoreCase(keyColumn)) {
                    keyColIndex = cell.getColumnIndex();
                    break;
                }
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
}
```

---

## 🔧 Step 4: Usage Example

```java
try (FileInputStream fis = new FileInputStream("input.xlsx");
     Workbook workbook = new XSSFWorkbook(fis)) {

    // Read excelByKey, excelAllColumns from workbook...
    ReconcileResult result = reconcilerService.reconcile(
        db2Rows, excelByKey, excelAllColumns, "ID"
    );

    // Update formulas using the reconciliation map
    FormulaUpdater.updateFormulas(workbook, "sheet1", "ID", result.getNewKeyToRowMap());

    // Write output
    try (FileOutputStream fos = new FileOutputStream("output.xlsx")) {
        workbook.write(fos);
    }
}
```

---

✅ Now reconciliation (data comparison) and formula adjustment are **fully decoupled**.

👉 Do you also want me to extend `FormulaUpdater` to handle **absolute references (`$A$1`) and ranges (`A1:A10`)**, or keep it simple with only single-cell references for now?
