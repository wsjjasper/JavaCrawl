package work;

import com.opencsv.CSVReader;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Iterator;

public class ReadCSV2 {
    private static String NULL = "(空)";
    private static String[] GENDER_MATCHES = {"男", "女"};

    public static void main(String[] args) throws Exception {

        CSVReader reader = new CSVReader(new InputStreamReader(
                new FileInputStream("\\\\znn6f2\\u_t1464156519\\shujiaw\\Desktop\\Others\\non-work\\34.csv"), "gbk"));
        //XML file
        PrintWriter writer = new PrintWriter("\\\\znn6f2\\u_t1464156519\\shujiaw\\Desktop\\Others\\non-work\\result.xml", "UTF-8");
        //Error report
        PrintWriter writerError = new PrintWriter("\\\\znn6f2\\u_t1464156519\\shujiaw\\Desktop\\Others\\non-work\\error.csv", "UTF-8");
        writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        writer.println("<XMLDATA>");
        writer.println("<MAIN>");
        writer.println("<SQLIST>");
        Iterator<String[]> it = reader.iterator();
        String[] headers = {};
        int line = 1;
        while (it.hasNext()) {
            String[] nextLine = it.next();

            if (line == 2) {
                headers = nextLine;
            } else if(line > 2) {
                writer.println("<SQ>");
                for (int i = 0; i < nextLine.length; i++) {
                    writer.println("<" + headers[i] + ">" + nextLine[i] + "</" + headers[i] + ">");
                }
                writer.println("</SQ>");
            }
            line++;
        }
        writer.println("</SQLIST>");
        writer.println("</MAIN>");
        writer.println("</XMLDATA>");
        writer.close();
        writerError.close();//TODO move into finally block
    }

    private static String cleanStr(String[] strs, int num, boolean required) {
        String str = strs[num];
        if (required && (str == null || str.equals(NULL) || str.trim().equals(""))) {
            throw new RuntimeException("第" + num + "列！必填字段不能为空！");
        }
        if (str != null && str.equals(NULL)) {
            return "";
        } else {
            return str.trim();//TODO consider if need remove start space
        }
    }

    private static String cleanStr(String[] strs, int num, boolean required, String[] matches) {
        String str = cleanStr(strs, num, required);
        int responseNum = -1;
        for (int i = 0; i < matches.length; i++) {
            if (str.equals(matches[i])) {
                responseNum = i + 1;
                break;
            }
        }
        if (responseNum > -1) {
            return String.valueOf(responseNum);
        } else {
            throw new RuntimeException("第" + num + "列！输入的值[" + str + "]不正确，应该为(" + Arrays.toString(matches) + ")中的一种!");
        }
    }
}
