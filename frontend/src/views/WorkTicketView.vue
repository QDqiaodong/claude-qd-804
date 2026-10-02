<template>
  <div class="ops-page">
    <div class="ops-row" style="align-items: flex-start">
      <!-- ============ 当日工作票列表 ============ -->
      <div class="ops-block" style="width: 290px; flex: none">
        <div class="ops-title">
          {{ date }} 工作票
          <span class="ops-sub">{{ list.length }} 张</span>
        </div>
        <el-button size="small" type="primary" plain @click="createVisible = true">新建工作票</el-button>
        <div class="tk-list">
          <div
            v-for="t in list"
            :key="t.id"
            class="tk-item"
            :class="{ 'is-active': active && active.id === t.id }"
            @click="selectTicket(t.id)"
          >
            <div class="tk-item-top">
              <span class="tk-no">{{ t.ticketNo }}</span>
              <el-tag size="small" :type="statusType(t.status)">{{ t.statusName }}</el-tag>
            </div>
            <div class="tk-item-bay">{{ t.bayName }} · {{ t.voltageLevel }}</div>
            <div class="tk-ms-progress">
              安措 {{ t.measureTotal || 0 }} 项 ·
              <span class="ms-pending">待装 {{ t.measurePending || 0 }}</span> ·
              <span class="ms-remove">待拆 {{ t.measureWaitingRemove || 0 }}</span> ·
              <span class="ms-done">已拆 {{ t.measureRemoved || 0 }}</span>
              <span v-if="t.measureCanceled"> · 作废 {{ t.measureCanceled }}</span>
            </div>
            <div class="tk-steps">
              <i :class="{ on: t.issuer }">签</i><i :class="{ on: t.permittor }">许</i><i :class="{ on: t.terminator }">终</i>
            </div>
          </div>
          <div v-if="!list.length" class="ops-empty">当天暂无工作票，可在时间轴上换一天</div>
        </div>

        <div class="ops-title" style="margin-top: 12px">票面状态统计</div>
        <div v-for="s in stats" :key="s.status" class="stat-row">
          <span class="stat-name">{{ s.statusName }}</span>
          <el-progress
            :percentage="Math.round((s.count / Math.max(1, statsTotal)) * 100)"
            :stroke-width="10"
            color="#e65100"
            style="flex: 1"
          />
          <b class="stat-count">{{ s.count }}</b>
        </div>
        <div v-if="!stats.length" class="ops-empty">当日暂无统计</div>
      </div>

      <!-- ============ 票面 + 安全措施闭环 ============ -->
      <div class="ops-block ops-flex-1" style="min-width: 560px">
        <template v-if="!active">
          <div class="ops-title">工作票票面 · 安全措施执行闭环</div>
          <div class="ops-empty">左侧选择一张工作票查看票面</div>
        </template>

        <template v-else>
          <div class="ops-title">
            {{ active.ticketNo }} · {{ active.bayName }}
            <el-tag size="small" :type="statusType(active.status)" style="margin-left: 6px">{{ active.statusName }}</el-tag>
            <span class="ops-sub" style="margin-left: 6px">{{ active.waitingText }}</span>
          </div>

          <!-- 下一步指引 -->
          <div class="next-banner" :class="gateBannerClass">
            <span class="next-label">下一步：</span>{{ nextStepText }}
          </div>

          <!-- A4 票面（压缩展示） -->
          <div class="paper">
            <div class="paper-head">
              <span class="paper-company">国网 · 变电运行工区</span>
              <span class="paper-type">第 一 种 工 作 票</span>
              <span class="paper-no">{{ active.ticketNo }}</span>
            </div>
            <table class="paper-table">
              <tr>
                <td class="lb">工作负责人</td><td>{{ active.workLeader }}</td>
                <td class="lb">电压等级</td><td>{{ active.voltageLevel }}</td>
              </tr>
              <tr>
                <td class="lb">作业间隔</td><td>{{ active.bayName }}（{{ active.bayStatusName }}）</td>
                <td class="lb">业务日期</td><td>{{ active.issueDate }}</td>
              </tr>
              <tr>
                <td class="lb">班成员</td><td colspan="3">{{ active.teamMembers || '—' }}</td>
              </tr>
              <tr>
                <td class="lb">工作内容</td><td colspan="3">{{ active.workContent }}</td>
              </tr>
              <tr>
                <td class="lb">停电范围</td><td colspan="3">{{ active.powerCutRange || '—' }}</td>
              </tr>
              <tr>
                <td class="lb">安措概览</td>
                <td colspan="3">
                  共 {{ active.measureTotal }} 项
                  （接地线 {{ typeCount('GROUND_WIRE') }} / 接地刀闸 {{ typeCount('EARTH_SWITCH') }}
                  / 遮栏 {{ typeCount('BARRIER') }} / 标示牌 {{ typeCount('SIGNBOARD') }}）；
                  <b class="ms-pending">待执行 {{ statusCount('PENDING') }}</b>，
                  <b class="ms-exec">已执行 {{ statusCount('EXECUTED') }}</b>，
                  <b class="ms-remove">待拆除 {{ statusCount('PENDING_REMOVE') }}</b>，
                  <b class="ms-done">已拆除 {{ statusCount('REMOVED') }}</b>，
                  <span v-if="active.measureCanceled">已作废 {{ active.measureCanceled }} 项（痕迹保留）</span>
                </td>
              </tr>
            </table>
            <div class="paper-signs">
              <div class="sign-cell">
                <div class="sign-title">① 签发</div>
                <div class="sign-name" :class="{ signed: active.issuer }">{{ active.issuer || '待签发' }}</div>
                <div class="sign-time">{{ fmt(active.issueTime) }}</div>
              </div>
              <div class="sign-cell">
                <div class="sign-title">② 许可</div>
                <div class="sign-name" :class="{ signed: active.permittor }">{{ active.permittor || '待许可' }}</div>
                <div class="sign-time">{{ fmt(active.permitTime) }}</div>
              </div>
              <div class="sign-cell">
                <div class="sign-title">③ 终结</div>
                <div class="sign-name" :class="{ signed: active.terminator }">{{ active.terminator || '待终结' }}</div>
                <div class="sign-time">{{ fmt(active.terminateTime) }}</div>
              </div>
            </div>
          </div>

          <!-- 双侧闸道卡点 -->
          <div v-if="showPermitGate" class="gate-box" :class="{ blocked: !active.permitGate.ok, clear: active.permitGate.ok }">
            <div class="gate-head">
              许可前核对（安全措施侧 ＋ 同间隔关联操作票侧）
              <el-tag size="small" :type="active.permitGate.ok ? 'success' : 'danger'">
                {{ active.permitGate.ok ? '双侧一致，可许可' : '未闭环，禁止许可' }}
              </el-tag>
            </div>
            <template v-if="!active.permitGate.ok">
              <div v-if="active.permitGate.measureBlockers.length" class="gate-group">
                <div class="gate-group-name">安全措施侧：</div>
                <div v-for="(b, i) in active.permitGate.measureBlockers" :key="'pm' + i" class="gate-item">✗ {{ b }}</div>
              </div>
              <div v-if="active.permitGate.operationBlockers.length" class="gate-group">
                <div class="gate-group-name">关联操作票侧：</div>
                <div v-for="(b, i) in active.permitGate.operationBlockers" :key="'po' + i" class="gate-item">✗ {{ b }}</div>
              </div>
            </template>
            <div v-else class="gate-ok">所有要求装设/合上的措施均已由执行人逐项确认，且关联倒闸操作步骤均已执行。</div>
          </div>

          <div v-if="active.status === 'PERMITTED'" class="gate-box" :class="{ blocked: !active.terminateGate.ok, clear: active.terminateGate.ok }">
            <div class="gate-head">
              终结前核对（应拆除措施 ＋ 关联恢复操作票恢复步骤）
              <el-tag size="small" :type="active.terminateGate.ok ? 'success' : 'danger'">
                {{ active.terminateGate.ok ? '已全部拆除恢复，可终结' : '未闭环，禁止终结' }}
              </el-tag>
            </div>
            <template v-if="!active.terminateGate.ok">
              <div v-if="active.terminateGate.measureBlockers.length" class="gate-group">
                <div class="gate-group-name">安全措施侧：</div>
                <div v-for="(b, i) in active.terminateGate.measureBlockers" :key="'tm' + i" class="gate-item">✗ {{ b }}</div>
              </div>
              <div v-if="active.terminateGate.operationBlockers.length" class="gate-group">
                <div class="gate-group-name">关联恢复操作票侧：</div>
                <div v-for="(b, i) in active.terminateGate.operationBlockers" :key="'to' + i" class="gate-item">✗ {{ b }}</div>
              </div>
            </template>
            <div v-else class="gate-ok">应拆除措施全部拆除确认，关联恢复步骤全部执行完毕。</div>
          </div>

          <!-- ============ 安全措施逐项表 ============ -->
          <div class="ms-head">
            安全措施（逐项确认闭环）
            <el-button
              v-if="active.canEditMeasures"
              size="small" type="primary" plain style="margin-left: 10px"
              @click="openAddMeasure"
            >＋ 新增措施</el-button>
            <span class="ops-sub" style="margin-left: 8px">装设确认仅在签发后、许可前；变更须先撤回许可</span>
          </div>

          <div v-if="!active.measures || !active.measures.length" class="ops-empty" style="padding: 14px">
            本票尚未编排结构化安全措施，请「新增措施」逐项登记接地线 / 接地刀闸 / 遮栏 / 标示牌
          </div>

          <div v-else class="ms-list">
            <div v-for="m in active.measures" :key="m.id" class="ms-row" :class="msRowClass(m)">
              <div class="ms-seq">{{ m.seqNo }}</div>
              <div class="ms-main">
                <div class="ms-line1">
                  <el-tag size="small" :color="typeColor(m.measureType)" style="color: #fff; border: none; width: 64px">
                    {{ m.measureTypeName }}
                  </el-tag>
                  <span class="ms-location">{{ m.bayName }} · {{ m.location }}</span>
                  <el-tag size="small" :type="msStatusType(m.status)" effect="plain" style="margin-left: auto">
                    {{ m.statusName }}
                  </el-tag>
                </div>
                <div class="ms-line2">要求动作：{{ m.requiredAction }}<template v-if="m.signboardText">｜牌文：{{ m.signboardText }}</template></div>
                <div class="ms-line3">
                  <span>装设关联：<b :class="linkClass(m, true)">{{ m.linkedOpText }}</b></span>
                  <span>恢复关联：<b :class="linkClass(m, false)">{{ m.linkedRestoreText }}</b></span>
                </div>
                <div class="ms-line4">
                  <span v-if="m.executePerson" class="ms-who-install">装设：{{ m.executePerson }} · {{ fmt(m.executeTime) }}</span>
                  <span v-else class="ms-who-none">装设：未确认</span>
                  <span v-if="m.removePerson" class="ms-who-remove">拆除：{{ m.removePerson }} · {{ fmt(m.removeTime) }}</span>
                  <span v-else-if="m.status === 'REMOVED'"></span>
                  <span v-else-if="m.status !== 'CANCELED'" class="ms-who-none">拆除：未确认</span>
                  <span v-if="m.changeRemark" class="ms-change">变更说明：{{ m.changeRemark }}</span>
                </div>
              </div>
              <div class="ms-actions">
                <el-button
                  v-if="canExecute(m)" size="small" type="primary"
                  @click="openExecute(m)"
                >装设确认</el-button>
                <el-button
                  v-if="canRemove(m)" size="small" type="success" plain
                  @click="openRemove(m)"
                >拆除确认</el-button>
                <el-button
                  v-if="canEdit(m)" size="small"
                  @click="openEditMeasure(m)"
                >编辑</el-button>
                <el-button
                  v-if="canCancelMeasure(m)" size="small" type="danger" plain
                  @click="openCancelMeasure(m)"
                >作废</el-button>
              </div>
            </div>
          </div>

          <!-- 三审 + 撤回 + 作废 按钮条 -->
          <div class="sign-bar">
            <el-button
              v-for="btn in signButtons"
              :key="btn.stage"
              :type="btn.reached ? 'primary' : 'default'"
              :class="{ 'btn-next': btn.next }"
              :disabled="!btn.next"
              size="large"
              @click="doSign(btn.stage)"
            >
              {{ btn.label }}
            </el-button>
            <el-button
              v-if="active.status === 'PERMITTED'"
              size="large" type="warning" plain
              @click="openWithdraw"
            >撤回许可（现场变更）</el-button>
            <el-button
              v-if="active.status === 'DRAFT' || active.status === 'ISSUED'"
              size="large" type="danger" plain
              @click="openCancelTicket"
            >作废工作票</el-button>
          </div>
        </template>
      </div>

      <!-- ============ 操作痕迹 ============ -->
      <div class="ops-block" style="width: 300px; flex: none" v-if="active">
        <div class="ops-title">操作痕迹（只增不删）</div>
        <div class="ops-sub" style="margin-bottom: 8px">撤回 / 作废 / 部分失败后刷新重进，真实阶段与已发生记录均在此可追溯</div>
        <div v-if="!active.logs || !active.logs.length" class="ops-empty">暂无痕迹</div>
        <div class="log-list">
          <div v-for="l in active.logs" :key="l.id" class="log-item">
            <div class="log-dot" :class="logDotClass(l.action)"></div>
            <div class="log-body">
              <div class="log-head">
                <b>{{ l.actionName }}</b>
                <span v-if="l.person" class="log-person">{{ l.person }}</span>
              </div>
              <div class="log-detail">{{ l.detail }}</div>
              <div class="log-time">{{ fmt(l.createdAt) }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ============ 三审签字 ============ -->
    <el-dialog v-model="signVisible" :title="signTitle" width="360">
      <el-input v-model="signPerson" placeholder="请输入签字人姓名" />
      <template #footer>
        <el-button @click="signVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmSign">确认签字</el-button>
      </template>
    </el-dialog>

    <!-- ============ 装设 / 拆除确认 ============ -->
    <el-dialog v-model="confirmVisible" :title="confirmTitle" width="380">
      <div class="confirm-desc">
        <el-tag size="small" :color="typeColor(confirmForm.measureType)" style="color: #fff; border: none">
          {{ confirmForm.measureTypeName }}
        </el-tag>
        <b style="margin-left: 6px">第 {{ confirmForm.seqNo }} 项 · {{ confirmForm.location }}</b>
        <div style="margin-top: 6px; color: var(--ops-grey); font-size: 12px">{{ confirmForm.requiredAction }}</div>
      </div>
      <el-input v-model="confirmForm.person" placeholder="执行人姓名" style="margin-top: 10px" />
      <template #footer>
        <el-button @click="confirmVisible = false">取消</el-button>
        <el-button :type="confirmForm.mode === 'execute' ? 'primary' : 'success'" @click="confirmMeasureAction">
          确认{{ confirmForm.mode === 'execute' ? '装设' : '拆除' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- ============ 撤回许可 ============ -->
    <el-dialog v-model="withdrawVisible" title="撤回许可（已确认痕迹保留）" width="420">
      <el-alert type="warning" :closable="false" style="margin-bottom: 10px"
        title="撤回后票面回到「已签发」：新增或变更项回到待执行，需重新装设确认并再次核对关联操作票步骤后才能重新许可。" />
      <el-input v-model="withdrawForm.person" placeholder="撤回人（许可人）" />
      <el-input v-model="withdrawForm.reason" type="textarea" :rows="2" placeholder="撤回原因 / 现场变更情况" style="margin-top: 8px" />
      <template #footer>
        <el-button @click="withdrawVisible = false">取消</el-button>
        <el-button type="warning" @click="confirmWithdraw">确认撤回许可</el-button>
      </template>
    </el-dialog>

    <!-- ============ 作废工作票 ============ -->
    <el-dialog v-model="cancelTicketVisible" title="作废工作票" width="420">
      <el-alert v-if="active && active.status === 'ISSUED'" type="error" :closable="false" style="margin-bottom: 10px"
        title="已签发且现场仍有在装安措时不能作废：需先撤回许可、拆除措施并完成关联恢复步骤。" />
      <el-input v-model="cancelTicketForm.person" placeholder="操作人" />
      <el-input v-model="cancelTicketForm.reason" type="textarea" :rows="2" placeholder="作废原因" style="margin-top: 8px" />
      <template #footer>
        <el-button @click="cancelTicketVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmCancelTicket">确认作废</el-button>
      </template>
    </el-dialog>

    <!-- ============ 作废措施 ============ -->
    <el-dialog v-model="cancelMeasureVisible" title="现场变更：作废该项措施" width="420">
      <el-alert type="info" :closable="false" style="margin-bottom: 10px"
        title="原装设/拆除执行人与时间作为历史痕迹保留，不会被删除；作废项不再参与许可与终结核对。" />
      <el-input v-model="cancelMeasureForm.remark" type="textarea" :rows="2" placeholder="变更/作废说明（必填）" />
      <template #footer>
        <el-button @click="cancelMeasureVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmCancelMeasure">确认作废</el-button>
      </template>
    </el-dialog>

    <!-- ============ 新增 / 编辑 措施 ============ -->
    <el-dialog v-model="measureVisible" :title="measureForm.id ? '编辑安全措施' : '新增安全措施'" width="560">
      <el-form :model="measureForm" label-width="104px" size="small">
        <el-form-item label="措施类型" required>
          <el-radio-group v-model="measureForm.measureType">
            <el-radio-button label="GROUND_WIRE">接地线</el-radio-button>
            <el-radio-button label="EARTH_SWITCH">接地刀闸</el-radio-button>
            <el-radio-button label="BARRIER">遮栏</el-radio-button>
            <el-radio-button label="SIGNBOARD">标示牌</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="所在间隔" required>
          <el-select v-model="measureForm.bayId" placeholder="选择间隔" style="width: 100%">
            <el-option v-for="b in bayOptions" :key="b.id" :label="`${b.code} ${b.name}`" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="具体位置" required>
          <el-input v-model="measureForm.location" placeholder="如：2017 接地刀闸 / 主变10kV套管侧 / 212断路器操作把手" />
        </el-form-item>
        <el-form-item label="要求动作" required>
          <el-input v-model="measureForm.requiredAction" :placeholder="actionPlaceholder" />
        </el-form-item>
        <el-form-item v-if="measureForm.measureType === 'SIGNBOARD'" label="标示牌文字" required>
          <el-input v-model="measureForm.signboardText" placeholder="如：禁止合闸，有人工作 / 在此工作" />
        </el-form-item>
        <el-form-item label="装设操作票">
          <el-select v-model="measureForm.opTicketId" placeholder="接地线/接地刀闸必须选择同间隔装设票" clearable style="width: 100%"
            @change="measureForm.opStepNo = null">
            <el-option v-for="o in opTicketOptions" :key="o.id" :label="`${o.ticketNo} ${o.taskName}`" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="装设步骤">
          <el-select v-model="measureForm.opStepNo" placeholder="选择装设/合上对应步骤" clearable style="width: 100%"
            :disabled="!measureForm.opTicketId">
            <el-option v-for="s in selectedOpSteps('opTicketId')" :key="s.stepNo"
              :label="`第${s.stepNo}步 ${s.actionDesc}${s.done ? '（已执行）' : '（未执行）'}`" :value="s.stepNo" />
          </el-select>
        </el-form-item>
        <el-form-item label="恢复操作票">
          <el-select v-model="measureForm.restoreTicketId" placeholder="终结前需核对的恢复票（可选）" clearable style="width: 100%"
            @change="measureForm.restoreStepNo = null">
            <el-option v-for="o in opTicketOptions" :key="o.id" :label="`${o.ticketNo} ${o.taskName}`" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="恢复步骤">
          <el-select v-model="measureForm.restoreStepNo" placeholder="选择拆除/拉开对应步骤" clearable style="width: 100%"
            :disabled="!measureForm.restoreTicketId">
            <el-option v-for="s in selectedOpSteps('restoreTicketId')" :key="s.stepNo"
              :label="`第${s.stepNo}步 ${s.actionDesc}${s.done ? '（已执行）' : '（未执行）'}`" :value="s.stepNo" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="measureVisible = false">取消</el-button>
        <el-button type="primary" @click="submitMeasure">保存（措施为待执行）</el-button>
      </template>
    </el-dialog>

    <!-- ============ 新建工作票 ============ -->
    <el-dialog v-model="createVisible" title="新建工作票（票号由存储过程生成）" width="520">
      <el-form :model="newForm" label-width="96px" size="small">
        <el-form-item label="作业间隔">
          <el-select v-model="newForm.bayId" placeholder="选择间隔" style="width: 100%">
            <el-option v-for="b in bayOptions" :key="b.id" :label="`${b.code} ${b.name}`" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="工作负责人">
          <el-input v-model="newForm.workLeader" />
        </el-form-item>
        <el-form-item label="班成员">
          <el-input v-model="newForm.teamMembers" placeholder="逗号分隔" />
        </el-form-item>
        <el-form-item label="工作内容">
          <el-input v-model="newForm.workContent" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="停电范围">
          <el-input v-model="newForm.powerCutRange" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="安措补充说明">
          <el-input v-model="newForm.safetyMeasures" type="textarea" :rows="2"
            placeholder="结构化安措请在开票后于下方「安全措施」区逐项新增（接地线/接地刀闸/遮栏/标示牌）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">开票</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  bays, createWorkTicket, signWorkTicket, workTicket, workTicketStats, workTickets,
  withdrawWorkTicket, cancelWorkTicket,
  addMeasure, updateMeasure, executeMeasure, removeMeasure, cancelMeasure,
  operationTicketsByBay
} from '../api'

const props = defineProps({
  date: { type: String, required: true },
  substationId: { type: [Number, String, null], default: null }
})

const list = ref([])
const stats = ref([])
const active = ref(null)
const bayOptions = ref([])
const opTicketOptions = ref([])

// ---- 弹窗状态 ----
const signVisible = ref(false)
const signStage = ref('')
const signPerson = ref('')

const createVisible = ref(false)
const newForm = ref(emptyNewForm())

const confirmVisible = ref(false)
const confirmForm = ref({ id: null, mode: 'execute', person: '', version: null })

const withdrawVisible = ref(false)
const withdrawForm = ref({ person: '', reason: '' })

const cancelTicketVisible = ref(false)
const cancelTicketForm = ref({ person: '', reason: '' })

const cancelMeasureVisible = ref(false)
const cancelMeasureForm = ref({ id: null, remark: '' })

const measureVisible = ref(false)
const measureForm = ref(emptyMeasureForm())

function emptyNewForm() {
  return { bayId: null, issueDate: props.date, workLeader: '', teamMembers: '', workContent: '', powerCutRange: '', safetyMeasures: '' }
}
function emptyMeasureForm() {
  return {
    id: null, measureType: 'GROUND_WIRE', bayId: null, location: '', requiredAction: '', signboardText: '',
    opTicketId: null, opStepNo: null, restoreTicketId: null, restoreStepNo: null
  }
}

const statsTotal = computed(() => stats.value.reduce((s, i) => s + Number(i.count), 0))

const signTitle = computed(() => ({ ISSUE: '签发人签字', PERMIT: '许可人签字', TERMINATE: '终结人签字' })[signStage.value] || '签字')

const confirmTitle = computed(() => confirmForm.value.mode === 'execute' ? '装设 / 合上确认' : '拆除 / 拉开确认')

const actionPlaceholder = computed(() => ({
  GROUND_WIRE: '如：装设接地线 1 组（编号 021）',
  EARTH_SWITCH: '如：合上 2017 接地刀闸',
  BARRIER: '如：装设封闭式遮栏 / 安全围栏',
  SIGNBOARD: '如：在操作把手悬挂标示牌'
}[measureForm.value.measureType]))

const signButtons = computed(() => {
  if (!active.value) return []
  const signed = active.value.signedCount || 0
  return [
    { stage: 'ISSUE', label: '签发', index: 0 },
    { stage: 'PERMIT', label: '许可', index: 1 },
    { stage: 'TERMINATE', label: '终结', index: 2 }
  ].map((b) => ({
    ...b,
    reached: signed > b.index,
    next: active.value && active.value.nextStage === b.stage && gateAllows(b.stage)
  }))
})

function gateAllows(stage) {
  if (!active.value) return false
  if (stage === 'PERMIT') return active.value.permitGate ? active.value.permitGate.ok : false
  if (stage === 'TERMINATE') return active.value.terminateGate ? active.value.terminateGate.ok : false
  return true
}

const showPermitGate = computed(() => active.value && (active.value.status === 'ISSUED' || active.value.status === 'DRAFT'))

const gateBannerClass = computed(() => {
  const t = active.value
  if (!t) return ''
  if (t.status === 'ISSUED') return t.permitGate && t.permitGate.ok ? 'banner-ok' : 'banner-block'
  if (t.status === 'PERMITTED') return t.terminateGate && t.terminateGate.ok ? 'banner-ok' : 'banner-warn'
  if (t.status === 'TERMINATED') return 'banner-done'
  if (t.status === 'CANCELED') return 'banner-dead'
  return 'banner-info'
})

const nextStepText = computed(() => {
  const t = active.value
  if (!t) return ''
  if (t.status === 'DRAFT') return '可继续逐项编排安全措施，完成后由签发人签发；签发后再由现场执行人逐项装设确认。'
  if (t.status === 'ISSUED') {
    if (t.permitGate && t.permitGate.ok) return '双侧核对一致，可由许可人许可开工。'
    const n = (t.permitGate.measureBlockers.length + t.permitGate.operationBlockers.length)
    return `尚有 ${n} 个卡点：先完成下列安全措施装设确认，并到「倒闸操作」页执行同间隔关联步骤，再发起许可。`
  }
  if (t.status === 'PERMITTED') {
    if (t.terminateGate && t.terminateGate.ok) return '应拆措施已全部拆除、恢复步骤已完成，可终结。'
    return '作业结束后逐项拆除确认，并完成关联恢复操作票步骤；如现场需变更措施，请先撤回许可。'
  }
  if (t.status === 'TERMINATED') return '本票已终结，闭环完成。'
  return '本票已作废。'
})

function statusType(status) {
  if (status === 'TERMINATED') return 'success'
  if (status === 'CANCELED') return 'danger'
  if (status === 'DRAFT') return 'info'
  return 'warning'
}

function typeColor(type) {
  return { GROUND_WIRE: '#1565c0', EARTH_SWITCH: '#6a1b9a', BARRIER: '#e65100', SIGNBOARD: '#2e7d32' }[type] || '#757575'
}

function msStatusType(status) {
  return { PENDING: 'info', EXECUTED: 'warning', PENDING_REMOVE: 'danger', REMOVED: 'success', CANCELED: 'info' }[status] || 'info'
}

function fmt(v) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '—'
}

function typeCount(type) {
  return (active.value.measures || []).filter((m) => m.measureType === type && m.status !== 'CANCELED').length
}
function statusCount(status) {
  return (active.value.measures || []).filter((m) => m.status === status).length
}

function msRowClass(m) {
  return {
    'row-pending': m.status === 'PENDING',
    'row-exec': m.status === 'EXECUTED',
    'row-remove': m.status === 'PENDING_REMOVE',
    'row-done': m.status === 'REMOVED',
    'row-canceled': m.status === 'CANCELED'
  }
}

function canExecute(m) {
  return active.value.status === 'ISSUED' && (m.status === 'PENDING' || m.status === 'REMOVED') && m.status !== 'CANCELED'
}
function canRemove(m) {
  return (active.value.status === 'PERMITTED' || active.value.status === 'ISSUED')
    && (m.status === 'EXECUTED' || m.status === 'PENDING_REMOVE')
}
function canEdit(m) {
  return active.value.canEditMeasures && !m.confirmed && m.status !== 'CANCELED'
}
function canCancelMeasure(m) {
  return active.value.status === 'ISSUED' && m.status !== 'CANCELED'
}

function linkClass(m, install) {
  const ticketId = install ? m.opTicketId : m.restoreTicketId
  if (!ticketId) return 'link-none'
  // 关联步骤状态在选项列表中体现，这里仅给样式
  return 'link-on'
}

function logDotClass(action) {
  if (action === 'PERMIT' || action === 'TERMINATE') return 'dot-ok'
  if (action === 'WITHDRAW' || action === 'CANCEL' || action === 'MEASURE_CANCEL') return 'dot-warn'
  return 'dot-normal'
}

// ============ 数据加载 ============
async function selectTicket(id) {
  const res = await workTicket(id)
  active.value = res.data
  await loadOpTickets(active.value.bayId)
}

async function loadOpTickets(bayId) {
  try {
    const res = await operationTicketsByBay(bayId)
    opTicketOptions.value = res.data || []
  } catch (e) {
    opTicketOptions.value = []
  }
}

function selectedOpSteps(key) {
  const id = measureForm.value[key]
  const t = opTicketOptions.value.find((o) => o.id === id)
  return t ? t.steps : []
}

async function reload(keepId) {
  const [res, statRes] = await Promise.all([workTickets(props.date), workTicketStats(props.date)])
  list.value = res.data || []
  stats.value = statRes.data || []
  const targetId = keepId || (active.value && active.value.id) || (list.value[0] && list.value[0].id)
  if (targetId && list.value.some((i) => i.id === targetId)) {
    await selectTicket(targetId)
  } else {
    active.value = null
  }
}

// ============ 三审 ============
function doSign(stage) {
  signStage.value = stage
  signPerson.value = ''
  signVisible.value = true
}

async function confirmSign() {
  if (!signPerson.value) {
    ElMessage.warning('请输入签字人姓名')
    return
  }
  try {
    await signWorkTicket(active.value.id, signStage.value, signPerson.value)
    ElMessage.success('签字完成')
    signVisible.value = false
    await reload(active.value.id)
  } catch (e) {
    ElMessage.error(e.message)
    await reload(active.value.id)
  }
}

// ============ 装设 / 拆除 ============
function openExecute(m) {
  confirmForm.value = { id: m.id, seqNo: m.seqNo, measureType: m.measureType, measureTypeName: m.measureTypeName, location: m.location, requiredAction: m.requiredAction, mode: 'execute', person: '', version: m.version }
  confirmVisible.value = true
}
function openRemove(m) {
  confirmForm.value = { id: m.id, seqNo: m.seqNo, measureType: m.measureType, measureTypeName: m.measureTypeName, location: m.location, requiredAction: m.requiredAction, mode: 'remove', person: '', version: m.version }
  confirmVisible.value = true
}
async function confirmMeasureAction() {
  const f = confirmForm.value
  if (!f.person) {
    ElMessage.warning('请填写执行人')
    return
  }
  try {
    if (f.mode === 'execute') {
      await executeMeasure(active.value.id, f.id, f.person, f.version)
      ElMessage.success(`第 ${f.seqNo} 项装设确认完成`)
    } else {
      await removeMeasure(active.value.id, f.id, f.person, f.version)
      ElMessage.success(`第 ${f.seqNo} 项拆除确认完成`)
    }
    confirmVisible.value = false
    await reload(active.value.id)
  } catch (e) {
    ElMessage.error(e.message)
    await reload(active.value.id)
  }
}

// ============ 撤回 / 作废 ============
function openWithdraw() {
  withdrawForm.value = { person: active.value.permittor || '', reason: '' }
  withdrawVisible.value = true
}
async function confirmWithdraw() {
  if (!withdrawForm.value.person || !withdrawForm.value.reason) {
    ElMessage.warning('撤回人与原因必填')
    return
  }
  try {
    await withdrawWorkTicket(active.value.id, withdrawForm.value.person, withdrawForm.value.reason)
    ElMessage.success('已撤回许可，措施确认痕迹保留')
    withdrawVisible.value = false
    await reload(active.value.id)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openCancelTicket() {
  cancelTicketForm.value = { person: '', reason: '' }
  cancelTicketVisible.value = true
}
async function confirmCancelTicket() {
  if (!cancelTicketForm.value.person || !cancelTicketForm.value.reason) {
    ElMessage.warning('操作人与作废原因必填')
    return
  }
  try {
    await cancelWorkTicket(active.value.id, cancelTicketForm.value.person, cancelTicketForm.value.reason)
    ElMessage.success('工作票已作废')
    cancelTicketVisible.value = false
    await reload(active.value.id)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openCancelMeasure(m) {
  cancelMeasureForm.value = { id: m.id, remark: '' }
  cancelMeasureVisible.value = true
}
async function confirmCancelMeasure() {
  if (!cancelMeasureForm.value.remark) {
    ElMessage.warning('请填写作废/变更说明')
    return
  }
  try {
    await cancelMeasure(active.value.id, cancelMeasureForm.value.id, cancelMeasureForm.value.remark)
    ElMessage.success('措施已作废，原确认痕迹保留')
    cancelMeasureVisible.value = false
    await reload(active.value.id)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

// ============ 新增 / 编辑 措施 ============
function openAddMeasure() {
  measureForm.value = { ...emptyMeasureForm(), bayId: active.value.bayId }
  loadOpTickets(active.value.bayId)
  measureVisible.value = true
}
function openEditMeasure(m) {
  measureForm.value = {
    id: m.id, measureType: m.measureType, bayId: m.bayId, location: m.location,
    requiredAction: m.requiredAction, signboardText: m.signboardText,
    opTicketId: m.opTicketId, opStepNo: m.opStepNo,
    restoreTicketId: m.restoreTicketId, restoreStepNo: m.restoreStepNo
  }
  loadOpTickets(m.bayId)
  measureVisible.value = true
}
async function submitMeasure() {
  const f = measureForm.value
  if (!f.bayId || !f.location || !f.requiredAction) {
    ElMessage.warning('类型、间隔、位置、要求动作为必填')
    return
  }
  if (f.measureType === 'SIGNBOARD' && !f.signboardText) {
    ElMessage.warning('标示牌必须填写牌上文字')
    return
  }
  const payload = {
    measureType: f.measureType, bayId: f.bayId, location: f.location, requiredAction: f.requiredAction,
    signboardText: f.signboardText || null,
    opTicketId: f.opTicketId || null, opStepNo: f.opStepNo || null,
    restoreTicketId: f.restoreTicketId || null, restoreStepNo: f.restoreStepNo || null
  }
  try {
    if (f.id) {
      await updateMeasure(active.value.id, f.id, payload)
      ElMessage.success('措施已变更')
    } else {
      await addMeasure(active.value.id, payload)
      ElMessage.success('措施已新增（待执行）')
    }
    measureVisible.value = false
    await reload(active.value.id)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

// ============ 新建工作票 ============
async function submitCreate() {
  if (!newForm.value.bayId || !newForm.value.workLeader || !newForm.value.workContent) {
    ElMessage.warning('间隔、负责人、工作内容为必填')
    return
  }
  try {
    const res = await createWorkTicket({ ...newForm.value, issueDate: props.date })
    ElMessage.success(`开票成功，票号 ${res.data.ticketNo}（存储过程生成），请逐项编排安全措施`)
    createVisible.value = false
    newForm.value = emptyNewForm()
    await reload(res.data.id)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

watch(
  () => props.date,
  () => reload(),
  { immediate: true }
)

watch(
  () => props.substationId,
  async () => {
    const res = await bays(props.substationId || null, props.date)
    bayOptions.value = (res.data || []).filter((b) => b.status !== 'RUNNING')
  },
  { immediate: true }
)

watch(createVisible, async (v) => {
  if (v && !bayOptions.value.length) {
    const res = await bays(props.substationId || null, props.date)
    bayOptions.value = (res.data || []).filter((b) => b.status !== 'RUNNING')
  }
})
</script>

<style scoped>
.tk-list {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tk-item {
  border: 1px solid var(--ops-line);
  border-radius: 8px;
  padding: 8px 10px;
  cursor: pointer;
  background: #fff;
  transition: all 0.15s;
}
.tk-item:hover { border-color: var(--ops-orange); }
.tk-item.is-active { border-color: var(--ops-orange); background: var(--ops-orange-soft); }
.tk-item-top { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.tk-no { font-size: 12.5px; font-weight: 700; color: var(--ops-orange-dark); }
.tk-item-bay { font-size: 12px; color: var(--ops-grey); margin-top: 3px; }
.tk-ms-progress { font-size: 11px; color: var(--ops-grey); margin-top: 4px; }
.tk-steps { display: flex; gap: 5px; margin-top: 6px; }
.tk-steps i {
  width: 20px; height: 20px; border-radius: 50%; font-style: normal; font-size: 11px;
  display: flex; align-items: center; justify-content: center;
  border: 1px solid var(--ops-line); color: #9e9e9e; background: #fafafa;
}
.tk-steps i.on { background: var(--ops-orange); border-color: var(--ops-orange); color: #fff; font-weight: 700; }

.stat-row { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.stat-name { font-size: 12px; width: 56px; color: var(--ops-ink-2); }
.stat-count { width: 22px; text-align: right; font-size: 12.5px; color: var(--ops-orange-dark); }

.ms-pending { color: #616161; }
.ms-exec { color: #e65100; }
.ms-remove { color: #c62828; }
.ms-done { color: #2e7d32; }

/* ---------- 下一步横幅 ---------- */
.next-banner {
  border-radius: 8px; padding: 8px 12px; font-size: 12.5px; margin: 8px 0;
  border: 1px solid; line-height: 1.5;
}
.next-banner .next-label { font-weight: 800; }
.banner-info { background: #f5f5f5; border-color: #e0e0e0; color: #616161; }
.banner-block { background: #ffebee; border-color: #ef9a9a; color: #b71c1c; }
.banner-warn { background: #fff3e0; border-color: #ffcc80; color: #bf360c; }
.banner-ok { background: #e8f5e9; border-color: #a5d6a7; color: #1b5e20; }
.banner-done { background: #f1f8e9; border-color: #c5e1a5; color: #33691e; }
.banner-dead { background: #eceff1; border-color: #cfd8dc; color: #455a64; }

/* ---------- 票面 ---------- */
.paper {
  border: 1px solid #bcaaa4; border-radius: 4px; background: #fffdfb;
  padding: 14px 16px; margin-top: 8px;
}
.paper-head { display: flex; align-items: baseline; justify-content: space-between; border-bottom: 2px solid var(--ops-orange); padding-bottom: 6px; }
.paper-company { font-size: 12px; letter-spacing: 2px; font-weight: 700; }
.paper-type { font-size: 17px; font-weight: 800; letter-spacing: 4px; color: var(--ops-orange-dark); }
.paper-no { font-size: 11px; color: var(--ops-grey); }
.paper-table { width: 100%; border-collapse: collapse; margin-top: 8px; font-size: 12px; }
.paper-table td { border: 1px solid #d7ccc8; padding: 4px 7px; line-height: 1.5; vertical-align: top; }
.paper-table td.lb { width: 76px; background: var(--ops-orange-soft); color: var(--ops-ink-2); font-weight: 700; text-align: center; }
.paper-signs { display: flex; gap: 10px; margin-top: 10px; }
.sign-cell { flex: 1; border: 1px dashed #bcaaa4; border-radius: 6px; padding: 5px 8px; }
.sign-title { font-size: 11px; color: var(--ops-grey); }
.sign-name { font-size: 14px; font-weight: 800; color: #bcaaa4; margin-top: 3px; font-family: 'Kaiti SC', 'STKaiti', serif; }
.sign-name.signed { color: var(--ops-orange-dark); }
.sign-time { font-size: 10.5px; color: #9e9e9e; }

/* ---------- 闸道卡点 ---------- */
.gate-box { border-radius: 8px; padding: 8px 12px; margin-top: 10px; border: 1px solid; font-size: 12px; }
.gate-box.blocked { background: #ffebee; border-color: #ef9a9a; }
.gate-box.clear { background: #e8f5e9; border-color: #a5d6a7; }
.gate-head { font-weight: 800; display: flex; align-items: center; gap: 8px; color: var(--ops-ink); }
.gate-group { margin-top: 6px; }
.gate-group-name { font-weight: 700; color: #b71c1c; }
.gate-item { color: #b71c1c; line-height: 1.6; padding-left: 6px; }
.gate-ok { color: #1b5e20; margin-top: 4px; line-height: 1.5; }

/* ---------- 安措列表 ---------- */
.ms-head { font-weight: 800; margin: 14px 0 8px; font-size: 13.5px; display: flex; align-items: center; }
.ms-list { display: flex; flex-direction: column; gap: 8px; }
.ms-row {
  display: flex; gap: 10px; border: 1px solid var(--ops-line); border-left: 4px solid var(--ops-line);
  border-radius: 8px; padding: 8px 10px; background: #fff;
}
.ms-row.row-pending { border-left-color: #9e9e9e; }
.ms-row.row-exec { border-left-color: var(--ops-orange); background: #fff8f0; }
.ms-row.row-remove { border-left-color: #c62828; background: #fff5f5; }
.ms-row.row-done { border-left-color: #2e7d32; background: #f4faf4; }
.ms-row.row-canceled { border-left-color: #b0bec5; opacity: 0.7; background: #f7f9fa; }
.ms-seq {
  width: 26px; height: 26px; border-radius: 50%; flex: none;
  background: var(--ops-orange-soft); color: var(--ops-orange-dark);
  font-weight: 800; font-size: 12.5px; display: flex; align-items: center; justify-content: center;
}
.ms-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.ms-line1 { display: flex; align-items: center; gap: 8px; }
.ms-location { font-size: 13px; font-weight: 700; color: var(--ops-ink); }
.ms-line2 { font-size: 12px; color: var(--ops-ink-2); }
.ms-line3 { font-size: 11.5px; color: var(--ops-grey); display: flex; gap: 18px; flex-wrap: wrap; }
.link-on { color: #1565c0; }
.link-none { color: #9e9e9e; font-weight: 400; }
.ms-line4 { font-size: 11.5px; display: flex; gap: 16px; flex-wrap: wrap; }
.ms-who-install { color: var(--ops-orange-dark); font-weight: 700; }
.ms-who-remove { color: #2e7d32; font-weight: 700; }
.ms-who-none { color: #9e9e9e; }
.ms-change { flex-basis: 100%; color: #6a1b9a; }
.ms-actions { flex: none; display: flex; flex-direction: column; gap: 6px; justify-content: center; }

/* ---------- 按钮条 ---------- */
.sign-bar { margin-top: 14px; display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.sign-bar .btn-next { box-shadow: 0 0 0 3px rgba(230, 81, 0, 0.16); font-weight: 700; }

/* ---------- 确认弹窗 ---------- */
.confirm-desc { background: #faf5f2; border-radius: 6px; padding: 8px 10px; font-size: 13px; }

/* ---------- 痕迹 ---------- */
.log-list { display: flex; flex-direction: column; gap: 0; margin-top: 6px; }
.log-item { display: flex; gap: 8px; position: relative; padding-bottom: 12px; }
.log-item::before {
  content: ''; position: absolute; left: 4px; top: 12px; bottom: -2px;
  width: 2px; background: #e0e0e0;
}
.log-item:last-child::before { display: none; }
.log-dot { width: 9px; height: 9px; border-radius: 50%; margin-top: 4px; flex: none; z-index: 1; background: #90a4ae; }
.dot-ok { background: #2e7d32; }
.dot-warn { background: #e65100; }
.dot-normal { background: #90a4ae; }
.log-body { min-width: 0; }
.log-head { display: flex; gap: 6px; align-items: baseline; font-size: 12.5px; }
.log-person { color: var(--ops-grey); font-size: 11px; }
.log-detail { font-size: 11.5px; color: var(--ops-ink-2); line-height: 1.5; margin: 2px 0; }
.log-time { font-size: 10.5px; color: #b0bec5; }
</style>
