package br.com.roxs.domctba.crawler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.tika.Tika;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class DomCtbaCrawler {

	private static final StandardAnalyzer STANDARD_ANALYZER = new StandardAnalyzer();

	private static final String DOM_BASE_URL = "https://legisladocexterno.curitiba.pr.gov.br";

	private static final String DOM_URL = DOM_BASE_URL + "/DiarioConsultaExterna_Pesquisa.aspx";

	private static final File DATA_DIR = new File("data");

	private static final File DOWNLOAD_ROOT = new File(DATA_DIR, "diarios_oficiais");

	private static final File LUCENE_DIR = new File(DATA_DIR, "lucene");

	private static final Tika TIKA = new Tika();

	public static void main(String[] args) throws Exception {

		List<File> arquivos = efetuarDownloadDomSeNecessario();

		IndexWriter indexWriter = openIndexWriter();

		try {
			for (File f : arquivos) {
				if (!isFileIndexed(f)) {
					indexFile(indexWriter, f);
				}
			}
		} finally {
			indexWriter.close();
		}

		String query = "\"Segurança do Trabalho\"";

		List<File> files = search(query);

		System.out.println("Resultado da busca por '" + query + "' (" + files.size() + ")");

		for (File f : files) {
			System.out.println("\t" + f.getName());
		}

	}

	protected static List<File> efetuarDownloadDomSeNecessario() throws Exception {
		DOWNLOAD_ROOT.mkdirs();
		LUCENE_DIR.mkdirs();

		HttpClientContext context = Util.createHttpClientContext();

		String page = Util.get(DOM_URL, context);

		Map<String, String> paramsBase = Util.getFormInputs(page);

		List<File> arquivos = new ArrayList<File>();

		List<Integer> meses = Arrays.asList(9, 10, 11, 12);

		for (int mes : meses) {

			int tabIndex = mes - 1;

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
				arquivos.add(f);
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
					arquivos.add(f);
				}
			}
		}
		return arquivos;
	}

	protected static List<File> search(String query) throws Exception {

		List<File> files = new ArrayList<File>();

		IndexReader reader = openIndexReader();

		try {
			IndexSearcher searcher = new IndexSearcher(reader);

			TopDocs docs = searcher.search(new QueryParser("conteudo", STANDARD_ANALYZER).parse(query), Integer.MAX_VALUE);

			for (ScoreDoc hit : docs.scoreDocs) {

				org.apache.lucene.document.Document d = searcher.doc(hit.doc);

				String filename = d.getField("nome").stringValue();

				files.add(new File(DOWNLOAD_ROOT, filename));
			}

			Collections.sort(files, (x, y) -> y.getName().compareTo(x.getName()));

			return files;

		} finally {
			reader.close();
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

					CloseableHttpClient httpClient = Util.createHttpClient();

					try (OutputStream out = new FileOutputStream(file)) {
						Util.download(httpClient, downloadUrl, out);

						System.out.println("Efetuado download do arquivo: " + file.getName());

					} catch (Exception e) {
						file.delete();
						throw e;
					} finally {
						httpClient.close();
					}

					return file;
				}
			}
		}

		return null;
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

	private static void indexFile(IndexWriter indexWriter, File file) throws Exception {

		org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document();

		doc.add(new StringField("nome", file.getName(), Field.Store.YES));
		doc.add(new TextField("conteudo", TIKA.parseToString(file), Field.Store.NO));

		indexWriter.addDocument(doc);
	}

	private static IndexWriter openIndexWriter() throws IOException {
		Map<String, Analyzer> analyzerMap = new HashMap<>();
		analyzerMap.put("nome", new KeywordAnalyzer());
		analyzerMap.put("conteudo", STANDARD_ANALYZER);

		PerFieldAnalyzerWrapper wrapper = new PerFieldAnalyzerWrapper(STANDARD_ANALYZER, analyzerMap);

		IndexWriterConfig conf = new IndexWriterConfig(wrapper);

		IndexWriter indexWriter = new IndexWriter(FSDirectory.open(LUCENE_DIR.toPath()), conf);
		return indexWriter;
	}

	private static boolean isFileIndexed(File file) throws Exception {

		IndexReader reader = openIndexReader();

		try {
			IndexSearcher searcher = new IndexSearcher(reader);

			TopDocs docs = searcher.search(new TermQuery(new Term("nome", file.getName())), 1);

			return docs.scoreDocs.length > 0;

		} finally {
			reader.close();
		}
	}

	private static IndexReader openIndexReader() throws IOException {
		return DirectoryReader.open(FSDirectory.open(LUCENE_DIR.toPath()));
	}

}
