package com.pigeonmq.persistence.repository;

import com.pigeonmq.persistence.entity.DestinationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DestinationRepository extends JpaRepository<DestinationEntity, String> {
}

