<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted } from "vue";
import {
  BookOpen,
  LogOut,
  Plus,
  RefreshCw,
  X,
  ChevronLeft,
  ChevronRight,
  ArrowUpRight,
  ShieldCheck,
} from "@lucide/vue";
import { api, resetCsrf } from "./api.js";
import { states, commands, labels, errors, actions, date } from "./schema.js";
const me = ref(null),
  login = ref({ username: "", password: "" }),
  page = ref("workbench"),
  loading = ref(false),
  saving = ref(false),
  error = ref(""),
  notice = ref(""),
  options = ref({
    accounts: [],
    departments: [],
    dictionaries: [],
    settings: [],
  }),
  rows = ref([]),
  total = ref(0),
  offset = ref(0),
  search = ref(""),
  status = ref(""),
  sort = ref("newest"),
  detail = ref(null),
  kind = ref("courses"),
  dialog = ref(null),
  stats = ref({ counts: {} }),
  work = ref({ learning: [], reviews: [], courses: [], expired: [] }),
  roles = ref([]),
  permissions = ref([]),
  answers = ref([]),
  examRequest = ref(null);
const business = ["courses", "enrollments"],
  can = (p) => me.value?.permissions.includes(p),
  record = computed(() => detail.value?.record),
  title = computed(
    () =>
      me.value?.menus.find((m) => m.code === page.value)?.name || "培训工作台",
  );
const person = (id) =>
    options.value.accounts.find((a) => a.id === id)?.name || "#" + id,
  department = (id) =>
    options.value.departments.find((d) => d.id === id)?.name || "#" + id;
const filtered = computed(() =>
    rows.value.filter((r) =>
      JSON.stringify(r).toLowerCase().includes(search.value.toLowerCase()),
    ),
  ),
  visibleRows = computed(() =>
    business.includes(page.value)
      ? rows.value
      : filtered.value.slice(offset.value * 20, offset.value * 20 + 20),
  ),
  pageTotal = computed(() =>
    business.includes(page.value) ? total.value : filtered.value.length,
  );
const statuses = computed(() =>
  page.value === "courses"
    ? ["DRAFT", "PUBLISHED", "ARCHIVED"]
    : page.value === "enrollments"
      ? [
          "ASSIGNED",
          "LEARNING",
          "PRACTICAL",
          "QUALIFIED",
          "EXPIRED",
          "FAILED",
          "CANCELLED",
          "REVOKED",
        ]
      : [],
);
const f = (key, type = "text", extra = {}) => ({ key, type, ...extra }),
  choices = (list, label = "name", value = "id") =>
    list.map((a) => ({ label: a[label], value: a[value] }));
