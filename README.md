# Dynatrace Generic Log Ingest Log4j2 Appender
Log4j2 Appender to integrate Java applications with Dynatrace Generic Log Ingest functionality.

To make it works you have to provide two parameters:
- `activeGateUrl` - URL to ActiveGate instance with Generic Log Ingest module enabled
- `token` - valid token with _Log Import_ permission enabled

Log4j2 version >= 2.12.0

### How to use it:
Gradle
```groovy
repositories {
	jcenter()
}

dependencies {
	compileOnly group: 'org.apache.logging.log4j', name: 'log4j-api', version: '2.14.0'
	compile group: 'org.apache.logging.log4j', name: 'log4j-core', version: '2.14.0'

	runtimeOnly group: 'pl.coffeepower.log4j', name: 'dynatrace-log4j2-appender', version: '0.0.1'
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
	<version>0.0.1</version>
	<type>pom</type>
</dependency>
```
### Example
log4j2.xml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration>
	<Appenders>
		<DynatraceGenericLogIngestAppender name="myAppender"
										   activeGateUrl="https://{ACTIVE_GATE_URL}/api/v2/logs/ingest"
										   token="{TOKEN_WITH_LOG_IMPORT_PERMISSION}"
										   sslValidation="false">
			<PatternLayout pattern="%d{yyyy.MM.dd HH:mm:ss.SSS} [%t] %-5level %logger - %msg%n"/>
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