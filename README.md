![Dynatrace Generic Log Ingest Log4j2 Appender](icon.png)
# Dynatrace Generic Log Ingest Log4j2 Appender
Log4j2 Appender & Lookup to integrate Java applications with _Dynatrace Generic Log Ingest_ functionality.

Last stable version: _0.0.6_

To make it works you have to provide two parameters:
- `activeGateUrl` - URL to ActiveGate instance with Generic Log Ingest module enabled
- `token` - valid token with _Log Import_ permission enabled
- `sslValidation` - SSL certificate has to be valid. _false_ value will pass self-signed certificates. (OPTIONAL)


_DynatraceLookup_ is used to lookup entity attributes used internally by the Dynatrace.
Attributes can be accessed with prefix `${dt:}` in the configuration.

Lookup attribute | Configuration | Description
--- | --- | ---
`dt.entity.process_group_instance` | ${dynatrace:dt.entity.process_group_instance} | Process Group Instance of application running appender
`dt.entity.host` | ${dynatrace:dt.entity.host} | Host running application

_OpenTelemetryLookup_ is used to lookup thread-local contextual information delivered by OpenTelemetry Instrumentation for Java.
Attributes can be accessed with prefix `${otel:}` in the configuration.

Lookup attribute | Configuration | Description
--- | --- | ---
`trace_id` | ${otel:trace_id} | The current trace id
`span_id` | ${otel:span_id} | The current span id
`trace_flags` | ${otel:trace_flags} | The current trace flags, formatted according to W3C traceflags format

### Requirements
- **Log4j2** version >= 2.12.0
- for _DynatraceLookup_ ** [Dynatrace OneAgent](https://www.dynatrace.com/support/help/dynatrace-api/environment-api/deployment/oneagent/download-oneagent-latest/) ** 1.215 and newer is needed
- for _OpenTelemetryLookup_ ** [OpenTelemetry Instrumentation for Java](https://github.com/open-telemetry/opentelemetry-java-instrumentation) ** is needed

### How to use it:
Gradle
```groovy
repositories {
	jcenter()
}

dependencies {
	compileOnly group: 'org.apache.logging.log4j', name: 'log4j-api', version: '2.14.0'
	compile group: 'org.apache.logging.log4j', name: 'log4j-core', version: '2.14.0'

	runtimeOnly group: 'pl.coffeepower.log4j', name: 'dynatrace-log4j2-appender', version: '0.0.5'
	runtimeOnly group: 'org.apache.logging.log4j', name: 'log4j-slf4j-impl', version: '2.14.0'
}
```
Maven
```xml
<repositories>
    <repository>
        <id>jcenter</id>
        <name>jcenter</name>
        <url>https://jcenter.bintray.com</url>
    </repository>
</repositories>

<dependency>
	<groupId>pl.coffeepower.log4j</groupId>
	<artifactId>dynatrace-log4j2-appender</artifactId>
	<version>0.0.5</version>
	<type>pom</type>
</dependency>
```


### Examples
Simple configuration with defined layout for a message (will be shown as content in Dynatrace Log Viewer):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration>
	<Appenders>
		<DynatraceGenericLogIngestAppender name="myAppender" 
				activeGateUrl="https://{ACTIVE_GATE_URL}/api/v2/logs/ingest"
				token="{TOKEN_WITH_LOG_IMPORT_PERMISSION}"
				sslValidation="false">
			<PatternLayout pattern="%d{yyyy.MM.dd HH:mm:ss.SSS} [%t] %-5level %logger - %msg"/>
		</DynatraceGenericLogIngestAppender>
		<Console name="console" target="SYSTEM_OUT">
			<PatternLayout pattern="%d{yyyy.MM.dd HH:mm:ss.SSS} [%t] %-5level %logger{1.} - %msg%n"/>
		</Console>
	</Appenders>
	<Loggers>
		<Root level="info">
			<AppenderRef ref="console"/>
			<AppenderRef ref="myAppender"/>
		</Root>
	</Loggers>
</Configuration>
```


Simple configuration with three additional attributes (will be part of the log) and custom layout for a message:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration>
	<Appenders>
		<DynatraceGenericLogIngestAppender name="myAppender"
				activeGateUrl="https://{ACTIVE_GATE_URL}/api/v2/logs/ingest"
				token="{TOKEN_WITH_LOG_IMPORT_PERMISSION}">
			<Property name="service.name">Log4j2 Appender Tester</Property>
			<Property name="dt.os.type">${java:os}</Property>
			<Property name="dt.logpath">${sys:user.dir}/debug.log</Property>
			<PatternLayout pattern="[%t] %-5level %logger - %msg"/>
		</DynatraceGenericLogIngestAppender>
	</Appenders>
	<Loggers>
		<Root level="info">
			<AppenderRef ref="myAppender"/>
		</Root>
	</Loggers>
</Configuration>
```

Simple configuration with three additional attributes, lookup attributes from _DynatraceLookup_ and custom layout for a message:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration>
	<Appenders>
		<DynatraceGenericLogIngestAppender name="myAppender"
				activeGateUrl="https://{ACTIVE_GATE_URL}/api/v2/logs/ingest"
				token="{TOKEN_WITH_LOG_IMPORT_PERMISSION}">
			<Property name="service.name">Log4j2 Appender Tester</Property>
			<Property name="dt.os.type">${java:os}</Property>
			<Property name="log.source">${sys:user.dir}/debug.log</Property>
			<Property name="dt.entity.process_group_instance">${dt:dt.entity.process_group_instance}</Property>
			<Property name="dt.entity.host">${dt:dt.entity.host}</Property>
			<PatternLayout pattern="[%t] %-5level %logger - %msg"/>
		</DynatraceGenericLogIngestAppender>
	</Appenders>
	<Loggers>
		<Root level="info">
			<AppenderRef ref="myAppender"/>
		</Root>
	</Loggers>
</Configuration>
```

Simple configuration with three additional attributes, lookup attribute from _DynatraceLookup_, lookup attribute from _OpenTelemetryLookup_ and custom layout for a message:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration>
	<Appenders>
		<DynatraceGenericLogIngestAppender name="myAppender"
				activeGateUrl="https://{ACTIVE_GATE_URL}/api/v2/logs/ingest"
				token="{TOKEN_WITH_LOG_IMPORT_PERMISSION}">
			<Property name="service.name">Log4j2 Appender Tester</Property>
			<Property name="dt.os.type">${java:os}</Property>
			<Property name="log.source">${sys:user.dir}/debug.log</Property>
			<Property name="dt.entity.process_group_instance">${dt:dt.entity.process_group_instance}</Property>
			<Property name="dt.http.application_id">${otel:trace_id}</Property>
			<PatternLayout pattern="[%t] [%X{trace_id}]%-5level %logger - %msg"/>
		</DynatraceGenericLogIngestAppender>
	</Appenders>
	<Loggers>
		<Root level="info">
			<AppenderRef ref="myAppender"/>
		</Root>
	</Loggers>
</Configuration>
```