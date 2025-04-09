import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class ProcedureScanner {
    private static final Pattern CALL_PATTERN = Pattern.compile("\\bCALL\\s+(CRDW|CRDB)\\.([A-Za-z0-9_]+)\\b", Pattern.CASE_INSENSITIVE);

    // Identifier supporting quoted ("My Table") and unquoted (SCHEMA.TABLE) names
    private static final String IDENTIFIER = "(?:\"[^\"]+\"|[A-Za-z0-9_]+)(?:\\.(?:\"[^\"]+\"|[A-Za-z0-9_]+))*";
    private static final Pattern SELECT_FROM_PATTERN = Pattern.compile("\\bFROM\\s+(?!\\()((?:" + IDENTIFIER + "\s*,\s*)*" + IDENTIFIER + ")", Pattern.CASE_INSENSITIVE);
    String regexAll = 
    "(?i)"              // case‑insensitive
  + "(?:\\bfrom\\s*|"   //  either start at "from "
  +   "\\G\\s*,\\s*)"   //  or continue at end of last match + a comma
  + "([A-Za-z_]\\w*"    //  capture one identifier
  +   "(?:\\.[A-Za-z_]\\w*)?"  //  optional ".schema"
  + ")";

Pattern p2 = Pattern.compile(regexAll);
    private static final Pattern JOIN_PATTERN        = Pattern.compile("\\bJOIN\\s+(?!\\()(" + IDENTIFIER + ")", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSERT_INTO_PATTERN= Pattern.compile("\\bINSERT\\s+INTO\\s+(" + IDENTIFIER + ")", Pattern.CASE_INSENSITIVE);
    private static final Pattern UPDATE_PATTERN     = Pattern.compile("\\bUPDATE\\s+(" + IDENTIFIER + ")", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE_FROM_PATTERN= Pattern.compile("\\bDELETE\\s+FROM\\s+(" + IDENTIFIER + ")", Pattern.CASE_INSENSITIVE);
    private static final Pattern MERGE_INTO_PATTERN  = Pattern.compile("\\bMERGE\\s+INTO\\s+(" + IDENTIFIER + ")", Pattern.CASE_INSENSITIVE);
    private static final Pattern SELECT_INTO_FROM_PATTERN = Pattern.compile("\\bSELECT\\s+.+?\\s+INTO\\s+.+?\\s+FROM\\s+(" + IDENTIFIER + ")", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TABLE_FUNCTION_PATTERN = Pattern.compile("\\bFROM\\s+TABLE\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DYNAMIC_SQL_PATTERN= Pattern.compile("\\bEXECUTE\\s+IMMEDIATE\\b", Pattern.CASE_INSENSITIVE);

    public static Map<String, Set<String>> scanProcedures(String folderPath, List<String> procedureNames) throws IOException {
        Map<String, String> sqlFiles = loadSqlFiles(folderPath);
        Map<String, Set<String>> procedureCalls = new HashMap<>();
        Set<String> allFoundProcedures = new HashSet<>();

        for (String procedure : procedureNames) {
            if (sqlFiles.containsKey(procedure)) {
                Set<String> nested = new HashSet<>();
                findAllNestedProcedures(procedure, sqlFiles, nested);
                procedureCalls.put(procedure, nested);
                allFoundProcedures.add(procedure);
                allFoundProcedures.addAll(nested);
            }
        }

        System.out.println("All found stored procedures: " + allFoundProcedures);

        for (String proc : allFoundProcedures) {
            if (sqlFiles.containsKey(proc)) {
                String content = sqlFiles.get(proc);
                Set<String> usages = findTableUsages(content);
                boolean dynamic = hasDynamicSQL(content);
                System.out.println("Procedure: " + proc + (dynamic ? " [dynamic SQL]" : "") + " uses: " + usages);
            }
        }

        return procedureCalls;
    }

    private static Map<String, String> loadSqlFiles(String folderPath) throws IOException {
        Map<String, String> sqlFiles = new HashMap<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(folderPath), "*.sql")) {
            for (Path path : stream) {
                String name = path.getFileName().toString().replaceFirst("\\.sql$", "");
                sqlFiles.put(name, Files.readString(path));
            }
        }
        return sqlFiles;
    }

    private static void findAllNestedProcedures(String proc, Map<String, String> sqlFiles, Set<String> result) {
        if (!sqlFiles.containsKey(proc)) return;
        Set<String> direct = findNestedProcedures(sqlFiles.get(proc), sqlFiles.keySet());
        for (String called : direct) {
            if (result.add(called)) {
                findAllNestedProcedures(called, sqlFiles, result);
            }
        }
    }

    private static Set<String> findNestedProcedures(String sql, Set<String> known) {
        Set<String> out = new HashSet<>();
        String clean = removeCommentsAndCTEs(sql);
        Matcher m = CALL_PATTERN.matcher(clean);
        while (m.find()) {
            String called = m.group(2);
            if (known.contains(called)) out.add(called);
        }
        return out;
    }

    private static Set<String> findTableUsages(String sql) {
        Set<String> usages = new HashSet<>();
        String clean = removeCommentsAndCTEs(sql);
        extract(clean, SELECT_FROM_PATTERN, "SELECT", usages);
        extract(clean, SELECT_INTO_FROM_PATTERN, "SELECT", usages);
        extract(clean, JOIN_PATTERN,        "JOIN",   usages);
        extract(clean, INSERT_INTO_PATTERN,"INSERT", usages);
        extract(clean, UPDATE_PATTERN,     "UPDATE", usages);
        extract(clean, DELETE_FROM_PATTERN,"DELETE", usages);
        extract(clean, MERGE_INTO_PATTERN,  "MERGE",  usages);
        extract(clean, TABLE_FUNCTION_PATTERN, "TABLE_FUNC", usages);
        return usages;
    }

    private static void extract(String sql, Pattern p, String op, Set<String> out) {
        Matcher m = p.matcher(sql);
        while (m.find()) {
            String obj = m.group(1).trim();
            if (!obj.isEmpty()) out.add(op + " -> " + obj);
        }
    }

    private static String removeCommentsAndCTEs(String sql) {
        // Strip CTEs at the start: WITH ... AS (...)
        sql = sql.replaceAll("(?is)^\\s*WITH\\s+[\\s\\S]+?\\)\\s*", "");
        // Strip block comments
        sql = sql.replaceAll("(?s)/\\*.*?\\*/", "");
        // Strip line comments
        sql = sql.replaceAll("(?m)--.*$", "");
        return sql;
    }

    private static boolean hasDynamicSQL(String sql) {
        return DYNAMIC_SQL_PATTERN.matcher(removeCommentsAndCTEs(sql)).find();
    }

    public static void main(String[] args) throws IOException {
        String folderPath = "path/to/sql/folder";
        List<String> procedureNames = Arrays.asList("sp_123", "sp_456");
        scanProcedures(folderPath, procedureNames);
    }
}
