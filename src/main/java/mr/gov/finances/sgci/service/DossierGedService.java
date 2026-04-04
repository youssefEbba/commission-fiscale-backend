package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.*;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.*;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.web.dto.DocumentDto;
import mr.gov.finances.sgci.web.dto.DossierEtapeGed;
import mr.gov.finances.sgci.web.dto.DossierGedDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DossierGedService {

    private final DossierGedRepository dossierRepository;
    private final DemandeCorrectionRepository demandeCorrectionRepository;
    private final CertificatCreditRepository certificatCreditRepository;

    private final DocumentRepository documentRepository;
    private final DocumentCertificatCreditRepository documentCertificatCreditRepository;
    private final UtilisationCreditRepository utilisationCreditRepository;
    private final DocumentUtilisationCreditRepository documentUtilisationCreditRepository;
    private final TransfertCreditRepository transfertCreditRepository;
    private final DocumentTransfertCreditRepository documentTransfertCreditRepository;
    private final SousTraitanceRepository sousTraitanceRepository;
    private final DocumentSousTraitanceRepository documentSousTraitanceRepository;

    private final ClotureCreditRepository clotureCreditRepository;
    private final DocumentClotureCreditRepository documentClotureCreditRepository;
    private final AvenantRepository avenantRepository;
    private final DocumentAvenantRepository documentAvenantRepository;
    private final DocumentConventionRepository documentConventionRepository;
    private final DocumentMarcheRepository documentMarcheRepository;
    private final DocumentProjetRepository documentProjetRepository;
    private final ReferentielProjetRepository referentielProjetRepository;

    private final UtilisateurRepository utilisateurRepository;

    private static String generateReference() {
        return "DOSS-" + Instant.now().getEpochSecond() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Transactional
    public DossierGed ensureCreatedForDemandeCorrection(Long demandeCorrectionId) {
        if (demandeCorrectionId == null) {
            throw new RuntimeException("demandeCorrectionId manquant");
        }
        return dossierRepository.findByDemandeCorrectionId(demandeCorrectionId)
                .orElseGet(() -> {
                    DemandeCorrection dc = demandeCorrectionRepository.findById(demandeCorrectionId)
                            .orElseThrow(() -> new RuntimeException("Demande de correction non trouvée: " + demandeCorrectionId));
                    DossierGed d = DossierGed.builder()
                            .reference(generateReference())
                            .entreprise(dc.getEntreprise())
                            .demandeCorrection(dc)
                            .dateCreation(Instant.now())
                            .build();
                    return dossierRepository.save(d);
                });
    }

    @Transactional
    public void attachCertificatToDossier(Long demandeCorrectionId, Long certificatCreditId) {
        if (demandeCorrectionId == null || certificatCreditId == null) {
            return;
        }
        DossierGed dossier = ensureCreatedForDemandeCorrection(demandeCorrectionId);
        if (dossier.getCertificatCredit() != null && dossier.getCertificatCredit().getId() != null) {
            return;
        }
        CertificatCredit c = certificatCreditRepository.findById(certificatCreditId)
                .orElseThrow(() -> new RuntimeException("Certificat de crédit non trouvé: " + certificatCreditId));
        dossier.setCertificatCredit(c);
        dossierRepository.save(dossier);
    }

    @Transactional(readOnly = true)
    public List<DossierGedDto> findAll(AuthenticatedUser user) {
        List<DossierGed> list = resolveDossierList(user);
        return list.stream()
                .sorted(Comparator.comparing(DossierGed::getDateCreation, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DossierGedDto findById(Long id, AuthenticatedUser user) {
        DossierGed dossier = dossierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Dossier GED non trouvé: " + id));
        if (!canAccessDossier(dossier, user)) {
            throw new RuntimeException("Accès refusé: dossier hors périmètre");
        }
        return toDto(dossier);
    }

    private List<DossierGed> resolveDossierList(AuthenticatedUser user) {
        if (user == null || user.getUserId() == null) {
            return dossierRepository.findAll();
        }
        Utilisateur u = utilisateurRepository.findById(user.getUserId())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        Role role = u.getRole();

        if (role == Role.AUTORITE_CONTRACTANTE) {
            if (u.getAutoriteContractante() == null) {
                throw new RuntimeException("Aucune autorité contractante liée à l'utilisateur");
            }
            return dossierRepository.findAllByAutoriteContractanteId(u.getAutoriteContractante().getId());
        }

        if (role == Role.AUTORITE_UPM || role == Role.AUTORITE_UEP) {
            return dossierRepository.findAllByDelegueId(u.getId());
        }

        if (role == Role.ENTREPRISE) {
            if (u.getEntreprise() == null) {
                throw new RuntimeException("Aucune entreprise liée à l'utilisateur");
            }
            return dossierRepository.findByEntrepriseId(u.getEntreprise().getId());
        }

        return dossierRepository.findAll();
    }

    private boolean canAccessDossier(DossierGed dossier, AuthenticatedUser user) {
        if (dossier == null || dossier.getId() == null) {
            return false;
        }
        if (user == null || user.getUserId() == null) {
            return true;
        }
        Utilisateur u = utilisateurRepository.findById(user.getUserId()).orElse(null);
        if (u == null || u.getRole() == null) {
            return false;
        }

        if (u.getRole() == Role.ENTREPRISE) {
            Long userEntrepriseId = u.getEntreprise() != null ? u.getEntreprise().getId() : null;
            Long dossierEntrepriseId = dossier.getEntreprise() != null ? dossier.getEntreprise().getId() : null;
            return userEntrepriseId != null && userEntrepriseId.equals(dossierEntrepriseId);
        }

        // Pour les autres rôles, on réutilise le filtrage par liste
        return resolveDossierList(user).stream().anyMatch(d -> d.getId().equals(dossier.getId()));
    }

    private DossierGedDto toDto(DossierGed dossier) {
        Long entrepriseId = dossier.getEntreprise() != null ? dossier.getEntreprise().getId() : null;
        Long demandeCorrectionId = dossier.getDemandeCorrection() != null ? dossier.getDemandeCorrection().getId() : null;
        Long certificatId = dossier.getCertificatCredit() != null ? dossier.getCertificatCredit().getId() : null;

        List<DossierEtapeGed> etapes = buildEtapes(dossier);

        return DossierGedDto.builder()
                .id(dossier.getId())
                .reference(dossier.getReference())
                .entrepriseId(entrepriseId)
                .demandeCorrectionId(demandeCorrectionId)
                .certificatId(certificatId)
                .dateCreation(dossier.getDateCreation())
                .etapes(etapes)
                .build();
    }

    private List<DossierEtapeGed> buildEtapes(DossierGed dossier) {
        List<DossierEtapeGed> etapes = new ArrayList<>();

        etapes.add(step("DEMANDE_CORRECTION", "Demande de correction",
                mergeByDateDesc(documentsCorrection(dossier), documentsMarche(dossier))));
        etapes.add(step("TRAITEMENT_CORRECTION", "Traitement de la correction", List.of()));
        etapes.add(step("RETOUR_CORRECTION", "Retour de la correction", List.of()));

        etapes.add(step("DEMANDE_CREDIT_IMPOT", "Demande de crédit d'impôt",
                mergeByDateDesc(documentsConvention(dossier), documentsReferentielProjets(dossier))));
        etapes.add(step("EMISSION_CERTIFICAT", "Émission du certificat de crédit", documentsCertificat(dossier)));

        etapes.add(step("UTILISATION_DOUANE", "Utilisation du crédit – Douane", documentsUtilisation(dossier, TypeUtilisation.DOUANIER)));
        etapes.add(step("UTILISATION_TVA", "Utilisation du crédit – TVA", documentsUtilisation(dossier, TypeUtilisation.TVA_INTERIEURE)));

        etapes.add(step("MODIFICATION_AVENANT", "Modification / Avenant / Note", documentsAvenants(dossier)));

        etapes.add(step("TRANSFERT_CREDIT", "Transfert du crédit", documentsTransferts(dossier)));
        etapes.add(step("SOUS_TRAITANCE", "Sous-traitance", documentsSousTraitance(dossier)));
        etapes.add(step("CLOTURE_CREDIT", "Clôture du crédit", documentsCloture(dossier)));

        return etapes;
    }

    private List<DocumentDto> mergeByDateDesc(List<DocumentDto> first, List<DocumentDto> second) {
        List<DocumentDto> out = new ArrayList<>();
        if (first != null) {
            out.addAll(first);
        }
        if (second != null) {
            out.addAll(second);
        }
        out.sort(Comparator.comparing(DocumentDto::getDateUpload, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return out;
    }

    private DossierEtapeGed step(String code, String label, List<DocumentDto> documents) {
        return DossierEtapeGed.builder()
                .etape(code)
                .label(label)
                .documents(documents)
                .build();
    }

    private List<DocumentDto> documentsCorrection(DossierGed dossier) {
        if (dossier == null || dossier.getDemandeCorrection() == null || dossier.getDemandeCorrection().getId() == null) {
            return List.of();
        }
        return documentRepository.findByDemandeCorrectionId(dossier.getDemandeCorrection().getId()).stream()
                .map(this::toDocumentDto)
                .sorted(Comparator.comparing(DocumentDto::getDateUpload, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .collect(Collectors.toList());
    }

    private List<DocumentDto> documentsCertificat(DossierGed dossier) {
        if (dossier == null || dossier.getCertificatCredit() == null || dossier.getCertificatCredit().getId() == null) {
            return List.of();
        }
        Long id = dossier.getCertificatCredit().getId();
        return documentCertificatCreditRepository.findByCertificatCreditId(id).stream()
                .map(this::toDocumentDto)
                .sorted(Comparator.comparing(DocumentDto::getDateUpload, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .collect(Collectors.toList());
    }

    private List<DocumentDto> documentsUtilisation(DossierGed dossier, TypeUtilisation type) {
        if (dossier == null || dossier.getCertificatCredit() == null || dossier.getCertificatCredit().getId() == null) {
            return List.of();
        }
        Long certificatId = dossier.getCertificatCredit().getId();

        List<UtilisationCredit> utilisations = utilisationCreditRepository.findByCertificatCreditId(certificatId).stream()
                .filter(u -> u != null && u.getType() == type)
                .collect(Collectors.toList());

        List<DocumentDto> docs = new ArrayList<>();
        for (UtilisationCredit u : utilisations) {
            if (u.getId() == null) {
                continue;
            }
            documentUtilisationCreditRepository.findByUtilisationCreditId(u.getId())
                    .stream()
                    .map(this::toDocumentDto)
                    .forEach(docs::add);
        }

        docs.sort(Comparator.comparing(DocumentDto::getDateUpload, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return docs;
    }

    private List<DocumentDto> documentsTransferts(DossierGed dossier) {
        if (dossier == null || dossier.getCertificatCredit() == null || dossier.getCertificatCredit().getId() == null) {
            return List.of();
        }
        Long certificatId = dossier.getCertificatCredit().getId();
        List<DocumentDto> docs = new ArrayList<>();
        for (TransfertCredit transfert : transfertCreditRepository.findByCertificatCreditId(certificatId)) {
            if (transfert == null || transfert.getId() == null) {
                continue;
            }
            documentTransfertCreditRepository.findByTransfertCreditId(transfert.getId()).stream()
                    .map(this::toDocumentDto)
                    .forEach(docs::add);
        }
        docs.sort(Comparator.comparing(DocumentDto::getDateUpload, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return docs;
    }

    private List<DocumentDto> documentsSousTraitance(DossierGed dossier) {
        if (dossier == null || dossier.getCertificatCredit() == null || dossier.getCertificatCredit().getId() == null) {
            return List.of();
        }
        Long certificatId = dossier.getCertificatCredit().getId();
        SousTraitance st = sousTraitanceRepository.findByCertificatCreditId(certificatId).orElse(null);
        if (st == null || st.getId() == null) {
            return List.of();
        }
        return documentSousTraitanceRepository.findBySousTraitanceId(st.getId()).stream()
                .map(this::toDocumentDto)
                .sorted(Comparator.comparing(DocumentDto::getDateUpload, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .collect(Collectors.toList());
    }

    private DocumentDto toDocumentDto(Document d) {
        return DocumentDto.builder()
                .id(d.getId())
                .type(d.getType())
                .nomFichier(d.getNomFichier())
                .chemin(d.getChemin())
                .dateUpload(d.getDateUpload())
                .taille(d.getTaille())
                .version(d.getVersion())
                .actif(d.getActif())
                .build();
    }

    private DocumentDto toDocumentDto(DocumentCertificatCredit d) {
        return DocumentDto.builder()
                .id(d.getId())
                .type(d.getType())
                .nomFichier(d.getNomFichier())
                .chemin(d.getChemin())
                .dateUpload(d.getDateUpload())
                .taille(d.getTaille())
                .version(d.getVersion())
                .actif(d.getActif())
                .build();
    }

    private DocumentDto toDocumentDto(DocumentUtilisationCredit d) {
        return DocumentDto.builder()
                .id(d.getId())
                .type(d.getType())
                .nomFichier(d.getNomFichier())
                .chemin(d.getChemin())
                .dateUpload(d.getDateUpload())
                .taille(d.getTaille())
                .version(d.getVersion())
                .actif(d.getActif())
                .build();
    }

    private DocumentDto toDocumentDto(DocumentTransfertCredit d) {
        return DocumentDto.builder()
                .id(d.getId())
                .type(d.getType())
                .nomFichier(d.getNomFichier())
                .chemin(d.getChemin())
                .dateUpload(d.getDateUpload())
                .taille(d.getTaille())
                .version(d.getVersion())
                .actif(d.getActif())
                .build();
    }

    private DocumentDto toDocumentDto(DocumentSousTraitance d) {
        return DocumentDto.builder()
                .id(d.getId())
                .type(d.getType())
                .nomFichier(d.getNomFichier())
                .chemin(d.getChemin())
                .dateUpload(d.getDateUpload())
                .taille(d.getTaille())
                .version(d.getVersion())
                .actif(d.getActif())
                .build();
    }

    private List<DocumentDto> documentsMarche(DossierGed dossier) {
        if (dossier == null || dossier.getDemandeCorrection() == null || dossier.getDemandeCorrection().getMarche() == null
                || dossier.getDemandeCorrection().getMarche().getId() == null) {
            return List.of();
        }
        Long marcheId = dossier.getDemandeCorrection().getMarche().getId();
        return documentMarcheRepository.findByMarcheId(marcheId).stream()
                .map(this::toDocumentDto)
                .sorted(Comparator.comparing(DocumentDto::getDateUpload, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .collect(Collectors.toList());
    }

    private List<DocumentDto> documentsConvention(DossierGed dossier) {
        if (dossier == null || dossier.getDemandeCorrection() == null || dossier.getDemandeCorrection().getConvention() == null
                || dossier.getDemandeCorrection().getConvention().getId() == null) {
            return List.of();
        }
        Long conventionId = dossier.getDemandeCorrection().getConvention().getId();
        return documentConventionRepository.findByConventionId(conventionId).stream()
                .map(this::toDocumentDto)
                .sorted(Comparator.comparing(DocumentDto::getDateUpload, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .collect(Collectors.toList());
    }

    private List<DocumentDto> documentsReferentielProjets(DossierGed dossier) {
        if (dossier == null || dossier.getDemandeCorrection() == null || dossier.getDemandeCorrection().getConvention() == null
                || dossier.getDemandeCorrection().getConvention().getId() == null) {
            return List.of();
        }
        Long conventionId = dossier.getDemandeCorrection().getConvention().getId();
        List<DocumentDto> docs = new ArrayList<>();
        for (ReferentielProjet rp : referentielProjetRepository.findByConventionId(conventionId)) {
            if (rp == null || rp.getId() == null) {
                continue;
            }
            documentProjetRepository.findByReferentielProjetId(rp.getId()).stream()
                    .map(this::toDocumentDto)
                    .forEach(docs::add);
        }
        docs.sort(Comparator.comparing(DocumentDto::getDateUpload, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return docs;
    }

    private List<DocumentDto> documentsAvenants(DossierGed dossier) {
        if (dossier == null || dossier.getCertificatCredit() == null || dossier.getCertificatCredit().getId() == null) {
            return List.of();
        }
        Long certificatId = dossier.getCertificatCredit().getId();
        List<DocumentDto> docs = new ArrayList<>();
        for (Avenant avenant : avenantRepository.findByCertificatCreditId(certificatId)) {
            if (avenant == null || avenant.getId() == null) {
                continue;
            }
            documentAvenantRepository.findByAvenantId(avenant.getId()).stream()
                    .map(this::toDocumentDto)
                    .forEach(docs::add);
        }
        docs.sort(Comparator.comparing(DocumentDto::getDateUpload, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return docs;
    }

    private List<DocumentDto> documentsCloture(DossierGed dossier) {
        if (dossier == null || dossier.getCertificatCredit() == null || dossier.getCertificatCredit().getId() == null) {
            return List.of();
        }
        Long certificatId = dossier.getCertificatCredit().getId();
        return clotureCreditRepository.findByCertificatCreditId(certificatId)
                .map(cc -> {
                    List<DocumentDto> docs = documentClotureCreditRepository.findByClotureCreditId(cc.getId()).stream()
                            .map(this::toDocumentDto)
                            .collect(Collectors.toList());
                    docs.sort(Comparator.comparing(DocumentDto::getDateUpload, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
                    return docs;
                })
                .orElse(List.of());
    }

    private DocumentDto toDocumentDto(mr.gov.finances.sgci.domain.entity.DocumentConvention d) {
        return DocumentDto.builder()
                .id(d.getId())
                .type(null)
                .typeDetail("CONVENTION:" + (d.getType() != null ? d.getType().name() : ""))
                .nomFichier(d.getNomFichier())
                .chemin(d.getChemin())
                .dateUpload(d.getDateUpload())
                .taille(d.getTaille())
                .build();
    }

    private DocumentDto toDocumentDto(mr.gov.finances.sgci.domain.entity.DocumentMarche d) {
        return DocumentDto.builder()
                .id(d.getId())
                .type(null)
                .typeDetail("MARCHE:" + (d.getType() != null ? d.getType().name() : ""))
                .nomFichier(d.getNomFichier())
                .chemin(d.getChemin())
                .dateUpload(d.getDateUpload())
                .taille(d.getTaille())
                .build();
    }

    private DocumentDto toDocumentDto(mr.gov.finances.sgci.domain.entity.DocumentProjet d) {
        return DocumentDto.builder()
                .id(d.getId())
                .type(null)
                .typeDetail("REFERENTIEL_PROJET:" + (d.getType() != null ? d.getType().name() : ""))
                .nomFichier(d.getNomFichier())
                .chemin(d.getChemin())
                .dateUpload(d.getDateUpload())
                .taille(d.getTaille())
                .build();
    }

    private DocumentDto toDocumentDto(DocumentClotureCredit d) {
        return DocumentDto.builder()
                .id(d.getId())
                .type(d.getType())
                .nomFichier(d.getNomFichier())
                .chemin(d.getChemin())
                .dateUpload(d.getDateUpload())
                .taille(d.getTaille())
                .version(d.getVersion())
                .actif(d.getActif())
                .build();
    }

    private DocumentDto toDocumentDto(DocumentAvenant d) {
        return DocumentDto.builder()
                .id(d.getId())
                .type(d.getType())
                .nomFichier(d.getNomFichier())
                .chemin(d.getChemin())
                .dateUpload(d.getDateUpload())
                .taille(d.getTaille())
                .version(d.getVersion())
                .actif(d.getActif())
                .build();
    }
}
