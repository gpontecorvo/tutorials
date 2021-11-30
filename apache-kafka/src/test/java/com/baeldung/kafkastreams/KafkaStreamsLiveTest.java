package com.baeldung.kafkastreams;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Produced;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.regex.Pattern;

public class KafkaStreamsLiveTest {
    private String bootstrapServers = "localhost:9092";
    private Path stateDirectory;

    @Test
//    @Ignore("it needs to have kafka broker running on local")
    public void shouldTestKafkaStreams() throws InterruptedException {
        // given
        String  inputTopic = "inputTopic";

        Properties streamsConfiguration = new Properties();
        streamsConfiguration.put(StreamsConfig.APPLICATION_ID_CONFIG, "wordcount-live-test");
        streamsConfiguration.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        streamsConfiguration.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String()
            .getClass()
            .getName());
        streamsConfiguration.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String()
            .getClass()
            .getName());
        streamsConfiguration.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 1000);
        streamsConfiguration.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        // Use a temporary directory for storing state, which will be automatically removed after the test.
        try {
            this.stateDirectory = Files.createTempDirectory("kafka-streams");
            streamsConfiguration.put(StreamsConfig.STATE_DIR_CONFIG, this.stateDirectory.toAbsolutePath()
                .toString());
        } catch (final IOException e) {
            throw new UncheckedIOException("Cannot create temporary directory", e);
        }

        // when
        final StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> textLines = builder.stream(inputTopic);
        Pattern pattern = Pattern.compile("\\W+", Pattern.UNICODE_CHARACTER_CLASS);

        KTable<String, Long> wordCounts = textLines.flatMapValues(value -> Arrays.asList(pattern.split(value.toLowerCase())))
                                                   .groupBy((key, word) -> word)  // count words
                                                   .count();

        KTable<String, Long> alphaCounts = textLines.flatMapValues(value -> Arrays.asList(pattern.split(value.toLowerCase())))
                                                   .groupBy((key, word) -> word.substring(0,1).toLowerCase())       // count words starting with letters
                                                   .count();

//        wordCounts.toStream()
//                  .foreach((word, count) -> System.out.println("word: " + word + " -> " + count));

//        alphaCounts.toStream()
//                  .foreach((letter, count) -> System.out.printf("%n%d words start with letter: %s", count, letter ));

        String wordCountTopic = "wordCountTopic";

        wordCounts.toStream()
                  .to(wordCountTopic, Produced.with(Serdes.String(), Serdes.Long()));

        String letterCountTopic = "letterCountTopic";

        alphaCounts.toStream()
                  .to(letterCountTopic, Produced.with(Serdes.String(), Serdes.Long()));

        final Topology topology = builder.build();
        //System.out.println("topology: " + topology.describe());

        final int RUN_FOR_THIS_MANY_SECONDS = 60;
        try (KafkaStreams streams = new KafkaStreams(topology, streamsConfiguration);) {
            streams.start();
            Thread.sleep(RUN_FOR_THIS_MANY_SECONDS * 1000);
        }
    }
}
