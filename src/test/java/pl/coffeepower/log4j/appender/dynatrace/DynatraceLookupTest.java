package pl.coffeepower.log4j.appender.dynatrace;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DynatraceLookupTest {

	@Test
	void readMetadata(@TempDir Path path) throws IOException {
		Path metadataPath = Files.write(
				path.resolve("metadata.properties"),
				Arrays.asList("pgi=1234", "hostid=host1", "pg="),
				StandardOpenOption.CREATE);

		DynatraceLookup lookup = new DynatraceLookup(
				Files.write(path.resolve("magic.properties"), metadataPath.toString().getBytes(), StandardOpenOption.CREATE)
		);

		assertThat(lookup.lookup("pgi"))
				.isEqualTo("1234");
		assertThat(lookup.lookup("hostid"))
				.isEqualTo("host1");
		assertThat(lookup.lookup("pg"))
				.isEmpty();
		assertThat(lookup.lookup(UUID.randomUUID().toString()))
				.isEmpty();
	}
}