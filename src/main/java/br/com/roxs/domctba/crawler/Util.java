package br.com.roxs.domctba.crawler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public abstract class Util {
	private static final CloseableHttpClient httpClient;

	static {
		httpClient = createHttpClient();
	}

	protected static CloseableHttpClient createHttpClient() {

		RequestConfig globalConfig = RequestConfig.custom() //
				.setCookieSpec(CookieSpecs.STANDARD) //
				.build();

		return HttpClients.custom().setUserAgent("Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36")//
				.setDefaultRequestConfig(globalConfig).build();
	}

	public static CloseableHttpClient getHttpClient() {
		return httpClient;
	}

	public static HttpClientContext createHttpClientContext() {
		HttpClientContext localContext = HttpClientContext.create();
		localContext.setCookieStore(new BasicCookieStore());
		return localContext;
	}

	public static Map<String, String> getQueryMap(String query) {

		if (query.indexOf('?') > 0)
			query = query.split("\\?")[1];

		String[] params = query.split("&");
		Map<String, String> map = new HashMap<String, String>();
		for (String param : params) {
			String name = param.split("=")[0];
			String value = param.split("=")[1];
			map.put(name, value);
		}
		return map;
	}

	public static Map<String, String> getFormInputs(String page) {
		return getFormInputs(page, 0);
	}

	public static Map<String, String> getFormInputs(String page, int formNumber) {

		Document doc = Jsoup.parse(page);

		Map<String, String> params = new LinkedHashMap<String, String>();

		Element form = doc.select("form").get(formNumber);

		for (Element e : form.select("input"))
			params.put(e.attr("name"), e.attr("value"));

		for (Element e : form.select("input"))
			params.put(e.attr("name"), e.attr("value"));

		for (Element e : form.select("select option[selected]"))
			params.put(e.parent().attr("name"), e.attr("value"));

		return params;
	}

	public static Map<String, String> parseStringParams(String... keysValues) {

		Map<String, String> params = new LinkedHashMap<String, String>();

		for (int i = 0; i < keysValues.length; i++) {

			if (i > 0 && i % 2 == 1)
				params.put(keysValues[i - 1], keysValues[i]);

		}
		return params;
	}

	public static String post(String url, HttpContext context) throws Exception {
		return postInternal(url, null, null, null, context);
	}

	public static String post(String url, Map<String, String> params, HttpContext context) throws Exception {
		return postInternal(url, null, params, null, context);
	}

	public static String post(String url, Map<String, String> params) throws Exception {
		return postInternal(url, null, params, null, null);
	}

	public static String post(String url, String referer, Map<String, String> params) throws Exception {
		return postInternal(url, referer, params, null, null);
	}

	public static String post(String url, String referer, Map<String, String> params, HttpContext context) throws Exception {
		return postInternal(url, referer, params, null, context);
	}

	public static String post(String url, String referer, Map<String, String> params, Map<String, String> headers, HttpContext context) throws Exception {
		return postInternal(url, referer, params, headers, context);
	}

	private static String postInternal(String url, String referer, Map<String, String> params, Map<String, String> headers, HttpContext context) throws Exception {

		String response = null;

		HttpPost post = new HttpPost(url);

		List<NameValuePair> pairs = new ArrayList<NameValuePair>();

		if (params != null) {
			for (Entry<String, String> e : params.entrySet())
				pairs.add(new BasicNameValuePair(e.getKey(), e.getValue()));

			post.setEntity(new UrlEncodedFormEntity(pairs));
		}

		if (referer != null)
			post.setHeader("Referer", referer);

		if (headers != null) {
			for (Entry<String, String> e : headers.entrySet()) {
				post.setHeader(e.getKey(), e.getValue());
			}
		}

		if (context != null)
			response = getTextFromHttpMethod(getHttpClient().execute(post, context));
		else
			response = getTextFromHttpMethod(getHttpClient().execute(post));

		return response;
	}

	public static void downloadFile(String downloadUrl, File destFile) throws Exception {

		URL u = new URL(downloadUrl);
		HttpURLConnection conn = (HttpURLConnection) u.openConnection();
		conn.setDoInput(true);

		byte[] buffer = new byte[4096];

		int responseCode = conn.getResponseCode();

		if (responseCode != 200) {
			InputStream in = conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream();
			throw new RuntimeException(IOUtils.toString(in, StandardCharsets.UTF_8));
		}

		try (OutputStream out = new FileOutputStream(destFile)) {
			try (InputStream in = conn.getInputStream()) {
				int n = 0;
				while (-1 != (n = in.read(buffer))) {
					out.write(buffer, 0, n);
				}
			}
		} finally {
			conn.disconnect();
		}

	}

	public static HttpResponse getHttpResponse(String url, HttpContext context) throws Exception {

		HttpGet get = new HttpGet(url);

		CloseableHttpResponse resp = null;

		if (context != null)
			resp = getHttpClient().execute(get, context);
		else
			resp = getHttpClient().execute(get);

		try {
			return resp;
		} finally {
			resp.close();
		}
	}

	public static String get(String url, HttpContext context) throws Exception {

		HttpGet get = new HttpGet(url);

		if (context != null)
			return getTextFromHttpMethod(getHttpClient().execute(get, context));
		else
			return getTextFromHttpMethod(getHttpClient().execute(get));

	}

	public static String get(String url, String userAgent, HttpContext context) throws Exception {

		HttpGet get = new HttpGet(url);

		get.setHeader("User-Agent", userAgent);

		if (context != null)
			return getTextFromHttpMethod(getHttpClient().execute(get, context));
		else
			return getTextFromHttpMethod(getHttpClient().execute(get));

	}

	public static String get(String url, String authorization) throws HttpException, IOException {
		HttpGet get = new HttpGet(url);

		if (authorization != null)
			get.setHeader("Authorization", authorization);

		return getTextFromHttpMethod(getHttpClient().execute(get));
	}

	private static String getTextFromHttpMethod(CloseableHttpResponse response) {
		try {
			return getTextFromInputStream(response.getEntity().getContent());
		} catch (IOException e) {
			return null;
		} finally {
			try {
				response.close();
			} catch (IOException e) {
			}
		}
	}

	private static String getTextFromInputStream(final InputStream is) {
		final char[] buffer = new char[4096];
		final StringBuilder out = new StringBuilder();
		try {
			final Reader in = new InputStreamReader(is, "UTF-8");
			try {
				for (;;) {
					int rsz = in.read(buffer, 0, buffer.length);
					if (rsz < 0)
						break;
					out.append(buffer, 0, rsz);
				}
			} finally {
				in.close();
			}
		} catch (UnsupportedEncodingException ex) {
		} catch (IOException ex) {
		}
		return out.toString();
	}

	public static String encodeURL(String url) {
		try {
			return URLEncoder.encode(url, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	public static String byteArray2Hex(byte[] bytes) {
		return Hex.encodeHexString(bytes);
	}

	public static String base64ToHex(String base64) {
		return Hex.encodeHexString(Base64.decodeBase64(base64));
	}

	public static boolean isBlank(String str) {
		return str == null || isBlank(str.toCharArray());
	}

	public static boolean isBlank(char[] str) {
		return str == null || str.length == 0;
	}

	public static String readFile(String file) throws IOException {
		Path path = Paths.get(file);

		if (!path.toFile().exists())
			return null;

		byte[] encoded = Files.readAllBytes(path);
		return new String(encoded, Charset.forName("UTF-8"));
	}

	public static void writeFile(File file, String content) throws Exception {

		File parentFile = file.getParentFile();
		if (parentFile != null)
			parentFile.mkdirs();

		Files.write(file.toPath(), content.getBytes(Charset.forName("UTF-8")));
	}

	public static void joinFiles(List<File> files, File output) throws IOException {

		Charset charset = StandardCharsets.UTF_8;

		for (File f : files) {
			List<String> lines = Files.readAllLines(f.toPath(), charset);
			Files.write(output.toPath(), lines, charset, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		}
	}

	public static void joinStringsInFile(List<String> strings, File output) throws IOException {

		Charset charset = StandardCharsets.UTF_8;

		for (String s : strings) {
			List<String> lines = Arrays.asList(StringUtils.split(s, "\r\n"));
			Files.write(output.toPath(), lines, charset, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		}
	}

}
