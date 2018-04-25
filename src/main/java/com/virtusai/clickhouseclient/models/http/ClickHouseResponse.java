package com.virtusai.clickhouseclient.models.http;

import java.util.List;

public final class ClickHouseResponse<T> {
	public final List<MetaRow> meta;
	public final List<T> data;
	public final Long rows;
	public final Long rows_before_limit_at_least;
	public final Statistics statistics;
	
	private ClickHouseResponse(List<MetaRow> meta, List<T> data, Long rows, Long rows_before_limit_at_least, Statistics statistics) {
		this.meta = meta;
		this.data = data;
		this.rows = rows;
		this.rows_before_limit_at_least = rows_before_limit_at_least;
		this.statistics = statistics;
	}

	@Override
	public String toString() {
		return "[meta=" + meta + ", data=" + data + ", rows=" + rows + ", rows_before_limit_at_least=" + rows_before_limit_at_least + ", statistics=" + statistics + "]";
	}


	public static class MetaRow {
		public final String name;
		public final String type;
		
		private MetaRow(String name, String type) {
			this.name = name;
			this.type = type;
		}

		@Override
		public String toString() {
			return "[name=" + name + ", type=" + type + "]";
		}
	}
	
	public static class Statistics {
		public final Double elapsed;
		public final Long rows_read;
		public final Long bytes_read;
		
		private Statistics(Double elapsed, Long rows_read, Long bytes_read) {
			this.elapsed = elapsed;
			this.rows_read = rows_read;
			this.bytes_read = bytes_read;
		}

		@Override
		public String toString() {
			return "[elapsed=" + elapsed + ", rows_read=" + rows_read + ", bytes_read=" + bytes_read + "]";
		}
	}
}
