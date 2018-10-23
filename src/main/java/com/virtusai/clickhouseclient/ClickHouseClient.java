package com.virtusai.clickhouseclient;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.AsyncHttpClientConfig;
import org.asynchttpclient.BoundRequestBuilder;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.Param;
import org.asynchttpclient.Realm;
import org.asynchttpclient.Request;
import org.asynchttpclient.request.body.multipart.FilePart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.virtusai.clickhouseclient.models.exceptions.HttpRequestException;
import com.virtusai.clickhouseclient.models.http.ClickHouseResponse;
import com.virtusai.clickhouseclient.utils.POJOMapper;

public class ClickHouseClient implements AutoCloseable {
	private static final Logger LOG = LoggerFactory.getLogger(ClickHouseClient.class);

	private static final String SELECT_FORMAT = "JSON";
	private static final String INSERT_FORMAT = "TabSeparated";

	private final List<Param> optParams;
	private final String endpoint;
	private final AsyncHttpClient httpClient;
	
	public ClickHouseClient(String endpoint) {
		this.endpoint = endpoint;
		this.optParams = new ArrayList<>();
		this.httpClient = new DefaultAsyncHttpClient();
	}
	

	public ClickHouseClient(String endpoint, String username, String password) {
		this.endpoint = endpoint;

		this.optParams = new ArrayList<>();
		
		AsyncHttpClientConfig config = new DefaultAsyncHttpClientConfig.Builder()
				.setRealm(new Realm.Builder(username, password)
						.setUsePreemptiveAuth(true)
						.setScheme(Realm.AuthScheme.BASIC)
						.build())
				.build();

		this.httpClient = new DefaultAsyncHttpClient(config);
	}

	public void close() {
		try {
			this.httpClient.close();
		} catch (Exception e) {
			LOG.error("Error closing http client", e);
		}
	}
	
	public ClickHouseClient setOptionalParams(Map<String, String> params) {
		params.entrySet().forEach(e -> optParams.add(new Param(e.getKey(), e.getValue())));
		return this;
	}

	public <T> CompletableFuture<ClickHouseResponse<T>> get(String query, Class<T> clazz) {
		String queryWithFormat = query + " FORMAT " + SELECT_FORMAT;

		Request request = httpClient.prepareGet(endpoint)
				.addQueryParams(new ArrayList<>())
				.addQueryParams(optParams)
				.addQueryParam("query", queryWithFormat)
				.build();
		
		LOG.debug("querying GET {}", queryWithFormat);

		return sendRequest(request).thenApply(POJOMapper.toPOJO(clazz));
	}

	public <T> CompletableFuture<ClickHouseResponse<T>> post(String query, Class<T> clazz) {
		String queryWithFormat = query + " FORMAT " + SELECT_FORMAT;

		Request request = httpClient.preparePost(endpoint)
				.addQueryParams(new ArrayList<>())
				.addQueryParams(optParams)
				.setBody(queryWithFormat)
				.build();
		
		LOG.debug("querying POST {}", queryWithFormat);

		return sendRequest(request).thenApply(POJOMapper.toPOJO(clazz));
	}

	public CompletableFuture<Void> post(String query, List<Object[]> data) {
		String queryWithFormat = query + " FORMAT " + INSERT_FORMAT;

		Request request = httpClient.preparePost(endpoint)
				.addQueryParams(new ArrayList<>())
				.addQueryParam("query", queryWithFormat)
				.setBody(tabSeparatedString(data))
				.build();

		return sendRequest(request).thenApply(rs -> null);
	}
	
	public <T> CompletableFuture<ClickHouseResponse<T>> queryWithExternalData(String query, String structure, List<Object[]> data, Class<T> clazz) {
		String queryWithFormat = query + " FORMAT " + SELECT_FORMAT;
		
		try {
			final File temp = File.createTempFile("temp", ".tsv");
			
			try (OutputStreamWriter fr = new OutputStreamWriter(Files.newOutputStream(temp.toPath()), StandardCharsets.UTF_8)) {
				fr.write(tabSeparatedString(data));
			}
				
			Request request = httpClient.preparePost(endpoint)
					.addQueryParams(new ArrayList<>())
					.addQueryParams(optParams)
					.addQueryParam("query", queryWithFormat)
					.addQueryParam("temp_structure", structure)
					.addHeader("Content-Type", "multipart/form-data")
					.addBodyPart(new FilePart("temp", temp))
					.build();
			
			LOG.debug("querying POST EXTERNAL {}", queryWithFormat);
			
			return sendRequest(request).thenApply(POJOMapper.toPOJO(clazz)).whenComplete((res, t) -> temp.delete());
			
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
	}
	
	public CompletableFuture<String> healthcheck() {
		Request request = httpClient.prepareGet(endpoint).build();

		return sendRequest(request);
	}
	
	public <T> CompletableFuture<ClickHouseResponse<T>> queryWithMultipleExternalData(String query, List<String> structure, List<List<Object[]>> data, Class<T> clazz) {
		String queryWithFormat = query + " FORMAT " + SELECT_FORMAT;
		
		try {
			
			BoundRequestBuilder reqBuilder = httpClient.preparePost(endpoint)
					.addQueryParams(new ArrayList<>())
					.addQueryParams(optParams)
					.addQueryParam("query", queryWithFormat)
					.addHeader("Content-Type", "multipart/form-data");
			
			List<File> tempFiles = new ArrayList<>(data.size());
			
			for(int i=0; i<data.size(); i++) {
			
				final File temp = File.createTempFile("temp" + i, ".tsv");
				
				try (OutputStreamWriter fr = new OutputStreamWriter(Files.newOutputStream(temp.toPath()), StandardCharsets.UTF_8)) {
					fr.write(tabSeparatedString(data.get(i)));
				}
				
				reqBuilder = reqBuilder
						.addBodyPart(new FilePart("temp" + i, temp))
						.addQueryParam("temp" + i + "_structure", structure.get(i));
			}
				
			Request request = reqBuilder.build();
			
			LOG.debug("querying POST EXTERNAL {}", queryWithFormat);
			
			return sendRequest(request).thenApply(POJOMapper.toPOJO(clazz)).whenComplete((res, t) -> tempFiles.forEach(f -> f.delete()));
			
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
	}

	private CompletableFuture<String> sendRequest(Request request) {
		LOG.debug("Sending request {}", request.getUrl());
		return httpClient.executeRequest(request).toCompletableFuture()
		.handle((response, t) -> {
			if (t != null) {
				LOG.error("Error sending request to endpoint=" + endpoint, t);
				throw new RuntimeException("Error sending request to endpoint=" + endpoint);
				
			} else {
				final int statusCode = response.getStatusCode();
				final String body = response.getResponseBody();

				if (statusCode != 200) {
					final String decodedUrl = decodedUrl(request);	
					
					HttpRequestException e = new HttpRequestException(statusCode, body, decodedUrl);
					LOG.error("[{}] {} : {}", statusCode, decodedUrl, body);

					throw e;
				}

				return body;
			}
		});
	}
	
	private static String tabSeparatedString(List<Object[]> data) {
		return data.stream().map(row -> Arrays.stream(row).map(col -> col.toString()).collect(Collectors.joining("\t"))).collect(Collectors.joining("\n"));
	}

	private static String decodedUrl(Request request) {
		final String url = request.getUrl();

		try {
			return URLDecoder.decode(url, StandardCharsets.UTF_8.name());
		} catch (UnsupportedEncodingException e) {
			return url;
		}
	}
}
