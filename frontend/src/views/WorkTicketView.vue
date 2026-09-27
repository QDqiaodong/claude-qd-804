<template>
  <div class="ops-page">
    <div class="ops-row">
      <!-- ============ 当日工作票列表 ============ -->
      <div class="ops-block" style="width: 330px">
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
            @click="selectTicket(t)"
          >
            <div class="tk-item-top">
              <span class="tk-no">{{ t.ticketNo }}</span>
              <el-tag size="small" :type="statusType(t.status)">{{ t.statusName }}</el-tag>
            </div>
            <div class="tk-item-bay">{{ t.bayName }} · {{ t.voltageLevel }}</div>
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

      <!-- ============ A4 票面预览 ============ -->
      <div class="ops-block ops-flex-1" style="min-width: 480px">
        <div class="ops-title">
          工作票票面（A4 比例）· 三审签字
          <span class="ops-sub">{{ active ? active.waitingText : '请选择一张票' }}</span>
        </div>

        <div v-if="!active" class="ops-empty">左侧选择一张工作票查看票面</div>
        <div v-else class="paper-wrap">
          <div class="paper">
            <div class="paper-head">
              <div class="paper-company">国 网 · 变 电 运 行 工 区</div>
              <div class="paper-type">第 一 种 工 作 票</div>
              <div class="paper-no">票号：{{ active.ticketNo }}</div>
            </div>

            <table class="paper-table">
              <tr>
                <td class="lb">工作负责人</td>
                <td>{{ active.workLeader }}</td>
                <td class="lb">电压等级</td>
                <td>{{ active.voltageLevel }}</td>
              </tr>
              <tr>
                <td class="lb">作业间隔</td>
                <td>{{ active.bayName }}（{{ active.bayStatusName }}）</td>
                <td class="lb">业务日期</td>
                <td>{{ active.issueDate }}</td>
              </tr>
              <tr>
                <td class="lb">工作班成员</td>
                <td colspan="3">{{ active.teamMembers || '—' }}</td>
              </tr>
              <tr class="tall">
                <td class="lb">工作内容</td>
                <td colspan="3">{{ active.workContent }}</td>
              </tr>
              <tr class="tall">
                <td class="lb">停电范围</td>
                <td colspan="3">{{ active.powerCutRange || '—' }}</td>
              </tr>
              <tr class="tall">
                <td class="lb">安全措施</td>
                <td colspan="3">{{ active.safetyMeasures || '—' }}</td>
              </tr>
              <tr>
                <td class="lb">计划开工</td>
                <td>{{ fmt(active.planStartTime) }}</td>
                <td class="lb">计划完工</td>
                <td>{{ fmt(active.planEndTime) }}</td>
              </tr>
            </table>

            <div class="paper-signs">
              <div class="sign-cell">
                <div class="sign-title">① 签发人签字</div>
                <div class="sign-name" :class="{ signed: active.issuer }">{{ active.issuer || '待签发人签字' }}</div>
                <div class="sign-time">{{ fmt(active.issueTime) }}</div>
              </div>
              <div class="sign-cell">
                <div class="sign-title">② 许可人签字</div>
                <div class="sign-name" :class="{ signed: active.permittor }">{{ active.permittor || '待许可人签字' }}</div>
                <div class="sign-time">{{ fmt(active.permitTime) }}</div>
              </div>
              <div class="sign-cell">
                <div class="sign-title">③ 终结人签字</div>
                <div class="sign-name" :class="{ signed: active.terminator }">{{ active.terminator || '待终结人签字' }}</div>
                <div class="sign-time">{{ fmt(active.terminateTime) }}</div>
              </div>
            </div>

            <div class="paper-foot">
              本票须经签发、许可、终结三人分别签字方可闭环，三人不得为同一人。
            </div>
          </div>
        </div>

        <!-- 三个签字按钮：依次点亮 -->
        <div v-if="active" class="sign-bar">
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
          <span class="ops-sub" style="margin-left: 8px">{{ active.waitingText }}</span>
        </div>
      </div>
    </div>

    <!-- ============ 签字：填人名 ============ -->
    <el-dialog v-model="signVisible" :title="signTitle" width="360">
      <el-input v-model="signPerson" placeholder="请输入签字人姓名" />
      <template #footer>
        <el-button @click="signVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmSign">确认签字</el-button>
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
        <el-form-item label="安全措施">
          <el-input v-model="newForm.safetyMeasures" type="textarea" :rows="2" />
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
import { bays, createWorkTicket, signWorkTicket, workTicket, workTicketStats, workTickets } from '../api'

const props = defineProps({
  date: { type: String, required: true },
  substationId: { type: [Number, String, null], default: null }
})

const list = ref([])
const stats = ref([])
const active = ref(null)
const bayOptions = ref([])

const signVisible = ref(false)
const signStage = ref('')
const signPerson = ref('')
const createVisible = ref(false)
const newForm = ref({
  bayId: null,
  issueDate: props.date,
  workLeader: '',
  teamMembers: '',
  workContent: '',
  powerCutRange: '',
  safetyMeasures: ''
})

const statsTotal = computed(() => stats.value.reduce((s, i) => s + Number(i.count), 0))

const signTitle = computed(() => {
  const map = { ISSUE: '签发人签字', PERMIT: '许可人签字', TERMINATE: '终结人签字' }
  return map[signStage.value] || '签字'
})

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
    next: active.value && active.value.nextStage === b.stage
  }))
})

