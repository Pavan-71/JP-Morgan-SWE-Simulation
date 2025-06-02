package com.jpmc.midascore.kafka;

import com.jpmc.midascore.foundation.Transaction;
import com.jpmc.midascore.entity.TransactionRecord;
import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.repository.TransactionRecordRepository;
import com.jpmc.midascore.repository.UserRepository;
import com.jpmc.midascore.IncentiveResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class TransactionListener {

    private final List<Transaction> receivedTransactions = new ArrayList<>();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRecordRepository transactionRecordRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    @KafkaListener(
        topics = "${general.kafka-topic}",
        groupId = "midas-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void listen(Transaction transaction) {
        System.out.println("✅ Received transaction: " + transaction);

        long senderId = transaction.getSenderId();
        long recipientId = transaction.getRecipientId();
        float amount = transaction.getAmount();

        Optional<UserRecord> senderOpt = userRepository.findById(senderId);
        Optional<UserRecord> recipientOpt = userRepository.findById(recipientId);

        if (senderOpt.isEmpty() || recipientOpt.isEmpty()) {
            System.out.println("❌ Invalid transaction: sender or recipient does not exist. Discarding.");
            return;
        }

        UserRecord sender = senderOpt.get();
        UserRecord recipient = recipientOpt.get();

        if (sender.getBalance() < amount) {
            System.out.println("❌ Invalid transaction: sender has insufficient balance. Discarding.");
            return;
        }

        float incentiveAmount = 0f;
        try {
            ResponseEntity<IncentiveResponse> response = restTemplate.postForEntity(
                "http://localhost:8080/incentive", transaction, IncentiveResponse.class);
            if (response.getBody() != null) {
                incentiveAmount = response.getBody().getAmount();
            }
        } catch (Exception e) {
            System.out.println("⚠️ Failed to fetch incentive: " + e.getMessage());
        }

        // Update balances
        sender.setBalance(sender.getBalance() - amount);
        recipient.setBalance(recipient.getBalance() + amount + incentiveAmount);

        userRepository.save(sender);
        userRepository.save(recipient);

        // Print Wilbur’s balance if involved
        if ("wilbur".equalsIgnoreCase(sender.getName()) || "wilbur".equalsIgnoreCase(recipient.getName())) {
            Optional<UserRecord> wilburOpt = userRepository.findByName("wilbur");
            if (wilburOpt.isPresent()) {
                System.out.println("🔥 Wilbur's balance is now: " + wilburOpt.get().getBalance());
            } else {
                System.out.println("⚠️ Wilbur user not found!");
            }
        }

        TransactionRecord record = new TransactionRecord(sender, recipient, amount, incentiveAmount);
        transactionRecordRepository.save(record);

        synchronized (receivedTransactions) {
            if (receivedTransactions.size() < 4) {
                receivedTransactions.add(transaction);
                System.out.println("📌 Captured transaction amount: " + amount);
            }
        }
    }

    public List<Transaction> getReceivedTransactions() {
        synchronized (receivedTransactions) {
            return new ArrayList<>(receivedTransactions);
        }
    }
}
