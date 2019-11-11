package br.com.roxs.domctba.crawler.util;

import java.security.cert.CertificateException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.security.cert.X509Certificate;

public class SSLRelax {

	public static final String PATCHED_HOSTNAME_VERIFIER_NAME = "SSLRelaxBean";

	private static SSLContext sslContext;

	static {
		load();
	}

	public static void load() {

		// Create all-trusting host name verifier
		HostnameVerifier allHostsValid = new HostnameVerifier() {
			@Override
			public boolean verify(String hostname, SSLSession session) {
				return true;
			}

			@Override
			public String toString() {
				return PATCHED_HOSTNAME_VERIFIER_NAME;
			}
		};

		String hnvf = HttpsURLConnection.getDefaultHostnameVerifier().toString();
		if (hnvf != null && hnvf.equalsIgnoreCase(PATCHED_HOSTNAME_VERIFIER_NAME)) {
			return; // ja foi patcheado, nao deve executar
		}

		// Install the all-trusting host verifier
		HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

		// Create a trust manager that does not validate certificate chains
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			@Override
			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			@SuppressWarnings("unused")
			public void checkClientTrusted(X509Certificate[] certs, String authType) {
			}

			@SuppressWarnings("unused")
			public void checkServerTrusted(X509Certificate[] certs, String authType) {
			}

			@Override
			public void checkClientTrusted(java.security.cert.X509Certificate[] arg0, String arg1) throws CertificateException {
				// TODO Auto-generated method stub

			}

			@Override
			public void checkServerTrusted(java.security.cert.X509Certificate[] arg0, String arg1) throws CertificateException {
				// TODO Auto-generated method stub

			}
		} };

		// Install the all-trusting trust manager
		try {
			sslContext = SSLContext.getInstance("SSL");
			sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
			SSLContext.setDefault(sslContext);
		} catch (Throwable e) {
		}
	}

	public static SSLContext getSslContext() {
		return sslContext;
	}

}