function statusType(status) {
  if (status === 'TERMINATED') return 'success'
  if (status === 'CANCELED') return 'danger'
  if (status === 'DRAFT') return 'info'
  return 'warning'
}

function fmt(v) {
  return v ? String(v).replace('T', ' ') : '—'
}

function selectTicket(t) {
  active.value = t
}

async function reload(keepId) {
  const [res, statRes] = await Promise.all([workTickets(props.date), workTicketStats(props.date)])
  list.value = res.data || []
  stats.value = statRes.data || []
  const target = keepId ? list.value.find((i) => i.id === keepId) : list.value[0]
  if (target) {
    const d = await workTicket(target.id)
    active.value = d.data
  } else {
    active.value = null
  }
}

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
    reload(active.value.id)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function submitCreate() {
  if (!newForm.value.bayId || !newForm.value.workLeader || !newForm.value.workContent) {
    ElMessage.warning('间隔、负责人、工作内容为必填')
    return
  }
  try {
    const res = await createWorkTicket({ ...newForm.value, issueDate: props.date })
    ElMessage.success(`开票成功，票号 ${res.data.ticketNo}（存储过程生成）`)
    createVisible.value = false
    reload()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

watch(
  () => props.date,
  () => {
    bayOptions.value = []
    reload()
  },
  { immediate: true }
)

watch(
  () => props.substationId,
  async (v) => {
    const res = await bays(v || null, props.date)
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

.tk-item:hover {
  border-color: var(--ops-orange);
}

.tk-item.is-active {
  border-color: var(--ops-orange);
  background: var(--ops-orange-soft);
}

.tk-item-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.tk-no {
  font-size: 12.5px;
  font-weight: 700;
  color: var(--ops-orange-dark);
}

.tk-item-bay {
  font-size: 12px;
  color: var(--ops-grey);
  margin-top: 3px;
}

.tk-steps {
  display: flex;
  gap: 5px;
  margin-top: 6px;
}

.tk-steps i {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  font-style: normal;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--ops-line);
  color: #9e9e9e;
  background: #fafafa;
}

.tk-steps i.on {
  background: var(--ops-orange);
  border-color: var(--ops-orange);
  color: #fff;
  font-weight: 700;
}

.stat-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.stat-name {
  font-size: 12px;
  width: 56px;
  color: var(--ops-ink-2);
}

.stat-count {
  width: 22px;
  text-align: right;
  font-size: 12.5px;
  color: var(--ops-orange-dark);
}

/* ---------- A4 纸比例票面 ---------- */
.paper-wrap {
  display: flex;
  justify-content: center;
}

.paper {
  width: 100%;
  max-width: 560px;
  aspect-ratio: 1 / 1.414;
  border: 1px solid #bcaaa4;
  border-radius: 3px;
  background: repeating-linear-gradient(#fffdfb 0 27px, #f7f1ec 27px 28px);
  box-shadow: 0 6px 18px rgba(94, 66, 55, 0.16);
  padding: 22px 24px;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
}

.paper::after {
  content: '';
  position: absolute;
  inset: 8px;
  border: 1px solid rgba(230, 81, 0, 0.18);
  pointer-events: none;
}

.paper-head {
  text-align: center;
  border-bottom: 2px solid var(--ops-orange);
  padding-bottom: 6px;
}

.paper-company {
  font-size: 13px;
  letter-spacing: 3px;
  color: var(--ops-ink);
  font-weight: 700;
}

.paper-type {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: 6px;
  color: var(--ops-orange-dark);
  margin: 4px 0;
}

.paper-no {
  font-size: 11.5px;
  color: var(--ops-grey);
  font-variant-numeric: tabular-nums;
}

.paper-table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 10px;
  font-size: 12px;
}

.paper-table td {
  border: 1px solid #d7ccc8;
  padding: 5px 7px;
  color: var(--ops-ink);
  vertical-align: top;
  line-height: 1.5;
}

.paper-table tr.tall td {
  height: 58px;
}

.paper-table td.lb {
  width: 82px;
  background: var(--ops-orange-soft);
  color: var(--ops-ink-2);
  font-weight: 700;
  text-align: center;
}

.paper-signs {
  margin-top: auto;
  display: flex;
  gap: 10px;
  padding-top: 10px;
}

.sign-cell {
  flex: 1;
  border: 1px dashed #bcaaa4;
  border-radius: 6px;
  padding: 6px 8px;
  min-height: 62px;
}

.sign-title {
  font-size: 11px;
  color: var(--ops-grey);
}

.sign-name {
  font-size: 15px;
  font-weight: 800;
  color: #bcaaa4;
  margin-top: 6px;
  font-family: 'Kaiti SC', 'STKaiti', serif;
}

.sign-name.signed {
  color: var(--ops-orange-dark);
}

.sign-time {
  font-size: 10.5px;
  color: #9e9e9e;
}

.paper-foot {
  margin-top: 8px;
  font-size: 10.5px;
  color: #9e9e9e;
  text-align: center;
}

/* ---------- 签字按钮条 ---------- */
.sign-bar {
  margin-top: 14px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.sign-bar .btn-next {
  box-shadow: 0 0 0 3px rgba(230, 81, 0, 0.16);
  font-weight: 700;
}
</style>
