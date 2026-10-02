package com.substation.ops.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 工作票操作痕迹：只追加、不修改、不删除。
 * 撤回许可、现场变更、部分执行失败后刷新重进，仍能据此还原真实阶段，
 * 已发生的签发/许可/确认/拆除记录永不被抹掉。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "work_ticket_log")
public class WorkTicketLog {

    public static final String ACTION_ISSUE = "ISSUE";
    public static final String ACTION_PERMIT = "PERMIT";
    public static final String ACTION_WITHDRAW = "WITHDRAW";
    public static final String ACTION_TERMINATE = "TERMINATE";
    public static final String ACTION_CANCEL = "CANCEL";
    public static final String ACTION_MEASURE_EXECUTE = "MEASURE_EXECUTE";
    public static final String ACTION_MEASURE_REMOVE = "MEASURE_REMOVE";
    public static final String ACTION_MEASURE_ADD = "MEASURE_ADD";
    public static final String ACTION_MEASURE_UPDATE = "MEASURE_UPDATE";
    public static final String ACTION_MEASURE_CANCEL = "MEASURE_CANCEL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "action", length = 32)
    private String action;

    @Column(name = "measure_id")
    private Long measureId;

    @Column(name = "person", length = 32)
    private String person;

    @Column(name = "detail", length = 512)
    private String detail;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
