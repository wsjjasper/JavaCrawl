@Service
public class ReconcilerService {

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

        final String STATUS_COL = "MatchStatus";
        final String CHANGED_COLS = "ChangedColumns";
        if (!columnsOrdered.contains(STATUS_COL)) columnsOrdered.add(STATUS_COL);
        if (!columnsOrdered.contains(CHANGED_COLS)) columnsOrdered.add(CHANGED_COLS);

        Set<String> dbKeys = new LinkedHashSet<>();
        for (Map<String, Object> row : db2Rows) {
            Object keyObj = row.get(keyColumn);
            if (keyObj != null) dbKeys.add(String.valueOf(keyObj));
        }

        List<Map<String, String>> outputRows = new ArrayList<>();

        // 1) DB2 rows
        for (Map<String, Object> dbRow : db2Rows) {
            String keyVal = dbRow.get(keyColumn) == null ? "" : String.valueOf(dbRow.get(keyColumn));
            Map<String, String> out = new LinkedHashMap<>();

            // baseline from DB2
            for (String c : dbColumns) {
                Object v = dbRow.get(c);
                out.put(c, v == null ? "" : String.valueOf(v));
            }

            Map<String, String> excelRow = excelByKey.get(keyVal);
            if (excelRow != null) {
                // Add Excel-only columns
                for (String c : excelOnlyColumns) {
                    out.put(c, excelRow.getOrDefault(c, ""));
                }

                // Check for differences in shared columns
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
            }
        }

        return new ReconcileResult(columnsOrdered, outputRows);
    }
}
