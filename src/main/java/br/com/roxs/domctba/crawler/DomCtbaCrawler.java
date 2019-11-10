package br.com.roxs.domctba.crawler;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.http.client.protocol.HttpClientContext;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class DomCtbaCrawler {

	private static final String DOM_BASE_URL = "https://legisladocexterno.curitiba.pr.gov.br";

	private static final String DOM_URL = DOM_BASE_URL + "/DiarioConsultaExterna_Pesquisa.aspx";

	private static final File DOWNLOAD_ROOT = new File("diarios_oficiais");

	public static void main(String[] args) throws Exception {

		DOWNLOAD_ROOT.mkdirs();

		HttpClientContext context = Util.createHttpClientContext();

		String page = Util.get(DOM_URL, context);

		Map<String, String> paramsBase = Util.getFormInputs(page);

		int tabIndex = 9;

		Map<String, String> m = new HashMap<String, String>(paramsBase);

		m.put("ctl00$smrAjax", "ctl00$cphMasterPrincipal$upPesquisaExternaDO|ctl00$cphMasterPrincipal$TabContainer1");
		m.put("ctl00_cphMasterPrincipal_TabContainer1_ClientState", "{\"ActiveTabIndex\":" + tabIndex + ",\"TabState\":[true,true,true,true,true,true,true,true,true,true,true,true]}");

		m.put("__EVENTTARGET", "ctl00$cphMasterPrincipal$TabContainer1");
		m.put("__EVENTARGUMENT", "activeTabChanged:" + tabIndex);
		m.put("__ASYNCPOST", "true");

		Map<String, String> headers = getHeaders();

		page = Util.post(DOM_URL, DOM_URL, m, headers, context);
		updateParams(page, m);

		Document doc = Jsoup.parse(page);

		Set<Integer> paginas = new TreeSet<Integer>();

		for (Element e : doc.select(".grid_Pager a")) {
			paginas.add(new Integer(e.text().trim()));
		}

		for (Element edicao : doc.select(".grid_Row")) {
			File f = parseEdicao(edicao, m, headers, context);
			System.out.println(f);
		}

		for (Integer p : paginas) {

			m.put("ctl00$smrAjax", "tl00$cphMasterPrincipal$upPesquisaExternaDO|ctl00$cphMasterPrincipal$gdvGrid2");
			m.put("__EVENTTARGET", "ctl00$cphMasterPrincipal$gdvGrid2");
			m.put("__EVENTARGUMENT", "Page$" + p);

			page = Util.post(DOM_URL, DOM_URL, m, headers, context);
			updateParams(page, m);

			doc = Jsoup.parse(page);

			for (Element edicao : doc.select(".grid_Row")) {
				File f = parseEdicao(edicao, m, headers, context);
				System.out.println(f);
			}
		}

	}

	private static File parseEdicao(Element edicaoElm, Map<String, String> m, Map<String, String> headers, HttpClientContext context) throws Exception {

		Elements cols = edicaoElm.select("td");

		String edicao = cols.get(0).text();

		String data = cols.get(1).text();

		SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy");

		SimpleDateFormat df2 = new SimpleDateFormat("yyyy-MM-dd");

		String downloadId = cols.get(2).select("a").get(0).attr("id").replaceAll("_", "\\$");

		File file = new File(DOWNLOAD_ROOT, df2.format(df.parse(data)) + " - " + edicao + ".pdf");

		if (file.exists()) {
			return file;
		}

		m.put("ctl00$smrAjax", "ctl00$cphMasterPrincipal$upPesquisaExternaDO|" + downloadId);
		m.put("__EVENTTARGET", downloadId);
		m.put("__EVENTARGUMENT", "");

		String page = Util.post(DOM_URL, DOM_URL, m, headers, context);

		int idx = page.indexOf("|0|");

		if (idx > 0) {

			String[] tokens = page.substring(idx).split("\\|");

			for (int i = 0; i < tokens.length; i++) {

				String s = tokens[i];

				if (s.contains("window.open")) {

					String[] parts = s.split("'");

					String downloadPage = parts[1];

					String downloadUrl = DOM_BASE_URL + "/" + downloadPage;

					Util.downloadFile(downloadUrl, file);

					break;
				}
			}
		}

		return file;
	}

	private static void updateParams(String page, Map<String, String> m) {

		int idx = page.indexOf("|0|");

		if (idx > 0) {

			String[] tokens = page.substring(idx).split("\\|");

			for (int i = 0; i < tokens.length; i++) {

				String s = tokens[i];

				if ("hiddenField".equals(s)) {
					String paramName = tokens[i + 1];
					String paramValue = tokens[i + 2];

					m.put(paramName, paramValue);
				}
			}
		}
	}

	private static Map<String, String> getHeaders() {
		Map<String, String> headers = new HashMap<String, String>();
		headers.put("X-MicrosoftAjax", "Delta=true");
		headers.put("X-Requested-With", "XMLHttpRequest");
		return headers;
	}

}
