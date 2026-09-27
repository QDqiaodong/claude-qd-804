package com.substation.ops.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.substation.ops.domain.OperationTicket;
import com.substation.ops.dto.TimelineDay;

public interface OperationTicketRepository extends JpaRepository<OperationTicket, Long> {

    List<OperationTicket> findByIssueDateOrderByIdAsc(LocalDate issueDate);

    /** 顶部时间轴：按天统计操作票数量 */
    @Query("select new com.substation.ops.dto.TimelineDay(o.issueDate, 0L, count(o)) "
            + "from OperationTicket o "
            + "where o.issueDate between :from and :to "
            + "group by o.issueDate")
    List<TimelineDay> countByDay(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
