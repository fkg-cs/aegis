package com.aegis.backend.service;

import com.aegis.backend.dto.AgentDisplayDTO;
import com.aegis.backend.dto.MissionDTO;
import com.aegis.backend.dto.NoteDTO;
import com.aegis.backend.model.AgentProfile;
import com.aegis.backend.model.Mission;
import com.aegis.backend.model.MissionNote;
import com.aegis.backend.model.MissionStatus;
import com.aegis.backend.repository.AgentProfileRepository;
import com.aegis.backend.repository.MissionNoteRepository;
import com.aegis.backend.repository.MissionRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.apache.tika.Tika;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class MissionService {

    private final MissionRepository repository;
    private final AgentProfileRepository agentRepository;
    private final MissionNoteRepository noteRepository;
    private final Path fileStorageLocation;

    // --- 🔐 CHIAVE DI CIFRATURA (AES-128) ---
    // --- 🔐 CHIAVE DI CIFRATURA (AES-128) ---
    // TODO: In produzione utilizzare variabili d'ambiente 
    private static final String SECRET_KEY = "AegisIntelSecret"; // 16 caratteri esatti = 128 bit
    private static final String ALGORITHM = "AES";

    private static final Pattern URL_PATTERN = Pattern.compile("(?i)\\b((?:https?://|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,4}/)(?:[^\\s()<>]+|\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\))+(?:\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\)|[^\\s`!()\\[\\]{};:'\".,<>?«»“”‘’]))");

    public MissionService(MissionRepository repository,
                          AgentProfileRepository agentRepository,
                          MissionNoteRepository noteRepository) {
        this.repository = repository;
        this.agentRepository = agentRepository;
        this.noteRepository = noteRepository;

        this.fileStorageLocation = Paths.get("uploads").toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Impossibile creare la cartella uploads.", ex);
        }
    }

    // --- 🛡️ METODI DI SICUREZZA (AES & HASH) ---

    private byte[] encrypt(byte[] data) {
        try {
            SecretKeySpec key = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("Errore critico durante la cifratura del file", e);
        }
    }

    private byte[] decrypt(byte[] data) {
        try {
            SecretKeySpec key = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("Errore decifratura: File corrotto o chiave errata.", e);
        }
    }

    private String calculateHash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(data);
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "HASH_ERROR";
        }
    }

    // --- 1. SYNC PROFILO ---
    @Transactional
    public void syncAgentProfile(String username, String codeName, String matricola, Integer tokenClearance, String email,
                                 String fullName, String phone, String office, String department) {
        AgentProfile agent = agentRepository.findById(username).orElse(new AgentProfile());
        if (agent.getUsername() == null) agent.setUsername(username);

        agent.setCodeName(codeName);
        agent.setMatricola(matricola);
        agent.setEmail(email);
        agent.setFullName(fullName);
        agent.setPhone(phone);
        agent.setOffice(office);
        agent.setDepartment(department);

        if (tokenClearance != null) {
            agent.setClearanceLevel(tokenClearance);
        } else if (agent.getClearanceLevel() == null) {
            agent.setClearanceLevel(0);
        }

        agentRepository.save(agent);
        agentRepository.flush();
    }

    public int getAgentClearance(String username) {
        return agentRepository.findById(username).map(AgentProfile::getClearanceLevel).orElse(0);
    }

    // --- 2. CREAZIONE ---
    public MissionDTO createMission(MissionDTO input, String ownerId) {
        Mission mission = new Mission();
        mission.setDescription(input.description());
        mission.setGeographicZone(input.geographicZone());
        mission.setClearanceLevel(input.clearanceLevel());
        mission.setStatus(MissionStatus.DRAFT);
        mission.setOwnerId(ownerId);
        mission.setAssignedAgentIds(input.assignedAgentIds() != null ? input.assignedAgentIds() : new HashSet<>());
        return mapToDTO(repository.save(mission), true);
    }

    // --- 3. LETTURA SINGOLA ---
    public MissionDTO getMission(UUID id, boolean isSupervisor) {
        return repository.findById(id)
                .map(m -> mapToDTO(m, isSupervisor))
                .orElseThrow(() -> new RuntimeException("Missione non trovata"));
    }

    // --- 4. LETTURA TUTTI ---
    public List<MissionDTO> getAllMissions(boolean isSupervisor) {
        return repository.findAll().stream()
                .map(m -> mapToDTO(m, isSupervisor))
                .collect(Collectors.toList());
    }

    // --- 5. AGGIORNAMENTO STATO ---
    public MissionDTO updateStatus(UUID id, MissionStatus newStatus) {
        Mission mission = repository.findById(id).orElseThrow(() -> new RuntimeException("Missione non trovata"));
        mission.setStatus(newStatus);
        return mapToDTO(repository.save(mission), true);
    }

    // --- 6. ASSEGNAZIONE AGENTE ---
    public MissionDTO addAgentToMission(UUID id, String agentUsername) {
        Mission mission = repository.findById(id).orElseThrow(() -> new RuntimeException("Missione non trovata"));
        AgentProfile agentCandidate = agentRepository.findById(agentUsername)
                .orElseThrow(() -> new RuntimeException("Agente non trovato nel registro."));

        int agentLevel = agentCandidate.getClearanceLevel() != null ? agentCandidate.getClearanceLevel() : 0;
        int missionLevel = mission.getClearanceLevel() != null ? mission.getClearanceLevel() : 0;

        if (agentLevel < missionLevel) {
            throw new SecurityException("VIOLAZIONE CLEARANCE: Livello insufficiente.");
        }

        if (mission.getAssignedAgentIds() == null) mission.setAssignedAgentIds(new HashSet<>());
        mission.getAssignedAgentIds().add(agentUsername);

        return mapToDTO(repository.save(mission), true);
    }

    // --- 7. UPLOAD FILE SICURO (HASH + AES) ---
    public MissionDTO uploadAttachment(UUID missionId, MultipartFile file) {
        Mission mission = repository.findById(missionId)
                .orElseThrow(() -> new RuntimeException("Missione non trovata"));

        try {
            // A. Validazione Tipo (Tika)
            Tika tika = new Tika();
            String detectedType = tika.detect(file.getInputStream());
            if (!"application/pdf".equals(detectedType)) {
                throw new SecurityException("VIOLAZIONE: Il file non è un PDF valido.");
            }

            // B. Lettura Bytes
            byte[] originalBytes = file.getBytes();

            // C. 🕵️‍♂️ INTEGRITY CHECK (SHA-256)
            String integrityHash = calculateHash(originalBytes);
            System.out.println(">>> [SECURITY AUDIT] File Upload Hash (SHA-256): " + integrityHash);

            // D. 🔒 CRITTOGRAFIA (AES-128)
            byte[] encryptedBytes = encrypt(originalBytes);

            // E. Scrittura su Disco (Salva solo i dati cifrati!)
            String filename = "SECURE_" + missionId + "_" + System.currentTimeMillis() + ".pdf";
            Path targetLocation = this.fileStorageLocation.resolve(filename);
            Files.write(targetLocation, encryptedBytes); // Usa write, non copy

            mission.setAttachmentFilename(filename);
            return mapToDTO(repository.save(mission), true);

        } catch (IOException e) {
            throw new RuntimeException("Errore salvataggio file sicuro", e);
        }
    }

    // --- 8. DOWNLOAD SICURO (DECIFRATURA + WATERMARK) ---
    public Resource loadFileWithWatermark(String filename, String userIdentity) {
        try {
            Path filePath = this.fileStorageLocation.resolve(filename).normalize();

            // A. Leggi file cifrato
            byte[] encryptedBytes = Files.readAllBytes(filePath);

            // B. 🔓 DECIFRA AL VOLO (In Memoria)
            byte[] decryptedBytes = decrypt(encryptedBytes);

            // C. Applica Watermark al file in chiaro
            try (PDDocument doc = PDDocument.load(decryptedBytes)) {
                for (PDPage page : doc.getPages()) {
                    PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true);
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 40);
                    PDExtendedGraphicsState graphicsState = new PDExtendedGraphicsState();
                    graphicsState.setNonStrokingAlphaConstant(0.2f);
                    cs.setGraphicsStateParameters(graphicsState);
                    cs.setNonStrokingColor(200, 0, 0);
                    float pageWidth = page.getMediaBox().getWidth();
                    float pageHeight = page.getMediaBox().getHeight();
                    float centerX = pageWidth / 2;
                    float centerY = pageHeight / 2;
                    cs.beginText();
                    cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(45), centerX - 150, centerY - 50));
                    cs.showText("RISERVATO: " + userIdentity);
                    cs.endText();
                    cs.close();
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                doc.save(out);
                return new ByteArrayResource(out.toByteArray());
            }
        } catch (IOException e) {
            throw new RuntimeException("Errore lettura/decifratura file", e);
        }
    }

    // --- 9. AGGIUNTA NOTA ---
    public MissionDTO addNote(UUID missionId, String content, String authorId) {
        if (URL_PATTERN.matcher(content).find()) {
            throw new SecurityException("VIOLAZIONE SICUREZZA: Non è consentito inserire link.");
        }
        MissionNote note = new MissionNote(content, authorId, missionId);
        noteRepository.save(note);
        return getMission(missionId, false);
    }

    // --- MAPPER ---
    private MissionDTO mapToDTO(Mission m, boolean requesterIsSupervisor) {
        List<AgentDisplayDTO> privacySafeAgents = new ArrayList<>();

        if (m.getAssignedAgentIds() != null) {
            for (String username : m.getAssignedAgentIds()) {
                AgentProfile profile = agentRepository.findById(username).orElse(null);
                String safeName = (profile != null) ? profile.getCodeName() : "Sconosciuto (" + username + ")";
                String email = (profile != null) ? profile.getEmail() : "N/D";
                String fullName = null;
                String phone = null;
                String office = null;
                String department = null;

                if (requesterIsSupervisor && profile != null) {
                    fullName = profile.getFullName();
                    phone = profile.getPhone();
                    office = profile.getOffice();
                    department = profile.getDepartment();
                }
                privacySafeAgents.add(new AgentDisplayDTO(safeName, "OPERATIVO", email, fullName, phone, office, department));
            }
        }

        List<NoteDTO> noteDTOs = noteRepository.findByMissionIdOrderByTimestampAsc(m.getId()).stream().map(n -> {
            String author = agentRepository.findById(n.getAuthorId()).map(AgentProfile::getCodeName).orElse("Agente");
            return new NoteDTO(n.getId().toString(), n.getContent(), author, n.getTimestamp());
        }).collect(Collectors.toList());

        return new MissionDTO(
                m.getId(), m.getDescription(), m.getGeographicZone(),
                m.getClearanceLevel(), m.getStatus(), m.getAttachmentFilename(),
                m.getAssignedAgentIds(),
                privacySafeAgents,
                noteDTOs
        );
    }
}