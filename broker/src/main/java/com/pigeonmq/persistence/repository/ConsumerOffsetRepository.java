package com.pigeonmq.persistence.repository;

import com.pigeonmq.persistence.entity.ConsumerOffsetEntity;
import com.pigeonmq.persistence.entity.ConsumerOffsetId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsumerOffsetRepository extends JpaRepository<ConsumerOffsetEntity, ConsumerOffsetId> {
    List<ConsumerOffsetEntity> findByIdClientId(String clientId);
}

