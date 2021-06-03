package io.github.michaljonko.log4j.example;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.ThreadContext;

import lombok.extern.log4j.Log4j2;

@Log4j2
public final class SimpleLogsGeneratorApp {

	private final ScheduledExecutorService executor;

	private SimpleLogsGeneratorApp() {
		log.debug("Creating app...");
		this.executor = Executors.newSingleThreadScheduledExecutor();
	}

	private void start() {
		log.debug("Starting...");
		executor.scheduleWithFixedDelay(() -> {
			ThreadContext.put("trace_id", UUID.randomUUID().toString());
			log.info("Current date: {}", LocalDateTime.now());
			ThreadContext.clearMap();
		}, 1_000L, 6000L, TimeUnit.MILLISECONDS);
	}

	private void stop() {
		log.debug("Closing app...");
		executor.shutdownNow();
	}

	public static void main(String[] args) {
		final SimpleLogsGeneratorApp simpleQuotesGeneratorApp = new SimpleLogsGeneratorApp();
		simpleQuotesGeneratorApp.start();
		Runtime.getRuntime().addShutdownHook(new Thread(simpleQuotesGeneratorApp::stop));
	}
}
