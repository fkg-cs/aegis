import { useState, useEffect, useRef } from 'react';
import axios from 'axios';
import keycloak from './keycloak';

// --- CONFIGURAZIONE AXIOS ---
const api = axios.create({ baseURL: 'https://localhost:8443/api/intel' });

api.interceptors.request.use(async (config) => {
    if (keycloak.authenticated) {
        try {
            await keycloak.updateToken(30);
            config.headers.Authorization = `Bearer ${keycloak.token}`;
        } catch (error) { keycloak.login(); }
    }
    return config;
}, error => Promise.reject(error));

// --- PALETTE COLORI DINAMICA ---
const COLORS_THEMES = {
    GOD: {
        bg: '#050505', card: 'rgba(15, 10, 20, 0.85)', text: '#a2a2a2',
        primary: '#d946ef', accent: '#8b5cf6', border: '#4c1d95',
        success: '#00ff41', danger: '#ff003c', warning: '#facc15'
    },
    SUPERVISOR: {
        bg: '#020617', card: 'rgba(20, 15, 10, 0.85)', text: '#94a3b8',
        primary: '#f59e0b', accent: '#d97706', border: '#78350f',
        success: '#10b981', danger: '#ef4444', warning: '#eab308'
    },
    AGENT: {
        bg: '#02040a', card: 'rgba(10, 15, 25, 0.85)', text: '#94a3b8',
        primary: '#06b6d4', accent: '#3b82f6', border: '#1e3a8a',
        success: '#22c55e', danger: '#f43f5e', warning: '#eab308'
    }
};

const STATUS_BADGES = {
    DRAFT: { bg: 'transparent', color: '#0ea5e9', border: '#0ea5e9' },
    STANDBY: { bg: 'transparent', color: '#eab308', border: '#eab308' },
    ACTIVE: { bg: 'transparent', color: '#f97316', border: '#f97316' },
    COMPLETED: { bg: 'transparent', color: '#22c55e', border: '#22c55e' },
    ABORTED: { bg: 'transparent', color: '#ef4444', border: '#ef4444' }
};

const sanitizeInput = (input) => {
    if (!input) return "";
    return input.replace(/[<>]/g, "");
};

