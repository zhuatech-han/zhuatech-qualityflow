<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted } from "vue";
import { api, resetCsrf } from "./api.js";
import {
  states,
  commands,
  labels,
  adminFields,
  commandFields,
  errors,
  date,
  canComplete,
  severityNames,
  scopeNames,
  kindNames,
  eventName,
} from "./schema.js";
const lang = ref(localStorage.getItem("qualityflow-language") || "zh"),
  me = ref(null),
  view = ref("workbench"),
  loading = ref(false),
  error = ref(""),
  notice = ref(""),
  loginForm = ref({ username: "", password: "" });
const options = ref({
    people: [],
    departments: [],
    dictionaries: [],
    settings: [],
  }),
  directory = ref({ roles: [], departments: [], permissions: [] }),
  rows = ref([]),
  results = ref({ items: [], total: 0 }),
  work = ref({ cases: [], actions: [] }),
  stats = ref({ status: {}, severity: {}, source: {} }),
  detail = ref(null),
  modal = ref(null),
  form = ref({}),
  search = ref(""),
  status = ref(""),
  sort = ref("newest"),
  page = ref(0),
  history = ref(false);
const t = (zh, en) => (lang.value === "zh" ? zh : en),
  pair = (v) => v?.[lang.value === "zh" ? 0 : 1] || "—",
  label = (k) => pair(labels[k] || [k, k]),
  has = (p) => me.value?.permissions.includes(p),
  person = (id) =>
    options.value.people.find((p) => p.id === id)?.displayName || `#${id}`,
  department = (id) =>
    options.value.departments.find((d) => d.id === id)?.name || `#${id}`;
const record = computed(() => detail.value?.case),
  zone = computed(
    () =>
      options.value.settings.find((s) => s.code === "timezone")?.value ||
      "Asia/Shanghai",
  ),
  company = computed(
    () =>
      options.value.settings.find((s) => s.code === "companyName")?.value ||
      t("知华质量整改协同", "ZhuaTech QualityFlow"),
  ),
  title = computed(() => {
    const m = me.value?.menus.find((m) => m.code === view.value);
    return m ? (lang.value === "zh" ? m.name : m.nameEn) : company.value;
  }),
  fields = computed(() => modal.value?.fields || []),
  isAdmin = computed(() => !!adminFields[view.value]);
