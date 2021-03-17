# Dynatrace Generic Log Ingest Log4j2 Appender
Log4j2 Appender to integrate Java applications with Dynatrace Generic Log Ingest functionality.

To make it works you have to provide two parameters:
- `activeGateUrl` - URL to ActiveGate instance with Generic Log Ingest module enabled
- `token` - valid token with _Log Import_ permission enabled

Log4j2 version >= 2.12.0

## Example
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