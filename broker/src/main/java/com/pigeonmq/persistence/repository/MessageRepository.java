package com.pigeonmq.persistence.repository;

import com.pigeonmq.domain.DestinationType;
import com.pigeonmq.persistence.entity.MessageEntity;
import com.pigeonmq.persistence.entity.MessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<MessageEntity, UUID> {

    List<MessageEntity> findByDestinationNameAndDestTypeOrderByOffsetValAsc(String destinationName, DestinationType destType);

    List<MessageEntity> findByDestinationNameAndDestTypeAndStatusInOrderByOffsetValAsc(
            String destinationName,
            DestinationType destType,
            Collection<MessageStatus> statuses
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update MessageEntity m set m.status = :status where m.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") MessageStatus status);
}

