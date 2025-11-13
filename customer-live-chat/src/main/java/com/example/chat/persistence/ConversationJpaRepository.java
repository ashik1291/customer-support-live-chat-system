package com.example.chat.persistence;

import com.example.chat.domain.ConversationStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationJpaRepository extends JpaRepository<ConversationEntity, String> {

    List<ConversationEntity> findByAgentId(String agentId);

    List<ConversationEntity> findByAgentIdAndStatusIn(String agentId, List<ConversationStatus> statuses);

    @Query("select c from ConversationEntity c where c.status <> 'CLOSED'")
    List<ConversationEntity> findOpenConversations();

    @Query(
            "select c from ConversationEntity c "
                    + "where c.agentId = :agentId "
                    + "and (:statuses is null or c.status in (:statuses))")
    List<ConversationEntity> findForAgent(
            @Param("agentId") String agentId, @Param("statuses") List<ConversationStatus> statuses);
}


