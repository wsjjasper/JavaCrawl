Awesome — I’ll give you a clean, production-ready Spring Boot CLI you can run with Java 17. It reads `sheet1` in an Excel file, queries DB2, reconciles by a key column, and writes a new Excel with DB2 as the baseline + Excel-only extra columns + a `MatchStatus`.

Below is a minimal but complete implementation (Maven). You can copy these files into a new project and run.

---

### 1) `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
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
    <!-- Spring Boot CLI + JDBC -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>

    <!-- DB2 JDBC driver (version may vary in your repo) -->
    <dependency>
      <groupId>com.ibm.db2</groupId>
      <artifactId>jcc</artifactId>
      <version>11.5.9.0</version>
    </dependency>

    <!-- Apache POI for Excel -->
    <dependency>
      <groupId>org.apache.poi</groupId>
      <artifactId>poi-ooxml</artifactId>
      <version>5.2.5</version>
    </dependency>

    <!-- Optional: annotation-based getters/setters -->
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

### 2) `src/main/resources/application.yml`

```yaml
app:
  inputExcelPath: "./input.xlsx"
  sheetName: "sheet1"
  keyColumn: "ID"
  outputExcelPath: "./output.xlsx"
  # Example query: must include the key column and all DB2 columns you want as baseline
  query: "SELECT ID, NAME, AGE FROM YOUR_SCHEMA.YOUR_TABLE"

spring:
  datasource:
    url: "jdbc:db2://HOST:50000/DBNAME"
    username: "DB_USER"
    password: "DB_PASS"
    driver-class-name: "com.ibm.db2.jcc.DB2Driver"

# Logging tweaks
logging:
  level:
    root: INFO
    com.example: INFO
```

---

### 3) `src/main/java/com/example/App.java`

```java
package com.example;

import com.example.core.ReconcilerService;
import com.example.io.Db2DataFetcher;
import com.example.io.ExcelReader;
import com.example.io.ExcelWriter;
import com.example.model.ReconcileResult;
import com.example.support.AppProps;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

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
            // 1) Read Excel
            if (!Files.exists(Path.of(props.getInputExcelPath()))) {
                throw new IllegalArgumentException("Input Excel not found: " + props.getInputExcelPath());
            }
            ExcelReader.ExcelData excelData = excelReader.readExcel(props.getInputExcelPath(), props.getSheetName());

            // 2) Query DB2
            List<Map<String, Object>> db2Rows = db2DataFetcher.fetch(props.getQuery());

            if (db2Rows.isEmpty()) {
                System.out.println("DB2 query returned 0 rows. Output will contain NEW rows from Excel only.");
            }

            // 3) Reconcile (DB2 baseline, Excel extra columns, status flag)
            ReconcileResult result = reconciler.reconcile(
                    db2Rows,
                    excelData.rowsByKey(props.getKeyColumn()),
                    excelData.getAllColumns(),
                    props.getKeyColumn()
            );

            // 4) Write output Excel
            excelWriter.write(props.getOutputExcelPath(), result.getColumnsOrdered(), result.getRows());
            System.out.println("Done. Wrote: " + props.getOutputExcelPath());
        };
    }
}
```

---

### 4) `src/main/java/com/example/support/AppProps.java`

```java
package com.example.support;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProps {
    private String inputExcelPath;
    private String sheetName;
    private String keyColumn;
    private String outputExcelPath;
    private String query;

    public String getInputExcelPath() { return inputExcelPath; }
    public void setInputExcelPath(String inputExcelPath) { this.inputExcelPath = inputExcelPath; }
    public String getSheetName() { return sheetName; }
    public void setSheetName(String sheetName) { this.sheetName = sheetName; }
    public String getKeyColumn() { return keyColumn; }
    public void setKeyColumn(String keyColumn) { this.keyColumn = keyColumn; }
    public String getOutputExcelPath() { return outputExcelPath; }
    public void setOutputExcelPath(String outputExcelPath) { this.outputExcelPath = outputExcelPath; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
}
```

---

### 5) `src/main/java/com/example/io/ExcelReader.java`

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
        private final List<Map<String, String>> rows; // each row: column -> String value
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
            if (sheet == null) {
                throw new IllegalArgumentException("Sheet not found: " + sheetName);
            }

            Iterator<Row> rowIterator = sheet.rowIterator();
            if (!rowIterator.hasNext()) {
                return new ExcelData(Collections.emptyList(), Collections.emptyList());
            }

            // Header
            Row headerRow = rowIterator.next();
            List<String> headers = new ArrayList<>();
            for (Cell c : headerRow) {
                headers.add(cellToString(c).trim());
            }

            // Rows
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
        for (Cell c : r) {
            if (c != null && c.getCellType() != CellType.BLANK && !cellToString(c).isBlank()) return false;
        }
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
                // avoid trailing .0
                if (Math.floor(d) == d) yield String.valueOf((long) d);
                yield Double.toString(d);
            }
            case FORMULA -> {
                try {
                    yield c.getStringCellValue();
                } catch (Exception e) {
                    yield Double.toString(c.getNumericCellValue());
                }
            }
            default -> "";
        };
    }
}
```

---

### 6) `src/main/java/com/example/io/Db2DataFetcher.java`

```java
package com.example.io;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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
            // Preserve column order using LinkedHashMap
            Map<String, Object> row = new java.util.LinkedHashMap<>();
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

### 7) `src/main/java/com/example/core/ReconcilerService.java`

