import java.io.; import java.nio.file.; import java.util.; import java.util.regex.;

public class ProcedureScanner { private static final Pattern CALL_PATTERN = Pattern.compile("\bCALL\s+(CRDW|CRDB)\.([a-zA-Z0-9_]+)\b", Pattern.CASE_INSENSITIVE); private static final Pattern SELECT_FROM_PATTERN = Pattern.compile("\bFROM\s+([a-zA-Z0-9_.]+)\b", Pattern.CASE_INSENSITIVE); private static final Pattern INSERT_INTO_PATTERN = Pattern.compile("\bINSERT\s+INTO\s+([a-zA-Z0-9_.]+)\b", Pattern.CASE_INSENSITIVE); private static final Pattern UPDATE_PATTERN = Pattern.compile("\bUPDATE\s+([a-zA-Z0-9_.]+)\b", Pattern.CASE_INSENSITIVE); private static final Pattern DELETE_FROM_PATTERN = Pattern.compile("\bDELETE\s+FROM\s+([a-zA-Z0-9_.]+)\b", Pattern.CASE_INSENSITIVE); private static final Pattern MERGE_INTO_PATTERN = Pattern.compile("\bMERGE\s+INTO\s+([a-zA-Z0-9_.]+)\b", Pattern.CASE_INSENSITIVE);

public static Map<String, Set<String>> scanProcedures(String folderPath, List<String> procedureNames) throws IOException {
    Map<String, String> sqlFiles = loadSqlFiles(folderPath);
    Map<String, Set<String>> procedureCalls = new HashMap<>();
    Set<String> allFoundProcedures = new HashSet<>();

    for (String procedure : procedureNames) {
        if (sqlFiles.containsKey(procedure)) {
            Set<String> allNestedCalls = new HashSet<>();
            findAllNestedProcedures(procedure, sqlFiles, allNestedCalls);
            procedureCalls.put(procedure, allNestedCalls);
            allFoundProcedures.add(procedure);
            allFoundProcedures.addAll(allNestedCalls);
        }
    }

    // Print all found stored procedures
    System.out.println("All found stored procedures: " + allFoundProcedures);

    // Print table/view usage
    for (String procedure : allFoundProcedures) {
        if (sqlFiles.containsKey(procedure)) {
            Set<String> usages = findTableUsages(sqlFiles.get(procedure));
            System.out.println("Procedure: " + procedure + " uses tables/views: " + usages);
        }
    }

    return procedureCalls;
}

private static Map<String, String> loadSqlFiles(String folderPath) throws IOException {
    Map<String, String> sqlFiles = new HashMap<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(folderPath), "*.sql")) {
        for (Path path : stream) {
            String procedureName = path.getFileName().toString().replace(".sql", "");
            String content = Files.readString(path);
            sqlFiles.put(procedureName, content);
        }
    }
    return sqlFiles;
}

private static void findAllNestedProcedures(String procedure, Map<String, String> sqlFiles, Set<String> allNestedCalls) {
    if (!sqlFiles.containsKey(procedure)) {
        return;
    }
    Set<String> directCalls = findNestedProcedures(sqlFiles.get(procedure), sqlFiles.keySet());
    for (String calledProcedure : directCalls) {
        if (allNestedCalls.add(calledProcedure)) { // Prevent infinite recursion
            findAllNestedProcedures(calledProcedure, sqlFiles, allNestedCalls);
        }
    }
}

private static Set<String> findNestedProcedures(String sqlContent, Set<String> knownProcedures) {
    Set<String> nestedProcedures = new HashSet<>();
    String cleanSql = removeComments(sqlContent);
    Matcher matcher = CALL_PATTERN.matcher(cleanSql);
    while (matcher.find()) {
        String calledProcedure = matcher.group(2);
        if (knownProcedures.contains(calledProcedure)) {
            nestedProcedures.add(calledProcedure);
        }
    }
    return nestedProcedures;
}

private static Set<String> findTableUsages(String sqlContent) {
    Set<String> usages = new HashSet<>();
    String cleanSql = removeComments(sqlContent);

    // SELECT ... FROM
    Matcher selectMatcher = SELECT_FROM_PATTERN.matcher(cleanSql);
    while (selectMatcher.find()) {
        usages.add("SELECT -> " + selectMatcher.group(1));
    }
    // INSERT INTO
    Matcher insertMatcher = INSERT_INTO_PATTERN.matcher(cleanSql);
    while (insertMatcher.find()) {
        usages.add("INSERT -> " + insertMatcher.group(1));
    }
    // UPDATE
    Matcher updateMatcher = UPDATE_PATTERN.matcher(cleanSql);
    while (updateMatcher.find()) {
        usages.add("UPDATE -> " + updateMatcher.group(1));
    }
    // DELETE FROM
    Matcher deleteMatcher = DELETE_FROM_PATTERN.matcher(cleanSql);
    while (deleteMatcher.find()) {
        usages.add("DELETE -> " + deleteMatcher.group(1));
    }
    // MERGE INTO
    Matcher mergeMatcher = MERGE_INTO_PATTERN.matcher(cleanSql);
    while (mergeMatcher.find()) {
        usages.add("MERGE -> " + mergeMatcher.group(1));
    }

    return usages;
}

private static String removeComments(String sqlContent) {
    sqlContent = sqlContent.replaceAll("(?s)/\\*.*?\\*/", "");
    sqlContent = sqlContent.replaceAll("(?m)--.*$", "");
    return sqlContent;
}

public static void main(String[] args) throws IOException {
    String folderPath = "path/to/sql/folder"; // Change to actual path
    List<String> procedureNames = Arrays.asList("sp_123", "sp_456"); // Example procedures

    Map<String, Set<String>> result = scanProcedures(folderPath, procedureNames);
    result.forEach((proc, calls) -> {
        System.out.println("Procedure: " + proc + " calls " + calls);
    });
}

}