function App() {
    // --- STATI ---
    const [activeTab, setActiveTab] = useState('missions');
    const detailsRef = useRef(null);
    const agentDetailsRef = useRef(null);
    const logsEndRef = useRef(null);
    const [isLoading, setIsLoading] = useState(false);
    const [toast, setToast] = useState({ show: false, message: '', type: 'info' });
    const [systemLogs, setSystemLogs] = useState([]);

    const [missionId, setMissionId] = useState('');
    const [result, setResult] = useState(null);
    const [viewError, setViewError] = useState(null);
    const [newMission, setNewMission] = useState({ description: '', geographicZone: '', clearanceLevel: 0 });
    const [attachmentFile, setAttachmentFile] = useState(null);
    const [createStatus, setCreateStatus] = useState('');
    const [isCreating, setIsCreating] = useState(false);
    const [newNote, setNewNote] = useState('');
    const [agentToAdd, setAgentToAdd] = useState('');
    const [agentSuggestions, setAgentSuggestions] = useState([]);

    const [adminAgents, setAdminAgents] = useState([]);
    const [selectedAgent, setSelectedAgent] = useState(null);
    const [allMissions, setAllMissions] = useState([]);

    const token = keycloak.tokenParsed || {};
    const codeName = token.code_name || "Unknown";
    const matricola = token.matricola || "N/D";

    // FIX 1: Inizializzazione sicura dello stato clearance
    const [userClearance, setUserClearance] = useState(() => {
        // Tenta di leggere subito dal token se disponibile
        return (token.clearance_level !== undefined) ? parseInt(token.clearance_level) : 0;
    });

    const isSuperSupervisor = token.realm_access?.roles.includes('SUPER_SUPERVISOR');
    const isSupervisor = token.realm_access?.roles.includes('SUPERVISOR');

    let THEME = COLORS_THEMES.AGENT;
    if (isSuperSupervisor) THEME = COLORS_THEMES.GOD;
    else if (isSupervisor) THEME = COLORS_THEMES.SUPERVISOR;

    const addLog = (action, detail = "") => {
        const timestamp = new Date().toISOString().split('T')[1].slice(0, -1);
        const newLog = `[${timestamp}] ${action.toUpperCase()} >> ${detail}`;
        setSystemLogs(prev => [...prev.slice(-15), newLog]);
    };

    useEffect(() => { logsEndRef.current?.scrollIntoView({ behavior: "smooth" }); }, [systemLogs]);

    // SCROLL AUTOMATICO AL DOSSIER
    useEffect(() => {
        if (selectedAgent && agentDetailsRef.current) {
            setTimeout(() => {
                agentDetailsRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }, 100);
        }
    }, [selectedAgent]);

    const showToast = (message, type = 'info') => {
        setToast({ show: true, message, type });
        setTimeout(() => setToast(prev => ({ ...prev, show: false })), 3000);
        addLog("ALERT", `${type.toUpperCase()}: ${message}`);
    };

    // --- INIT & POLLING ---
    useEffect(() => {
        let pollingInterval;
        if (keycloak.authenticated) {
            addLog("INIT", "SECURE CONNECTION ESTABLISHED");

            // FIX 2: Aggiornamento forzato stato clearance dal token Keycloak (senza aspettare backend)
            if (keycloak.tokenParsed && keycloak.tokenParsed.clearance_level !== undefined) {
                console.log("Token Clearance Found:", keycloak.tokenParsed.clearance_level);
                setUserClearance(parseInt(keycloak.tokenParsed.clearance_level));
            }

            api.get('/missions').catch(() => { });

            // Sincronizzazione col backend (conferma)
            api.get('/agents/me').then(res => {
                if (res.data && res.data.clearanceLevel !== undefined) {
                    setUserClearance(res.data.clearanceLevel);
                }
                addLog("SYNC", `CLEARANCE LEVEL ${res.data.clearanceLevel} CONFIRMED`);
            }).catch(err => console.log("Error syncing profile", err));

            if (isSuperSupervisor) {
                fetchAdminData(false);
                pollingInterval = setInterval(() => { fetchAdminData(true); }, 2000);
            }
        }
        return () => { if (pollingInterval) clearInterval(pollingInterval); };
    }, [isSuperSupervisor]);

    const fetchAdminData = async (isBackground = false) => {
        if (!isBackground) { setIsLoading(true); addLog("ROOT_ACCESS", "DUMPING DATABASE..."); }

        try {
            const agentsRes = await api.get('/admin/agents');
            setAdminAgents(agentsRes.data.sort((a, b) => a.username.localeCompare(b.username)));
        } catch (err) { if (!isBackground) addLog("ERROR", "AGENT FETCH FAIL"); }

        try {
            const missionsRes = await api.get('/missions');
            const sortedMissions = missionsRes.data.sort((a, b) => a.id.localeCompare(b.id));
            setAllMissions(sortedMissions);
            if (!isBackground) addLog("DATA", `RETRIEVED ${missionsRes.data.length} RECORDS`);
        } catch (err) { if (!isBackground) addLog("ERROR", "MISSION FETCH FAIL"); }

        try {
            const logsRes = await api.get('/audit');
            const historyLogs = logsRes.data.map(l => {
                const time = new Date(l.timestamp).toISOString().split('T')[1].slice(0, -1);
                return `[${time}] ${l.actor.toUpperCase()} :: ${l.action} >> ${l.details}`;
            });
            setSystemLogs(historyLogs.reverse());
        } catch (err) { }

        if (!isBackground) setIsLoading(false);
    };

    // --- OPERAZIONI ---
    const fetchMission = async (idOverride) => {
        const targetId = idOverride || missionId;
        if (!targetId) return;
        setIsLoading(true); setResult(null); setViewError(null);
        addLog("SEARCH", `TARGET UUID: ${targetId}`);
        try {
            const response = await api.get(`/missions/${targetId}`);
            setResult(response.data);
            addLog("SUCCESS", "PACKET DECRYPTED");
            setTimeout(() => detailsRef.current?.scrollIntoView({ behavior: 'smooth' }), 100);
        } catch (err) { handleError(err); }
        finally { setIsLoading(false); }
    };

    const handleCreateAndUpload = async () => {
        setIsCreating(true); setCreateStatus("ENCRYPTING...");
        addLog("UPLOAD", "INITIATING NEW ORDER");
        try {
            const response = await api.post('/missions', newMission);
            const newId = response.data.id;
            if (attachmentFile) {
                setCreateStatus("UPLOADING BINARY...");
                const formData = new FormData();
                formData.append("file", attachmentFile);
                await api.post(`/missions/${newId}/attachment`, formData, { headers: { 'Content-Type': 'multipart/form-data' } });
            }
            showToast("Missione creata e cifrata", "success");
            setCreateStatus("");
            setMissionId(newId);
            setNewMission({ description: '', geographicZone: '', clearanceLevel: 0 });
            setAttachmentFile(null);
            await fetchMission(newId);
            if (isSuperSupervisor) await fetchAdminData(false);
        } catch (err) {
            const msg = err.response?.data || "";
            if (msg.includes("VIOLAZIONE")) showToast("BLOCCO SICUREZZA: " + msg, "error");
            else handleError(err);
        }
        finally { setIsCreating(false); setCreateStatus(""); }
    };

    const handleDownload = async () => {
        if (!result?.attachmentFilename) return;
        setIsLoading(true);
        addLog("DL", `RETRIEVING ASSET: ${result.attachmentFilename}`);
        try {
            const response = await api.get(`/missions/${result.id}/attachment`, { responseType: 'blob' });
            const fileURL = window.URL.createObjectURL(new Blob([response.data], { type: 'application/pdf' }));
            window.open(fileURL, '_blank');
            showToast("Asset recuperato", "success");
        } catch (err) { showToast("Asset non disponibile", "error"); }
        finally { setIsLoading(false); }
    };

    const handleAddNote = async () => {
        if (!newNote.trim()) return;
        setIsLoading(true);
        addLog("MSG", "APPENDING TO LEDGER");
        try {
            const response = await api.post(`/missions/${result.id}/notes`, newNote, { headers: { 'Content-Type': 'text/plain' } });
            setResult(response.data); setNewNote('');
            showToast("Log aggiornato", "success");
        } catch (err) { showToast("Errore scrittura", "error"); }
        finally { setIsLoading(false); }
    };

    const updateStatus = async (targetId, newStatus) => {
        setIsLoading(true);
        const idToUpdate = targetId || result?.id;
        addLog("STATUS", `MOD ${idToUpdate} -> ${newStatus}`);
        try {
            const response = await api.patch(`/missions/${idToUpdate}/status?status=${newStatus}`);
            if (result && result.id === idToUpdate) { setResult(response.data); }
            if (isSuperSupervisor) await fetchAdminData(false);
            showToast(`State updated -> ${newStatus}`, "success");
        } catch (err) { showToast("Errore aggiornamento", "error"); }
        finally { setIsLoading(false); }
    };

    const addAgent = async () => {
        if (!agentToAdd) return;
        setIsLoading(true);
        addLog("AUTH", `GRANTING ACCESS TO ${agentToAdd}`);
        try {
            const response = await api.post(`/missions/${result.id}/agents?agentId=${agentToAdd}`);
            setResult(response.data); setAgentToAdd(''); setAgentSuggestions([]);
            showToast("Accesso concesso", "success");
        } catch (err) {
            const msg = err.response?.data || "";
            if (msg.includes("VIOLAZIONE")) showToast("BLOCCO SICUREZZA: " + msg, "error");
            else showToast("Errore concessione", "error");
        } finally { setIsLoading(false); }
    };

    const changeAgentClearance = async (username, newLevel) => {
        addLog("ADMIN", `MOD CLEARANCE ${username} -> ${newLevel}`);
        try {
            await api.patch(`/admin/agents/${username}/clearance?newLevel=${newLevel}`);
            showToast(`Clearance ${username} -> LIV ${newLevel}`, "success");
        } catch (err) { showToast("Errore aggiornamento", "error"); }
    };

    const handleSearchAgent = async (value) => {
        const cleanValue = sanitizeInput(value);
        setAgentToAdd(cleanValue);
        if (cleanValue.length < 2) { setAgentSuggestions([]); return; }
        try {
            const res = await api.get(`/agents/search?query=${cleanValue}`);
            setAgentSuggestions(res.data);
        } catch (err) { }
    };

    const handleError = (err) => {
        const code = err.response?.status || 500;
        addLog("ERR", `EXCEPTION CODE ${code}`);
        if (code === 403) showToast("ACCESSO NEGATO", "error");
        else if (code === 404) showToast("TARGET NON TROVATO", "warning");
        else {
            const errMsg = typeof err.response?.data === 'string' ? err.response.data : 'Critical Failure';
            showToast(`⚠️ SYSTEM FAILURE: ${errMsg}`, "error");
        }
    };

    const openAgentDossier = (agent) => {
        setSelectedAgent(agent);
        addLog("INTEL", `ACCESSING DOSSIER: ${agent.username.toUpperCase()}`);
    };

    // --- STILI CSS-IN-JS (Aggiornati) ---
    const styles = {
        container: { minHeight: '100vh', background: THEME.bg, backgroundImage: 'linear-gradient(rgba(0, 255, 0, 0.03) 1px, transparent 1px), linear-gradient(90deg, rgba(0, 255, 0, 0.03) 1px, transparent 1px)', backgroundSize: '40px 40px', fontFamily: "'Share Tech Mono', 'Consolas', monospace", color: THEME.text, padding: '20px', paddingBottom: '160px', transition: 'all 0.3s ease', position: 'relative', overflowX: 'hidden' },
        hudBar: { display: 'flex', justifyContent: 'space-between', borderBottom: `1px solid ${THEME.primary}`, marginBottom: '20px', paddingBottom: '10px', fontSize: '0.8em', color: THEME.primary, textTransform: 'uppercase', letterSpacing: '0.2em', textShadow: `0 0 5px ${THEME.primary}` },
        header: { background: 'rgba(20, 20, 30, 0.6)', border: `1px solid ${THEME.border}`, borderLeft: `4px solid ${THEME.primary}`, backdropFilter: 'blur(5px)', color: 'white', padding: '15px 25px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '30px', boxShadow: `0 0 20px ${THEME.primary}20` },
        card: { background: THEME.card, backdropFilter: 'blur(10px)', padding: '25px', border: `1px solid ${THEME.border}`, boxShadow: '0 0 10px rgba(0,0,0,0.3)', marginBottom: '20px', position: 'relative', animation: 'glowPulse 4s infinite' },
        label: { display: 'block', fontSize: '0.7em', fontWeight: 'bold', color: THEME.primary, marginBottom: '6px', textTransform: 'uppercase', letterSpacing: '0.15em' },
        input: { width: '100%', padding: '12px', border: `1px solid ${THEME.border}`, background: 'rgba(0,0,0,0.3)', color: '#fff', fontSize: '0.95em', marginBottom: '15px', fontFamily: 'monospace', outline: 'none' },
        table: { width: '100%', borderCollapse: 'collapse', marginTop: '10px' },
        th: { textAlign: 'left', padding: '12px 15px', fontSize: '0.75em', color: THEME.accent, textTransform: 'uppercase', letterSpacing: '0.1em', borderBottom: `1px solid ${THEME.border}` },
        td: { padding: '15px', borderBottom: `1px solid ${THEME.border}`, fontSize: '0.9em', color: '#ccc' },

        // UPDATE: Bottoni più compatti e testo più grande
        cyberBtn: {
            background: 'rgba(0,0,0,0.3)',
            border: `1px solid ${THEME.border}`,
            color: THEME.text,
            padding: '8px 16px',
            cursor: 'pointer',
            fontWeight: 'bold',
            textTransform: 'uppercase',
            fontSize: '0.95em',
            letterSpacing: '0.1em',
            transition: 'all 0.2s',
            marginRight: '10px',
            position: 'relative',
            overflow: 'hidden'
        },
        cyberBtnActive: { background: `${THEME.primary}20`, borderColor: THEME.primary, color: '#fff', textShadow: `0 0 8px ${THEME.primary}` },

        loaderOverlay: { position: 'fixed', top: 0, left: 0, width: '100%', height: '100%', background: 'rgba(0,0,0,0.95)', display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', zIndex: 9999, color: THEME.success, fontFamily: 'monospace', letterSpacing: '0.2em' },
        toast: { position: 'fixed', bottom: '150px', right: '30px', background: 'rgba(0,0,0,0.9)', border: `1px solid ${toast.type === 'error' ? THEME.danger : THEME.success}`, color: toast.type === 'error' ? THEME.danger : THEME.success, padding: '15px 25px', boxShadow: '0 0 20px rgba(0,0,0,0.5)', zIndex: 10000, fontWeight: 'bold', fontSize: '0.9em', display: 'flex', alignItems: 'center', gap: '10px', fontFamily: 'monospace', animation: 'slideIn 0.3s cubic-bezier(0.16, 1, 0.3, 1)' },
        terminalPanel: { position: 'fixed', bottom: 0, left: 0, width: '100%', height: '140px', background: '#0a0a0f', borderTop: `1px solid ${THEME.border}`, padding: '10px', fontFamily: 'monospace', fontSize: '0.8em', color: '#6b7280', overflowY: 'auto', zIndex: 9000, boxShadow: '0 -5px 20px rgba(0,0,0,0.5)', display: 'block' }
    };

    const renderAgentDossier = () => (
        <div style={{ ...styles.card, borderTop: `4px solid ${THEME.primary}`, marginTop: '30px', padding: '30px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '30px', borderBottom: `1px dashed ${THEME.border}`, paddingBottom: '10px' }}>
                <div>
                    <h2 style={{ margin: 0, color: THEME.primary, fontSize: '1.8em', letterSpacing: '0.1em' }}>
                        {`// PERSONNEL_DOSSIER: ${selectedAgent.username.toUpperCase()}`}
                    </h2>
                    <div style={{ color: THEME.accent, fontSize: '0.9em', marginTop: '5px' }}>
                        STATUS: ACTIVE | CLEARANCE: L{selectedAgent.clearanceLevel}
                    </div>
                </div>
                <div style={{ textAlign: 'right' }}>
                    <div style={{ fontSize: '3em', color: THEME.border }}>👤</div>
                </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '30px' }}>
                <div>
                    <h4 style={{ color: THEME.text, borderBottom: `1px solid ${THEME.border}`, paddingBottom: '5px' }}>SERVICE_DATA</h4>
                    <div style={{ marginBottom: '15px' }}><span style={styles.label}>CODE_NAME</span><span style={{ color: '#fff', fontSize: '1.2em', fontWeight: 'bold' }}>{selectedAgent.codeName}</span></div>
                    <div style={{ marginBottom: '15px' }}><span style={styles.label}>MATRICOLA_ID</span><span style={{ fontFamily: 'monospace' }}>{selectedAgent.matricola}</span></div>
                    <div style={{ marginBottom: '15px' }}><span style={styles.label}>OFFICE_LOCATION</span><span>{selectedAgent.office || 'N/D'}</span></div>
                    <div style={{ marginBottom: '15px' }}><span style={styles.label}>DEPARTMENT</span><span>{selectedAgent.department || 'N/D'}</span></div>
                </div>
                <div>
                    <h4 style={{ color: THEME.danger, borderBottom: `1px solid ${THEME.danger}`, paddingBottom: '5px' }}>PERSONAL_DATA [CLASSIFIED]</h4>
                    <div style={{ marginBottom: '15px' }}><span style={styles.label}>FULL_NAME</span><span className="classified" style={{ color: '#fff' }}>{selectedAgent.fullName || 'UNKNOWN'}</span></div>
                    <div style={{ marginBottom: '15px' }}><span style={styles.label}>SECURE_EMAIL</span><span className="classified">{selectedAgent.email || 'UNKNOWN'}</span></div>
                    <div style={{ marginBottom: '15px' }}><span style={styles.label}>CONTACT_PHONE</span><span className="classified">{selectedAgent.phone || 'N/D'}</span></div>
                </div>
            </div>
            <div style={{ marginTop: '30px', textAlign: 'right' }}>
                <button onClick={() => setSelectedAgent(null)} style={{ ...styles.cyberBtn, borderColor: THEME.danger, color: THEME.danger }}>CLOSE_DOSSIER</button>
            </div>
        </div>
    );

    const renderMissionDetailCard = () => (
        <div style={{ ...styles.card, borderLeft: `4px solid ${result.clearanceLevel >= 2 ? THEME.danger : THEME.success}` }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '15px' }}>
                <h2 style={{ margin: 0, color: THEME.primary, letterSpacing: '-1px' }}>{`>> ID_${result.id}`}</h2>
                <span style={{ background: 'transparent', color: STATUS_BADGES[result.status]?.color, border: `1px solid ${STATUS_BADGES[result.status]?.border}`, padding: '5px 12px', fontWeight: 'bold', fontSize: '0.8em' }}>{`[${result.status}]`}</span>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px', background: 'rgba(255,255,255,0.03)', padding: '15px', borderRadius: '0', border: `1px solid ${THEME.border}`, marginBottom: '20px' }}>
                <div><span style={styles.label}>LOCATION</span><span style={{ fontWeight: 'bold' }}>{result.geographicZone}</span></div>
                <div><span style={styles.label}>MIN_CLEARANCE_REQUIRED</span><span style={{ fontWeight: 'bold', color: result.clearanceLevel >= 2 ? THEME.danger : THEME.success }}>LVL-{result.clearanceLevel}</span></div>
            </div>
            <div style={styles.label}>DESCRIPTION</div>
            <p style={{ fontFamily: 'monospace', fontSize: '1.1em', background: 'rgba(0,0,0,0.3)', padding: '10px', border: `1px solid ${THEME.border}`, color: '#d1d5db' }}>{result.description}</p>
            <div style={{ marginTop: '20px', paddingTop: '15px', borderTop: `1px solid ${THEME.border}`, display: 'flex', alignItems: 'center', gap: '10px' }}>
                <span style={{ fontSize: '1.5em' }}>ATTACHMENT</span>
                {result.attachmentFilename ? (<button onClick={handleDownload} disabled={isLoading} style={{ background: 'none', border: 'none', padding: 0, color: THEME.accent, fontWeight: 'bold', cursor: 'pointer', textDecoration: 'underline', fontSize: '1em', fontFamily: 'monospace' }}>{result.attachmentFilename}</button>) : <span style={{ color: '#64748b', fontStyle: 'italic' }}>NO_DATA_ATTACHED</span>}
            </div>
            <div style={{ marginTop: '25px', borderTop: `1px solid ${THEME.border}`, paddingTop: '15px' }}>
                <h4 style={{ margin: '0 0 15px 0', color: THEME.primary, fontSize: '0.9em', textTransform: 'uppercase', letterSpacing: '0.1em' }}>{'> ENCRYPTED_CHAT'}</h4>
                <div style={{ background: 'rgba(0,0,0,0.5)', border: `1px solid ${THEME.border}`, height: '200px', overflowY: 'auto', padding: '15px', marginBottom: '10px' }}>
                    {result.notes?.length > 0 ? result.notes.map(n => (
                        <div key={n.id} style={{ marginBottom: '10px', borderBottom: `1px dashed ${THEME.border}`, paddingBottom: '5px' }}>
                            <div style={{ fontSize: '0.75em', color: '#64748b', display: 'flex', justifyContent: 'space-between' }}>
                                <span style={{ fontWeight: 'bold', color: THEME.accent }}>{`<${n.authorCodeName}>`}</span>
                                <span>{new Date(n.timestamp).toISOString().split('T')[1].substring(0, 8)}</span>
                            </div>
                            <div style={{ fontSize: '0.9em', color: THEME.text, whiteSpace: 'pre-wrap', marginTop: '2px' }}>{n.content}</div>
                        </div>
                    )) : <div style={{ textAlign: 'center', color: '#475569', marginTop: '80px' }}>WAITING FOR COMMUNICATION...</div>}
                </div>
                <div style={{ display: 'flex', gap: '10px' }}>
                    <textarea placeholder="Write message here..." value={newNote} onChange={e => setNewNote(sanitizeInput(e.target.value))} style={{ ...styles.input, marginBottom: 0, resize: 'none', height: '50px' }} />
                    <button onClick={handleAddNote} disabled={isLoading} style={{ background: THEME.primary, color: '#000', border: 'none', padding: '0 20px', cursor: 'pointer', fontWeight: 'bold', opacity: isLoading ? 0.5 : 1 }}>SEND</button>
                </div>
            </div>
            <div style={{ marginTop: '25px', borderTop: `1px solid ${THEME.border}`, paddingTop: '15px' }}>
                <h4 style={{ margin: '0 0 10px 0', color: '#64748b', fontSize: '0.9em', textTransform: 'uppercase', letterSpacing: '0.1em' }}>👥 MISSION_PERSONNEL</h4>
                <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap', marginTop: '5px' }}>
                    {result.assignedAgentsDetails?.length > 0 ? result.assignedAgentsDetails.map((agent, idx) => (
                        <div key={idx} style={{ background: 'rgba(255,255,255,0.02)', padding: '10px', border: `1px solid ${THEME.border}`, fontSize: '0.9em', color: THEME.text, display: 'flex', flexDirection: 'column', gap: '4px', minWidth: '200px' }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: `1px solid ${THEME.border}`, paddingBottom: '5px', marginBottom: '2px' }}>
                                <span style={{ fontWeight: 'bold', fontSize: '1.1em', color: THEME.accent }}>{agent.codeName}</span>
                                {agent.department && <span style={{ fontSize: '0.75em', background: '#1e293b', padding: '2px 6px', borderRadius: '4px' }}>{agent.department}</span>}
                            </div>
                            {agent.fullName && <div style={{ color: '#94a3b8' }}>FULL NAME: <strong className="classified">{agent.fullName}</strong></div>}
                            <div style={{ color: '#64748b', fontSize: '0.85em' }}>MAIL: <span className="classified">{agent.email || 'N/D'}</span></div>
                        </div>
                    )) : <span style={{ fontStyle: 'italic', color: '#64748b' }}>// NO_AGENTS_ASSIGNED_YET</span>}
                </div>
            </div>
            {(isSupervisor || isSuperSupervisor) && (
                <div style={{ marginTop: '25px', background: `${THEME.primary}0D`, padding: '15px', border: `1px solid ${THEME.primary}` }}>
                    <h4 style={{ margin: '0 0 10px 0', color: THEME.primary, fontSize: '0.9em', textTransform: 'uppercase', letterSpacing: '0.1em' }}>MISSION_COMMAND_CONSOLE</h4>
                    <div style={{ display: 'flex', gap: '15px' }}>
                        <div style={{ flex: 1 }}>
                            <label style={{ ...styles.label, marginBottom: '2px' }}>MODIFY STATUS</label>
                            <select disabled={isLoading} style={{ ...styles.input, padding: '8px', marginBottom: 0 }} onChange={(e) => updateStatus(null, e.target.value)} value={result.status}>
                                {Object.keys(STATUS_BADGES).map(s => <option key={s} value={s}>{s}</option>)}
                            </select>
                        </div>
                        <div style={{ flex: 1 }}>
                            <label style={{ ...styles.label, marginBottom: '2px' }}>ADD AGENT</label>
                            <div style={{ display: 'flex', gap: '5px' }}>
                                <input list="agent-list" style={{ ...styles.input, padding: '8px', marginBottom: 0 }} placeholder="Search..." value={agentToAdd} onChange={e => handleSearchAgent(e.target.value)} />
                                <datalist id="agent-list">{agentSuggestions.map(a => <option key={a.username} value={a.username}>{a.username} • {a.codeName}</option>)}</datalist>
                                <button onClick={addAgent} disabled={isLoading} style={{ background: THEME.success, color: '#000', border: 'none', cursor: 'pointer', padding: '0 15px', fontWeight: 'bold' }}>+</button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );

    return (
        <div style={styles.container}>
            <style>{`
        @keyframes slideIn { from { transform: translateY(100%); opacity: 0; } to { transform: translateY(0); opacity: 1; } }
        @keyframes glowPulse { 0% { box-shadow: 0 0 5px ${THEME.primary}20; } 50% { box-shadow: 0 0 15px ${THEME.primary}50; } 100% { box-shadow: 0 0 5px ${THEME.primary}20; } }
        @keyframes loading { 0% { opacity:0.2 } 50% { opacity:1 } 100% { opacity:0.2 } }
        @keyframes scanline { 0% { transform: translateY(-100%); } 100% { transform: translateY(100%); } }
        .crt-overlay { position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; background: linear-gradient(rgba(18, 16, 16, 0) 50%, rgba(0, 0, 0, 0.25) 50%), linear-gradient(90deg, rgba(255, 0, 0, 0.06), rgba(0, 255, 0, 0.02), rgba(0, 0, 255, 0.06)); background-size: 100% 2px, 3px 100%; pointer-events: none; z-index: 99998; opacity: 0.4; }
        .scanline { width: 100%; height: 5px; background: rgba(0, 255, 0, 0.1); position: fixed; z-index: 99999; animation: scanline 8s linear infinite; pointer-events: none; }
      `}</style>

            <div className="crt-overlay"></div>
            <div className="scanline"></div>

            {isLoading && <div style={styles.loaderOverlay}>
                <div style={{ marginBottom: '20px' }}>PROCESSING...</div>
                <div style={{ width: '200px', height: '2px', background: '#333' }}>
                    <div style={{ width: '50%', height: '100%', background: THEME.primary, animation: 'loading 1s infinite' }}></div>
                </div>
            </div>}

            {toast.show && (<div style={styles.toast}><span>{toast.type === 'error' ? '💀' : (toast.type === 'success' ? '⚡' : 'ℹ️')}</span>{toast.message}</div>)}

            <div style={{ maxWidth: '1200px', margin: '0 auto', position: 'relative', zIndex: 1 }}>
                <div style={styles.hudBar}><span>NET: <span style={{ color: THEME.success }}>SECURE</span></span><span></span><span>LOGGED AS: {codeName}</span></div>
                <header style={styles.header}>
                    <div><h2 style={{ margin: 0, fontSize: '2em', letterSpacing: '0.1em', textShadow: `0 0 10px ${THEME.primary}` }}>{isSuperSupervisor ? 'AEGIS // ROOT_ACCESS' : (isSupervisor ? 'AEGIS // COMMAND' : 'AEGIS // FIELD_OP')}</h2></div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
                        <div style={{ textAlign: 'right', fontSize: '0.9em' }}><div style={{ fontWeight: 'bold' }}>#{matricola}</div><div style={{ opacity: 0.8, color: THEME.accent }}>LIV-{userClearance}</div></div>
                        <button onClick={() => keycloak.logout()} style={{ background: THEME.danger, color: 'white', border: 'none', padding: '8px 15px', cursor: 'pointer', fontFamily: 'inherit', fontWeight: 'bold' }}>[ LOGOUT ]</button>
                    </div>
                </header>

                {isSuperSupervisor ? (
                    <div>
                        <div style={{ display: 'flex', marginBottom: '30px', borderBottom: `1px solid ${THEME.border}`, paddingBottom: '10px' }}>
                            <button onClick={() => { setActiveTab('agents'); addLog("NAV", "SWITCH AGENT DB"); }} style={activeTab === 'agents' ? { ...styles.cyberBtn, ...styles.cyberBtnActive } : styles.cyberBtn}>MANAGE AGENTS</button>
                            <button onClick={() => { setActiveTab('missions'); addLog("NAV", "SWITCH MISSION DB"); }} style={activeTab === 'missions' ? { ...styles.cyberBtn, ...styles.cyberBtnActive } : styles.cyberBtn}>MANAGE MISSIONS</button>
                            <button onClick={() => fetchAdminData(false)} style={{ marginLeft: 'auto', background: 'none', border: 'none', color: THEME.text, cursor: 'pointer', fontFamily: 'monospace', opacity: 0.7 }}>// SYNC</button>
                        </div>

                        {activeTab === 'agents' && (
                            <div style={styles.card}>
                                <h3 style={{ color: THEME.primary, borderBottom: `1px solid ${THEME.border}`, paddingBottom: '10px', marginTop: 0, letterSpacing: '0.2em' }}>:// AGENTS_REGISTRY</h3>
                                <table style={styles.table}>
                                    <thead>
                                        <tr>
                                            <th style={styles.th}>ID</th>
                                            <th style={styles.th}>CODENAME</th>
                                            <th style={{ ...styles.th, textAlign: 'center' }}>ACCESS_DATA</th>
                                            <th style={{ ...styles.th, textAlign: 'center' }}>CLEAREANCE</th>
                                            <th style={{ ...styles.th, textAlign: 'center' }}>MODIFY_CLEAREANCE</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {adminAgents.map(agent => (
                                            <tr key={agent.username} style={{ borderBottom: `1px solid ${THEME.border}` }}>
                                                <td style={styles.td}><strong style={{ color: THEME.text }}>{agent.username}</strong></td>
                                                <td style={styles.td}><span style={{ color: THEME.accent }}>{agent.codeName}</span></td>
                                                {/* COLONNA DOSSIER */}
                                                <td style={{ ...styles.td, textAlign: 'center' }}>
                                                    <button
                                                        onClick={() => openAgentDossier(agent)}
                                                        style={{ ...styles.cyberBtn, padding: '6px 12px', fontSize: '0.9em', width: 'auto', margin: 0, color: THEME.accent, borderColor: THEME.accent, display: 'inline-block' }}>
                                                        [ DOSSIER ]
                                                    </button>
                                                </td>
                                                <td style={{ ...styles.td, textAlign: 'center' }}><span style={{ color: THEME.success }}>{agent.clearanceLevel}</span></td>
                                                {/* COLONNA MODIFY CLEARANCE CENTRATA */}
                                                <td style={styles.td}>
                                                    <div style={{ display: 'flex', justifyContent: 'center' }}>
                                                        <select
                                                            value={agent.clearanceLevel}
                                                            onChange={(e) => {
                                                                const newLvl = parseInt(e.target.value);
                                                                setAdminAgents(prev => prev.map(a => a.username === agent.username ? { ...a, clearanceLevel: newLvl } : a));
                                                                changeAgentClearance(agent.username, e.target.value);
                                                            }}
                                                            style={{
                                                                ...styles.input,
                                                                padding: '5px 10px',
                                                                width: 'auto',
                                                                minWidth: '80px',
                                                                marginBottom: 0,
                                                                background: 'rgba(0,0,0,0.3)',
                                                                border: `1px solid ${THEME.primary}`,
                                                                color: THEME.primary,
                                                                fontSize: '1em',
                                                                fontWeight: 'bold',
                                                                cursor: 'pointer',
                                                                textAlign: 'center'
                                                            }}>
                                                            <option value="0">0</option><option value="1">1</option><option value="2">2</option><option value="3">3</option>
                                                        </select>
                                                    </div>
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>

                                <div ref={agentDetailsRef}>
                                    {selectedAgent && renderAgentDossier()}
                                </div>
                            </div>
                        )}

                        {activeTab === 'missions' && (
                            <div style={styles.card}>
                                <div className="radar-container"><div className="radar-sweep"></div></div>
                                <h3 style={{ color: THEME.primary, borderBottom: `1px solid ${THEME.border}`, paddingBottom: '10px', marginTop: 0, letterSpacing: '0.2em' }}>:// OPS_LIST</h3>
                                <table style={styles.table}>
                                    {/* INVERTED ORDER: DETAILS first, then STATUS */}
                                    <thead><tr><th style={styles.th}>UUID</th><th style={styles.th}>ZONE</th><th style={styles.th}>CLEAREANCE</th><th style={styles.th}>DETAILS</th><th style={{ ...styles.th, textAlign: 'center' }}>STATUS</th></tr></thead>
                                    <tbody>
                                        {allMissions.map(m => (
                                            <tr key={m.id} style={{ borderBottom: `1px solid ${THEME.border}` }}>
                                                <td style={styles.td}><code style={{ color: THEME.text }}>{m.id}</code></td>
                                                <td style={styles.td}><strong style={{ color: THEME.accent }}>{m.geographicZone}</strong></td>
                                                <td style={styles.td}><span style={{ color: m.clearanceLevel >= 2 ? THEME.danger : THEME.success }}>L_{m.clearanceLevel}</span></td>

                                                {/* DETAILS CELL (Moved here) */}
                                                <td style={styles.td}>
                                                    <button onClick={() => fetchMission(m.id)} disabled={isLoading} style={{ background: 'transparent', color: THEME.primary, border: `1px solid ${THEME.primary}`, padding: '4px 10px', cursor: 'pointer', fontFamily: 'monospace' }}>INSPECT</button>
                                                </td>

                                                {/* STATUS CELL (Moved here) */}
                                                <td style={styles.td}>
                                                    <div style={{ display: 'flex', justifyContent: 'center' }}>
                                                        <select value={m.status} onChange={(e) => updateStatus(m.id, e.target.value)} style={{ background: 'transparent', color: STATUS_BADGES[m.status]?.color, border: `1px solid ${STATUS_BADGES[m.status]?.border}`, padding: '2px', fontFamily: 'monospace', cursor: 'pointer' }}>
                                                            {Object.keys(STATUS_BADGES).map(s => <option key={s} value={s}>{s}</option>)}
                                                        </select>
                                                    </div>
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                                <div ref={detailsRef}></div>
                                {result && (<div style={{ marginTop: '30px', borderTop: `1px dashed ${THEME.border}`, paddingTop: '20px' }}><h3 style={{ color: THEME.primary, marginBottom: '20px', letterSpacing: '0.1em' }}>{`>> MISSION_DETAILS`}</h3>{renderMissionDetailCard()}</div>)}
                            </div>
                        )}
                    </div>
                ) : (

                    /* --- VISTA STANDARD (ORA CYBERPUNK) --- */
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                        <div style={{ ...styles.card, display: 'flex', gap: '10px', alignItems: 'center' }}>
                            <span style={{ fontSize: '1.5em', color: THEME.accent }}>🔍</span>
                            <input style={{ ...styles.input, marginBottom: 0, border: 'none', fontSize: '1.1em' }} placeholder="SEARCH MISSION BY UUID..." value={missionId} onChange={e => setMissionId(sanitizeInput(e.target.value))} />
                            <button onClick={() => fetchMission()} disabled={isLoading} style={{ ...styles.cyberBtn, ...styles.cyberBtnActive, marginRight: 0 }}>SEARCH</button>
                        </div>

                        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                            {isSupervisor && (
                                <div style={{ ...styles.card, borderTop: `4px solid ${THEME.accent}`, height: 'fit-content' }}>
                                    <h3 style={{ marginTop: 0, color: THEME.primary, letterSpacing: '0.1em' }}>:// NEW_MISSION</h3>

                                    {/* GRID LAYOUT PER BILANCIARE LOCATION E CLEARANCE */}
                                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 140px', gap: '20px' }}>
                                        <div>
                                            <label style={styles.label}>LOCATION</label>
                                            <input style={styles.input} placeholder="Insert mission location..." value={newMission.geographicZone} onChange={e => setNewMission({ ...newMission, geographicZone: sanitizeInput(e.target.value) })} />
                                        </div>
                                        <div>
                                            <label style={styles.label}>MIN_CLEARANCE</label>
                                            <input
                                                style={styles.input}
                                                type="number"
                                                min="0"
                                                max="3"
                                                value={newMission.clearanceLevel}
                                                onChange={e => {
                                                    let val = parseInt(e.target.value);
                                                    if (isNaN(val)) val = 0;
                                                    if (val < 0) val = 0;
                                                    if (val > 3) val = 3; // FORZA IL LIMITE MASSIMO SE L'UTENTE DIGITA 5
                                                    setNewMission({ ...newMission, clearanceLevel: val });
                                                }}
                                            />
                                        </div>
                                    </div>

                                    <label style={styles.label}>DESCRIPTION</label>
                                    <textarea style={{ ...styles.input, height: '55px', resize: 'vertical' }} placeholder="Insert mission description..." value={newMission.description} onChange={e => setNewMission({ ...newMission, description: sanitizeInput(e.target.value) })} />
                                    <label style={styles.label}>MISSION_ATTACHMENTS</label>
                                    <input type="file" accept=".pdf" style={{ marginBottom: '20px', color: THEME.text }} onChange={(e) => setAttachmentFile(e.target.files[0])} />
                                    <button onClick={handleCreateAndUpload} disabled={isCreating} style={{ ...styles.cyberBtn, width: '100%', background: THEME.primary, color: '#000' }}>
                                        {isCreating ? 'ENCRYPTING...' : 'CREATE MISSION'}
                                    </button>
                                    {createStatus && <div style={{ marginTop: '10px', fontSize: '0.9em', color: THEME.accent }}>{createStatus}</div>}
                                </div>
                            )}
                            <div>
                                {result ? renderMissionDetailCard() : (
                                    !viewError && <div style={{ textAlign: 'center', color: THEME.text, marginTop: '50px', fontFamily: 'monospace', opacity: 0.5 }}>{`// AEGIS PLATFORM TRADEMARK`}</div>
                                )}
                            </div>
                        </div>
                    </div>
                )}
            </div>

            {/* TERMINAL LOG CONSOLE (PER TUTTI ORA) */}
            <div style={styles.terminalPanel}>
                <div style={{ borderBottom: `1px solid ${THEME.border}`, marginBottom: '5px', color: '#fff', fontWeight: 'bold', display: 'flex', justifyContent: 'space-between' }}>
                    <span>{`>> SYSTEM_LOG_OF_${codeName.toUpperCase()}`}</span>
                    <span style={{ color: THEME.success }}>● ONLINE</span>
                </div>
                {systemLogs.map((log, i) => (
                    <div key={i} style={{ marginBottom: '2px', color: log.includes('ERR') || log.includes('FAIL') ? THEME.danger : '#6b7280' }}>
                        {log}
                    </div>
                ))}
                <div ref={logsEndRef} />
            </div>
        </div>
    );
}

export default App;
