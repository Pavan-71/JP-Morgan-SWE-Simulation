package com.jpmc.midascore.repository;

import com.jpmc.midascore.entity.TransactionRecord;
import org.springframework.data.repository.CrudRepository;

public interface TransactionRecordRepository extends CrudRepository<TransactionRecord, Long> {
    // Add custom query methods here if needed later
}
