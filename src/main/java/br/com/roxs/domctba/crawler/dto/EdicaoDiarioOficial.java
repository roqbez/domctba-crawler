package br.com.roxs.domctba.crawler.dto;

import java.io.File;
import java.util.Date;

public class EdicaoDiarioOficial {

	private Date data;

	private String nome;

	private File arquivo;

	public Date getData() {
		return data;
	}

	public void setData(Date data) {
		this.data = data;
	}

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public File getArquivo() {
		return arquivo;
	}

	public void setArquivo(File arquivo) {
		this.arquivo = arquivo;
	}

	@Override
	public String toString() {
		return data + " - " + nome;
	}

}
