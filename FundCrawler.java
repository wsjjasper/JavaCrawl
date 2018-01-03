package fundSpider;


import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import util.HTMLReader;

import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 商城爬虫
 */
public class FundCrawler {

    private HTMLReader htmlReader = new HTMLReader();
    Set<String> allurlSet = new HashSet<>();//所有的网页url，用来去重
    ArrayList<String> notCrawlurlSet = new ArrayList<>();//未爬过的网页url
    static Map<String, FundBean> fundMap = new HashMap<>();

    public static void main(String[] args) throws InterruptedException {
        long start = System.currentTimeMillis();
        final FundCrawler wc = new FundCrawler();
        wc.parseHomePage("http://fund.eastmoney.com/LJ_jzzzl.html#os_0;isall_0;ft_;pt_11");
        System.out.println("开始爬虫.........................................");
        ExecutorService executorService = Executors.newFixedThreadPool(100);
        wc.begin(executorService);
        executorService.shutdown();
        executorService.awaitTermination(999999999999l, TimeUnit.MINUTES);
        long end = System.currentTimeMillis();
        System.out.println("总共爬了" + wc.allurlSet.size() + "个网页");
        System.out.println("总共耗时" + (end - start) / 1000 + "秒");
        System.out.println("总共耗时" + (end - start) / 1000 + "秒");
        fundMap.size();
    }

    private void begin(ExecutorService executorService) {
        for (final String url : allurlSet) {
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    crawler(url);
                }
            });
        }
    }

    public synchronized void addUrl(String url) {
        notCrawlurlSet.add(url);
        allurlSet.add(url);
    }

    //爬基金详情Url
    public void crawler(String sUrl) {
        String detailContent = htmlReader.readStreamToStr(sUrl);
        String fundCode = sUrl.replace("http://fund.eastmoney.com/f10/F10DataApi.aspx?type=lsjz&code=", "").replace("&page=1&per=99999", "");
        FundBean fund = fundMap.get(fundCode);
        if (fund != null) {
            Document detailDoc = Jsoup.parse(detailContent);
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
            for (Element tr : detailDoc.getElementsByTag("tr")) {
                if (tr.getElementsByTag("td").size() != 0) {
                    FundDetail fd = new FundDetail();
                    String dateStr = tr.getElementsByTag("td").get(0).text();
                    try {
                        fd.setValueDate(format.parse(dateStr));
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    fd.setValue(convertFromStr(tr.getElementsByTag("td"), 1));
                    fd.setAggregateValue(convertFromStr(tr.getElementsByTag("td"), 2));
                    fd.setDailyGrowth(convertFromStr(tr.getElementsByTag("td"), 3));
                    fund.getDetails().add(fd);
                }
            }
        }
    }

    private Double convertFromStr(Elements els, int index) {
        String str = null;
        try{
            str = els.get(index).text();
        }catch(Exception e){
            e.printStackTrace();
        }
        if (str != null && !str.trim().equals("")) {
            if (str.contains("%")) {
                str = str.replace("%", "");
                return Double.parseDouble(str) * 0.01;
            }
            return Double.parseDouble(str);
        }
        return null;
    }


    //从获取主页上分类的url
    public void parseHomePage(String sUrl) {
        InputStream homePageIs = null;
        try {
            String content = htmlReader.readStreamToStr(sUrl);
            List<String> matchedUrls = htmlReader.findMatchUrls(content, "href=\"\\S*_jzzzl\\.html", "href=\"|\"");
            if (matchedUrls != null) {
                for (String matchedUrl : matchedUrls) {
                    //Skip 场内基金
                    if (matchedUrl.contains("cnjy")) {
                        continue;
                    }
                    matchedUrl += "#os_0;isall_1;ft_;pt_2";
                    String fundTypeContent = htmlReader.readStreamToStr(matchedUrl);
                    Pattern pattern = Pattern.compile("<tr id=\"\\S*\"");
                    Document doc = Jsoup.parse(fundTypeContent);
                    Matcher matcher = pattern.matcher(fundTypeContent);
                    //Find all fund info and related fund detail urls, add them into a set for multiple thread use
                    while (matcher.find()) {
                        String fundId = matcher.group().replaceAll("<tr id=\"", "").replaceAll("\"", "");
                        String fundCode = fundId.replaceAll("tr", "");
                        String fundName = doc.getElementById(fundId).getElementsByTag("nobr").get(0).children().get(0).text();
                        String detailUrl = "http://fund.eastmoney.com/f10/F10DataApi.aspx?type=lsjz&code=" + fundCode + "&page=1&per=99999";
                        FundBean fund = new FundBean();
                        fund.setFundName(fundName);
                        fund.setFundCode(fundCode);
                        fund.setFundUrl(matchedUrl);
                        fund.setDetailUrl(detailUrl);
                        fundMap.put(fundCode, fund);
                        addUrl(detailUrl);
                    }
                }
                System.out.println("All fund detail urls are set, count: " + allurlSet.size());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (homePageIs != null) {
                try {
                    homePageIs.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
