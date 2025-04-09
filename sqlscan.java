import java.io.; import java.nio.file.; import java.util.; import java.util.regex.;

public class ProcedureScanner { private static final Pattern CALL_PATTERN = Pattern.compile("\bCALL\s+(CRDW|CRDB)\.([a-zA-Z0-9_]+)\b", Pattern.CASE_INSENSITIVE); private static final Pattern TABLE_USAGE_PATTERN = Pattern.compile( "\b(SELECT|INSERT\s+INTO|UPDATE|DELETE\s+FROM|MERGE\s+INTO)\s+([a-zA-Z0-9_.]+)", Pattern.CASE_INSENSITIVE);

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
    Matcher matcher = TABLE_USAGE_PATTERN.matcher(cleanSql);
    while (matcher.find()) {
        String operation = matcher.group(1).toUpperCase();
        String object = matcher.group(2);
        usages.add(operation + " -> " + object);
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

