package com.substation.ops.common;

import java.util.HashMap;
import java.util.Map;

/**
 * 本仓的词面对照表（供电运行口径的中文说法）。所有 Controller/Service 共用。
 */
public final class Labels {

    private Labels() {
    }

    private static final Map<String, String> BAY_STATUS = new HashMap<>();
    private static final Map<String, String> BAY_TYPE = new HashMap<>();
    private static final Map<String, String> DEFECT_LEVEL = new HashMap<>();
    private static final Map<String, String> DEFECT_STATUS = new HashMap<>();
    private static final Map<String, String> WORK_STATUS = new HashMap<>();
    private static final Map<String, String> OP_STATUS = new HashMap<>();
    private static final Map<String, String> EQUIP_TYPE = new HashMap<>();
    private static final Map<String, String> EQUIP_STATUS = new HashMap<>();
    private static final Map<String, String> INSPECT_RESULT = new HashMap<>();

    static {
        BAY_STATUS.put("RUNNING", "运行");
        BAY_STATUS.put("HOT_STANDBY", "热备");
        BAY_STATUS.put("COLD_STANDBY", "冷备");
        BAY_STATUS.put("MAINTENANCE", "检修");

        BAY_TYPE.put("MAIN_TRANSFORMER", "主变间隔");
        BAY_TYPE.put("OUTGOING", "出线间隔");
        BAY_TYPE.put("CAPACITOR", "电容器间隔");
        BAY_TYPE.put("BUS_TIE", "母联间隔");

        DEFECT_LEVEL.put("URGENT", "紧急");
        DEFECT_LEVEL.put("MAJOR", "重大");
        DEFECT_LEVEL.put("MINOR", "一般");

        DEFECT_STATUS.put("REGISTERED", "已登记");
        DEFECT_STATUS.put("DISPATCHED", "已派工");
        DEFECT_STATUS.put("PROCESSING", "处理中");
        DEFECT_STATUS.put("ACCEPTED", "已验收");

        WORK_STATUS.put("DRAFT", "待签发");
        WORK_STATUS.put("ISSUED", "已签发");
        WORK_STATUS.put("PERMITTED", "已许可");
        WORK_STATUS.put("TERMINATED", "已终结");
        WORK_STATUS.put("CANCELED", "已作废");

        OP_STATUS.put("PENDING", "待执行");
        OP_STATUS.put("RUNNING", "执行中");
        OP_STATUS.put("FINISHED", "已完成");
        OP_STATUS.put("CANCELED", "已作废");

        EQUIP_TYPE.put("TRANSFORMER", "主变压器");
        EQUIP_TYPE.put("BREAKER", "断路器");
        EQUIP_TYPE.put("DISCONNECTOR", "隔离开关");
        EQUIP_TYPE.put("CT_PT", "电流/电压互感器");
        EQUIP_TYPE.put("ARRESTER", "避雷器");

        EQUIP_STATUS.put("RUNNING", "运行");
        EQUIP_STATUS.put("STANDBY", "备用");
        EQUIP_STATUS.put("MAINTENANCE", "检修");
        EQUIP_STATUS.put("DEFECTIVE", "缺陷运行");

        INSPECT_RESULT.put("NORMAL", "正常");
        INSPECT_RESULT.put("ABNORMAL", "异常");
    }

    public static String of(Map<String, String> source, String key) {
        return key == null ? "" : source.getOrDefault(key, key);
    }

    public static String bayStatus(String status) {
        return of(BAY_STATUS, status);
    }

    public static String bayType(String type) {
        return of(BAY_TYPE, type);
    }

    public static String defectLevel(String level) {
        return of(DEFECT_LEVEL, level);
    }

    public static String defectStatus(String status) {
        return of(DEFECT_STATUS, status);
    }

    public static String workStatus(String status) {
        return of(WORK_STATUS, status);
    }

    public static String opStatus(String status) {
        return of(OP_STATUS, status);
    }

    public static String equipType(String type) {
        return of(EQUIP_TYPE, type);
    }

    public static String equipStatus(String status) {
        return of(EQUIP_STATUS, status);
    }

    public static String inspectResult(String result) {
        return of(INSPECT_RESULT, result);
    }
}
