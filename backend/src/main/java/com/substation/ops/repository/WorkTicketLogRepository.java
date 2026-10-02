package com.substation.ops.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.substation.ops.domain.WorkTicketLog;

public interface WorkTicketLogRepository extends JpaRepository<WorkTicketLog, Long> {

    List<WorkTicketLog> findByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId);
}
