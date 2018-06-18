Asynchronous Java library for [ClickHouse](https://clickhouse.yandex/) wrapping the [HTTP interface](https://clickhouse.yandex/docs/en/interfaces/http_interface/).

## Installation

Maven (add to `pom.xml`):
```XML
<repositories>
	<repository>
		<id>jitpack.io</id>
		<url>https://jitpack.io</url>
	</repository>
</repositories>

<dependencies>
	<dependency>
		<groupId>com.github.virtusai</groupId>
		<artifactId>clickhouse-client-java</artifactId>
		<version>-SNAPSHOT</version>
	</dependency>
</dependencies>
```

## API
```Java
// <T> is the class of the POJO used for deserializing the response data
CompletableFuture<ClickHouseResponse<T>> get(String query, Class<T> clazz);
CompletableFuture<ClickHouseResponse<T>> post(String query, Class<T> clazz);
CompletableFuture<Void> post(String query, List<Object[]> data);
CompletableFuture<String> healthcheck();
```

## Example

Schema
```SQL
CREATE DATABASE IF NOT EXISTS test;

CREATE TABLE test.metrics (
    device_id FixedString(36),
    metric String,
    time UInt64,
    value Float64
)
ENGINE = Memory;
```

Java
```Java
// Initialize client (endpoint, username, password)
ClickHouseClient client = new ClickHouseClient("http://localhost:8123", "default", "");

// Insert data
List<Object[]> rows = new ArrayList<>();
rows.add(generateRow());
rows.add(generateRow());

client.post("INSERT INTO test.metrics", rows);

Thread.sleep(2000);

// Retrieve data
client.get("SELECT * from test.metrics", Metric.class)
.thenAccept(res -> System.out.println(res));
// OUTPUT: [meta=[[name=device_id, type=FixedString(36)], [name=metric, type=String], [name=time, type=UInt64], [name=value, type=Float64]], data=[[device_id=aa2d13ba-55b7-4480-9907-cdb9afe28ef9, metric=metric_1, time=1524689742443, value=0.8831476609946413], [device_id=38097658-1c0b-4715-84d5-671f6eabd94f, metric=metric_1, time=1524689742443, value=0.9270612447757496]], rows=2, rows_before_limit_at_least=null, statistics=[elapsed=3.71E-5, rows_read=2, bytes_read=146]]

Thread.sleep(2000);

// Close client
client.close();
```

```Java
private static Object[] generateRow() {
	UUID deviceId = UUID.randomUUID();
	String metric = "metric_1";
	long time = System.currentTimeMillis();
	double value = new Random().nextDouble();
	
	return new Object[] { deviceId, metric, time, value };
}
```

```Java
public final class Metric {
	public final UUID device_id;
	public final String metric;
	public final Long time;
	public final Double value;
	
	private Metric(UUID device_id, String metric, Long time, Double value) {
		this.device_id = device_id;
		this.metric = metric;
		this.time = time;
		this.value = value;
	}

	@Override
	public String toString() {
		return "[device_id=" + device_id + ", metric=" + metric + ", time=" + time + ", value=" + value + "]";
	}
}
```
