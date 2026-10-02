package com.substation.ops.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.substation.ops.domain.OperationTicket;
import com.substation.ops.dto.TimelineDay;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface OperationTicketRepository extends JpaRepository<OperationTicket, Long> {

    List<OperationTicket> findByIssueDateOrderByIdAsc(LocalDate issueDate);

    /** 同一间隔的操作票（编排安全措施关联装设/恢复步骤时下拉用） */
    List<OperationTicket> findByBayIdOrderByIssueDateDescIdDesc(Long bayId);

    /** 行锁加载操作票：两人同时勾选同一步骤时串行化，杜绝重复执行记录。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select o from OperationTicket o where o.id = :id")
    Optional<OperationTicket> findByIdForUpdate(@Param("id") Long id);

    /** 顶部时间轴：按天统计操作票数量 */
    @Query("select new com.substation.ops.dto.TimelineDay(o.issueDate, 0L, count(o)) "
            + "from OperationTicket o "
            + "where o.issueDate between :from and :to "
            + "group by o.issueDate")
    List<TimelineDay> countByDay(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
