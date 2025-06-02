package com.jpmc.midascore;

import com.jpmc.midascore.KafkaProducer;
import com.jpmc.midascore.kafka.TransactionListener;
import com.jpmc.midascore.FileLoader;

import com.jpmc.midascore.foundation.Transaction;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@DirtiesContext
@EmbeddedKafka(partitions = 1, brokerProperties = {"listeners=PLAINTEXT://localhost:9092", "port=9092"})
class TaskTwoTests {

    static final Logger logger = LoggerFactory.getLogger(TaskTwoTests.class);

    @Autowired
    private KafkaProducer kafkaProducer;

    @Autowired
    private FileLoader fileLoader;

    @Autowired
    private TransactionListener transactionListener;

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () ->
                System.getProperty("spring.embedded.kafka.brokers", "localhost:9092"));
    }

    @Test
    void task_two_verifier() throws InterruptedException {
        // Send all transactions to Kafka
        String[] transactionLines = fileLoader.loadStrings("/test_data/poiuytrewq.uiop");
        for (String transactionLine : transactionLines) {
            kafkaProducer.send(transactionLine);
        }

        // Wait up to 5 seconds for transactions to be processed
        int attempts = 0;
        while (transactionListener.getReceivedTransactions().size() < 4 && attempts < 10) {
            Thread.sleep(500);
            attempts++;
        }

        List<Transaction> transactions = transactionListener.getReceivedTransactions();
        assertEquals(4, transactions.size(), "Should have received 4 transactions");

        logger.info("===== FIRST 4 TRANSACTION AMOUNTS =====");
        for (int i = 0; i < transactions.size(); i++) {
            logger.info("Transaction {} amount: {}", (i + 1), transactions.get(i).getAmount());
        }
        logger.info("=======================================");
    }
}
