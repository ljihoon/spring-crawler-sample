package makeshop;

import com.google.common.base.Strings;
import common.Price;
import common.Product;
import edu.uci.ics.crawler4j.crawler.Page;
import edu.uci.ics.crawler4j.crawler.WebCrawler;
import edu.uci.ics.crawler4j.parser.HtmlParseData;
import edu.uci.ics.crawler4j.url.WebURL;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import regex.CategoryPattern;
import regex.IDPattern;
import regex.RegexGenerator;
import regex.URLPattern;

public class MakeShopCrawler extends WebCrawler {

  // 중복 URL 수집 불가
  private Set<String> urls = new HashSet<>();
  private List<String> permitURLs = new ArrayList<>();
  private Map<String, String> categoryMap = new HashMap<>();
  private String domain;

  public MakeShopCrawler() {
    logger.info("new");
  }

  @Override
  public boolean shouldVisit(Page referringPage, WebURL url) {

    if (Strings.isNullOrEmpty(domain)) {
      domain = referringPage.getWebURL().getDomain();
      logger.info("domain : {}", domain);
    }
    //logger.info("url : {}", url.toString());
    if (!url.getDomain().equals(domain)) {
      return false;
    }
    if (!url.getPath().startsWith("/shop/shopdetail.html") && permitURLs.stream().noneMatch(u -> u.startsWith(url.getPath()))) {
      return false;
    }
    collectCategory(referringPage);
//    logger.info("shouldVisit : {}", url.toString());
    return true;
  }

  @Override
  public void visit(Page page) {
    HtmlParseData data = (HtmlParseData) page.getParseData();

    WebURL webURL = page.getWebURL();
    Document doc = Jsoup.parse(data.getHtml());

    // 1. 품절 체크
    // 변경되는 부분 품절 체크 타겟 정보
    /**/
    Elements element = doc.select("div.prd-btns a");
    if (element.size() == 0) {
      logger.debug("품절상품 입니다.");
      return;
    }
    /**/
    /*
    // 허닭 용
    Elements element = doc.select("div.box-buy-btns>ul");
    if (element.size() == 0) {
      logger.error("품절상품 입니다.");
      return;
    }
    */

    // 2. 중복 체크
    RegexGenerator regexGenerator = new RegexGenerator(IDPattern.MAKE_SHOP, URLPattern.MAKE_SHOP, CategoryPattern.MAKE_SHOP);
    URL url = new URL("http://" + webURL.getDomain(), webURL.getPath());
    String id = regexGenerator.generateId(webURL.toString());

    String newURL = url.getCombineURL() + "?branduid=" + id;

    if (urls.contains(newURL)) {
      return;
    }
//    logger.info("visit : {}", webURL.toString());
    urls.add(newURL);

    Product product = new Product();
    product.setId(id);
    product.setLink(newURL);
    product.setTitle(doc.select("h3.tit-prd").text());
    product.setPrice(new Price(doc.select("input[name='price']").attr("value")));
    product.setImageLink(url.getHost() + doc.select("div.thumb-wrap>.thumb img").attr("src"));
    product.setCategoryName1(categoryMap.get(regexGenerator.generateCategory(webURL.toString())));

    System.out.println(product.toString());
  }

  /**
   * 상품에 맵핑 시키기 위한 카테고리 정보를 생성한다.
   */
  private void collectCategory(Page page) {
    if (categoryMap.size() == 0) {
      HtmlParseData data = (HtmlParseData) page.getParseData();
      //logger.info("data {}", data);
      if (data != null) {
        Document document = null;
        try {
          document = Jsoup.connect(page.getWebURL().toString()).get();
        } catch (IOException e) {
          e.printStackTrace();
        }

        Pattern pattern = Pattern.compile("(xcode=([0-9]+))");
        Matcher codeMatcher;
        assert document != null;

        Elements elements = document.select("a[href^=/shop/shopbrand.html]");
        for (Element el : elements) {
          //System.out.println(el);
          if (!Strings.isNullOrEmpty(el.text()) && el.attr("href").indexOf("mcode") <= 0) {
            String link = el.attr("href");
            codeMatcher = pattern.matcher(link);
            String key = codeMatcher.find() ? codeMatcher.group(2) : "Crawling";
            String categoryName = el.text();
            categoryMap.put(key, categoryName);
            permitURLs.add(link);
          }
        }
        logger.info("category map : {}", categoryMap);
        logger.info("permitURLs : {}", permitURLs);
      }
    }
  }
}