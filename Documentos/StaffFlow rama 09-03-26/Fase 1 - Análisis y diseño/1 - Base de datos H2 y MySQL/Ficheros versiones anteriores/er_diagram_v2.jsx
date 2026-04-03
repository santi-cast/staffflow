import { useState } from "react";

// ─── colour palette (matches original diagram) ───────────────────────────────
const C = {
  configuracion : { header:"#1B3A5C", light:"#EBF2FA", border:"#1B3A5C", text:"#fff" },
  empleados     : { header:"#1A6B4A", light:"#E6F5EE", border:"#1A6B4A", text:"#fff" },
  usuarios      : { header:"#1565C0", light:"#E3F0FF", border:"#1565C0", text:"#fff" },
  planificacion : { header:"#7B1FA2", light:"#F3E5F5", border:"#7B1FA2", text:"#fff" },
  fichajes      : { header:"#B71C1C", light:"#FFEBEE", border:"#B71C1C", text:"#fff" },
  pausas        : { header:"#E65100", light:"#FFF3E0", border:"#E65100", text:"#fff" },
  saldos        : { header:"#00695C", light:"#E0F2F1", border:"#00695C", text:"#fff" },
};
const FK_COL    = "#D32F2F";
const AUDIT_COL = "#9E9E9E";
const IDX_COL   = "#1565C0";
const NOTE_COL  = "#7B1FA2";
const NEW_COL   = "#E65100";    // highlight for new fields

// ─── entity box component ─────────────────────────────────────────────────────
function Box({ id, title, c, fields, notes, style }) {
  return (
    <div style={{
      position:"absolute", width:265,
      border:`2px solid ${c.border}`, borderRadius:8,
      boxShadow:"0 2px 10px rgba(0,0,0,.18)",
      fontFamily:"'Consolas','Courier New',monospace",
      fontSize:10.5, background:c.light,
      ...style,
    }}>
      {/* header */}
      <div style={{
        background:c.header, color:c.text,
        fontWeight:700, fontSize:12,
        textAlign:"center", padding:"7px 6px",
        borderRadius:"6px 6px 0 0", letterSpacing:.4,
      }}>{title}</div>

      {/* fields */}
      <div style={{ padding:"8px 10px 4px 10px" }}>
        {fields.map((f,i) => {
          const col = f.pk ? "#1B3A5C" : f.fk ? FK_COL : f.audit ? AUDIT_COL : f.isNew ? NEW_COL : "#212121";
          return (
            <div key={i} style={{
              color: col,
              fontWeight: f.pk||f.fk ? 700 : f.isNew ? 600 : 400,
              marginBottom: 2,
            }}>
              {f.pk ? "PK " : f.fk ? "FK " : "   "}{f.label}
            </div>
          );
        })}
      </div>

      {/* notes */}
      {notes?.length > 0 && (
        <div style={{ borderTop:`1px solid ${c.border}44`, padding:"5px 10px 7px 10px" }}>
          {notes.map((n,i) => (
            <div key={i} style={{
              fontSize:9.5, fontStyle:"italic",
              color: n.idx ? IDX_COL : NOTE_COL,
              marginBottom:1,
            }}>{n.text}</div>
          ))}
        </div>
      )}
    </div>
  );
}

// ─── svg arrow ────────────────────────────────────────────────────────────────
function Arrow({ x1,y1,x2,y2, dashed, color="#555" }) {
  return (
    <line x1={x1} y1={y1} x2={x2} y2={y2}
      stroke={color} strokeWidth={dashed?1.5:2}
      strokeDasharray={dashed?"6,4":"none"}
      markerEnd="url(#arr)" />
  );
}

function Label({ x, y, text, color="#555", bold }) {
  return (
    <text x={x} y={y} fontSize={11} fontFamily="Arial"
      fontWeight={bold?"700":"400"} fill={color}>{text}</text>
  );
}

