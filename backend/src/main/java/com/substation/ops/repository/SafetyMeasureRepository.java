package com.substation.ops.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import com.substation.ops.domain.SafetyMeasure;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface SafetyMeasureRepository extends JpaRepository<SafetyMeasure, Long> {

    List<SafetyMeasure> findByTicketIdOrderBySeqNoAscIdAsc(Long ticketId);

    /**
     * 带行锁取单条措施：两人同时确认/拆除同一项时，后到的事务在锁内看到的是最新状态，
     * 配合 {@code @Version} 乐观锁，保证只有一次有效推进、不产生重复执行记录。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select m from SafetyMeasure m where m.id = :id")
    Optional<SafetyMeasure> findByIdForUpdate(Long id);

    long countByTicketId(Long ticketId);
}
