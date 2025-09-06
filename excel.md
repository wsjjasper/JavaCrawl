You got it — here’s a **clean, end-to-end Spring Boot CLI** that implements everything we agreed on, bundled into a tidy project. It:

* Reads `sheet1` from the input Excel.
* Queries DB2.
* Uses **DB2 as baseline**, merges Excel-only columns.
* Flags `MATCHED / CHANGED / DB_ONLY / NEW` + `ChangedColumns`.
* **Derives formula templates from the existing sheet** (per column), then **re-applies those formulas to every row** in the output (including newly added DB rows). Templates are row-aware (e.g., `=VLOOKUP(A{row},Static!A:B,2,FALSE)`), so reordering/new rows are handled.
* Generates a **new Excel** by **cloning the original workbook**, replacing just `sheet1` so other static sheets (like lookup tables) remain intact and formulas keep working.

---

# Project Structure

```
excel-db2-reconciler/
├─ pom.xml
├─ src/main/resources/application.yml
└─ src/main/java/com/example/
   ├─ App.java
   ├─ support/AppProps.java
   ├─ io/
   │  ├─ Db2DataFetcher.java
   │  ├─ ExcelReader.java
   │  ├─ ExcelWriter.java
   │  ├─ FormulaTemplateExtractor.java
   │  └─ FormulaApplier.java
   ├─ core/ReconcilerService.java
   └─ model/ReconcileResult.java
```

---

## pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.example</groupId>
  <artifactId>excel-db2-reconciler</artifactId>
  <version>1.0.0</version>

  <properties>
    <java.version>17</java.version>
    <spring-boot.version>3.3.2</spring-boot.version>
  </properties>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-dependencies</artifactId>
        <version>${spring-boot.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter</artifactId>
    </dependency>

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>

    <!-- DB2 JDBC driver (adjust to your repo/version if needed) -->
    <dependency>
      <groupId>com.ibm.db2</groupId>
      <artifactId>jcc</artifactId>
      <version>11.5.9.0</version>
    </dependency>

    <dependency>
      <groupId>org.apache.poi</groupId>
      <artifactId>poi-ooxml</artifactId>
      <version>5.2.5</version>
    </dependency>

    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <optional>true</optional>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

---

## application.yml

```yaml
app:
  inputExcelPath: "./input.xlsx"
  outputExcelPath: "./output.xlsx"
  sheetName: "sheet1"
  keyColumn: "ID"
  query: "SELECT ID, NAME, AGE FROM YOUR_SCHEMA.YOUR_TABLE"

spring:
  datasource:
    url: "jdbc:db2://HOST:50000/DBNAME"
    username: "DB_USER"
    password: "DB_PASS"
    driver-class-name: "com.ibm.db2.jcc.DB2Driver"

logging:
  level:
    root: INFO
    com.example: INFO
```

---

## App.java

```java
package com.example;

import com.example.core.ReconcilerService;
import com.example.io.Db2DataFetcher;
import com.example.io.ExcelReader;
import com.example.io.ExcelWriter;
import com.example.model.ReconcileResult;
import com.example.support.AppProps;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Bean
    CommandLineRunner run(AppProps props,
                          ExcelReader excelReader,
                          Db2DataFetcher db2DataFetcher,
                          ReconcilerService reconciler,
                          ExcelWriter excelWriter) {
        return args -> {
            if (!Files.exists(Path.of(props.getInputExcelPath()))) {
                throw new IllegalArgumentException("Input Excel not found: " + props.getInputExcelPath());
            }

            // Load original workbook (so we can preserve other sheets and learn formula templates)
            Workbook originalWb;
            try (FileInputStream fis = new FileInputStream(props.getInputExcelPath())) {
                originalWb = new XSSFWorkbook(fis);
            }

            // Read Excel data from sheet1
            ExcelReader.ExcelData excelData = excelReader.readExcel(props.getInputExcelPath(), props.getSheetName());

            // Query DB2
            List<Map<String, Object>> db2Rows = db2DataFetcher.fetch(props.getQuery());

            // Reconcile (pure data)
            ReconcileResult result = reconciler.reconcile(
                    db2Rows,
                    excelData.rowsByKey(props.getKeyColumn()),
                    excelData.getAllColumns(),
                    props.getKeyColumn()
            );

            // Write a new workbook:
            // - clone all sheets from original
            // - replace 'sheet1' with reconciled data
            // - reapply column formulas via templates learned from original 'sheet1'
            excelWriter.writeWithTemplates(
                    originalWb,
                    props.getSheetName(),
                    props.getOutputExcelPath(),
                    result.getColumns(),
                    result.getRows()
            );

            System.out.println("Done. Output written: " + props.getOutputExcelPath());
        };
    }
}
```

---

## support/AppProps.java

```java
package com.example.support;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProps {
    private String inputExcelPath;
    private String outputExcelPath;
    private String sheetName;
    private String keyColumn;
    private String query;

    public String getInputExcelPath() { return inputExcelPath; }
    public void setInputExcelPath(String inputExcelPath) { this.inputExcelPath = inputExcelPath; }
    public String getOutputExcelPath() { return outputExcelPath; }
    public void setOutputExcelPath(String outputExcelPath) { this.outputExcelPath = outputExcelPath; }
    public String getSheetName() { return sheetName; }
    public void setSheetName(String sheetName) { this.sheetName = sheetName; }
    public String getKeyColumn() { return keyColumn; }
    public void setKeyColumn(String keyColumn) { this.keyColumn = keyColumn; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
}
```

---

## io/Db2DataFetcher.java

```java
package com.example.io;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class Db2DataFetcher {
    private final JdbcTemplate jdbcTemplate;

    public Db2DataFetcher(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> fetch(String query) {
        return jdbcTemplate.query(query, (rs, rowNum) -> {
            int colCount = rs.getMetaData().getColumnCount();
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= colCount; i++) {
                String col = rs.getMetaData().getColumnLabel(i);
                if (col == null || col.isBlank()) col = rs.getMetaData().getColumnName(i);
                row.put(col, rs.getObject(i));
            }
            return row;
        });
    }
}
```

---

## io/ExcelReader.java

```java
package com.example.io;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.util.*;

@Component
public class ExcelReader {

    public static class ExcelData {
        private final List<Map<String, String>> rows;
        private final List<String> headerColumns;

        public ExcelData(List<Map<String, String>> rows, List<String> headerColumns) {
            this.rows = rows;
            this.headerColumns = headerColumns;
        }

        public Map<String, Map<String, String>> rowsByKey(String keyColumn) {
            Map<String, Map<String, String>> byKey = new LinkedHashMap<>();
            for (Map<String, String> row : rows) {
                String key = row.getOrDefault(keyColumn, "");
                if (key != null && !key.isBlank()) {
                    byKey.put(key, row);
                }
            }
            return byKey;
        }

        public Set<String> getAllColumns() {
            return new LinkedHashSet<>(headerColumns);
        }
    }

    public ExcelData readExcel(String path, String sheetName) throws Exception {
        try (FileInputStream fis = new FileInputStream(path);
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheet(sheetName);
            if (sheet == null) throw new IllegalArgumentException("Sheet not found: " + sheetName);

            Iterator<Row> rowIterator = sheet.rowIterator();
            if (!rowIterator.hasNext()) return new ExcelData(Collections.emptyList(), Collections.emptyList());

            Row headerRow = rowIterator.next();
            List<String> headers = new ArrayList<>();
            for (Cell c : headerRow) headers.add(cellToString(c).trim());

            List<Map<String, String>> rows = new ArrayList<>();
            while (rowIterator.hasNext()) {
                Row r = rowIterator.next();
                if (isRowBlank(r)) continue;

                Map<String, String> map = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    Cell c = r.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    map.put(headers.get(i), c == null ? "" : cellToString(c));
                }
                rows.add(map);
            }
            return new ExcelData(rows, headers);
        }
    }

    private static boolean isRowBlank(Row r) {
        if (r == null) return true;
        for (Cell c : r) if (c != null && c.getCellType() != CellType.BLANK && !cellToString(c).isBlank()) return false;
        return true;
    }

    private static String cellToString(Cell c) {
        if (c == null) return "";
        return switch (c.getCellType()) {
            case STRING -> c.getStringCellValue();
            case BOOLEAN -> Boolean.toString(c.getBooleanCellValue());
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(c)) yield c.getLocalDateTimeCellValue().toString();
                double d = c.getNumericCellValue();
                yield (Math.floor(d) == d) ? String.valueOf((long) d) : Double.toString(d);
            }
            case FORMULA -> {
                try { yield c.getStringCellValue(); }
                catch (Exception e) { yield Double.toString(c.getNumericCellValue()); }
            }
            default -> "";
        };
    }
}
```

---

## core/ReconcilerService.java

```java
package com.example.core;

import com.example.model.ReconcileResult;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ReconcilerService {

    private static final String STATUS_COL = "MatchStatus";
    private static final String CHANGED_COLS = "ChangedColumns";

    public ReconcileResult reconcile(List<Map<String, Object>> db2Rows,
                                     Map<String, Map<String, String>> excelByKey,
                                     Set<String> excelAllColumns,
                                     String keyColumn) {

        LinkedHashSet<String> dbColumns = new LinkedHashSet<>();
        if (!db2Rows.isEmpty()) dbColumns.addAll(db2Rows.get(0).keySet());

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
                        if (!dbVal.equals(exVal)) changedCols.add(c);
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
                for (String c : excelOnlyColumns) out.putIfAbsent(c, "");
                out.put(STATUS_COL, "DB_ONLY");
                out.put(CHANGED_COLS, "");
            }
            outputRows.add(out);
        }

        // 2) Excel-only rows (NEW)
        for (Map.Entry<String, Map<String, String>> e : excelByKey.entrySet()) {
            String key = e.getKey();
            if (!dbKeys.contains(key)) {
                Map<String, String> excelRow = e.getValue();
                Map<String, String> out = new LinkedHashMap<>();
                for (String c : dbColumns) out.put(c, "");
                for (String c : excelOnlyColumns) out.put(c, excelRow.getOrDefault(c, ""));
                if (dbColumns.contains(keyColumn) && out.get(keyColumn).isBlank()) out.put(keyColumn, key);
                out.put(STATUS_COL, "NEW");
                out.put(CHANGED_COLS, "");
                outputRows.add(out);
            }
        }

        return new ReconcileResult(columnsOrdered, outputRows);
    }
}
```

---

## model/ReconcileResult.java

```java
package com.example.model;

import java.util.List;
import java.util.Map;

public class ReconcileResult {
    private final List<String> columns;
    private final List<Map<String, String>> rows;

    public ReconcileResult(List<String> columns, List<Map<String, String>> rows) {
        this.columns = columns;
        this.rows = rows;
    }

    public List<String> getColumns() { return columns; }
    public List<Map<String, String>> getRows() { return rows; }
}
```

---

## io/FormulaTemplateExtractor.java

```java
package com.example.io;

import org.apache.poi.ss.usermodel.*;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Learns per-column formula templates from an existing sheet.
 * Template replaces only references that point to the current row number with "{row}".
 * Supports absolute refs like $A$2.
 */
public class FormulaTemplateExtractor {

    // Matches refs like A2, $A$2, A$2, $A2
    private static final Pattern CELL_REF = Pattern.compile("\\$?([A-Z]+)\\$?(\\d+)");

    /**
     * @return map: columnIndex -> template string (e.g., "VLOOKUP(A{row},Static!A:B,2,FALSE)")
     */
    public static Map<Integer, String> extractTemplates(Sheet sheet, int headerRowIndex) {
        Map<Integer, String> templates = new HashMap<>();
        Row header = sheet.getRow(headerRowIndex);
        if (header == null) return templates;

        int lastRow = sheet.getLastRowNum();

        for (int c = 0; c < header.getLastCellNum(); c++) {
            String template = null;

            // Find first formula in this column to learn the pattern
            for (int r = headerRowIndex + 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Cell cell = row.getCell(c);
                if (cell != null && cell.getCellType() == CellType.FORMULA) {
                    String f = cell.getCellFormula();
                    template = buildTemplate(f, r + 1);
                    break;
                }
            }
            if (template != null) templates.put(c, template);
        }
        return templates;
    }

    private static String buildTemplate(String formula, int currentRow1Based) {
        Matcher m = CELL_REF.matcher(formula);
        StringBuffer sb = new StringBuffer();
        boolean replacedAny = false;

        while (m.find()) {
            String col = m.group(1);
            int row = Integer.parseInt(m.group(2));
            if (row == currentRow1Based) {
                // Preserve $ on column if present, preserve $ on row if present
                String matched = m.group(0);
                boolean colAbs = matched.startsWith("$");
                boolean rowAbs = matched.contains("$" + m.group(2));

                String repl = (colAbs ? "$" : "") + col + (rowAbs ? "$" : "") + "{row}";
                m.appendReplacement(sb, Matcher.quoteReplacement(repl));
                replacedAny = true;
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(sb);
        return replacedAny ? sb.toString() : null;
    }
}
```

---

## io/FormulaApplier.java

```java
package com.example.io;

import org.apache.poi.ss.usermodel.*;

import java.util.Map;

public class FormulaApplier {

    /**
     * Applies learned column templates to ALL data rows of target sheet.
     * Creates formula cells for new rows; updates existing ones when different.
     */
    public static void applyTemplates(Sheet sheet,
                                      int headerRowIndex,
                                      Map<Integer, String> columnTemplates) {

        if (columnTemplates == null || columnTemplates.isEmpty()) return;

        int lastRow = sheet.getLastRowNum();

        for (Map.Entry<Integer, String> e : columnTemplates.entrySet()) {
            int colIdx = e.getKey();
            String template = e.getValue();

            for (int r = headerRowIndex + 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String formula = template.replace("{row}", String.valueOf(r + 1)); // Excel is 1-based

                Cell cell = row.getCell(colIdx);
                if (cell == null) {
                    cell = row.createCell(colIdx, CellType.FORMULA);
                    cell.setCellFormula(formula);
                } else {
                    // If it's not a formula, convert; if it's a formula but different, update
                    if (cell.getCellType() != CellType.FORMULA || !formula.equals(cell.getCellFormula())) {
                        cell.setCellFormula(formula);
                    }
                }
            }
        }
    }
}
```

---

## io/ExcelWriter.java

```java
package com.example.io;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;

@Component
public class ExcelWriter {

    /**
     * Creates a new workbook by cloning the original, replaces the target sheet with reconciled data,
     * and reapplies column formulas based on templates learned from the original target sheet.
     */
    public void writeWithTemplates(Workbook originalWb,
                                   String targetSheetName,
                                   String outputPath,
                                   List<String> headers,
                                   List<Map<String, String>> rows) throws Exception {

        // 1) Learn formula templates from original target sheet
        Sheet originalSheet = originalWb.getSheet(targetSheetName);
        if (originalSheet == null) throw new IllegalArgumentException("Sheet not found: " + targetSheetName);
        var templates = FormulaTemplateExtractor.extractTemplates(originalSheet, 0);

        // 2) Clone original workbook (to keep static sheets)
        Workbook outWb = new XSSFWorkbook();
        // Copy all sheets except we will rebuild targetSheetName
        for (int i = 0; i < originalWb.getNumberOfSheets(); i++) {
            Sheet src = originalWb.getSheetAt(i);
            if (src.getSheetName().equals(targetSheetName)) continue;
            Sheet dst = outWb.createSheet(src.getSheetName());
            copySheet(src, dst);
        }

        // 3) Create the target sheet fresh with reconciled data
        Sheet outSheet = outWb.createSheet(targetSheetName);

        // Header
        Row headerRow = outSheet.createRow(0);
        CellStyle headerStyle = outWb.createCellStyle();
        Font bold = outWb.createFont();
        bold.setBold(true);
        headerStyle.setFont(bold);

        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headerStyle);
        }

        // Data rows
        int r = 1;
        for (Map<String, String> row : rows) {
            Row rr = outSheet.createRow(r++);
            for (int c = 0; c < headers.size(); c++) {
                String col = headers.get(c);
                rr.createCell(c, CellType.STRING).setCellValue(row.getOrDefault(col, ""));
            }
        }

        // Autosize (cap to protect performance)
        for (int i = 0; i < Math.min(headers.size(), 50); i++) outSheet.autoSizeColumn(i);

        // 4) Reapply formulas (if any template exists for those columns)
        FormulaApplier.applyTemplates(outSheet, 0, templates);

        // 5) Write to file
        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            outWb.write(fos);
        }
        outWb.close();
    }

    private static void copySheet(Sheet src, Sheet dst) {
        int maxCol = 0;
        for (int r = src.getFirstRowNum(); r <= src.getLastRowNum(); r++) {
            Row srcRow = src.getRow(r);
            if (srcRow == null) continue;
            Row dstRow = dst.createRow(r);
            for (int c = srcRow.getFirstCellNum(); c < srcRow.getLastCellNum(); c++) {
                Cell srcCell = srcRow.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (srcCell == null) continue;
                Cell dstCell = dstRow.createCell(c, srcCell.getCellType());
                copyCellValue(srcCell, dstCell);
                maxCol = Math.max(maxCol, c);
            }
        }
        for (int c = 0; c <= maxCol; c++) dst.autoSizeColumn(c);
    }

    private static void copyCellValue(Cell src, Cell dst) {
        switch (src.getCellType()) {
            case STRING -> dst.setCellValue(src.getStringCellValue());
            case NUMERIC -> dst.setCellValue(src.getNumericCellValue());
            case BOOLEAN -> dst.setCellValue(src.getBooleanCellValue());
            case FORMULA -> dst.setCellFormula(src.getCellFormula());
            case BLANK -> dst.setBlank();
            default -> dst.setBlank();
        }
        // (Styles, merged regions, etc. can be added if needed)
    }
}
```

---

# How to Run

```bash
mvn -q -DskipTests package

java -jar target/excel-db2-reconciler-1.0.0.jar \
  --app.inputExcelPath=./input.xlsx \
  --app.outputExcelPath=./output.xlsx \
  --app.sheetName=sheet1 \
  --app.keyColumn=ID \
  --app.query="SELECT ID, NAME, AGE FROM YOUR_SCHEMA.YOUR_TABLE" \
  --spring.datasource.url="jdbc:db2://HOST:50000/DBNAME" \
  --spring.datasource.username=DB_USER \
  --spring.datasource.password=DB_PASS
```

---

# Notes & Gotchas

* **Formulas:** We learn a **single template per column** from the first formula we find in that column. Then we apply it to **all rows** (including new DB rows), replacing only the current row number with `{row}`. This preserves ranges/absolute refs that point to other rows.
* **Other sheets:** We **clone** all other sheets, so `VLOOKUP`/`XLOOKUP` to static sheets continue to work.
* **ChangedColumns:** lists only overlapping DB2/Excel columns with differing values.
* **Validation:** If you require “Excel must contain all DB2 columns”, add a small validation step after reading Excel headers and before reconcile.

If you want, I can add:

* Conditional formatting to color `CHANGED` rows/columns.
* CSV export alongside Excel.
* A summary sheet with counts per status.
