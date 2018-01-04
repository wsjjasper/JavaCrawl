package util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HTMLReader {
    private Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("63.217.82.140", 8888));
    private String USER_AGENT = "User-Agent";
    private String FF_BROWSER = "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.0)";
    private String GBK_ENCODE = "gbk";

    private InputStream getUrlStream(String sUrl) throws IOException {
        URL url = new URL(sUrl);
        URLConnection urlconnection = url.openConnection(proxy);
        urlconnection.addRequestProperty(USER_AGENT, FF_BROWSER);
        return urlconnection.getInputStream();
    }

    public String readStreamToStr(String sUrl) {
        StringBuffer sb = new StringBuffer();//sb为爬到的网页内容
        InputStream is = null;
        try {
            is = getUrlStream(sUrl);
            BufferedReader bReader = new BufferedReader(new InputStreamReader(is, GBK_ENCODE));
            String rLine;
            while ((rLine = bReader.readLine()) != null) {
                sb.append(rLine);
            }
            System.out.println("爬网页" + sUrl + "成功, 是由线程" + Thread.currentThread().getName() + "来爬");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return sb.toString();
    }

    public List<String> findMatchUrls(String content, String regex, String replaceSignal) {
        Pattern pt = Pattern.compile(regex);
        Matcher mt = pt.matcher(content);
        System.out.println(content);
        System.out.println(mt);

        List<String> urls = new ArrayList<>();

        while (mt.find()) {
            String url = mt.group().replaceAll(replaceSignal, "");
            if (url.startsWith("http")) {
                urls.add(url);
            }
        }

        return urls;
    }
}
