import { useState } from "react";

/*
  StaffFlow — Diagrama de Gantt por días
  Inicio:  02 Mar 2026  |  Entrega: 22 Abr 2026
  52 días naturales · 225h · 42 días disponibles (lun-sáb, sin festivos Madrid)

  Fase 1: 30h  02 Mar – 08 Mar  (7 días)
  Fase 2: 80h  09 Mar – 29 Mar  (21 días)
  Fase 3: 35h  30 Mar – 07 Abr  (9 días)
  Fase 4: 30h  08 Abr – 14 Abr  (7 días)
  Fase 5: 50h  15 Abr – 22 Abr  (8 días)
  Total:  225h
*/

const START_MS = new Date(2026, 2,  2).getTime();
const END_MS   = new Date(2026, 3, 22).getTime();
const RANGE_MS = END_MS - START_MS;

const pct  = (d)    => ((new Date(d).getTime() - START_MS) / RANGE_MS) * 100;
const wPct = (s, e) => ((new Date(e).getTime() - new Date(s).getTime()) / RANGE_MS) * 100;

const X_TICKS = [
  { label: "02 Mar", p: 0.0  },
  { label: "09 Mar", p: 13.7 },
  { label: "16 Mar", p: 27.5 },
  { label: "23 Mar", p: 41.2 },
  { label: "30 Mar", p: 54.9 },
  { label: "06 Abr", p: 68.6 },
  { label: "13 Abr", p: 82.4 },
  { label: "20 Abr", p: 96.1 },
];

// Festivos Madrid en el período
const FESTIVOS = [
  { date: "19 Mar", p: pct("2026-03-19"), label: "San José" },
  { date: "02 Abr", p: pct("2026-04-02"), label: "Jue. Santo" },
  { date: "03 Abr", p: pct("2026-04-03"), label: "Vie. Santo" },
];

const phases = [
  {
    id: 1, tag: "FASE 1", label: "Análisis y Diseño",
    color: "#10B981", bar: "#D1FAE5", accent: "#065F46",
    hours: 30, dateLabel: "02 Mar – 08 Mar",
    start: "2026-03-02", end: "2026-03-08",
    tasks: [
      { name: "Análisis de requisitos",         s: "2026-03-02", e: "2026-03-03" },
      { name: "Script DDL MySQL + diagrama ER", s: "2026-03-04", e: "2026-03-05" },
      { name: "Especificación de endpoints",    s: "2026-03-05", e: "2026-03-06" },
      { name: "Wireframes Android",             s: "2026-03-06", e: "2026-03-08" },
    ],
    techs: ["H2", "MySQL Workbench", "Postman", "Draw.io", "Figma"],
    milestone: { label: "Diseño completo", date: "08 Mar" },
  },
  {
    id: 2, tag: "FASE 2", label: "Desarrollo Backend",
    color: "#3B82F6", bar: "#DBEAFE", accent: "#1E3A8A",
    hours: 80, dateLabel: "09 Mar – 29 Mar",
    start: "2026-03-09", end: "2026-03-29",
    tasks: [
      { name: "Setup + Entidades JPA + Repos",      s: "2026-03-09", e: "2026-03-15" },
      { name: "Servicios — lógica de negocio",      s: "2026-03-16", e: "2026-03-22" },
      { name: "Controllers + Security + Scheduled", s: "2026-03-23", e: "2026-03-29" },
    ],
    techs: ["Java 17", "Spring Boot 3.2", "Spring Data JPA", "Spring Security", "JWT", "H2", "BCrypt", "Swagger", "JUnit 5", "Maven", "Docker"],
    milestone: { label: "API funcional", date: "29 Mar" },
  },
  {
    id: 3, tag: "FASE 3", label: "Desarrollo Android",
    color: "#EF4444", bar: "#FEE2E2", accent: "#7F1D1D",
    hours: 35, dateLabel: "30 Mar – 07 Abr",
    start: "2026-03-30", end: "2026-04-07",
    tasks: [
      { name: "Setup + UI + Retrofit + DataStore",    s: "2026-03-30", e: "2026-04-02" },
      { name: "Fichaje + Pausas + Saldo + Ausencias", s: "2026-04-04", e: "2026-04-07" },
    ],
    techs: ["Kotlin 1.9", "Android SDK 24+", "Material Design 3", "Retrofit 2", "Hilt", "Coroutines", "Navigation", "DataStore", "Android Studio"],
    milestone: { label: "App funcional", date: "07 Abr" },
  },
  {
    id: 4, tag: "FASE 4", label: "Testing",
    color: "#F59E0B", bar: "#FEF3C7", accent: "#78350F",
    hours: 30, dateLabel: "08 Abr – 14 Abr",
    start: "2026-04-08", end: "2026-04-14",
    tasks: [
      { name: "Testing integración end-to-end", s: "2026-04-08", e: "2026-04-11" },
      { name: "Corrección de errores",          s: "2026-04-12", e: "2026-04-14" },
    ],
    techs: ["Postman", "JUnit 5", "Mockito", "Android Emulator", "H2 Console"],
    milestone: { label: "Sistema testeado", date: "14 Abr" },
  },
  {
    id: 5, tag: "FASE 5", label: "Documentación y Presentación",
    color: "#8B5CF6", bar: "#EDE9FE", accent: "#4C1D95",
    hours: 50, dateLabel: "15 Abr – 22 Abr",
    start: "2026-04-15", end: "2026-04-22",
    tasks: [
      { name: "Completar memoria TFG",         s: "2026-04-15", e: "2026-04-19" },
      { name: "Vídeo presentación (≤ 25 min)", s: "2026-04-19", e: "2026-04-22" },
    ],
    techs: ["Word / LibreOffice", "Draw.io", "OBS Studio", "DaVinci Resolve"],
    milestone: { label: "ENTREGA FINAL", date: "22 Abr", isFinal: true },
  },
];

