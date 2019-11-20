package br.com.roxs.domctba.crawler.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import br.com.roxs.domctba.crawler.dto.EdicaoDiarioOficial;
import br.com.roxs.domctba.crawler.services.DomCtbaCrawlerService;

@Component
@Scope("view")
public class DiarioOficialBean {

	@Autowired
	private DomCtbaCrawlerService service;

	private Map<String, Object> params = new HashMap<String, Object>();

	private List<EdicaoDiarioOficial> edicoes = new LinkedList<EdicaoDiarioOficial>();

	@PostConstruct
	public void setup() throws Exception {
		pesquisar();
	}

	public List<EdicaoDiarioOficial> pesquisar() throws Exception {
		edicoes = service.pesquisarDiarios((String) params.get("query"));
		return edicoes;
	}

	public void download(File file) throws Exception {
		downloadFile(file, "application/pdf");
	}

	public void verificarEdicoesDiarioOficial() throws Exception {
		service.verificarEdicoesDiarioOficial();
		pesquisar();
	}

	private void downloadFile(File file, String mimeType) throws IOException {

		ExternalContext context = FacesContext.getCurrentInstance().getExternalContext();

		HttpServletResponse response = (HttpServletResponse) context.getResponse();

		response.setContentLength((int) file.length());
		response.setContentType(mimeType);

		try (InputStream in = new FileInputStream(file)) {
			try (OutputStream out = response.getOutputStream()) {

				byte[] buf = new byte[4096];
				int count;
				while ((count = in.read(buf)) >= 0) {
					out.write(buf, 0, count);
				}
			}
		}

		FacesContext.getCurrentInstance().responseComplete();
	}

	public Map<String, Object> getParams() {
		return params;
	}

	public void setParams(Map<String, Object> params) {
		this.params = params;
	}

	public List<EdicaoDiarioOficial> getEdicoes() {
		return edicoes;
	}

}
