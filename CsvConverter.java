package shujiaw;

import com.opencsv.CSVReader;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CsvConverter {

    private String[] headers;
    private Map<String, String[]> validateMap = new HashMap<String, String[]>();
    private List<String[]> list = new ArrayList<String[]>();
    private boolean noError = true;
    private CSVReader reader;
    private int prefixCount = 12;

    private Map<String, String[]> applicantsIdMap = new HashMap<String, String[]>();
    private Map<String, List<String[]>> applicantsNameMap = new HashMap<String, List<String[]>>();
    private Map<String, String> assessmentsMap = new HashMap<String, String>();

    public String startConversion(String wenJuanXinPath, String applicationPath, String assessmentPath) throws IOException {
        init();
        initApplicants(applicationPath);
        initAssessments(assessmentPath);
        convertToList(wenJuanXinPath);
        return writeIntoCsv(noError);
    }

    private void convertToList(String wenJuanXinPath) throws UnsupportedEncodingException, FileNotFoundException {
        reader = new CSVReader(new InputStreamReader(new FileInputStream(wenJuanXinPath), "gbk"));
        Iterator<String[]> it = reader.iterator();
        boolean titleFlag = true;
        while (it.hasNext()) {
            String[] nextLine = it.next();
            String[] validatedLine;
            if (titleFlag) {
                validatedLine = nextLine;
                titleFlag = false;
            } else {
                validatedLine = validate(nextLine);
            }
            String[] convertedLine = truncateColumns(validatedLine);
            list.add(convertedLine);
        }
        // Add header
        list.add(1, headers);
    }

    private String[] truncateColumns(String[] values) {
        String[] strs = new String[values.length - prefixCount];
        for (int i = 0; i < strs.length; i++) {
            strs[i] = values[i + prefixCount];
        }
        return strs;
    }

    private String[] validate(String[] values) {
        //List all JBXX cells
        List<Integer> jbList = new ArrayList();
        for (int j = 1; j <= 10; j++) {
            String numStr = String.format("%02d", j);
            int num = getHeaderIndex("JB" + numStr) + prefixCount;
            jbList.add(num);
        }

        for (int i = prefixCount; i < values.length; i++) {
            // Clean invalid word to empty
            values[i] = cleanValue(values[i]);

            // Custom validate: start
            // Fetch SQH number and validate user base info
            String[] applicatnsArgs = applicantsIdMap.get(values[7]);
            if (applicatnsArgs == null) {
                List<String[]> applicatnslist = applicantsNameMap.get(values[13]);
                if (applicatnslist != null) {
                    // Found only one by name
                    if (applicatnslist.size() == 0) {
                        applicatnsArgs = applicatnslist.get(0);
                        values[7] = applicatnsArgs[5];
                    } else if (applicatnslist.size() > 1) {
                        int matchNum = 0;
                        String[] matchedApplicantsArgs = null;
                        // Found multiple by name
                        for (String[] args : applicatnslist) {
                            if (args[4].contains(values[29])) {
                                matchedApplicantsArgs = args;
                                matchNum++;
                            }
                        }
                        if (matchNum == 1) {
                            applicatnsArgs = matchedApplicantsArgs;
                            values[7] = matchedApplicantsArgs[5];
                        } else {
                            values[7] = values[7] + "(Error - 有多位同名者，并且系统无法根据姓名，地区，身份证号来自动判断申请号！)";
                        }
                    } else {
                        applicatnsArgs = applicatnslist.get(0);
                        values[7] = applicatnsArgs[5];
                    }

                }
            }
            if (i == prefixCount) {
                if (applicatnsArgs != null) {
                    values[i] = applicatnsArgs[0];
                }
                if (StringUtils.isEmpty(values[i])) {
                    values[i] = "(Error - 总表找不到申请号！)";
                    noError = false;
                }
            }
//            if (i == 13) {
//                String name = applicatnsArgs == null ? "根据姓名和身份证号" + values[7] + "未找到" : applicatnsArgs[1];
//                if (!values[i].equals(name)) {
//                    values[i] = values[i] + "(Error - 姓名和总表不匹配！[总表：" + name + "])";
//                    noError = false;
//                }
//            }
//            if (i == 14) {
//                String gender = applicatnsArgs == null ? "根据身份证号" + values[7] + "未找到" : applicatnsArgs[2];
//                if (!values[i].equals(gender)) {
//                    values[i] = values[i] + "(Error - 性别和总表不匹配！[总表：" + gender + "])";
//                    noError = false;
//                }
//            }
//            if (i == 15) {
//                String age = applicatnsArgs == null ? "根据身份证号" + values[7] + "未找到" : applicatnsArgs[3];
//                if (!values[i].equals(age)) {
//                    values[i] = values[i] + "(Error - 年龄和总表不匹配！[总表：" + age + "])";
//                    noError = false;
//                }
//            }

            // Validate assessments
            // G2
            if (i == 323) {
                String assessment = assessmentsMap.get(values[i]);
                values[i + 1] = cleanValue(values[i + 1]);
                if (assessment == null || !assessment.equals(values[i + 1])) {
                    values[i] = values[i] + "(Error -  调查员编号和评估人员信息不匹配[评估人员信息表："
                            + (assessment == null ? "未找到" : assessment) + "])";
                }
            }
            // G5
            if (i == 326) {
                String assessment = assessmentsMap.get(values[i]);
                values[i + 1] = cleanValue(values[i + 1]);
                if (assessment == null || !assessment.equals(values[i + 1])) {
                    values[i] = values[i] + "(Error -  调查员编号和评估人员信息不匹配[评估人员信息表："
                            + (assessment == null ? "未找到" : assessment) + "])";
                }
            }

            //Convert A16B
            int index = getHeaderIndex("A16B") + prefixCount;
            if (i == index) {
                if (!StringUtils.isEmpty(values[index]) && "2".equals(values[index - 1])) {
                    values[index] = "崇明话";
                }
            }

            //Convert JBXX 0 to empty
            if (jbList.contains(i)) {
                if ("0".equals(values[i])) {
                    values[i] = "";
                }
            }

            //Convert A28A, A28B, A29A, A29B, A29C 0 to empty
            convertEmptyToZero(i, "A28A", values);
            convertEmptyToZero(i, "A28B", values);
            convertEmptyToZero(i, "A29A", values);
            convertEmptyToZero(i, "A29B", values);
            convertEmptyToZero(i, "A29C", values);

            // Custom validate: End

            // If it has been validated above, then no need check again.
            if (values[i].contains("Error")) {
                continue;
            }
            // Validate by configuration
            String[] validaters = validateMap.get(headers[i - prefixCount]);
            if (validaters != null) {
                for (String validater : validaters) {
                    if ("Required".equals(validater) && StringUtils.isEmpty(values[i])) {
                        values[i] = values[i] + "(Error - 必填字段不能为空！)";
                        noError = false;
                    }
                    if ("Number".equals(validater) && !isNumeric(values[i])) {
                        values[i] = values[i] + "(Error - 字段需要为数字！)";
                        noError = false;
                    }
                    if ("Date".equals(validater) && !isValidDate(values[i])) {
                        values[i] = values[i] + "(Error - 字段需要为日期YYYYMMDD！)";
                        noError = false;
                    }
                    if (validater.startsWith("Depend") && !isValidDepend(validater, values[i], values)) {
                        values[i] = values[i] + "(Error - 当" + validater.substring(6, validater.length())
                                + "有值时，字段不能为空！)";
                        noError = false;
                    }
                    if (validater.startsWith("Range")) {
                        String rangStr = validater.substring(5, validater.length());
                        if (!rangStr.contains(values[i])) {
                            values[i] = values[i] + "(Error - 字段需要为"
                                    + Arrays.toString(rangStr.split("-")).replaceAll(",", "，") + "！)";
                            noError = false;
                        }
                    }
                }
            }

        }
        return values;
    }

    private void convertEmptyToZero(int i, String header, String[] values) {
        if (i == (getHeaderIndex(header) + prefixCount)) {
            if (StringUtils.isEmpty(values[i])) {
                values[i] = "0";
            }
        }
    }

    private void init() throws IOException {
        Properties properties = new Properties();
//        InputStream in = CsvConverter.class.getClassLoader().getResourceAsStream("C:\\Users\\shujiaw\\JavaTest\\src\\main\\java\\work\\conf.properties");

        InputStream inputStream = new FileInputStream("conf.properties");
        properties.load(inputStream);
        String headerStr = properties.getProperty("Header");
        headers = headerStr.split(",");
        for (String column : headers) {
            String columnValidaterStr = properties.getProperty(column);
            if (columnValidaterStr != null) {
                String[] columnValidaters = columnValidaterStr.split(";");
                validateMap.put(column, columnValidaters);
            }
        }
    }

    private void initApplicants(String applicationPath) throws IOException {
        Workbook wb = null;
        Sheet sheet = null;
        Row row = null;
        wb = readExcel(applicationPath);
        if (wb != null) {
            sheet = wb.getSheetAt(0);
            int rownum = sheet.getPhysicalNumberOfRows();
            // Ignore the title
            for (int i = 1; i < rownum; i++) {
                row = sheet.getRow(i);
                if (row != null) {
                    String assessmentStatus = getCellFormatValue(row.getCell(23));
//                    if ("评估机构已确认".equals(assessmentStatus) ||
//                            "受理完成".equals(assessmentStatus)) {
                    String sqh = getCellFormatValue(row.getCell(0));
                    String name = getCellFormatValue(row.getCell(1));
                    String gender = getCellFormatValue(row.getCell(2)).equals("男") ? "1" : "2";
                    String id = getCellFormatValue(row.getCell(3));
                    String age = getCellFormatValue(row.getCell(5));
                    String district = getCellFormatValue(row.getCell(6));
                    String[] args = new String[6];
                    args[0] = sqh;
                    args[1] = name;
                    args[2] = gender;
                    args[3] = age;
                    args[4] = district;
                    args[5] = id;
                    applicantsIdMap.put(id, args);
                    List<String[]> list = applicantsNameMap.get(name);
                    if (list == null) {
                        list = new ArrayList<String[]>();

                    }
                    list.add(args);
                    applicantsNameMap.put(name, list);
//                    }
                }
            }
        }
    }

    private void initAssessments(String assessmentPath) throws IOException {
        Workbook wb = null;
        Sheet sheet = null;
        Row row = null;
        wb = readExcel(assessmentPath);
        if (wb != null) {
            // 获取第一个sheet
            sheet = wb.getSheetAt(0);
            // 获取最大行数
            int rownum = sheet.getPhysicalNumberOfRows();
            // Ignore the title
            for (int i = 1; i < rownum; i++) {
                row = sheet.getRow(i);
                if (row != null) {
                    String id = getCellFormatValue(row.getCell(3));
                    String name = getCellFormatValue(row.getCell(4));
                    assessmentsMap.put(id, name);
                }
            }
        }
    }

    private String writeIntoCsv(boolean noError) throws IOException, UnsupportedEncodingException {
        String fileName = noError ? "修订后的文件.csv" : "需修改后使用.csv";
        PrintWriter writer = new PrintWriter(fileName, "gbk");
        for (String[] strs : list) {
            String line = Arrays.toString(strs).replace(" ","");
            writer.println(line.substring(1, line.length() - 1));
        }
        writer.close();
        return fileName;
    }

    private String cleanValue(String str) {
        str = StringUtils.deleteWhitespace(str);
        if (str == null || str.trim().equals("") || str.trim().equals("(空)")) {
            return "";
        } else if (isNumeric(str.trim()) && Double.valueOf(str.trim()) < 0) {
            return "";
        } else {
            return str.trim();
        }

    }

    private boolean isNumeric(String str) {
        // 该正则表达式可以匹配所有的数字 包括负数
        Pattern pattern = Pattern.compile("-?[0-9]+(\\.[0-9]+)?");
        String bigStr;
        try {
            bigStr = new BigDecimal(str).toString();
        } catch (Exception e) {
            return false;// 异常 说明包含非数字。
        }

        Matcher isNum = pattern.matcher(bigStr); // matcher是全匹配
        if (!isNum.matches()) {
            return false;
        }
        return true;
    }

    // 读取excel
    public Workbook readExcel(String filePath) throws IOException {
        Workbook wb = null;
        if (filePath == null) {
            return null;
        }
        String extString = filePath.substring(filePath.lastIndexOf("."));
        InputStream is = new FileInputStream(filePath);
        if (".xls".equals(extString)) {
            wb = new HSSFWorkbook(is);
        } else if (".xlsx".equals(extString)) {
            wb = new XSSFWorkbook(is);
        }
        return wb;
    }

    @SuppressWarnings("deprecation")
    public String getCellFormatValue(Cell cell) {
        Object cellValue = null;
        if (cell != null) {
            // 判断cell类型
            switch (cell.getCellType()) {
                case Cell.CELL_TYPE_NUMERIC: {
                    cellValue = String.valueOf(cell.getNumericCellValue());
                    break;
                }
                case Cell.CELL_TYPE_FORMULA: {
                    // 判断cell是否为日期格式
                    if (DateUtil.isCellDateFormatted(cell)) {
                        // 转换为日期格式YYYY-mm-dd
                        cellValue = cell.getDateCellValue();
                    } else {
                        // 数字
                        cellValue = String.valueOf(cell.getNumericCellValue());
                    }
                    break;
                }
                case Cell.CELL_TYPE_STRING: {
                    cellValue = cell.getRichStringCellValue().getString();
                    break;
                }
                default:
                    cellValue = "";
            }
        } else {
            cellValue = "";
        }
        return ((String) cellValue).trim();
    }

    public boolean isValidDate(String str) {
        boolean convertSuccess = true;
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
        try {
            format.setLenient(false);
            format.parse(str);
        } catch (ParseException e) {
            convertSuccess = false;
        }
        return convertSuccess;
    }

    public boolean isValidDepend(String validater, String str, String[] values) {
        if (StringUtils.isEmpty(str)) {
            String paramStr = validater.substring(6, validater.length());
            String[] params = {};
            if (validater.contains("=")) {
                params = paramStr.split("=");

            } else if (validater.contains("in")) {
                params = paramStr.split("in");
            }
            if (!StringUtils.isEmpty(values[getHeaderIndex(params[0]) + prefixCount]) && params[1].contains(values[getHeaderIndex(params[0]) + prefixCount])) {
                return false;
            }
        }
        return true;
    }

    private int getHeaderIndex(String header) {
        for (int i = 0; i < headers.length; i++) {
            if (header.equals(headers[i])) {
                return i;
            }
        }
        throw new RuntimeException("配置文件有问题：" + header);
    }
}
