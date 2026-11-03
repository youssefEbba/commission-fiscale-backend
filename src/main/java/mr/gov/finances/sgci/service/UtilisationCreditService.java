package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.enums.StatutUtilisation;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.utilisation.UtilisationBrouillonService;
import mr.gov.finances.sgci.service.utilisation.UtilisationCertificatService;
import mr.gov.finances.sgci.service.utilisation.UtilisationCircuitDgdService;
import mr.gov.finances.sgci.service.utilisation.UtilisationCircuitTresorService;
import mr.gov.finances.sgci.service.utilisation.UtilisationLectureService;
import mr.gov.finances.sgci.service.utilisation.UtilisationTvaService;
import mr.gov.finances.sgci.web.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UtilisationCreditService {

    private final UtilisationLectureService lecture;
    private final UtilisationBrouillonService brouillon;
    private final UtilisationCircuitDgdService dgd;
    private final UtilisationCircuitTresorService tresor;
    private final UtilisationCertificatService certificat;
    private final UtilisationTvaService tva;
    public List<UtilisationCreditDto> findAll() {
        return lecture.findAll();
    }

    public List<UtilisationCreditDto> findAllVisible(AuthenticatedUser auth, Boolean demandeurSousTraitantOnly, Long sousTraitantEntrepriseId) {
        return lecture.findAllVisible(auth, demandeurSousTraitantOnly, sousTraitantEntrepriseId);
    }

    public UtilisationCreditDto findById(Long id, AuthenticatedUser user) {
        return lecture.findById(id, user);
    }

    public List<UtilisationCreditDto> findByCertificatCreditId(Long certificatCreditId, AuthenticatedUser user) {
        return lecture.findByCertificatCreditId(certificatCreditId, user);
    }

    public List<TvaDeductibleStockDto> findTvaStockByCertificat(Long certificatCreditId) {
        return tva.findTvaStockByCertificat(certificatCreditId);
    }

    public UtilisationCreditDto create(CreateUtilisationCreditRequest request) {
        return brouillon.create(request);
    }

    public UtilisationCreditDto create(CreateUtilisationCreditRequest request, AuthenticatedUser user) {
        return brouillon.create(request, user);
    }

    public void deleteBrouillon(Long id, AuthenticatedUser user) {
        brouillon.deleteBrouillon(id, user);
    }

    public UtilisationCreditDto soumettreBrouillon(Long id, AuthenticatedUser user) {
        return brouillon.soumettreBrouillon(id, user);
    }

    public UtilisationCreditDto update(Long id, CreateUtilisationCreditRequest request, AuthenticatedUser user) {
        return brouillon.update(id, request, user);
    }

    public UtilisationCreditDto adminCorrectInfo(Long id, AdminCorrectionUtilisationRequest request, String motif, AuthenticatedUser user) {
        return brouillon.adminCorrectInfo(id, request, motif, user);
    }

    public UtilisationCreditDto deposerQuittanceDgi(Long id, String numeroQuittance, Instant dateQuittance, BigDecimal montant, MultipartFile file, AuthenticatedUser user) throws IOException {
        return tva.deposerQuittanceDgi(id, numeroQuittance, dateQuittance, montant, file, user);
    }

    public UtilisationCreditDto apurerTVAInterieure(Long id, ApurerTVAInterieureRequest request, AuthenticatedUser user) {
        return tva.apurerTVAInterieure(id, request, user);
    }

    public UtilisationCreditDto visaDgd(Long id, String decisionsJson, MultipartFile file, AuthenticatedUser user) throws IOException {
        return dgd.visaDgd(id, decisionsJson, file, user);
    }

    public UtilisationCreditDto saisirCheque(Long id, SaisirChequeRequest request, MultipartFile file, AuthenticatedUser user) throws IOException {
        return dgd.saisirCheque(id, request, file, user);
    }

    public UtilisationCreditDto transmettreAuPresident(Long id, AuthenticatedUser user) {
        return dgd.transmettreAuPresident(id, user);
    }

    public UtilisationCreditDto envoyerAuTresor(Long id, AuthenticatedUser user) {
        return tresor.envoyerAuTresor(id, user);
    }

    public UtilisationCreditDto saisirQuittances(Long id, String quittancesJson, List<MultipartFile> files, AuthenticatedUser user) throws IOException {
        return tresor.saisirQuittances(id, quittancesJson, files, user);
    }

    public UtilisationCreditDto cloturer(Long id, String motif, AuthenticatedUser user) {
        return tresor.cloturer(id, motif, user);
    }

    public UtilisationCreditDto liquiderDouane(Long id, AuthenticatedUser user) {
        return tresor.liquiderDouane(id, user);
    }

    public UtilisationCreditDto emettreCertificatUtilisation(Long id, AuthenticatedUser user) {
        return certificat.emettreCertificatUtilisation(id, user);
    }

    public UtilisationCreditDto reserverNumeroCertificatUtilisation(Long id, AuthenticatedUser user) {
        return certificat.reserverNumeroCertificatUtilisation(id, user);
    }

    public void emettreApresDepotCertificatSigne(Long id, AuthenticatedUser user) {
        certificat.emettreApresDepotCertificatSigne(id, user);
    }

    public UtilisationCreditDto adminEmettreCertificatUtilisation(Long id, String motif, MultipartFile file, AuthenticatedUser user) throws IOException {
        return certificat.adminEmettreCertificatUtilisation(id, motif, file, user);
    }

    public CertificatUtilisationEmissionDto etatEmissionCertificat(Long id, AuthenticatedUser user) {
        return certificat.etatEmissionCertificat(id, user);
    }

    public UtilisationCreditDto updateStatut(Long id, StatutUtilisation statut, AuthenticatedUser user) {
        return certificat.updateStatut(id, statut, user);
    }

    public List<QuittanceTresorDto> getQuittances(Long utilisationId, AuthenticatedUser user) {
        return tresor.getQuittances(utilisationId, user);
    }

}
