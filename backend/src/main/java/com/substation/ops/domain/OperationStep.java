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
 * 操作步骤：倒闸操作的一行。必须按 step_no 从小到大逐条执行，不得跳步。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "operation_step")
public class OperationStep {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_DONE = "DONE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id")
    private Long ticketId;

    /** 步骤序号，同一张票内唯一，从 1 开始 */
    @Column(name = "step_no")
    private Integer stepNo;

    /** 操作内容，如「拉开 101 断路器」 */
    @Column(name = "action_desc", length = 255)
    private String actionDesc;

    /** 该步的实际执行人 */
    @Column(name = "execute_person", length = 32)
    private String executePerson;

    @Column(name = "execute_time")
    private LocalDateTime executeTime;

    @Column(name = "status", length = 24)
    private String status;
}