const fields = {
  courses: () => [
    f("familyCode", "text", { readonly: !!dialog.value?.id }),
    f("title"),
    f("category", "select", {
      options: choices(
        options.value.dictionaries.filter((d) => d.type === "course"),
        "name",
        "code",
      ),
    }),
    f("departmentId", "select", {
      options: choices(options.value.departments),
      readonly: !!dialog.value?.id,
    }),
    f("content", "textarea", { max: 30000 }),
    f("passScore", "number", { min: 1, max: 100 }),
    f("maxAttempts", "number", { min: 1, max: 10 }),
    f("validDays", "number", { min: 1, max: 730 }),
    f("practicalRequired", "checkbox"),
  ],
  users: () => [
    f("username"),
    f("displayName"),
    f("password", "password", { required: !dialog.value?.id }),
    f("roleId", "select", { options: choices(roles.value) }),
    f("departmentId", "select", {
      options: choices(options.value.departments),
    }),
    f("enabled", "checkbox"),
  ],
  roles: () => [
    f("name"),
    f("scope", "select", {
      options: [
        { value: "ALL", label: "全部部门" },
        { value: "DEPARTMENT", label: "本部门" },
        { value: "SELF", label: "本人学习" },
      ],
    }),
    f("permissions", "permissions", {
      options: choices(permissions.value, "name", "code"),
    }),
  ],
  departments: () => [f("name")],
  menus: () => [
    f("code", "text", { readonly: true }),
    f("name"),
    f("nameEn"),
    f("permissionCode", "select", {
      options: choices(permissions.value, "name", "code"),
    }),
    f("position", "number"),
    f("enabled", "checkbox"),
  ],
  permissions: () => [f("code", "text", { readonly: true }), f("name")],
  dictionaries: () => [f("type"), f("code"), f("name"), f("nameEn")],
  settings: () => [f("code", "text", { readonly: true }), f("value")],
  password: () => [f("oldPassword", "password"), f("newPassword", "password")],
};
const dialogFields = computed(() => {
  const d = dialog.value;
  if (!d) return [];
  if (d.action === "assign") {
    const dept = record.value.departmentId;
    return [
      f("learnerId", "select", {
        options: choices(
          options.value.accounts.filter(
            (a) =>
              a.enabled &&
              a.permissions.includes("learn") &&
              (a.scope === "ALL" || a.departmentId === dept),
          ),
        ),
      }),
      ...(record.value.practicalRequired
        ? [
            f("reviewerId", "select", {
              options: choices(
                options.value.accounts.filter(
                  (a) =>
                    a.enabled &&
                    a.id !== me.value.id &&
                    a.id !== d.values.learnerId &&
                    a.permissions.includes("practical.review") &&
                    (a.scope === "ALL" || a.departmentId === dept),
                ),
              ),
            }),
          ]
        : []),
      f("dueDate", "date"),
    ];
  }
  if (d.action)
    return ["read", "revise", "delete"].includes(d.action)
      ? []
      : [f("note", "textarea", { max: 2000 })];
  return fields[d.kind]?.() || [];
});
/** 清除旧账号的工作区及考试答案。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
function clear() {
  me.value = null;
  detail.value = null;
  dialog.value = null;
  rows.value = [];
  roles.value = [];
  permissions.value = [];
  options.value = {
    accounts: [],
    departments: [],
    dictionaries: [],
    settings: [],
  };
  work.value = { learning: [], reviews: [], courses: [], expired: [] };
  stats.value = { counts: {} };
  page.value = "workbench";
  answers.value = [];
  examRequest.value = null;
  search.value = "";
  notice.value = "";
}
function fail(e) {
  error.value = errors[e.message] || "操作未完成，请检查输入后重试";
  if (e.message === "UNAUTHENTICATED") clear();
}
/** 刷新权限和范围后获取列表或明细。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function load() {
  if (!me.value) return;
  loading.value = true;
  error.value = "";
  try {
    me.value = await api("/auth/me");
    options.value = await api("/options");
    if (can("admin") && me.value.scope === "ALL") {
      roles.value = await api("/admin/roles");
      permissions.value = await api("/admin/permissions");
    }
    if (!me.value.menus.some((m) => m.code === page.value))
      page.value = me.value.menus[0]?.code || "workbench";
    if (detail.value) {
      detail.value = await api("/" + kind.value + "/" + record.value.id);
      return;
    }
    if (business.includes(page.value)) {
      const v = await api(
        "/" +
          page.value +
          "?" +
          new URLSearchParams({
            search: search.value,
            status: status.value,
            page: offset.value,
            size: 20,
            sort: sort.value,
          }),
      );
      rows.value = v.items;
      total.value = v.total;
    } else if (page.value === "workbench") work.value = await api("/workbench");
    else if (page.value === "dashboard") stats.value = await api("/dashboard");
    else
      rows.value = await api(
        page.value === "audit" ? "/audit" : "/admin/" + page.value,
      );
  } catch (e) {
    fail(e);
  } finally {
    loading.value = false;
  }
}
async function signIn() {
  saving.value = true;
  error.value = "";
  try {
    resetCsrf();
    me.value = await api("/auth/login", "POST", login.value);
    login.value.password = "";
    await load();
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
async function signOut() {
  try {
    await api("/auth/logout", "POST");
  } catch (e) {
    fail(e);
  } finally {
    resetCsrf();
    clear();
  }
}
async function navigate(code) {
  if (loading.value || saving.value) return;
  page.value = code;
  detail.value = null;
  rows.value = [];
  offset.value = 0;
  search.value = "";
  status.value = "";
  answers.value = [];
  notice.value = "";
  await load();
}
async function show(k, id) {
  if (loading.value || saving.value) return;
  loading.value = true;
  error.value = "";
  try {
    kind.value = k;
    detail.value = await api("/" + k + "/" + id);
    answers.value = [];
  } catch (e) {
    fail(e);
  } finally {
    loading.value = false;
  }
}
function blankQuestion() {
  return { prompt: "", options: ["", "", "", ""], correctAnswer: 0 };
}
function edit(k, r) {
  error.value = "";
  dialog.value = {
    kind: k,
    id: r?.id,
    version: r?.version,
    values: r
      ? {
          ...r,
          password: "",
          content: detail.value?.content,
          questions: detail.value?.questions.map((q) => ({
            prompt: q.prompt,
            options: [...q.options],
            correctAnswer: q.correctAnswer,
          })),
        }
      : {
          enabled: true,
          permissions: [],
          scope: "DEPARTMENT",
          departmentId: me.value.departmentId,
          category: "OPERATIONS",
          type: "course",
          passScore: 80,
          maxAttempts: 3,
          validDays: 365,
          practicalRequired: true,
          questions: [blankQuestion(), blankQuestion()],
        },
    requestKey: crypto.randomUUID(),
  };
}
function command(k, r, action) {
  error.value = "";
  dialog.value = {
    kind: k,
    id: r.id,
    version: r.version,
    action,
    values: { note: "", dueDate: options.value.now?.slice(0, 10) },
    requestKey: crypto.randomUUID(),
  };
}
/** 请求键保持精确重试；成功后重新读取状态和版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function save() {
  saving.value = true;
  error.value = "";
  try {
    const d = dialog.value,
      v = { ...d.values, version: d.version, requestKey: d.requestKey };
    let path,
      method = "POST";
    if (d.action === "delete") {
      path =
        (d.kind === "courses" ? "/courses/" : "/admin/" + d.kind + "/") +
        d.id +
        (d.kind === "courses" ? "?version=" + d.version : "");
      method = "DELETE";
    } else if (d.action) {
      path =
        "/" +
        d.kind +
        "/" +
        d.id +
        (d.action === "assign" ? "/assign" : "/commands/" + d.action);
    } else if (d.kind === "password") path = "/auth/password";
    else {
      path =
        (d.kind === "courses" ? "/courses" : "/admin/" + d.kind) +
        (d.id ? "/" + d.id : "");
      method = d.id ? "PUT" : "POST";
    }
    const result = await api(path, method, v);
    dialog.value = null;
    notice.value = "已保存";
    if (d.kind === "password") {
      resetCsrf();
      clear();
    } else {
      if (d.action === "delete") detail.value = null;
      if (d.action === "revise") {
        kind.value = "courses";
        detail.value = await api("/courses/" + result.id);
      }
      await load();
    }
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
async function submitExam() {
  saving.value = true;
  error.value = "";
  try {
    await api("/enrollments/" + record.value.id + "/commands/exam", "POST", {
      version: record.value.version,
      requestKey:
        examRequest.value?.version === record.value.version
          ? examRequest.value.key
          : (examRequest.value = {
              version: record.value.version,
              key: crypto.randomUUID(),
            }).key,
      answers: answers.value,
    });
    answers.value = [];
    notice.value = "考试已提交，分数与状态已更新";
    await load();
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
async function exportReport() {
  try {
    const data = await api(
        "/" + kind.value + "/" + record.value.id + "/report.json",
      ),
      url = URL.createObjectURL(
        new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }),
      ),
      a = document.createElement("a");
    a.href = url;
    a.download = kind.value + "-" + record.value.id + ".json";
    a.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    fail(e);
  }
}
function changePage(delta) {
  offset.value += delta;
  load();
}
onMounted(async () => {
  try {
    me.value = await api("/auth/me");
    await load();
  } catch (e) {
    if (e.message !== "UNAUTHENTICATED") fail(e);
  }
});
</script>
<template>
  <div v-if="!me" class="login-shell">
    <section class="login-brand">
      <img src="/brand/logo.jpg" alt="知华科技" /><span class="eyebrow"
        >TRAINFLOW / 0.1.0</span
      >
      <h1>从学习记录<br />到有效岗位能力</h1>
      <div class="brand-line"></div>
      <p>课程版本 · 本人考核 · 实操复核 · 到期重训</p>
      <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
        >知华科技官网 <ArrowUpRight :size="14"
      /></a>
    </section>
    <section class="login-form">
      <form @submit.prevent="signIn">
        <span class="eyebrow">ACCOUNT ACCESS</span>
        <h2>登录培训工作台</h2>
        <label
          >账号<input
            v-model="login.username"
            autocomplete="username"
            required
            maxlength="60" /></label
        ><label
          >密码<input
            v-model="login.password"
            type="password"
            autocomplete="current-password"
            required
            maxlength="128"
        /></label>
        <div v-if="error" role="alert" class="error">{{ error }}</div>
        <button class="primary wide" :disabled="saving">
          {{ saving ? "正在登录…" : "登录" }}
        </button>
        <p class="muted small">公开源码学习版 · 未经书面授权不得商用</p>
      </form>
      <footer>
        上海如静知华信息科技有限公司<br />商业咨询微信 zhuatech / zhuatech2
      </footer>
    </section>
  </div>
  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <img src="/brand/logo.jpg" alt="知华科技" /><span>TrainFlow</span>
      </div>
      <div class="sidebar-caption">培训与能力管理</div>
      <nav aria-label="主导航">
        <button
          v-for="m in me.menus"
          :key="m.code"
          :class="{ active: page === m.code }"
          :disabled="loading || saving"
          @click="navigate(m.code)"
        >
          <BookOpen
            v-if="business.includes(m.code) || m.code === 'workbench'"
            :size="16"
          /><ShieldCheck v-else :size="16" />{{ m.name }}
        </button>
      </nav>
      <footer>
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >知华科技官网 ↗</a
        ><small>公开源码学习版 0.1.0</small>
      </footer>
    </aside>
    <div class="content-shell">
      <header class="topbar">
        <span>{{ department(me.departmentId) }}</span>
        <div>
          <button @click="edit('password')">修改密码</button
          ><span>{{ me.displayName }} · {{ me.role }}</span
          ><button aria-label="退出登录" @click="signOut">
            <LogOut :size="17" />
          </button>
        </div>
      </header>
      <main :aria-busy="loading">
        <div class="page-heading">
          <div>
            <span class="eyebrow">LEARNING & COMPETENCE</span>
            <h1>{{ detail ? "培训详情" : title }}</h1>
          </div>
          <button :disabled="loading || saving" @click="load">
            <RefreshCw :size="16" />刷新
          </button>
        </div>
        <div v-if="error" role="alert" class="error">{{ error }}</div>
        <div v-if="notice" role="status" class="success">{{ notice }}</div>
        <template v-if="detail"
          ><button
            @click="
              detail = null;
              answers = [];
              load();
            "
          >
            <ChevronLeft :size="16" />返回列表
          </button>
          <section class="panel detail-head">
            <div>
              <h2>
                {{ record.familyCode }} · {{ record.title }}
                <small>v{{ record.edition }}</small>
              </h2>
              <span class="badge" :class="record.status">{{
                states[record.status]
              }}</span>
            </div>
            <div class="toolbar">
              <button
                v-if="
                  kind === 'courses' &&
                  record.status === 'DRAFT' &&
                  record.authorId === me.id &&
                  can('course.manage')
                "
                @click="edit('courses', record)"
              >
                编辑课程</button
              ><button
                v-if="
                  kind === 'courses' &&
                  record.status === 'DRAFT' &&
                  record.authorId === me.id &&
                  can('course.manage')
                "
                @click="command(kind, record, 'delete')"
              >
                删除草稿</button
              ><button
                v-if="
                  kind === 'courses' &&
                  record.status === 'PUBLISHED' &&
                  can('training.assign')
                "
                class="primary"
                @click="command(kind, record, 'assign')"
              >
                分配培训</button
              ><button
                v-for="a in actions(kind, record, me)"
                :key="a"
                @click="command(kind, record, a)"
              >
                {{ commands[a] }}</button
              ><button v-if="can('export')" @click="exportReport">
                导出 JSON
              </button>
            </div>
          </section>
          <section class="panel">
            <h3>培训信息</h3>
            <dl class="facts">
              <dt>归属部门</dt>
              <dd>{{ department(record.departmentId) }}</dd>
              <template v-if="kind === 'courses'"
                ><dt>编制人</dt>
                <dd>{{ record.authorName }}</dd>
                <dt>评分规则</dt>
                <dd>
                  {{ record.passScore }}分通过 · 最多{{ record.maxAttempts }}次
                </dd>
                <dt>通过后有效期</dt>
                <dd>{{ record.validDays }}天</dd>
                <dt>实操复核</dt>
                <dd>
                  {{ record.practicalRequired ? "必须复核" : "无需实操" }}
                </dd></template
              ><template v-else
                ><dt>学员</dt>
                <dd>{{ record.learnerName }}</dd>
                <dt>实操复核人</dt>
                <dd>
                  {{ record.reviewerName || "无需实操" }}
                </dd>
                <dt>完成截止日</dt>
                <dd>{{ record.dueDate }}</dd>
                <dt>考试成绩</dt>
                <dd>
                  最高{{ record.bestScore }}分 · 已考{{
                    record.attemptsUsed
                  }}/{{ detail.course.maxAttempts }}次
                </dd>
                <dt>能力有效至</dt>
                <dd>{{ record.validUntil || "—" }}</dd>
                <dt>通过时间</dt>
                <dd>{{ date(record.qualifiedAt) }}</dd></template
              >
            </dl>
          </section>
          <section class="panel">
            <h3>学习正文</h3>
            <div class="course-content">{{ detail.content }}</div>
          </section>
          <section v-if="kind === 'courses'" class="panel">
            <h3>课程题目 · {{ detail.questions.length }}题</h3>
            <article v-for="q in detail.questions" :key="q.id" class="question">
              <h4>{{ q.position + 1 }}. {{ q.prompt }}</h4>
              <p v-for="(o, i) in q.options" :key="i">
                {{ String.fromCharCode(65 + i) }} · {{ o }}
                <strong v-if="q.correctAnswer === i">✓ 标准答案</strong>
              </p>
            </article>
          </section>
          <section
            v-if="
              kind === 'enrollments' &&
              record.status === 'LEARNING' &&
              record.learnerId === me.id &&
              can('learn')
            "
            class="panel"
          >
            <h3>本人考试 · {{ detail.course.passScore }}分通过</h3>
            <form @submit.prevent="submitExam">
              <fieldset
                v-for="(q, n) in detail.questions"
                :key="q.id"
                class="question"
              >
                <legend>{{ n + 1 }}. {{ q.prompt }}</legend>
                <label v-for="(o, i) in q.options" :key="i" class="choice"
                  ><input
                    v-model="answers[n]"
                    type="radio"
                    :name="'answer-' + n"
                    :value="i"
                    required
                  />{{ String.fromCharCode(65 + i) }} · {{ o }}</label
                >
              </fieldset>
              <button class="primary" :disabled="saving || loading">
                提交考试
              </button>
            </form>
          </section>
          <section v-if="detail.attempts" class="panel">
            <h3>历次考试</h3>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>次数</th>
                    <th>得分</th>
                    <th>提交时间</th>
                    <th>本人选项</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="a in detail.attempts" :key="a.id">
                    <td>{{ a.number }}</td>
                    <td>{{ a.score }}</td>
                    <td>{{ date(a.submittedAt) }}</td>
                    <td>{{ a.answers }}</td>
                  </tr>
                  <tr v-if="!detail.attempts.length">
                    <td colspan="4" class="empty">暂无考试记录</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
          <section class="panel">
            <h3>流程记录</h3>
            <div v-for="e in detail.events" :key="e.id" class="timeline-row">
              <span>{{ date(e.createdAt) }}</span
              ><strong>{{
                commands[e.action] ||
                {
                  SAVE: "保存草稿",
                  ASSIGN: "分配培训",
                  REPLACED: "旧版归档",
                  REVISE: "创建新版",
                  read: "确认阅读",
                  exam: "考试提交",
                }[e.action] ||
                e.action
              }}</strong
              ><span>{{ person(e.actorId) }} · {{ e.note }}</span>
            </div>
          </section></template
        >
        <template v-else-if="page === 'workbench'"
          ><div class="work-grid">
            <section v-for="(items, key) in work" :key="key" class="panel">
              <h3>
                {{
                  {
                    learning: "我的学习与考试",
                    reviews: "我的实操复核",
                    courses: "课程发布待审",
                    expired: "已到期能力",
                  }[key]
                }}
                <small>{{ items.length }}</small>
              </h3>
              <button
                v-for="r in items"
                :key="r.id"
                class="task-row"
                @click="
                  show(key === 'courses' ? 'courses' : 'enrollments', r.id)
                "
              >
                <span
                  >{{ r.title }} · v{{ r.edition
                  }}<small>{{ r.dueDate || r.familyCode }}</small></span
                ><span class="badge">{{ states[r.status] }}</span>
              </button>
              <p v-if="!items.length" class="muted empty">暂无记录</p>
            </section>
          </div></template
        >
        <template v-else-if="page === 'dashboard'"
          ><div class="metrics">
            <section class="metric">
              <span>培训任务</span><strong>{{ stats.total }}</strong>
            </section>
            <section class="metric">
              <span>逾期未完成</span><strong>{{ stats.overdue }}</strong>
            </section>
            <section class="metric">
              <span>即将到期能力</span><strong>{{ stats.dueSoon }}</strong>
            </section>
          </div>
          <section class="panel">
            <h3>当前状态分布</h3>
            <div v-for="(n, s) in stats.counts" :key="s" class="stat-row">
              <span>{{ states[s] }}</span>
              <div class="bar">
                <i
                  :style="{ width: (n / Math.max(stats.total, 1)) * 100 + '%' }"
                ></i>
              </div>
              <strong>{{ n }}</strong>
            </div>
            <p v-if="!stats.total" class="empty muted">暂无培训记录</p>
          </section></template
        >
        <template v-else
          ><section class="panel">
            <div class="toolbar filters">
              <form
                @submit.prevent="
                  offset = 0;
                  load();
                "
              >
                <input
                  v-model="search"
                  aria-label="搜索"
                  placeholder="搜索名称或编号"
                  maxlength="120"
                /><select
                  v-if="statuses.length"
                  v-model="status"
                  aria-label="状态"
                >
                  <option value="">全部状态</option>
                  <option v-for="s in statuses" :key="s" :value="s">
                    {{ states[s] }}
                  </option></select
                ><select
                  v-if="business.includes(page)"
                  v-model="sort"
                  aria-label="排序"
                >
                  <option value="newest">最新优先</option>
                  <option value="oldest">最早优先</option></select
                ><button :disabled="loading">查询</button>
              </form>
              <button
                v-if="
                  (page === 'courses' && can('course.manage')) ||
                  (['users', 'roles', 'departments', 'dictionaries'].includes(
                    page,
                  ) &&
                    can('admin'))
                "
                class="primary"
                @click="edit(page)"
              >
                <Plus :size="16" />新建
              </button>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <template v-if="page === 'courses'"
                      ><th>编号 / 版本</th>
                      <th>课程</th>
                      <th>状态</th>
                      <th>部门</th>
                      <th>考试规则</th></template
                    ><template v-else-if="page === 'enrollments'"
                      ><th>课程 / 版本</th>
                      <th>学员</th>
                      <th>状态</th>
                      <th>截止日</th>
                      <th>最高分</th>
                      <th>能力有效至</th></template
                    ><template v-else-if="page === 'audit'"
                      ><th>时间</th>
                      <th>账号</th>
                      <th>操作</th>
                      <th>对象</th></template
                    ><template v-else
                      ><th>代码 / 账号</th>
                      <th>名称</th>
                      <th>配置</th>
                      <th>操作</th></template
                    >
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="r in visibleRows" :key="r.id">
                    <template v-if="page === 'courses'"
                      ><td>{{ r.familyCode }} / v{{ r.edition }}</td>
                      <td>
                        <button class="text-link" @click="show(page, r.id)">
                          {{ r.title }}
                        </button>
                      </td>
                      <td>
                        <span class="badge" :class="r.status">{{
                          states[r.status]
                        }}</span>
                      </td>
                      <td>{{ department(r.departmentId) }}</td>
                      <td>
                        {{ r.passScore }}分 · {{ r.maxAttempts }}次 ·
                        {{ r.practicalRequired ? "实操" : "笔试" }}
                      </td></template
                    ><template v-else-if="page === 'enrollments'"
                      ><td>
                        <button class="text-link" @click="show(page, r.id)">
                          {{ r.title }} / v{{ r.edition }}
                        </button>
                      </td>
                      <td>{{ r.learnerName }}</td>
                      <td>
                        <span class="badge" :class="r.status">{{
                          states[r.status]
                        }}</span>
                      </td>
                      <td>{{ r.dueDate }}</td>
                      <td>{{ r.bestScore }}</td>
                      <td>{{ r.validUntil || "—" }}</td></template
                    ><template v-else-if="page === 'audit'"
                      ><td>{{ date(r.createdAt) }}</td>
                      <td>{{ r.actor }}</td>
                      <td>{{ r.action }}</td>
                      <td>{{ r.objectId }}</td></template
                    ><template v-else
                      ><td>{{ r.username || r.code || r.id }}</td>
                      <td>{{ r.displayName || r.name || r.code }}</td>
                      <td>
                        {{
                          r.scope ||
                          r.value ||
                          (r.enabled === false
                            ? "停用"
                            : r.enabled === true
                              ? "启用"
                              : r.nameEn) ||
                          "—"
                        }}
                      </td>
                      <td>
                        <button @click="edit(page, r)">编辑</button
                        ><button
                          v-if="
                            !['menus', 'permissions', 'settings'].includes(page)
                          "
                          @click="command(page, r, 'delete')"
                        >
                          删除
                        </button>
                      </td></template
                    >
                  </tr>
                  <tr v-if="!visibleRows.length">
                    <td colspan="6" class="empty">
                      {{ loading ? "正在读取…" : "暂无记录" }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="pagination">
              <span>共{{ pageTotal }}条 · 第{{ offset + 1 }}页</span>
              <div>
                <button
                  :disabled="offset === 0 || loading"
                  @click="changePage(-1)"
                >
                  <ChevronLeft :size="16" />上一页</button
                ><button
                  :disabled="(offset + 1) * 20 >= pageTotal || loading"
                  @click="changePage(1)"
                >
                  下一页<ChevronRight :size="16" />
                </button>
              </div>
            </div></section
        ></template>
      </main>
    </div>
  </div>
  <div v-if="dialog" class="modal-backdrop">
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      :aria-label="
        dialog.action === 'delete'
          ? '删除确认'
          : dialog.action
            ? commands[dialog.action] || '分配培训'
            : dialog.kind === 'courses'
              ? '课程编辑'
              : '资料编辑'
      "
    >
      <div class="modal-header">
        <h2>
          {{
            dialog.action === "delete"
              ? "删除确认"
              : dialog.action
                ? commands[dialog.action] || "分配培训"
                : dialog.kind === "courses"
                  ? "课程编辑"
                  : "资料编辑"
          }}
        </h2>
        <button :disabled="saving" aria-label="关闭" @click="dialog = null">
          <X :size="18" />
        </button>
      </div>
      <form @submit.prevent="save">
        <p v-if="dialog.action === 'delete'">
          确认删除这条记录？有历史引用的记录不能删除。
        </p>
        <p v-if="dialog.action === 'read'">
          确认已经阅读当前课程内容，开始本人的考试。
        </p>
        <p v-if="dialog.action === 'revise'">
          复制当前内容为新草稿，原版培训和历史成绩继续保留。
        </p>
        <div class="form-grid">
          <component
            :is="field.type === 'permissions' ? 'div' : 'label'"
            v-for="field in dialogFields"
            :key="field.key"
            :class="{
              full: field.type === 'textarea' || field.type === 'permissions',
            }"
            >{{ labels[field.key] || field.key
            }}<textarea
              v-if="field.type === 'textarea'"
              v-model="dialog.values[field.key]"
              :required="field.required !== false"
              :maxlength="field.max || 2000"
              rows="5"
            ></textarea
            ><select
              v-else-if="field.type === 'select'"
              v-model="dialog.values[field.key]"
              :disabled="field.readonly"
              required
            >
              <option disabled :value="undefined">请选择</option>
              <option
                v-for="o in field.options"
                :key="o.value"
                :value="o.value"
              >
                {{ o.label }}
              </option></select
            ><input
              v-else-if="field.type === 'checkbox'"
              v-model="dialog.values[field.key]"
              type="checkbox" />
            <div
              v-else-if="field.type === 'permissions'"
              class="permission-grid"
            >
              <label v-for="o in field.options" :key="o.value" class="choice"
                ><input
                  v-model="dialog.values.permissions"
                  type="checkbox"
                  :value="o.value"
                />{{ o.label }}</label
              >
            </div>
            <input
              v-else
              v-model="dialog.values[field.key]"
              :type="field.type"
              :readonly="field.readonly"
              :required="field.required !== false"
              :min="field.min"
              :max="field.max"
              :maxlength="
                field.type === 'password'
                  ? 128
                  : field.key === 'title'
                    ? 160
                    : 120
              "
              :autocomplete="
                field.type === 'password' ? 'new-password' : 'off'
              "
          /></component>
        </div>
        <section
          v-if="dialog.kind === 'courses' && !dialog.action"
          class="question-editor"
        >
          <h3>考试题目 · 四选一</h3>
          <article
            v-for="(q, n) in dialog.values.questions"
            :key="n"
            class="question"
          >
            <label
              >第{{ n + 1 }}题题干<input
                v-model="q.prompt"
                required
                maxlength="1000"
            /></label>
            <div class="form-grid">
              <label v-for="(o, i) in q.options" :key="i"
                >选项{{ String.fromCharCode(65 + i)
                }}<input v-model="q.options[i]" required maxlength="500"
              /></label>
            </div>
            <label
              >标准答案<select v-model="q.correctAnswer">
                <option v-for="i in 4" :key="i" :value="i - 1">
                  {{ String.fromCharCode(64 + i) }}
                </option>
              </select></label
            ><button
              type="button"
              :disabled="dialog.values.questions.length <= 2"
              @click="dialog.values.questions.splice(n, 1)"
            >
              移除本题
            </button>
          </article>
          <button
            type="button"
            :disabled="dialog.values.questions.length >= 30"
            @click="dialog.values.questions.push(blankQuestion())"
          >
            <Plus :size="15" />添加题目
          </button>
        </section>
        <div v-if="error" class="error" role="alert">{{ error }}</div>
        <div class="modal-footer">
          <button type="button" :disabled="saving" @click="dialog = null">
            取消</button
          ><button class="primary" :disabled="saving">
            {{ saving ? "正在保存…" : "确认保存" }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>
