package br.com.roxs.domctba.crawler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import br.com.roxs.domctba.crawler.util.SSLRelax;

@SpringBootApplication
@EnableScheduling
public class DomCtbaCrawlerApplication {

	static {
		SSLRelax.load();
	}

	private static ConfigurableApplicationContext applicationContext;

	public static void main(String[] args) {
		boot(args);
	}

	public static ConfigurableApplicationContext boot(String... args) {
		return boot(true, args);
	}

	public static ConfigurableApplicationContext boot(boolean headless, String... args) {
		if (applicationContext == null) {

			SpringApplication springApplication = new SpringApplication(DomCtbaCrawlerApplication.class);
			springApplication.setHeadless(headless);

			applicationContext = springApplication.run(args);
		}
		return applicationContext;
	}

	public static ConfigurableApplicationContext getApplicationContext() {
		return getApplicationContext(true);
	}

	public static ConfigurableApplicationContext getApplicationContext(boolean headless) {
		if (applicationContext == null) {
			boot(headless);
		}
		return applicationContext;
	}

}
