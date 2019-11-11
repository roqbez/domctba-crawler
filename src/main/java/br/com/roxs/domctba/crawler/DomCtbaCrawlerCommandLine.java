package br.com.roxs.domctba.crawler;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;

import br.com.roxs.domctba.crawler.dto.EdicaoDiarioOficial;
import br.com.roxs.domctba.crawler.services.DomCtbaCrawlerService;

public class DomCtbaCrawlerCommandLine {

	private static final Logger logger = LoggerFactory.getLogger(DomCtbaCrawlerCommandLine.class);

	public static void main(String[] args) throws Exception {

		ConfigurableApplicationContext appContext = DomCtbaCrawlerApplication.boot();

		DomCtbaCrawlerService service = appContext.getBean(DomCtbaCrawlerService.class);

		List<EdicaoDiarioOficial> edicoes = service.getEdicoesDiarioOficial();

		service.indexarDiarios(edicoes);

		String query = "\"colonetti\"";

		List<EdicaoDiarioOficial> resultado = service.pesquisarDiarios(query);

		StringBuilder sb = new StringBuilder("Resultado da busca por '" + query + "' (" + resultado.size() + ")");

		for (EdicaoDiarioOficial e : resultado) {
			sb.append("\n\t" + e);
		}

		logger.info(sb.toString());

	}

}
