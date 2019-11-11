package br.com.roxs.domctba.crawler.services;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
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
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.tika.Tika;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import br.com.roxs.domctba.crawler.dto.EdicaoDiarioOficial;
import br.com.roxs.domctba.crawler.util.Util;

@Service
public class DomCtbaCrawlerService {

	private static final Logger logger = LoggerFactory.getLogger(DomCtbaCrawlerService.class);

	private static final StandardAnalyzer STANDARD_ANALYZER = new StandardAnalyzer();

	@Value("${data.dir}")
	private String dataDir;

	private static final String DOM_BASE_URL = "https://legisladocexterno.curitiba.pr.gov.br";

	private static final String DOM_URL = DOM_BASE_URL + "/DiarioConsultaExterna_Pesquisa.aspx";

	private File downloadDir;

	private File luceneDir;

	private static final Tika tika = new Tika();

	private AtomicBoolean verificando = new AtomicBoolean();

	@PostConstruct
	public void setup() {

		downloadDir = new File(new File(dataDir), "diarios_oficiais");

		luceneDir = new File(new File(dataDir), "lucene");

		downloadDir.mkdirs();
		luceneDir.mkdirs();

		tika.setMaxStringLength(-1);
	}

	@Scheduled(cron = "0 0 18 * * *")
	public void verificarEdicoesDiarioOficial() throws Exception {
		if (!verificando.get()) {
			logger.info("Verificando novas edições do DOM");
			getEdicoesDiarioOficial();
		}
	}

	public List<EdicaoDiarioOficial> getEdicoesDiarioOficial() throws Exception {

		if (verificando.compareAndSet(false, true)) {

			try {

				HttpClientContext context = Util.createHttpClientContext();

				String page = Util.get(DOM_URL, context);

				Map<String, String> paramsBase = Util.getFormInputs(page);

				List<EdicaoDiarioOficial> edicoes = new LinkedList<EdicaoDiarioOficial>();

				List<Integer> meses = Arrays.asList(8, 9, 10, 11, 12);

				for (int mes : meses) {

					logger.info("Verificando diários do mês " + mes);

					int tabIndex = mes - 1;

					Map<String, String> m = new HashMap<String, String>(paramsBase);

					m.put("ctl00$smrAjax", "ctl00$cphMasterPrincipal$upPesquisaExternaDO|ctl00$cphMasterPrincipal$TabContainer1");
					m.put("ctl00_cphMasterPrincipal_TabContainer1_ClientState", "{\"ActiveTabIndex\":" + tabIndex + ",\"TabState\":[true,true,true,true,true,true,true,true,true,true,true,true]}");

					m.put("__EVENTTARGET", "ctl00$cphMasterPrincipal$TabContainer1");
					m.put("__EVENTARGUMENT", "activeTabChanged:" + tabIndex);
					m.put("__ASYNCPOST", "true");

					Map<String, String> headers = new HashMap<String, String>();
					headers.put("X-MicrosoftAjax", "Delta=true");
					headers.put("X-Requested-With", "XMLHttpRequest");

					page = Util.post(DOM_URL, DOM_URL, m, headers, context);
					updateParams(page, m);

					Document doc = Jsoup.parse(page);

					Set<Integer> paginas = new TreeSet<Integer>();

					for (Element e : doc.select(".grid_Pager a")) {
						paginas.add(new Integer(e.text().trim()));
					}

					for (Element edicao : doc.select(".grid_Row")) {
						EdicaoDiarioOficial edicaoDiarioOficial = parseEdicao(edicao, m, headers, context);
						edicoes.add(edicaoDiarioOficial);
					}

					for (Integer p : paginas) {

						m.put("ctl00$smrAjax", "tl00$cphMasterPrincipal$upPesquisaExternaDO|ctl00$cphMasterPrincipal$gdvGrid2");
						m.put("__EVENTTARGET", "ctl00$cphMasterPrincipal$gdvGrid2");
						m.put("__EVENTARGUMENT", "Page$" + p);

						page = Util.post(DOM_URL, DOM_URL, m, headers, context);
						updateParams(page, m);

						doc = Jsoup.parse(page);

						for (Element edicao : doc.select(".grid_Row")) {
							EdicaoDiarioOficial edicaoDiarioOficial = parseEdicao(edicao, m, headers, context);
							edicoes.add(edicaoDiarioOficial);
						}
					}
				}

				Collections.sort(edicoes, (x, y) -> y.getArquivo().getName().compareTo(x.getArquivo().getName()));

				if (!edicoes.isEmpty()) {
					logger.info("O diário mais atual é: " + edicoes.get(0).getNome());
				}

				return edicoes;

			} finally {
				verificando.set(false);
			}
		} else {
			return Collections.emptyList();
		}
	}

