package org.petapico;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import com.opencsv.CSVReader;

public abstract class ApiAccess {

	private static HttpClient httpClient;

	static {
		RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(10000)
				.setConnectionRequestTimeout(100).setSocketTimeout(10000).build();
		httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();
	}

	public static String[] apiInstances = new String[] {
		"http://grlc.nanopubs.lod.labs.vu.nl/api/local/local/",
		"http://130.60.24.146:7881/api/local/local/"
	};

	protected abstract void processHeader(String[] line);

	protected abstract void processLine(String[] line);

	public void call(String operation, Map<String,String> params) {
		String paramString = "";
		if (params != null) {
			paramString = "?";
			for (String k : params.keySet()) {
				if (paramString.length() > 1) paramString += "&";
				paramString += k + "=";
				paramString += urlEncode(params.get(k));
			}
		}

		String apiUrl = apiInstances[0];  // TODO: check several APIs
		CSVReader csvReader = null;
		try {
			HttpGet get = new HttpGet(apiUrl + operation + paramString);
			get.setHeader("Accept", "text/csv");
			try {
				HttpResponse resp = httpClient.execute(get);
				if (!wasSuccessful(resp)) {
					EntityUtils.consumeQuietly(resp.getEntity());
					throw new IOException(resp.getStatusLine().toString());
				}
				csvReader = new CSVReader(new BufferedReader(new InputStreamReader(resp.getEntity().getContent())));
				String[] line = null;
				int n = 0;
				while ((line = csvReader.readNext()) != null) {
					n++;
					if (n == 1) {
						processHeader(line);
					} else {
						processLine(line);
					}
				}
			} finally {
				if (csvReader != null) csvReader.close();
			}
		} catch (IOException ex) {
			// TODO: proper logging
			ex.printStackTrace();
		}
	}

	public static List<String> getAll(String operation, Map<String,String> params, final int column) {
		final List<String> result = new ArrayList<>();
		ApiAccess a = new ApiAccess() {
			
			@Override
			protected void processLine(String[] line) {
				result.add(line[column]);
			}
			
			@Override
			protected void processHeader(String[] line) {
				// ignore
			}

		};
		a.call(operation, params);
		return result;
	}

	public static List<String[]> getAllFull(String operation, Map<String,String> params) {
		final List<String[]> result = new ArrayList<>();
		ApiAccess a = new ApiAccess() {
			
			@Override
			protected void processLine(String[] line) {
				result.add(line);
			}
			
			@Override
			protected void processHeader(String[] line) {
				// ignore
			}

		};
		a.call(operation, params);
		return result;
	}

	private static boolean wasSuccessful(HttpResponse resp) {
		int c = resp.getStatusLine().getStatusCode();
		return c >= 200 && c < 300;
	}

	private static String urlEncode(String s) {
		try {
			return URLEncoder.encode(s, "UTF-8");
		} catch (UnsupportedEncodingException ex) {
			throw new RuntimeException(ex);
		}
	}

}
