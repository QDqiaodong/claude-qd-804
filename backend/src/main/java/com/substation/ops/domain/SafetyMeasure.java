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
 * 工作票安全措施：从原来的一段文字拆成可逐项确认的闭环条目。
 *
 * 四阶段：待执行 PENDING → 已执行 EXECUTED（许可后票面呈现为待拆除 PENDING_REMOVE）
 *         → 已拆除 REMOVED；现场变更旧项置 CANCELED，痕迹保留、不再参与核对。
 * 每项可分别关联同间隔操作票的「装设步骤」与恢复操作票的「拆除步骤」，
 * 许可前、终结前两侧都要核对一致。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "safety_measure")
public class SafetyMeasure {

    /** 类型 */
    public static final String TYPE_GROUND_WIRE = "GROUND_WIRE";
    public static final String TYPE_EARTH_SWITCH = "EARTH_SWITCH";
    public static final String TYPE_BARRIER = "BARRIER";
    public static final String TYPE_SIGNBOARD = "SIGNBOARD";

    /** 阶段 */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_EXECUTED = "EXECUTED";
    public static final String STATUS_PENDING_REMOVE = "PENDING_REMOVE";
    public static final String STATUS_REMOVED = "REMOVED";
    public static final String STATUS_CANCELED = "CANCELED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id")
    private Long ticketId;

    /** 票面序号，从 1 开始 */
    @Column(name = "seq_no")
    private Integer seqNo;

    @Column(name = "measure_type", length = 16)
    private String measureType;

    @Column(name = "bay_id")
    private Long bayId;

    /** 具体位置 */
    @Column(name = "location", length = 128)
    private String location;

    /** 要求动作：装设/合上/拆除/拉开等 */
    @Column(name = "required_action", length = 255)
    private String requiredAction;

    /** 标示牌文字 */
    @Column(name = "signboard_text", length = 64)
    private String signboardText;

    /** 许可前核对的装设操作票与步骤 */
    @Column(name = "op_ticket_id")
    private Long opTicketId;

    @Column(name = "op_step_no")
    private Integer opStepNo;

    /** 终结前核对的恢复操作票与步骤 */
    @Column(name = "restore_ticket_id")
    private Long restoreTicketId;

    @Column(name = "restore_step_no")
    private Integer restoreStepNo;

    @Column(name = "status", length = 24)
    private String status;

    @Column(name = "execute_person", length = 32)
    private String executePerson;

    @Column(name = "execute_time")
    private LocalDateTime executeTime;

    @Column(name = "remove_person", length = 32)
    private String removePerson;

    @Column(name = "remove_time")
    private LocalDateTime removeTime;

    /** 现场变更/作废说明 */
    @Column(name = "change_remark", length = 255)
    private String changeRemark;

    /**
     * 版本号：每次确认/变更/拆除手动 +1。两名值班员同时确认时由行锁串行化（只有一次推进），
     * 后提交者页面版本过期，被要求刷新查看最新结果；不作为 JPA 乐观锁，避免与行锁语义冲突。
     */
    @Column(name = "version")
    private Integer version;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