// ─── main diagram ─────────────────────────────────────────────────────────────
export default function ERDiagram() {

  // positions
  const P = {
    cfg   : { left:420, top:18  },
    emp   : { left:385, top:210 },
    usr   : { left:40,  top:195 },
    plan  : { left:750, top:195 },
    fich  : { left:40,  top:555 },
    pau   : { left:385, top:555 },
    sal   : { left:750, top:555 },
  };

  const W=1085, H=895;

  return (
    <div style={{ background:"#F8FAFC", minHeight:"100vh", padding:"22px 24px",
      fontFamily:"Arial,sans-serif" }}>

      {/* ── title ── */}
      <div style={{ textAlign:"center", marginBottom:18 }}>
        <div style={{ fontSize:20, fontWeight:800, color:"#1B3A5C", letterSpacing:-.5 }}>
          Diagrama Entidad-Relación — StaffFlow v2.0
        </div>
        <div style={{ fontSize:12, color:"#607D8B", marginTop:3 }}>
          Base de datos H2 (desarrollo/entrega)&nbsp;&nbsp;·&nbsp;&nbsp;MySQL 8.0 (producción futura)
        </div>
      </div>

      {/* ── diagram canvas ── */}
      <div style={{ position:"relative", width:W, height:H, margin:"0 auto" }}>

        {/* SVG arrows */}
        <svg style={{ position:"absolute", top:0, left:0, width:W, height:H, pointerEvents:"none" }}>
          <defs>
            <marker id="arr" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
              <polygon points="0 0,8 3,0 6" fill="#555"/>
            </marker>
          </defs>

          {/* usuarios 1:1 empleados */}
          <Arrow x1={308} y1={315} x2={385} y2={315} color="#1A6B4A"/>
          <Label x={310} y={308} text="1" bold color="#1A6B4A"/>
          <Label x={340} y={308} text="1:1" bold color="#1A6B4A"/>
          <Label x={367} y={308} text="1" bold color="#1A6B4A"/>

          {/* empleados 1:N planificacion */}
          <Arrow x1={652} y1={315} x2={750} y2={315} color="#7B1FA2"/>
          <Label x={655} y={308} text="1" bold color="#7B1FA2"/>
          <Label x={680} y={308} text="1:N" bold color="#7B1FA2"/>
          <Label x={853} y={290} text="N" bold color="#7B1FA2"/>

          {/* empleados 1:N fichajes */}
          <Arrow x1={520} y1={465} x2={175} y2={555} color="#B71C1C"/>
          <Label x={490} y={480} text="1:N" bold color="#B71C1C"/>
          <Label x={118} y={550} text="N" bold color="#B71C1C"/>

          {/* empleados 1:N pausas */}
          <Arrow x1={518} y1={465} x2={518} y2={555} color="#E65100"/>
          <Label x={523} y={510} text="1:N" bold color="#E65100"/>
          <Label x={523} y={550} text="N" bold color="#E65100"/>

          {/* empleados 1:N saldos */}
          <Arrow x1={520} y1={465} x2={870} y2={555} color="#00695C"/>
          <Label x={600} y={490} text="1:N" bold color="#00695C"/>
          <Label x={873} y={550} text="N" bold color="#00695C"/>

          {/* audit dashed: usuarios → fichajes */}
          <Arrow x1={175} y1={440} x2={165} y2={555} dashed color={AUDIT_COL}/>
          {/* audit dashed: usuarios → pausas */}
          <Arrow x1={200} y1={440} x2={500} y2={555} dashed color={AUDIT_COL}/>
          {/* audit dashed: usuarios → planificacion */}
          <Arrow x1={270} y1={300} x2={750} y2={330} dashed color={AUDIT_COL}/>

          {/* empleados 1 bottom label */}
          <Label x={500} y={462} text="1" bold color="#555"/>
          <Label x={500} y={474} text="1" bold color="#555"/>
          <Label x={530} y={462} text="1" bold color="#555"/>
        </svg>

        {/* ── tables ── */}

        {/* configuracion_empresa */}
        <Box title="configuracion_empresa" c={C.configuracion} style={P.cfg}
          fields={[
            { pk:true,  label:"id  INT AUTO_INCREMENT" },
            {           label:"nombre_empresa  VARCHAR(100)" },
            {           label:"cif  VARCHAR(20)  UNIQUE" },
            {           label:"direccion  TEXT" },
            {           label:"email  VARCHAR(100)" },
            {           label:"telefono  VARCHAR(20)" },
            {           label:"logo_path  VARCHAR(255)" },
          ]}
          notes={[
            { text:"Registro singleton (id siempre = 1)" },
          ]}
        />

        {/* usuarios — NEW fields highlighted */}
        <Box title="usuarios" c={C.usuarios} style={P.usr}
          fields={[
            { pk:true,  label:"id  INT AUTO_INCREMENT" },
            {           label:"username  VARCHAR(50)  UNIQUE" },
            {           label:"password_hash  VARCHAR(255)" },
            {           label:"email  VARCHAR(100)  UNIQUE" },
            {           label:"rol  [ADMIN | ENCARGADO | EMPLEADO]" },
            {           label:"activo  BOOLEAN  DEFAULT TRUE" },
            {           label:"fecha_creacion  TIMESTAMP" },
            { isNew:true, label:"reset_token  VARCHAR(255)  NULL" },
            { isNew:true, label:"reset_token_expiry  DATETIME  NULL" },
          ]}
          notes={[
            { idx:true,  text:"IDX: username*, activo" },
            {            text:"Autenticación y control de roles" },
            { idx:true,  text:"IDX: reset_token* (validación 30 min)" },
          ]}
        />

        {/* empleados */}
        <Box title="empleados" c={C.empleados} style={P.emp}
          fields={[
            { pk:true,  label:"id  INT AUTO_INCREMENT" },
            { fk:true,  label:"usuario_id → usuarios  UNIQUE" },
            {           label:"nombre / apellido1 / apellido2" },
            {           label:"dni  VARCHAR(20)  UNIQUE" },
            {           label:"nss  VARCHAR(20)  UNIQUE" },
            {           label:"fecha_alta  DATE" },
            {           label:"jornada_semanal_horas  INT" },
            {           label:"dias_vacaciones_anuales  INT" },
            {           label:"dias_asuntos_propios_anuales  INT" },
            {           label:"pin_terminal  CHAR(4)  UNIQUE" },
            {           label:"codigo_nfc  VARCHAR(50)  UNIQUE" },
            {           label:"activo  BOOLEAN" },
          ]}
          notes={[
            { idx:true, text:"IDX: pin_terminal* (búsqueda en cada fichaje)" },
            { idx:true, text:"IDX: dni, activo" },
          ]}
        />

        {/* planificacion_ausencias */}
        <Box title="planificacion_ausencias" c={C.planificacion} style={P.plan}
          fields={[
            { pk:true,    label:"id  INT AUTO_INCREMENT" },
            { fk:true,    label:"empleado_id → empleados  *** NULL OK ***" },
            {             label:"fecha  DATE" },
            {             label:"tipo_ausencia  [FESTIVO|VAC|ASUNTO|PERM...]" },
            {             label:"procesado  BOOLEAN  DEFAULT FALSE" },
            { audit:true, label:"FK usuario_id → usuarios  (auditoría)" },
            {             label:"observaciones  TEXT" },
            {             label:"fecha_creacion  TIMESTAMP" },
          ]}
          notes={[
            {            text:"NULL en empleado_id = festivo global (todos)" },
            { idx:true,  text:"IDX: (fecha, procesado)* — proceso diario 00:01" },
            { idx:true,  text:"UNIQUE: (empleado_id, fecha)" },
          ]}
        />

        {/* fichajes */}
        <Box title="fichajes" c={C.fichajes} style={P.fich}
          fields={[
            { pk:true,    label:"id  INT AUTO_INCREMENT" },
            { fk:true,    label:"empleado_id → empleados" },
            {             label:"fecha  DATE" },
            {             label:"tipo  [NORMAL|VAC|FESTIVO|BAJA...]" },
            {             label:"hora_entrada  DATETIME  NULL" },
            {             label:"hora_salida  DATETIME  NULL" },
            {             label:"total_pausas_minutos  INT" },
            {             label:"jornada_efectiva_minutos  INT  (ceil)" },
            { audit:true, label:"FK usuario_id → usuarios  (auditoría)" },
            {             label:"observaciones  TEXT" },
          ]}
          notes={[
            { idx:true, text:"UNIQUE: (empleado_id, fecha) — 1 fichaje/día*" },
            { idx:true, text:"IDX: empleado_id, fecha, tipo" },
          ]}
        />

        {/* pausas */}
        <Box title="pausas" c={C.pausas} style={P.pau}
          fields={[
            { pk:true,    label:"id  INT AUTO_INCREMENT" },
            { fk:true,    label:"empleado_id → empleados" },
            {             label:"fecha  DATE" },
            {             label:"hora_inicio  DATETIME" },
            {             label:"hora_fin  DATETIME  NULL  (activa)" },
            {             label:"duracion_minutos  INT  (floor)" },
            {             label:"tipo_pausa  [COMIDA|DESC|AUS_RET|OTROS]" },
            { audit:true, label:"FK usuario_id → usuarios  (auditoría)" },
          ]}
          notes={[
            { idx:true, text:"IDX: (empleado_id, fecha)*" },
            {           text:"AUS_RETRIBUIDA no descuenta jornada" },
          ]}
        />

        {/* saldos_anuales */}
        <Box title="saldos_anuales" c={C.saldos} style={P.sal}
          fields={[
            { pk:true, label:"id  INT AUTO_INCREMENT" },
            { fk:true, label:"empleado_id → empleados" },
            {          label:"anio  INT" },
            {          label:"dias_trabajados  INT" },
            {          label:"dias_vacaciones_derecho_anio  INT" },
            {          label:"dias_vacaciones_pendientes_anterior  INT" },
            {          label:"dias_vacaciones_consumidos  INT" },
            {          label:"dias_vacaciones_disponibles  INT" },
            {          label:"dias_asuntos_propios_*  INT  (x4)" },
            {          label:"saldo_horas  DECIMAL(10,2)" },
            {          label:"calculado_hasta_fecha  DATE" },
          ]}
          notes={[
            { idx:true, text:"UNIQUE: (empleado_id, anio)* — 1 registro/año" },
            { idx:true, text:"IDX: anio" },
          ]}
        />

      </div>{/* end canvas */}

      {/* ── legend ── */}
      <div style={{
        width:W, margin:"14px auto 0",
        borderTop:"1px solid #CFD8DC", paddingTop:10,
        display:"flex", gap:24, flexWrap:"wrap",
        fontSize:11, color:"#455A64",
      }}>
        <span><b style={{color:"#1B3A5C"}}>PK</b> = Clave primaria</span>
        <span><b style={{color:FK_COL}}>FK</b> = Clave foránea</span>
        <span><b>*</b> = Índice de rendimiento</span>
        <span style={{display:"flex",alignItems:"center",gap:5}}>
          <svg width="30" height="8"><line x1="0" y1="4" x2="30" y2="4" stroke="#555" strokeWidth="2"/></svg>
          Relación FK
        </span>
        <span style={{display:"flex",alignItems:"center",gap:5}}>
          <svg width="30" height="8"><line x1="0" y1="4" x2="30" y2="4" stroke={AUDIT_COL} strokeWidth="1.5" strokeDasharray="5,3"/></svg>
          Auditoría (usuario_id)
        </span>
        <span><b style={{color:NEW_COL}}>Naranja</b> = Campos nuevos (reset_token)</span>
        <span><b style={{color:NOTE_COL}}>*** NULL</b> = festivo global (todos los empleados)</span>
      </div>

      <div style={{
        width:W, margin:"7px auto 0",
        fontSize:10, color:"#78909C", fontStyle:"italic",
      }}>
        Nota: En H2, los campos ENUM se almacenan como VARCHAR mediante @Enumerated(EnumType.STRING).
        El comportamiento funcional es idéntico al de MySQL con tipos ENUM nativos.
        Los campos <span style={{color:NEW_COL,fontWeight:600}}>reset_token</span> y <span style={{color:NEW_COL,fontWeight:600}}>reset_token_expiry</span> son NULL en condiciones normales;
        sólo tienen valor durante un proceso de recuperación de contraseña activo (ventana de 30 minutos).
      </div>

    </div>
  );
}