const C = {
  bg: "#F8FAFC", surface: "#FFFFFF",
  border: "#E2E8F0", sub: "#F1F5F9",
  text: "#0F172A", mid: "#475569", soft: "#94A3B8",
};

const Track = ({ children }) => (
  <div style={{ flex: 1, position: "relative", display: "flex", alignItems: "center" }}>
    {X_TICKS.map((t, i) => (
      <div key={i} style={{ position: "absolute", left: `${t.p}%`, top: 0, bottom: 0, borderLeft: `1px solid ${C.sub}`, pointerEvents: "none" }} />
    ))}
    {FESTIVOS.map((f, i) => (
      <div key={i} title={f.label} style={{ position: "absolute", left: `${f.p}%`, top: 0, bottom: 0, borderLeft: "1.5px dashed #FCA5A5", pointerEvents: "none", zIndex: 1 }} />
    ))}
    {children}
  </div>
);

export default function GanttStaffFlow() {
  const [open,     setOpen]     = useState(null);
  const [showTech, setShowTech] = useState(null);

  return (
    <div style={{ background: C.bg, minHeight: "100vh", fontFamily: "'Segoe UI', system-ui, sans-serif", padding: "28px 24px", color: C.text }}>

      {/* HEADER */}
      <div style={{ marginBottom: 24, borderBottom: `2px solid ${C.border}`, paddingBottom: 18, display: "flex", justifyContent: "space-between", alignItems: "flex-end", flexWrap: "wrap", gap: 12 }}>
        <div>
          <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: "0.25em", color: "#10B981", textTransform: "uppercase", marginBottom: 5 }}>TFG · DAM 2025 / 2026</div>
          <h1 style={{ margin: 0, fontSize: 22, fontWeight: 800, letterSpacing: "-0.02em" }}>
            StaffFlow
            <span style={{ fontSize: 11, fontWeight: 400, color: C.soft, letterSpacing: "0.06em", marginLeft: 10 }}>DIAGRAMA DE GANTT</span>
          </h1>
        </div>
        <div style={{ textAlign: "right", fontSize: 11, lineHeight: 1.9 }}>
          <div style={{ fontWeight: 600, color: C.mid }}>02 Mar 2026 → 22 Abr 2026</div>
          <div style={{ color: C.soft }}>52 días · 42 disponibles (lun–sáb) · 225h</div>
          <div style={{ color: "#8B5CF6", fontWeight: 600 }}>📹 Vídeo presentación ≤ 25 min · 22 Abr</div>
        </div>
      </div>

      {/* TABLE */}
      <div style={{ background: C.surface, borderRadius: 10, border: `1px solid ${C.border}`, overflow: "hidden", boxShadow: "0 1px 6px rgba(0,0,0,0.06)" }}>

        {/* X-axis header */}
        <div style={{ display: "flex", background: C.sub, borderBottom: `1px solid ${C.border}` }}>
          <div style={{ width: 240, flexShrink: 0, borderRight: `1px solid ${C.border}`, padding: "8px 14px", display: "flex", alignItems: "center" }}>
            <span style={{ fontSize: 9, fontWeight: 700, color: C.soft, letterSpacing: "0.12em", textTransform: "uppercase" }}>Fase / Tarea</span>
          </div>
          <div style={{ flex: 1, position: "relative", height: 42 }}>
            {X_TICKS.map((t, i) => (
              <div key={i} style={{ position: "absolute", left: `${t.p}%`, top: 0, height: "100%", display: "flex", flexDirection: "column", alignItems: "flex-start" }}>
                <div style={{ width: 1, background: C.border, height: 18 }} />
                <span style={{ fontSize: 8.5, fontWeight: 600, color: C.mid, whiteSpace: "nowrap", marginTop: 2, paddingLeft: 3 }}>{t.label}</span>
              </div>
            ))}
            {FESTIVOS.map((f, i) => (
              <div key={i} style={{ position: "absolute", left: `${f.p}%`, top: 0, height: "100%", display: "flex", flexDirection: "column", alignItems: "flex-start" }}>
                <div style={{ width: 1, borderLeft: "1.5px dashed #FCA5A5", height: 18 }} />
                <span style={{ fontSize: 7, color: "#EF4444", whiteSpace: "nowrap", marginTop: 2, paddingLeft: 2 }}>🚫 {f.label}</span>
              </div>
            ))}
          </div>
          <div style={{ width: 120, flexShrink: 0, borderLeft: `1px solid ${C.border}`, padding: "8px 10px", display: "flex", alignItems: "center" }}>
            <span style={{ fontSize: 9, fontWeight: 700, color: C.soft, letterSpacing: "0.12em", textTransform: "uppercase" }}>Hito</span>
          </div>
        </div>

        {/* Phase rows */}
        {phases.map((ph, pi) => {
          const isOpen = open === ph.id;
          const isTech = showTech === ph.id;
          const isLast = pi === phases.length - 1;
          const bLeft  = `${pct(ph.start)}%`;
          const bWidth = `${wPct(ph.start, ph.end)}%`;
          const mLeft  = `calc(${pct(ph.end)}% - 7px)`;

          return (
            <div key={ph.id} style={{ borderBottom: isLast ? "none" : `1px solid ${C.border}` }}>

              {/* Main row */}
              <div style={{ display: "flex", alignItems: "stretch", minHeight: 60 }}>
                {/* Label */}
                <div style={{ width: 240, flexShrink: 0, padding: "8px 12px", borderRight: `1px solid ${C.border}`, borderLeft: `4px solid ${ph.color}`, display: "flex", flexDirection: "column", justifyContent: "center", gap: 2 }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                    <span style={{ fontSize: 9, fontWeight: 700, background: ph.color, color: "#fff", padding: "2px 7px", borderRadius: 4 }}>{ph.tag}</span>
                    <span style={{ fontSize: 9, fontWeight: 600, color: C.soft }}>{ph.hours}h</span>
                  </div>
                  <div style={{ fontSize: 11, fontWeight: 700, color: "#1E293B" }}>{ph.label}</div>
                  <div style={{ fontSize: 9, color: C.soft }}>{ph.dateLabel}</div>
                  <div style={{ display: "flex", gap: 8, marginTop: 1 }}>
                    <span onClick={() => { setOpen(isOpen ? null : ph.id); setShowTech(null); }}
                      style={{ fontSize: 8, color: ph.color, cursor: "pointer", fontWeight: 700 }}>
                      {isOpen ? "▲ tareas" : "▼ tareas"}
                    </span>
                    <span onClick={() => { setShowTech(isTech ? null : ph.id); setOpen(null); }}
                      style={{ fontSize: 8, color: C.soft, cursor: "pointer", fontWeight: 700 }}>
                      {isTech ? "▲ tecn." : "⚙ tecn."}
                    </span>
                  </div>
                </div>

                {/* Bar track */}
                <Track>
                  <div style={{ position: "absolute", left: bLeft, width: bWidth, height: 32, background: ph.bar, border: `1.5px solid ${ph.color}55`, borderRadius: 5, display: "flex", alignItems: "center", paddingLeft: 10, overflow: "hidden", zIndex: 2 }}>
                    <div style={{ position: "absolute", inset: 0, background: `repeating-linear-gradient(45deg, ${ph.color}0D 0px, ${ph.color}0D 4px, transparent 4px, transparent 14px)` }} />
                    <span style={{ position: "relative", fontSize: 9, fontWeight: 700, color: ph.accent, whiteSpace: "nowrap" }}>{ph.label} — {ph.hours}h</span>
                  </div>
                  <div style={{ position: "absolute", left: mLeft, top: "50%", transform: "translateY(-50%)", zIndex: 10 }}>
                    <div style={{ width: 14, height: 14, background: ph.milestone.isFinal ? "#F59E0B" : ph.color, transform: "rotate(45deg)", borderRadius: 2, boxShadow: ph.milestone.isFinal ? "0 0 0 3px #FEF9C3, 0 2px 8px rgba(245,158,11,0.5)" : `0 0 0 3px ${ph.bar}, 0 2px 6px ${ph.color}55` }} />
                  </div>
                </Track>

                {/* Hito */}
                <div style={{ width: 120, flexShrink: 0, borderLeft: `1px solid ${C.border}`, padding: "0 10px", display: "flex", flexDirection: "column", justifyContent: "center" }}>
                  <div style={{ fontSize: 10, fontWeight: 700, color: ph.milestone.isFinal ? "#D97706" : ph.color }}>📍 {ph.milestone.date}</div>
                  <div style={{ fontSize: 9, color: C.mid, marginTop: 2, lineHeight: 1.4 }}>{ph.milestone.label}</div>
                </div>
              </div>

              {/* Task rows */}
              {isOpen && ph.tasks.map((task, ti) => (
                <div key={ti} style={{ display: "flex", alignItems: "stretch", minHeight: 28, borderTop: `1px dashed ${C.border}`, background: "#FAFBFF" }}>
                  <div style={{ width: 240, flexShrink: 0, padding: "4px 12px 4px 22px", borderRight: `1px solid ${C.border}`, borderLeft: `4px solid ${ph.color}22`, display: "flex", alignItems: "center" }}>
                    <span style={{ fontSize: 9, color: C.mid }}>↳ {task.name}</span>
                  </div>
                  <Track>
                    <div style={{ position: "absolute", left: `${pct(task.s)}%`, width: `${wPct(task.s, task.e)}%`, height: 16, background: `${ph.color}18`, border: `1px solid ${ph.color}44`, borderRadius: 3, display: "flex", alignItems: "center", paddingLeft: 6, overflow: "hidden" }}>
                      <span style={{ fontSize: 8, color: ph.accent, whiteSpace: "nowrap" }}>{task.name}</span>
                    </div>
                  </Track>
                  <div style={{ width: 120, flexShrink: 0, borderLeft: `1px solid ${C.border}` }} />
                </div>
              ))}

              {/* Tech row */}
              {isTech && (
                <div style={{ display: "flex", alignItems: "stretch", borderTop: `1px dashed ${C.border}`, background: "#FAFBFF" }}>
                  <div style={{ width: 240, flexShrink: 0, padding: "8px 12px 8px 22px", borderRight: `1px solid ${C.border}`, borderLeft: `4px solid ${ph.color}22`, display: "flex", alignItems: "center" }}>
                    <span style={{ fontSize: 9, fontWeight: 600, color: C.mid }}>⚙ Tecnologías</span>
                  </div>
                  <div style={{ flex: 1, padding: "8px 14px", display: "flex", flexWrap: "wrap", gap: 5, alignItems: "center" }}>
                    {ph.techs.map((t, i) => (
                      <span key={i} style={{ fontSize: 9, fontWeight: 600, background: `${ph.color}15`, color: ph.accent, border: `1px solid ${ph.color}44`, borderRadius: 4, padding: "2px 8px" }}>{t}</span>
                    ))}
                  </div>
                  <div style={{ width: 120, flexShrink: 0, borderLeft: `1px solid ${C.border}` }} />
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* STATS */}
      <div style={{ marginTop: 20, display: "flex", gap: 10, flexWrap: "wrap" }}>
        {[
          { label: "Inicio",       value: "02 Mar 2026" },
          { label: "Entrega",      value: "22 Abr 2026" },
          { label: "Días totales", value: "52 días"     },
          { label: "Disponibles",  value: "42 días"     },
          { label: "Esfuerzo",     value: "225 horas"   },
          { label: "Ritmo",        value: "~5,4h/día"   },
          { label: "📹 Vídeo",    value: "≤ 25 min"    },
        ].map((s, i) => (
          <div key={i} style={{ background: C.surface, border: `1px solid ${C.border}`, borderRadius: 8, padding: "7px 14px", boxShadow: "0 1px 3px rgba(0,0,0,0.04)" }}>
            <div style={{ fontSize: 8, color: C.soft, textTransform: "uppercase", letterSpacing: "0.1em" }}>{s.label}</div>
            <div style={{ fontSize: 13, fontWeight: 700, color: C.text, marginTop: 2 }}>{s.value}</div>
          </div>
        ))}
      </div>

      {/* RESUMEN FASES */}
      <div style={{ marginTop: 12, display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
        {phases.map(ph => (
          <div key={ph.id} style={{ display: "flex", alignItems: "center", gap: 6, background: C.surface, border: `1px solid ${C.border}`, borderLeft: `3px solid ${ph.color}`, borderRadius: 6, padding: "5px 10px" }}>
            <span style={{ fontSize: 10, fontWeight: 600, color: C.mid }}>{ph.tag}</span>
            <span style={{ fontSize: 10, color: C.soft }}>{ph.hours}h</span>
            <span style={{ fontSize: 9, color: C.soft }}>·</span>
            <span style={{ fontSize: 9, color: C.soft }}>{ph.dateLabel}</span>
          </div>
        ))}
        <div style={{ background: "#1E293B", borderRadius: 6, padding: "5px 12px" }}>
          <span style={{ fontSize: 10, fontWeight: 700, color: "#fff" }}>TOTAL 225h</span>
        </div>
      </div>

      {/* LEGEND */}
      <div style={{ marginTop: 12, display: "flex", gap: 12, alignItems: "center", flexWrap: "wrap" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 5 }}>
          <div style={{ width: 12, height: 12, background: C.soft, transform: "rotate(45deg)", borderRadius: 1 }} />
          <span style={{ fontSize: 10, color: C.soft }}>Hito de cierre</span>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 5 }}>
          <div style={{ width: 12, height: 12, background: "#F59E0B", transform: "rotate(45deg)", borderRadius: 1, boxShadow: "0 0 0 2px #FEF9C3" }} />
          <span style={{ fontSize: 10, color: C.soft }}>Entrega final</span>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 5 }}>
          <div style={{ width: 16, borderTop: "1.5px dashed #FCA5A5" }} />
          <span style={{ fontSize: 10, color: C.soft }}>Festivo Madrid</span>
        </div>
        <span style={{ fontSize: 10, color: C.soft }}>· ▼ tareas · ⚙ tecnologías · Eje X: cada lunes</span>
      </div>

    </div>
  );
}