```java
package com.example.core;

import com.example.model.ReconcileResult;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ReconcilerService {

    public ReconcileResult reconcile(List<Map<String, Object>> db2Rows,
                                     Map<String, Map<String, String>> excelByKey,
                                     Set<String> excelAllColumns,
                                     String keyColumn) {

        // DB2 baseline columns (ordered from metadata of first row, else empty)
        LinkedHashSet<String> dbColumns = new LinkedHashSet<>();
        if (!db2Rows.isEmpty()) {
            dbColumns.addAll(db2Rows.get(0).keySet());
        }

        // Extra Excel-only columns (exclude any found in DB)
        LinkedHashSet<String> excelOnlyColumns = new LinkedHashSet<>(excelAllColumns);
        excelOnlyColumns.removeAll(dbColumns);

        // Output headers: DB columns + Excel-only + MatchStatus
        List<String> columnsOrdered = new ArrayList<>(dbColumns);
        columnsOrdered.addAll(excelOnlyColumns);
        final String STATUS_COL = "MatchStatus";
        if (!columnsOrdered.contains(STATUS_COL)) {
            columnsOrdered.add(STATUS_COL);
        }

        // Build a quick lookup for DB2 keys
        Set<String> dbKeys = new LinkedHashSet<>();
        for (Map<String, Object> row : db2Rows) {
            Object keyObj = row.get(keyColumn);
            if (keyObj != null) dbKeys.add(String.valueOf(keyObj));
        }

        List<Map<String, String>> outputRows = new ArrayList<>();

        // 1) For every DB2 row: keep DB2 baseline, enrich with Excel-only, status MATCHED/DB_ONLY
        for (Map<String, Object> dbRow : db2Rows) {
            String keyVal = dbRow.get(keyColumn) == null ? "" : String.valueOf(dbRow.get(keyColumn));
            Map<String, String> out = new LinkedHashMap<>();
            // baseline
            for (String c : dbColumns) {
                Object v = dbRow.get(c);
                out.put(c, v == null ? "" : String.valueOf(v));
            }
            // enrich from excel
            Map<String, String> excelRow = excelByKey.get(keyVal);
            if (excelRow != null) {
                for (String c : excelOnlyColumns) {
                    out.put(c, excelRow.getOrDefault(c, ""));
                }
                out.put(STATUS_COL, "MATCHED");
            } else {
                for (String c : excelOnlyColumns) {
                    out.putIfAbsent(c, "");
                }
                out.put(STATUS_COL, "DB_ONLY");
            }
            outputRows.add(out);
        }

        // 2) For Excel rows not present in DB2: NEW
        for (Map.Entry<String, Map<String, String>> e : excelByKey.entrySet()) {
            String key = e.getKey();
            if (!dbKeys.contains(key)) {
                Map<String, String> excelRow = e.getValue();
                Map<String, String> out = new LinkedHashMap<>();
                // include DB columns with blanks (since baseline row doesn't exist)
                for (String c : dbColumns) {
                    out.put(c, "");
                }
                // add excel-only values
                for (String c : excelOnlyColumns) {
                    out.put(c, excelRow.getOrDefault(c, ""));
                }
                // ensure key column present if it's a DB column (often it is)
                if (dbColumns.contains(keyColumn) && out.get(keyColumn).isBlank()) {
                    out.put(keyColumn, key);
                }
                out.put(STATUS_COL, "NEW");
                outputRows.add(out);
            }
        }

        return new ReconcileResult(columnsOrdered, outputRows);
    }
}
```

---

### 8) `src/main/java/com/example/io/ExcelWriter.java`

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

    public void write(String outputPath, List<String> headers, List<Map<String, String>> rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("output");

            // Header style
            CellStyle headerStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            // Header row
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            int r = 1;
            for (Map<String, String> row : rows) {
                Row rr = sheet.createRow(r++);
                for (int c = 0; c < headers.size(); c++) {
                    String col = headers.get(c);
                    rr.createCell(c).setCellValue(row.getOrDefault(col, ""));
                }
            }

            // Autosize (safe cap)
            for (int i = 0; i < Math.min(headers.size(), 50); i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                wb.write(fos);
            }
        }
    }
}
```

---

### 9) `src/main/java/com/example/model/ReconcileResult.java`

```java
package com.example.model;

import java.util.List;
import java.util.Map;

public class ReconcileResult {
    private final List<String> columnsOrdered;
    private final List<Map<String, String>> rows;

    public ReconcileResult(List<String> columnsOrdered, List<Map<String, String>> rows) {
        this.columnsOrdered = columnsOrdered;
        this.rows = rows;
    }

    public List<String> getColumnsOrdered() { return columnsOrdered; }
    public List<Map<String, String>> getRows() { return rows; }
}
```

---

## How it works (quick recap)

* **Baseline:** DB2 rows & columns.
* **Key column:** `app.keyColumn` (must exist in both sources).
* **Excel-only columns:** any columns present in Excel that are **not** in DB2 result set.
* **Statuses:**

  * `MATCHED` — key exists in both
  * `DB_ONLY` — present in DB2 only
  * `NEW` — present in Excel only

---

## Build & Run

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

## Notes / Edge cases handled

* Excel header row defines Excel columns; data rows map by header order.
* DB2 column order is preserved (used as leading columns in output).
* All values are written as strings in output (keeps it simple & predictable).
* Missing columns default to `""`.
* If an Excel NEW row lacks the key in a DB2 column position, we copy the key into that column if it exists.

---

If you want, I can also add:

* A **schema validation** step to ensure “Excel contains all DB2 columns” (your point #3).
* A **CSV** output option.
* A **diff report** (counts & sample IDs for each status).