/** 忙碌期间禁用重复提交；统一显示接口错误。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function run(fn) {
  if (loading.value) return;
  loading.value = true;
  error.value = "";
  notice.value = "";
  try {
    await fn();
  } catch (e) {
    error.value = pair(errors[e.message] || [e.message, e.message]);
    if (e.message === "UNAUTHENTICATED") {
      me.value = null;
      detail.value = null;
      modal.value = null;
    }
  } finally {
    loading.value = false;
  }
}
/** 加载实时业务字典、参数和授权人员目录。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function loadOptions() {
  options.value = await api("/options");
  if (has("admin"))
    for (const k of ["roles", "departments", "permissions"])
      directory.value[k] = await api("/admin/" + k);
}
/** 每个页面从受保护接口读取持久化记录。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function loadView() {
  if (view.value === "cases")
    results.value = await api(
      `/cases?search=${encodeURIComponent(search.value)}&status=${status.value}&page=${page.value}&size=10&sort=${sort.value}`,
    );
  else if (view.value === "workbench") work.value = await api("/workbench");
  else if (view.value === "dashboard") stats.value = await api("/dashboard");
  else if (view.value === "audit") rows.value = await api("/audit");
  else if (isAdmin.value) rows.value = await api("/admin/" + view.value);
}
async function navigate(code) {
  await run(async () => {
    view.value = code;
    detail.value = null;
    page.value = 0;
    await loadView();
  });
}
async function signIn() {
  await run(async () => {
    me.value = await api("/auth/login", "POST", loginForm.value);
    loginForm.value.password = "";
    resetCsrf();
    await loadOptions();
    view.value = me.value.menus[0]?.code || "about";
    await loadView();
  });
}
async function logout() {
  await run(async () => {
    await api("/auth/logout", "POST");
    me.value = null;
    detail.value = null;
    resetCsrf();
  });
}
async function openCase(id) {
  await run(async () => {
    detail.value = await api("/cases/" + id);
    history.value = false;
  });
}
function language() {
  lang.value = lang.value === "zh" ? "en" : "zh";
  localStorage.setItem("qualityflow-language", lang.value);
  document.documentElement.lang = lang.value;
}
function today() {
  const a = new Intl.DateTimeFormat("en-CA", {
    timeZone: zone.value,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date());
  return ["year", "month", "day"]
    .map((k) => a.find((x) => x.type === k).value)
    .join("-");
}
/** 构造有限字段表单；命令保留固定幂等键便于安全重试。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
function openModal(kind, action = "", row = null) {
  error.value = "";
  const m = { kind, action, row, fields: [], title: "" };
  if (kind === "draft") {
    m.fields = [
      "title",
      "description",
      "source",
      "category",
      "severity",
      "reference",
      "departmentId",
      "ownerId",
      "reviewerId",
      "dueDate",
    ];
    m.title = t(
      row ? "修改草稿" : "报告质量问题",
      row ? "Edit draft" : "Report issue",
    );
    form.value = row
      ? { ...row }
      : {
          source: options.value.dictionaries.find((x) => x.type === "source")
            ?.code,
          category: options.value.dictionaries.find(
            (x) => x.type === "category",
          )?.code,
          severity: "MAJOR",
          departmentId: me.value.departmentId,
          dueDate: today(),
        };
  } else if (kind === "command") {
    m.fields = commandFields[action];
    m.title = pair(commands[action]);
    form.value = {
      version: record.value.version,
      requestKey: crypto.randomUUID(),
      rootCause: record.value.rootCause,
      verificationPlan: record.value.verificationPlan,
      verifyAfter: record.value.verifyAfter,
      containment: record.value.containment,
      dueDate: record.value.dueDate,
    };
  } else if (kind === "action") {
    m.fields = ["kind", "description", "ownerId", "dueDate"];
    m.title = t(
      row ? "修改措施" : "编制措施",
      row ? "Edit action" : "Add action",
    );
    form.value = row
      ? { ...row, version: record.value.version }
      : {
          version: record.value.version,
          kind: "CORRECTIVE",
          ownerId: record.value.ownerId,
          dueDate: record.value.dueDate,
        };
  } else if (kind === "complete") {
    m.fields = ["evidence"];
    m.title = t("完成措施", "Complete action");
    form.value = {
      version: record.value.version,
      requestKey: crypto.randomUUID(),
    };
  } else if (kind === "admin") {
    m.fields = adminFields[view.value];
    m.title = t(
      row ? "修改记录" : "新建记录",
      row ? "Edit record" : "New record",
    );
    form.value = row
      ? { ...row, password: "" }
      : { enabled: true, scope: "DEPARTMENT", permissions: [] };
  } else if (kind === "password") {
    m.fields = ["oldPassword", "newPassword"];
    m.title = t("修改密码", "Change password");
    form.value = {};
  } else if (kind === "delete") {
    m.title = t("确认删除", "Confirm deletion");
    form.value = {};
  }
  modal.value = m;
}
/** 用响应更新版本，保持失败表单不丢失。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function save() {
  await run(async () => {
    const m = modal.value;
    if (m.kind === "draft")
      detail.value = await api(
        "/cases" + (m.row ? "/" + m.row.id : ""),
        m.row ? "PUT" : "POST",
        form.value,
      );
    else if (m.kind === "command")
      detail.value = await api(
        `/cases/${record.value.id}/${m.action}`,
        "POST",
        form.value,
      );
    else if (m.kind === "action")
      detail.value = await api(
        `/cases/${record.value.id}/actions` + (m.row ? "/" + m.row.id : ""),
        m.row ? "PUT" : "POST",
        form.value,
      );
    else if (m.kind === "complete")
      detail.value = await api(
        `/cases/${record.value.id}/actions/${m.row.id}/complete`,
        "POST",
        form.value,
      );
    else if (m.kind === "admin") {
      await api(
        "/admin/" + view.value + (m.row ? "/" + m.row.id : ""),
        m.row ? "PUT" : "POST",
        form.value,
      );
      await loadOptions();
      await loadView();
      me.value = await api("/auth/me");
    } else if (m.kind === "password") {
      await api("/auth/password", "POST", form.value);
      me.value = null;
      detail.value = null;
      resetCsrf();
    } else if (m.kind === "delete") {
      if (m.action === "draft") {
        await api(
          `/cases/${record.value.id}?version=${record.value.version}`,
          "DELETE",
        );
        detail.value = null;
        await loadView();
      } else if (m.action === "action")
        detail.value = await api(
          `/cases/${record.value.id}/actions/${m.row.id}?version=${record.value.version}`,
          "DELETE",
        );
      else {
        await api(`/admin/${view.value}/${m.row.id}`, "DELETE");
        await loadOptions();
        await loadView();
      }
    }
    modal.value = null;
    notice.value = t("已保存", "Saved");
  });
}
function choices(k) {
  if (k === "departmentId")
    return options.value.departments.map((d) => ({
      value: d.id,
      name: d.name,
    }));
  if (k === "roleId")
    return directory.value.roles.map((r) => ({ value: r.id, name: r.name }));
  if (k === "permissionCode")
    return directory.value.permissions.map((p) => ({
      value: p.code,
      name: p.name,
    }));
  if (["source", "category"].includes(k))
    return options.value.dictionaries
      .filter((d) => d.type === k)
      .map((d) => ({
        value: d.code,
        name: lang.value === "zh" ? d.name : d.nameEn,
      }));
  if (k === "ownerId" || k === "reviewerId") {
    const perm =
        k === "reviewerId"
          ? "case.review"
          : modal.value?.kind === "action"
            ? "action.write"
            : "case.work",
      dept =
        modal.value?.kind === "draft"
          ? form.value.departmentId
          : record.value?.departmentId;
    return options.value.people
      .filter((p) => p.departmentId === dept && p.permissions.includes(perm))
      .map((p) => ({ value: p.id, name: p.displayName }));
  }
  const values = {
    severity: severityNames,
    scope: scopeNames,
    kind: kindNames,
  }[k];
  return values
    ? Object.entries(values).map(([value, v]) => ({ value, name: pair(v) }))
    : [];
}
const selectFields = [
    "departmentId",
    "roleId",
    "permissionCode",
    "source",
    "category",
    "ownerId",
    "reviewerId",
    "severity",
    "scope",
    "kind",
  ],
  textAreas = [
    "description",
    "containment",
    "rootCause",
    "verificationPlan",
    "note",
    "evidence",
  ];
const columns = computed(
  () =>
    ({
      users: ["username", "displayName", "roleId", "departmentId", "enabled"],
      roles: ["name", "scope", "permissions"],
      departments: ["name"],
      menus: ["code", "name", "permissionCode", "position", "enabled"],
      permissions: ["code", "name"],
      dictionaries: ["type", "code", "name"],
      settings: ["code", "value"],
    })[view.value] || [],
);
function dictionaryName(type, code) {
  const d = options.value.dictionaries.find(
    (x) => x.type === type && x.code === code,
  );
  return d ? (lang.value === "zh" ? d.name : d.nameEn) : code;
}
function cell(r, k) {
  if (k === "departmentId") return department(r[k]);
  if (k === "roleId")
    return directory.value.roles.find((x) => x.id === r[k])?.name || `#${r[k]}`;
  if (k === "enabled")
    return r[k] ? t("启用", "Active") : t("停用", "Disabled");
  if (k === "permissions") return r[k]?.length || 0;
  if (k === "scope") return pair(scopeNames[r[k]]);
  return r[k] ?? "—";
}
onMounted(() =>
  run(async () => {
    try {
      me.value = await api("/auth/me");
    } catch (e) {
      if (e.message !== "UNAUTHENTICATED") throw e;
      return;
    }
    await loadOptions();
    view.value = me.value.menus[0]?.code || "about";
    await loadView();
  }),
);
</script>
<template>
  <div v-if="!me" class="login-page">
    <header class="login-brand">
      <img src="/brand/logo.jpg" alt="知华科技 LOGO" /><span
        >知华科技 <b>QualityFlow</b></span
      ><button class="plain" @click="language">
        {{ lang === "zh" ? "English" : "中文" }}
      </button>
    </header>
    <main class="login-card">
      <div class="eyebrow">QUALITY OPERATIONS</div>
      <h1>{{ t("质量整改协同", "Quality corrective action") }}</h1>
      <p class="muted">{{ t("登录工作台", "Sign in to your workspace") }}</p>
      <form @submit.prevent="signIn">
        <label
          >{{ t("登录账号", "Username")
          }}<input
            v-model="loginForm.username"
            name="username"
            autocomplete="username"
            required
            maxlength="60" /></label
        ><label
          >{{ t("密码", "Password")
          }}<input
            v-model="loginForm.password"
            name="password"
            type="password"
            autocomplete="current-password"
            required
            maxlength="72"
        /></label>
        <p v-if="error" role="alert" class="error">{{ error }}</p>
        <button class="primary full" :disabled="loading">
          {{ loading ? t("登录中…", "Signing in…") : t("登录", "Sign in") }}
        </button>
      </form>
      <p class="license">
        {{
          t(
            "0.1.0 · 公开源码学习版 · 未经书面授权不得商用",
            "0.1.0 · Non-commercial source learning edition",
          )
        }}
      </p>
    </main>
    <footer>
      上海如静知华信息科技有限公司<br /><a
        href="https://www.zhuatech.cn/"
        target="_blank"
        rel="noopener"
        >www.zhuatech.cn</a
      >
      · {{ t("商业咨询微信", "Consultation WeChat") }} zhuatech / zhuatech2
    </footer>
  </div>
  <div v-else class="app-shell">
    <aside>
      <div class="brand">
        <img src="/brand/logo.jpg" alt="知华科技 LOGO" />
        <div>知华科技<strong>QualityFlow</strong></div>
      </div>
      <nav aria-label="业务导航">
        <button
          v-for="m in me.menus"
          :key="m.code"
          :class="{ active: view === m.code }"
          :disabled="loading"
          @click="navigate(m.code)"
        >
          {{ lang === "zh" ? m.name : m.nameEn }}
        </button>
      </nav>
      <div class="aside-footer">
        <span>{{ t("公开源码学习版", "Learning edition") }}</span
        ><a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >{{ t("联系知华科技", "Contact ZhuaTech") }} ↗</a
        >
      </div>
    </aside>
    <div class="workspace">
      <header class="topbar">
        <div class="company">{{ company }}</div>
        <div class="top-actions">
          <span>{{ me.displayName }}</span
          ><button class="plain" @click="language">
            {{ lang === "zh" ? "English" : "中文" }}</button
          ><button
            class="plain"
            @click="
              view = 'about';
              detail = null;
            "
          >
            {{ t("关于", "About") }}</button
          ><button class="plain" @click="openModal('password')">
            {{ t("修改密码", "Password") }}</button
          ><button class="plain" :disabled="loading" @click="logout">
            {{ t("退出", "Sign out") }}
          </button>
        </div>
      </header>
      <main>
        <div class="page-heading">
          <div>
            <div class="eyebrow">
              {{ detail ? record.number : "QUALITYFLOW" }}
            </div>
            <h1>{{ detail ? record.title : title }}</h1>
          </div>
          <button
            v-if="detail"
            :disabled="loading"
            @click="
              run(async () => {
                detail = null;
                await loadView();
              })
            "
          >
            ← {{ t("返回列表", "Back") }}</button
          ><button
            v-else-if="view !== 'about'"
            :disabled="loading"
            @click="run(loadView)"
          >
            {{ t("刷新", "Refresh") }}
          </button>
        </div>
        <div v-if="error" role="alert" class="error">{{ error }}</div>
        <div v-if="notice" role="status" class="success">{{ notice }}</div>
        <div v-if="loading" class="busy" role="status">
          {{ t("处理中…", "Working…") }}
        </div>
        <template v-if="detail"
          ><section class="detail-summary">
            <span :class="['status', record.status]">{{
              pair(states[record.status])
            }}</span
            ><span>{{ t("轮次", "Cycle") }} {{ record.cycle }}</span
            ><span>{{ department(record.departmentId) }}</span
            ><span>{{ t("截止", "Due") }} {{ record.dueDate }}</span
            ><span>{{ pair(severityNames[record.severity]) }}</span>
          </section>
          <ol class="workflow">
            <li
              v-for="s in [
                'TRIAGE',
                'INVESTIGATION',
                'PLAN_REVIEW',
                'EXECUTION',
                'VERIFY_READY',
                'CLOSED',
              ]"
              :key="s"
              :class="{ current: record.status === s }"
            >
              {{ pair(states[s]) }}
            </li>
          </ol>
          <div class="detail-grid">
            <section class="panel">
              <h2>{{ t("问题与责任", "Issue and responsibility") }}</h2>
              <dl>
                <dt>{{ label("description") }}</dt>
                <dd class="preserve">{{ record.description }}</dd>
                <dt>{{ label("source") }}</dt>
                <dd>{{ dictionaryName("source", record.source) }}</dd>
                <dt>{{ label("category") }}</dt>
                <dd>{{ dictionaryName("category", record.category) }}</dd>
                <dt>{{ label("reference") }}</dt>
                <dd>{{ record.reference || "—" }}</dd>
                <dt>{{ t("报告人", "Reporter") }}</dt>
                <dd>{{ person(record.reporterId) }}</dd>
                <dt>{{ label("ownerId") }}</dt>
                <dd>{{ person(record.ownerId) }}</dd>
                <dt>{{ label("reviewerId") }}</dt>
                <dd>{{ person(record.reviewerId) }}</dd>
                <dt>{{ label("verifyAfter") }}</dt>
                <dd>{{ record.verifyAfter || "—" }}</dd>
              </dl>
            </section>
            <section class="panel">
              <h2>{{ t("原因与验证依据", "Analysis and verification") }}</h2>
              <dl
                v-for="k in [
                  'containment',
                  'rootCause',
                  'verificationPlan',
                  'verificationEvidence',
                ]"
                :key="k"
              >
                <dt>{{ label(k) }}</dt>
                <dd class="preserve">{{ record[k] || "—" }}</dd>
              </dl>
            </section>
          </div>
          <section class="panel">
            <div class="section-heading">
              <h2>
                {{ t("整改与预防措施", "Corrective and preventive actions") }}
              </h2>
              <button
                v-if="detail.commands.includes('analysis')"
                @click="openModal('action')"
              >
                + {{ t("编制措施", "Add action") }}
              </button>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("轮次", "Cycle") }}</th>
                    <th>{{ t("措施", "Action") }}</th>
                    <th>{{ t("责任人", "Owner") }}</th>
                    <th>{{ label("dueDate") }}</th>
                    <th>{{ t("状态与证据", "Status and evidence") }}</th>
                    <th>{{ t("操作", "Operations") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="a in detail.actions" :key="a.id">
                    <td class="cycle-cell">{{ a.cycle }}</td>
                    <td>
                      <small>{{ pair(kindNames[a.kind]) }}</small
                      ><br />{{ a.description }}
                    </td>
                    <td>{{ person(a.ownerId) }}</td>
                    <td class="date-cell">{{ a.dueDate }}</td>
                    <td>
                      <span class="status">{{
                        a.status === "COMPLETED"
                          ? t("已完成", "Completed")
                          : t("待执行", "Pending")
                      }}</span>
                      <p v-if="a.evidence" class="preserve evidence">
                        {{ a.evidence }}
                      </p>
                    </td>
                    <td>
                      <button
                        v-if="canComplete(a, record, me)"
                        class="small primary"
                        @click="openModal('complete', '', a)"
                      >
                        {{ t("完成措施", "Complete") }}</button
                      ><template
                        v-if="
                          a.cycle === record.cycle &&
                          detail.commands.includes('analysis')
                        "
                        ><button
                          class="small"
                          @click="openModal('action', '', a)"
                        >
                          {{ t("编辑", "Edit") }}</button
                        ><button
                          class="small danger"
                          @click="openModal('delete', 'action', a)"
                        >
                          {{ t("删除", "Delete") }}
                        </button></template
                      >
                    </td>
                  </tr>
                  <tr v-if="!detail.actions.length">
                    <td colspan="6" class="empty">
                      {{ t("尚未编制措施", "No actions yet") }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
          <section class="action-bar">
            <button
              v-for="c in detail.commands"
              :key="c"
              :disabled="loading"
              :class="
                ['submit-plan', 'submit', 'verify', 'approve-plan'].includes(c)
                  ? 'primary'
                  : ''
              "
              @click="openModal('command', c)"
            >
              {{ pair(commands[c]) }}</button
            ><template
              v-if="
                record.status === 'DRAFT' &&
                record.reporterId === me.id &&
                has('case.write')
              "
              ><button @click="openModal('draft', '', record)">
                {{ t("编辑草稿", "Edit draft") }}</button
              ><button class="danger" @click="openModal('delete', 'draft')">
                {{ t("删除草稿", "Delete draft") }}
              </button></template
            ><a
              v-if="has('export')"
              class="button"
              :href="`/api/cases/${record.id}/report.json`"
              >{{ t("导出报告", "Export report") }}</a
            ><button :disabled="loading" @click="openCase(record.id)">
              {{ t("刷新详情", "Refresh detail") }}
            </button>
          </section>
          <section class="panel">
            <div class="section-heading">
              <h2>{{ t("整改记录", "Case history") }}</h2>
              <button class="plain" @click="history = !history">
                {{
                  history ? t("收起记录", "Collapse") : t("展开记录", "Expand")
                }}
              </button>
            </div>
            <div v-if="history" class="timeline">
              <article v-for="e in detail.events" :key="e.id">
                <div>
                  <strong>{{ eventName(e.action, lang) }}</strong
                  ><span>
                    · {{ e.actor }} · {{ date(e.createdAt, zone) }} ·
                    {{ t("轮次", "Cycle") }} {{ e.cycle }}</span
                  >
                </div>
                <small
                  >{{ pair(states[e.fromStatus]) }} →
                  {{ pair(states[e.toStatus]) }}</small
                >
                <p class="preserve">{{ e.note }}</p>
              </article>
            </div>
            <p v-else class="muted">
              {{ detail.events.length }} {{ t("条记录", "entries") }}
            </p>
          </section></template
        >
        <template v-else-if="view === 'workbench'"
          ><div class="section-heading">
            <p class="muted">
              {{
                t(
                  "按当前账号的责任归属显示待处理事项",
                  "Pending work assigned to your account",
                )
              }}
            </p>
            <button
              v-if="has('case.write')"
              class="primary"
              @click="openModal('draft')"
            >
              + {{ t("报告质量问题", "Report issue") }}
            </button>
          </div>
          <section class="panel">
            <h2>
              {{ t("待处理问题", "Pending cases") }}
              <span class="count">{{ work.cases.length }}</span>
            </h2>
            <div v-for="c in work.cases" :key="c.id" class="work-row">
              <div>
                <small>{{ c.number }}</small>
                <h3>{{ c.title }}</h3>
                <span class="muted"
                  >{{ t("截止", "Due") }} {{ c.dueDate }}</span
                >
              </div>
              <span class="status">{{ pair(states[c.status]) }}</span
              ><button @click="openCase(c.id)">{{ t("处理", "Open") }}</button>
            </div>
            <p v-if="!work.cases.length" class="empty">
              {{ t("当前没有待处理问题", "No pending cases") }}
            </p>
          </section>
          <section class="panel">
            <h2>
              {{ t("我的执行措施", "My action assignments") }}
              <span class="count">{{ work.actions.length }}</span>
            </h2>
            <div v-for="a in work.actions" :key="a.id" class="work-row">
              <div>
                <h3>{{ a.description }}</h3>
                <span class="muted"
                  >{{ t("截止", "Due") }} {{ a.dueDate }}</span
                >
              </div>
              <button @click="openCase(a.caseId)">
                {{ t("查看并提交证据", "Open and complete") }}
              </button>
            </div>
            <p v-if="!work.actions.length" class="empty">
              {{ t("当前没有待执行措施", "No pending actions") }}
            </p>
          </section></template
        >
        <template v-else-if="view === 'cases'"
          ><form
            class="filters"
            @submit.prevent="
              run(async () => {
                page = 0;
                await loadView();
              })
            "
          >
            <label class="search-label"
              >{{ t("搜索", "Search")
              }}<input
                v-model="search"
                :placeholder="t('问题标题或编号', 'Title or number')"
                maxlength="200" /></label
            ><label
              >{{ t("状态", "Status")
              }}<select v-model="status">
                <option value="">{{ t("全部状态", "All statuses") }}</option>
                <option v-for="(v, k) in states" :key="k" :value="k">
                  {{ pair(v) }}
                </option>
              </select></label
            ><label
              >{{ t("排序", "Sort")
              }}<select v-model="sort">
                <option value="newest">{{ t("最新报告", "Newest") }}</option>
                <option value="due">{{ t("截止日", "Due date") }}</option>
                <option value="severity">{{ t("严重度", "Severity") }}</option>
              </select></label
            ><button :disabled="loading">{{ t("查询", "Search") }}</button
            ><button
              v-if="has('case.write')"
              type="button"
              class="primary"
              @click="openModal('draft')"
            >
              + {{ t("报告质量问题", "Report issue") }}
            </button>
          </form>
          <section class="panel">
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("编号与问题", "Number and issue") }}</th>
                    <th>{{ t("严重度", "Severity") }}</th>
                    <th>{{ t("状态", "Status") }}</th>
                    <th>{{ t("责任人", "Owner") }}</th>
                    <th>{{ t("截止日", "Due date") }}</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="c in results.items" :key="c.id">
                    <td>
                      <small>{{ c.number }}</small
                      ><br /><strong>{{ c.title }}</strong>
                    </td>
                    <td>{{ pair(severityNames[c.severity]) }}</td>
                    <td>
                      <span :class="['status', c.status]">{{
                        pair(states[c.status])
                      }}</span>
                    </td>
                    <td>{{ person(c.ownerId) }}</td>
                    <td class="date-cell">{{ c.dueDate }}</td>
                    <td>
                      <button class="small" @click="openCase(c.id)">
                        {{ t("详情", "Detail") }}
                      </button>
                    </td>
                  </tr>
                  <tr v-if="!results.items.length">
                    <td colspan="6" class="empty">
                      {{ t("没有符合条件的问题", "No matching cases") }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="pagination">
              <span
                >{{ t("共", "Total") }} {{ results.total }}
                {{ t("条", "records") }} · {{ page + 1 }}</span
              ><button
                :disabled="loading || page === 0"
                @click="
                  run(async () => {
                    page--;
                    await loadView();
                  })
                "
              >
                {{ t("上一页", "Previous") }}</button
              ><button
                :disabled="loading || (page + 1) * 10 >= results.total"
                @click="
                  run(async () => {
                    page++;
                    await loadView();
                  })
                "
              >
                {{ t("下一页", "Next") }}
              </button>
            </div>
          </section></template
        >
        <template v-else-if="view === 'dashboard'"
          ><p class="muted">
            {{ t("统计范围", "Scope") }}：{{ pair(scopeNames[me.scope]) }}
          </p>
          <div class="metrics">
            <section>
              <small>{{ t("问题总数", "Total cases") }}</small
              ><strong>{{ stats.total }}</strong>
            </section>
            <section>
              <small>{{ t("未关闭且逾期", "Overdue open cases") }}</small
              ><strong>{{ stats.overdue }}</strong>
            </section>
            <section>
              <small>{{ t("进入多轮整改", "Multiple cycles") }}</small
              ><strong>{{ stats.reopened }}</strong>
            </section>
            <section>
              <small>{{ t("已关闭", "Closed") }}</small
              ><strong>{{ stats.status.CLOSED || 0 }}</strong>
            </section>
          </div>
          <section class="panel">
            <h2>{{ t("状态分布", "Status distribution") }}</h2>
            <div v-for="(v, k) in states" :key="k" class="distribution">
              <span>{{ pair(v) }}</span>
              <div class="bar-track">
                <div
                  :style="{
                    width:
                      (stats.total
                        ? ((stats.status[k] || 0) / stats.total) * 100
                        : 0) + '%',
                  }"
                ></div>
              </div>
              <strong>{{ stats.status[k] || 0 }}</strong>
            </div>
          </section>
          <div class="detail-grid">
            <section class="panel">
              <h2>{{ t("严重度", "Severity") }}</h2>
              <div v-for="(v, k) in severityNames" :key="k" class="stat-row">
                <span>{{ pair(v) }}</span
                ><strong>{{ stats.severity[k] || 0 }}</strong>
              </div>
            </section>
            <section class="panel">
              <h2>{{ t("问题来源", "Sources") }}</h2>
              <div
                v-for="d in options.dictionaries.filter(
                  (x) => x.type === 'source',
                )"
                :key="d.code"
                class="stat-row"
              >
                <span>{{ lang === "zh" ? d.name : d.nameEn }}</span
                ><strong>{{ stats.source[d.code] || 0 }}</strong>
              </div>
            </section>
          </div></template
        >
        <template v-else-if="isAdmin"
          ><div class="section-heading">
            <p class="muted">
              {{
                view === "roles"
                  ? t(
                      "数据范围与功能权限分别控制",
                      "Data scope and feature permissions are separate",
                    )
                  : t(
                      "管理系统目录与启用状态",
                      "Manage system records and active status",
                    )
              }}
            </p>
            <button
              v-if="!['menus', 'permissions', 'settings'].includes(view)"
              class="primary"
              @click="openModal('admin')"
            >
              + {{ t("新建记录", "New record") }}
            </button>
          </div>
          <section class="panel">
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th v-for="c in columns" :key="c">{{ label(c) }}</th>
                    <th>{{ t("操作", "Operations") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="r in rows" :key="r.id">
                    <td v-for="c in columns" :key="c">{{ cell(r, c) }}</td>
                    <td class="operations">
                      <button class="small" @click="openModal('admin', '', r)">
                        {{ t("编辑", "Edit") }}</button
                      ><button
                        v-if="
                          !['menus', 'permissions', 'settings'].includes(
                            view,
                          ) && !(view === 'departments' && r.id === 1)
                        "
                        class="small danger"
                        @click="openModal('delete', 'admin', r)"
                      >
                        {{ t("删除", "Delete") }}
                      </button>
                    </td>
                  </tr>
                  <tr v-if="!rows.length">
                    <td :colspan="columns.length + 1" class="empty">
                      {{ t("暂无记录", "No records") }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section></template
        >
        <template v-else-if="view === 'audit'"
          ><section class="panel">
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("时间", "Time") }}</th>
                    <th>{{ t("操作者", "Actor") }}</th>
                    <th>{{ t("操作", "Action") }}</th>
                    <th>{{ t("记录", "Record") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="r in rows" :key="r.id">
                    <td>{{ date(r.createdAt, zone) }}</td>
                    <td>{{ r.actor }}</td>
                    <td>{{ eventName(r.action, lang) }}</td>
                    <td>{{ r.objectId }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section></template
        >
        <template v-else-if="view === 'about'"
          ><section class="panel about">
            <img src="/brand/logo.jpg" alt="知华科技 LOGO" />
            <h2>{{ t("知华科技质量整改协同", "ZhuaTech QualityFlow") }}</h2>
            <p>上海如静知华信息科技有限公司 · 0.1.0</p>
            <p>
              {{
                t(
                  "公开源码学习版。个人学习、技术研究与非商业交流；未经书面授权不得商用。",
                  "Source learning edition for personal study, research and non-commercial exchange. Commercial use requires written authorization.",
                )
              }}
            </p>
            <p>
              <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
                >www.zhuatech.cn</a
              ><br />{{
                t(
                  "商业授权、定制开发、部署与系统集成咨询微信",
                  "Commercial licensing and integration WeChat",
                )
              }}：zhuatech / zhuatech2
            </p>
          </section></template
        >
      </main>
    </div>
  </div>
  <div v-if="modal" class="modal-backdrop">
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      :aria-label="modal.title"
    >
      <div class="section-heading">
        <h2>{{ modal.title }}</h2>
        <button class="plain" :disabled="loading" @click="modal = null">
          {{ t("关闭", "Close") }}
        </button>
      </div>
      <p v-if="modal.kind === 'delete'" class="muted">
        {{
          t(
            "删除后无法恢复；已提交或被引用记录受保护。",
            "Deletion cannot be undone. Submitted or referenced records are protected.",
          )
        }}
      </p>
      <p v-if="modal.kind === 'command' && !fields.length" class="muted">
        {{ t("确认提交当前记录？", "Submit the current record?") }}
      </p>
      <p
        v-if="
          modal.kind === 'password' ||
          (modal.kind === 'admin' && view === 'users')
        "
        class="muted"
      >
        {{
          t(
            "密码须 12–72 位，含大写、小写和数字。重置会使原会话失效。",
            "Use 12–72 characters with upper case, lower case and digits. Resetting invalidates prior sessions.",
          )
        }}
      </p>
      <form @submit.prevent="save">
        <div class="form-grid">
          <div
            v-for="k in fields"
            :key="k"
            class="field"
            :class="{ wide: textAreas.includes(k) || k === 'permissions' }"
          >
            <label v-if="k !== 'permissions'" :for="'field-' + k">{{
              label(k)
            }}</label
            ><span v-else>{{ label(k) }}</span>
            <select
              v-if="selectFields.includes(k)"
              :id="'field-' + k"
              v-model="form[k]"
              :name="k"
              required
            >
              <option :value="undefined" disabled>
                {{ t("请选择", "Select") }}
              </option>
              <option v-for="c in choices(k)" :key="c.value" :value="c.value">
                {{ c.name }}
              </option>
            </select>
            <textarea
              v-else-if="textAreas.includes(k)"
              :id="'field-' + k"
              v-model="form[k]"
              :name="k"
              rows="4"
              required
              :maxlength="k === 'note' || modal.kind === 'action' ? 2000 : 3000"
            ></textarea>
            <div
              v-else-if="k === 'permissions'"
              class="checkbox-list"
              role="group"
              :aria-label="label(k)"
            >
              <label v-for="p in directory.permissions" :key="p.code"
                ><input
                  v-model="form.permissions"
                  type="checkbox"
                  :value="p.code"
                />{{ p.name }} <small>{{ p.code }}</small></label
              >
            </div>
            <input
              v-else-if="k === 'enabled'"
              :id="'field-' + k"
              v-model="form.enabled"
              type="checkbox"
              :name="k"
            />
            <input
              v-else
              :id="'field-' + k"
              v-model="form[k]"
              :name="k"
              :type="
                k.toLowerCase().includes('password')
                  ? 'password'
                  : k === 'dueDate' || k === 'verifyAfter'
                    ? 'date'
                    : k === 'position'
                      ? 'number'
                      : 'text'
              "
              :autocomplete="
                k.toLowerCase().includes('password') ? 'new-password' : 'off'
              "
              :required="k !== 'reference' && !(k === 'password' && modal.row)"
              :maxlength="
                k.toLowerCase().includes('password')
                  ? 72
                  : k === 'title' || k === 'value'
                    ? 200
                    : 120
              "
            />
          </div>
        </div>
        <p v-if="error" role="alert" class="error">{{ error }}</p>
        <div class="modal-actions">
          <button type="button" :disabled="loading" @click="modal = null">
            {{ t("取消", "Cancel") }}</button
          ><button
            :class="modal.kind === 'delete' ? 'danger' : 'primary'"
            :disabled="loading"
          >
            {{
              loading
                ? t("处理中…", "Working…")
                : modal.kind === "delete"
                  ? t("确认删除", "Delete")
                  : t("确认保存", "Save")
            }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>
