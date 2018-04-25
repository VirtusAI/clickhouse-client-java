package com.virtusai.clickhouseclient.models.exceptions;

public final class HttpRequestException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public final int statusCode;
	public final String body;
	public final String endpoint;

	public HttpRequestException(int statusCode, String body, String endpoint) {
		this.statusCode = statusCode;
		this.body = body;
		this.endpoint = endpoint;
	}

	@Override
	public String getMessage() {
		return String.format("[%s] %s: %s", statusCode, endpoint, body);
	}
}