	public void indexarDiarios(List<EdicaoDiarioOficial> diarios) throws Exception, IOException {
		IndexWriter indexWriter = null;

		try {
			for (EdicaoDiarioOficial diario : diarios) {
				if (!isFileIndexed(diario.getArquivo())) {

					if (indexWriter == null) {
						indexWriter = openIndexWriter();
					}

					indexarDiario(indexWriter, diario);
				}
			}
		} finally {
			if (indexWriter != null) {
				indexWriter.close();
			}
		}
	}

	private static void indexarDiario(IndexWriter indexWriter, EdicaoDiarioOficial diario) throws Exception {

		org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document();

		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");

		doc.add(new StringField("data", df.format(diario.getData()), Field.Store.YES));
		doc.add(new StringField("nome", diario.getNome(), Field.Store.YES));
		doc.add(new StringField("arquivo", diario.getArquivo().getName(), Field.Store.YES));
		doc.add(new TextField("conteudo", tika.parseToString(diario.getArquivo()), Field.Store.NO));

		indexWriter.addDocument(doc);
		indexWriter.flush();
		indexWriter.commit();

		logger.info("Arquivo " + diario.getArquivo().getName() + " foi indexado");
	}

	public List<EdicaoDiarioOficial> pesquisarDiarios(String query) throws Exception {

		List<EdicaoDiarioOficial> edicoes = new LinkedList<EdicaoDiarioOficial>();

		IndexReader reader = openIndexReader();

		if (reader == null) {
			return Collections.emptyList();
		}

		try {
			IndexSearcher searcher = new IndexSearcher(reader);

			Query q = StringUtils.isEmpty(query) ? new MatchAllDocsQuery() : new QueryParser("conteudo", STANDARD_ANALYZER).parse(query);

			TopDocs docs = searcher.search(q, Integer.MAX_VALUE);

			for (ScoreDoc hit : docs.scoreDocs) {

				org.apache.lucene.document.Document d = searcher.doc(hit.doc);

				String filename = d.getField("nome").stringValue();

				edicoes.add(getEdicaoDiarioOficial(new File(downloadDir, filename)));
			}

			Collections.sort(edicoes, (x, y) -> y.getArquivo().getName().compareTo(x.getArquivo().getName()));

			return edicoes;

		} finally {
			reader.close();
		}
	}

	private IndexWriter openIndexWriter() throws IOException {

		Map<String, Analyzer> analyzerMap = new HashMap<>();
		analyzerMap.put("nome", new KeywordAnalyzer());
		analyzerMap.put("conteudo", STANDARD_ANALYZER);

		PerFieldAnalyzerWrapper wrapper = new PerFieldAnalyzerWrapper(STANDARD_ANALYZER, analyzerMap);

		IndexWriterConfig conf = new IndexWriterConfig(wrapper);

		IndexWriter indexWriter = new IndexWriter(FSDirectory.open(luceneDir.toPath()), conf);
		return indexWriter;
	}

	private boolean isFileIndexed(File file) throws Exception {

		IndexReader reader = openIndexReader();

		if (reader == null) {
			return false;
		}

		try {
			IndexSearcher searcher = new IndexSearcher(reader);

			TopDocs docs = searcher.search(new TermQuery(new Term("nome", file.getName())), 1);

			return docs.scoreDocs.length > 0;

		} finally {
			reader.close();
		}
	}

	private IndexReader openIndexReader() throws IOException {
		try {
			return DirectoryReader.open(FSDirectory.open(luceneDir.toPath()));
		} catch (IndexNotFoundException e) {
			return null;
		}
	}

	private EdicaoDiarioOficial parseEdicao(Element edicaoElm, Map<String, String> m, Map<String, String> headers, HttpClientContext context) throws Exception {

		Elements cols = edicaoElm.select("td");

		String edicao = cols.get(0).text();

		String data = cols.get(1).text();

		SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy");

		SimpleDateFormat df2 = new SimpleDateFormat("yyyy-MM-dd");

		String downloadId = cols.get(2).select("a").get(0).attr("id").replaceAll("_", "\\$");

		File file = new File(downloadDir, df2.format(df.parse(data)) + " - " + edicao + ".pdf");

		if (file.exists()) {
			return getEdicaoDiarioOficial(file);
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

						logger.info("Efetuado download do arquivo: " + file.getName() + " (" + (file.length() / 1024 / 1024) + " MB)");

					} catch (Exception e) {
						file.delete();
						throw e;
					} finally {
						httpClient.close();
					}

					return getEdicaoDiarioOficial(file);
				}
			}
		}

		return null;
	}

	private EdicaoDiarioOficial getEdicaoDiarioOficial(File file) throws ParseException {

		EdicaoDiarioOficial edicaoDOM = new EdicaoDiarioOficial();

		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");

		String[] s = file.getName().replaceAll("\\.pdf", "").split(" - ");

		edicaoDOM.setData(df.parse(s[0]));
		edicaoDOM.setNome(s[1]);
		edicaoDOM.setArquivo(file);

		return edicaoDOM;
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

}
