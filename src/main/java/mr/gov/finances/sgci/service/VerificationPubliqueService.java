package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.Entreprise;
import mr.gov.finances.sgci.domain.entity.UtilisationCredit;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.repository.CertificatCreditRepository;
import mr.gov.finances.sgci.repository.UtilisationCreditRepository;
import mr.gov.finances.sgci.web.dto.VerificationPubliqueDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Vérification d'authenticité d'un document officiel, depuis le QR code qu'il porte.
 *
 * <p>Servie <b>sans authentification</b> : c'est tout l'intérêt du QR, qu'un agent du Trésor, de la
 * DGI ou de la douane puisse contrôler une pièce au guichet avec son téléphone, sans compte.
 *
 * <p>En contrepartie, la réponse est réduite à ce qui atteste la pièce : authenticité, état, date
 * d'émission, bénéficiaire. <b>Aucun montant, aucun solde</b> — voir {@link VerificationPubliqueDto}.
 * Les numéros étant séquentiels, ils sont énumérables : la limitation de débit par adresse IP
 * ({@code VerificationPubliqueRateLimiter}) borne ce que coûterait un balayage systématique, mais la
 * vraie protection reste la pauvreté de la réponse.
 */
@Service
@RequiredArgsConstructor
public class VerificationPubliqueService {

    public static final String TYPE_CERTIFICAT_CREDIT = "CERTIFICAT_CREDIT";
    public static final String TYPE_CERTIFICAT_UTILISATION = "CERTIFICAT_UTILISATION";

    private final CertificatCreditRepository certificatCreditRepository;
    private final UtilisationCreditRepository utilisationCreditRepository;

    /**
     * Résout un code scanné, quel que soit le document qu'il désigne.
     *
     * <p>Les deux familles sont interrogées successivement plutôt que devinées au préfixe : un
     * numéro de certificat de crédit est saisi à la main et ne suit aucune forme garantie.
     */
    @Transactional(readOnly = true)
    public VerificationPubliqueDto verifier(String rawCode) {
        String code = normaliser(rawCode);
        if (code.isEmpty()) {
            return introuvable(code);
        }

        Optional<UtilisationCredit> utilisation =
                utilisationCreditRepository.findByNumeroCertificatUtilisation(code);
        if (utilisation.isPresent()) {
            return depuisUtilisation(utilisation.get(), code);
        }
        return certificatCreditRepository.findByNumero(code)
                .map(c -> depuisCertificat(c, code))
                .orElseGet(() -> introuvable(code));
    }

    static String normaliser(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase();
    }

    private VerificationPubliqueDto introuvable(String code) {
        return VerificationPubliqueDto.builder()
                .trouve(false)
                .code(code)
                .authentique(false)
                .libelleEtat("Document introuvable")
                .severiteUi("destructive")
                .motifs(List.of("Aucun document officiel ne porte ce code."))
                .build();
    }

    // ── certificat d'utilisation ────────────────────────────────────────────────────────────────

    private VerificationPubliqueDto depuisUtilisation(UtilisationCredit u, String code) {
        StatutUtilisation statut = u.getStatut();
        boolean annule = statut == StatutUtilisation.REJETEE;
        boolean clos = statut == StatutUtilisation.CLOTUREE;

        String libelle;
        String severite;
        List<String> motifs;
        if (annule) {
            libelle = "Certificat d'utilisation annulé";
            severite = "destructive";
            motifs = List.of("L'utilisation a été rejetée : ce certificat ne vaut plus titre.");
        } else if (clos) {
            libelle = "Certificat d'utilisation authentique — utilisation clôturée";
            severite = "muted";
            motifs = List.of("L'utilisation est terminée.");
        } else {
            libelle = "Certificat d'utilisation authentique";
            severite = "success";
            motifs = List.of("Émis par la Commission fiscale, utilisation en cours.");
        }

        Entreprise entreprise = u.getEntreprise() != null
                ? u.getEntreprise()
                : (u.getCertificatCredit() != null ? u.getCertificatCredit().getEntreprise() : null);

        return VerificationPubliqueDto.builder()
                .trouve(true)
                .code(code)
                .typeDocument(TYPE_CERTIFICAT_UTILISATION)
                .authentique(!annule)
                .libelleEtat(libelle)
                .severiteUi(severite)
                .dateEmission(u.getDateCertificatUtilisation())
                .entrepriseRaisonSociale(entreprise != null ? entreprise.getRaisonSociale() : null)
                .statut(statut != null ? statut.name() : null)
                .motifs(motifs)
                .build();
    }

    // ── certificat de crédit d'impôt ────────────────────────────────────────────────────────────

    private VerificationPubliqueDto depuisCertificat(CertificatCredit c, String code) {
        StatutCertificat statut = c.getStatut();
        boolean expire = c.getDateValidite() != null && c.getDateValidite().isBefore(Instant.now());
        boolean annule = statut == StatutCertificat.ANNULE;
        boolean clos = statut == StatutCertificat.CLOTURE;
        boolean actif = statut == StatutCertificat.OUVERT || statut == StatutCertificat.MODIFIE;

        String libelle;
        String severite;
        List<String> motifs;
        if (annule) {
            libelle = "Certificat annulé";
            severite = "destructive";
            motifs = List.of("Ce certificat a été annulé : il ne vaut plus titre.");
        } else if (clos) {
            libelle = "Certificat authentique — crédit clôturé";
            severite = "muted";
            motifs = List.of("Le crédit d'impôt est clôturé.");
        } else if (actif && expire) {
            libelle = "Certificat authentique — validité dépassée";
            severite = "warning";
            motifs = List.of("La date de validité est dépassée.");
        } else if (actif) {
            libelle = "Certificat authentique — crédit ouvert";
            severite = "success";
            motifs = List.of("Crédit d'impôt en cours de validité.");
        } else {
            libelle = "Certificat en cours de mise en place";
            severite = "warning";
            motifs = List.of("La mise en place n'est pas finalisée : ce certificat ne vaut pas encore titre.");
        }

        Entreprise entreprise = c.getEntreprise();
        return VerificationPubliqueDto.builder()
                .trouve(true)
                .code(code)
                .typeDocument(TYPE_CERTIFICAT_CREDIT)
                .authentique(!annule)
                .libelleEtat(libelle)
                .severiteUi(severite)
                .dateEmission(c.getDateEmission())
                .entrepriseRaisonSociale(entreprise != null ? entreprise.getRaisonSociale() : null)
                .statut(statut != null ? statut.name() : null)
                .motifs(motifs)
                .build();
    }
}
