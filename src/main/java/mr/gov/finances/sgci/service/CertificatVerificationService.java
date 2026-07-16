package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.enums.EtatVerificationCertificat;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.web.dto.CertificatUtilisationEligibilityDto;
import mr.gov.finances.sgci.web.dto.CertificatVerificationDto;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CertificatVerificationService {

    private static final Set<StatutCertificat> STATUTS_EN_COURS = EnumSet.of(
            StatutCertificat.BROUILLON,
            StatutCertificat.ENVOYEE,
            StatutCertificat.EN_CONTROLE,
            StatutCertificat.INCOMPLETE,
            StatutCertificat.A_RECONTROLER,
            StatutCertificat.EN_VALIDATION_PRESIDENT,
            StatutCertificat.VALIDE_PRESIDENT,
            StatutCertificat.EN_OUVERTURE_DGTCP
    );

    private static final Set<StatutCertificat> STATUTS_ACTIFS = EnumSet.of(
            StatutCertificat.OUVERT,
            StatutCertificat.MODIFIE
    );

    private final CertificatCreditRepository certificatCreditRepository;
    private final UtilisationCreditEligibilityHelper eligibilityHelper;

    @Transactional(readOnly = true)
    public CertificatVerificationDto verifyByNumero(String rawNumero) {
        String numero = normalizeNumero(rawNumero);
        if (numero.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED, "Le numéro du certificat est obligatoire");
        }

        return certificatCreditRepository.findByNumero(numero)
                .map(this::buildFound)
                .orElseGet(() -> buildNotFound(numero));
    }

    static String normalizeNumero(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toUpperCase();
    }

    private CertificatVerificationDto buildNotFound(String numero) {
        return CertificatVerificationDto.builder()
                .trouve(false)
                .numero(numero)
                .etatVerification(EtatVerificationCertificat.INCONNU)
                .libelleEtat("Certificat introuvable")
                .severiteUi("destructive")
                .motifs(List.of("Aucun certificat enregistré pour ce numéro"))
                .build();
    }

    private CertificatVerificationDto buildFound(CertificatCredit certificat) {
        StatutCertificat statut = certificat.getStatut();
        Instant dateValidite = certificat.getDateValidite();
        boolean expire = isExpired(dateValidite);

        CertificatUtilisationEligibilityDto eligDouane =
                eligibilityHelper.evaluate(certificat, TypeUtilisation.DOUANIER, null);
        CertificatUtilisationEligibilityDto eligTva =
                eligibilityHelper.evaluate(certificat, TypeUtilisation.TVA_INTERIEURE, null);

        EtatVerificationCertificat etat = resolveEtat(statut, expire);
        List<String> motifs = new ArrayList<>();
        switch (etat) {
            case VALIDE -> motifs.add("Certificat actif — crédit ouvert");
            case EXPIRE -> motifs.add("Date de validité dépassée");
            case CLOTURE -> motifs.add("Certificat clôturé");
            case ANNULE -> motifs.add("Certificat annulé");
            case EN_COURS -> motifs.add("Mise en place du certificat non finalisée (statut: " + statut + ")");
            case NON_VALIDE -> motifs.add("Certificat non utilisable (statut: " + statut + ")");
            default -> { }
        }
        if (expire && etat == EtatVerificationCertificat.VALIDE) {
            etat = EtatVerificationCertificat.EXPIRE;
        }
        if (eligDouane.getMotifs() != null && !eligDouane.isEligible() && etat == EtatVerificationCertificat.VALIDE) {
            motifs.addAll(eligDouane.getMotifs());
        }

        Entreprise entreprise = certificat.getEntreprise();
        Long marcheId = certificat.getDemandeCorrection() != null
                && certificat.getDemandeCorrection().getMarche() != null
                ? certificat.getDemandeCorrection().getMarche().getId()
                : null;

        UiLabel ui = uiLabel(etat);

        return CertificatVerificationDto.builder()
                .trouve(true)
                .numero(certificat.getNumero())
                .certificatId(certificat.getId())
                .statutCertificat(statut)
                .etatVerification(etat)
                .libelleEtat(ui.libelle())
                .severiteUi(ui.severite())
                .dateEmission(certificat.getDateEmission())
                .dateValidite(dateValidite)
                .expire(expire)
                .entrepriseRaisonSociale(entreprise != null ? entreprise.getRaisonSociale() : null)
                .marcheId(marcheId)
                .soldeCordon(certificat.getSoldeCordon())
                .soldeTVA(certificat.getSoldeTVA())
                .utilisableDouane(eligDouane.isEligible())
                .utilisableTVA(eligTva.isEligible())
                .motifs(motifs)
                .build();
    }

    private EtatVerificationCertificat resolveEtat(StatutCertificat statut, boolean expire) {
        if (statut == null) {
            return EtatVerificationCertificat.NON_VALIDE;
        }
        if (statut == StatutCertificat.CLOTURE) {
            return EtatVerificationCertificat.CLOTURE;
        }
        if (statut == StatutCertificat.ANNULE) {
            return EtatVerificationCertificat.ANNULE;
        }
        if (STATUTS_EN_COURS.contains(statut)) {
            return EtatVerificationCertificat.EN_COURS;
        }
        if (STATUTS_ACTIFS.contains(statut)) {
            return expire ? EtatVerificationCertificat.EXPIRE : EtatVerificationCertificat.VALIDE;
        }
        return EtatVerificationCertificat.NON_VALIDE;
    }

    private static boolean isExpired(Instant dateValidite) {
        return dateValidite != null && dateValidite.isBefore(Instant.now());
    }

    private record UiLabel(String libelle, String severite) {}

    private static UiLabel uiLabel(EtatVerificationCertificat etat) {
        return switch (etat) {
            case VALIDE -> new UiLabel("Certificat valide", "success");
            case EXPIRE -> new UiLabel("Certificat expiré", "warning");
            case CLOTURE -> new UiLabel("Certificat clôturé", "muted");
            case ANNULE -> new UiLabel("Certificat annulé", "destructive");
            case EN_COURS -> new UiLabel("Certificat en cours de mise en place", "warning");
            case INCONNU -> new UiLabel("Certificat introuvable", "destructive");
            case NON_VALIDE -> new UiLabel("Certificat non valide", "destructive");
        };
    }
}
