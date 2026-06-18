package mr.gov.finances.sgci.service;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.domain.entity.CertificatCredit;
import mr.gov.finances.sgci.domain.entity.LigneBulletinLiquidation;
import mr.gov.finances.sgci.domain.enums.AffectationTaxe;
import mr.gov.finances.sgci.domain.enums.StatutCertificat;
import mr.gov.finances.sgci.domain.enums.StatutTransfert;
import mr.gov.finances.sgci.domain.enums.TvaDeductibleStockSource;
import mr.gov.finances.sgci.domain.enums.TypeUtilisation;
import mr.gov.finances.sgci.repository.ClotureCreditRepository;
import mr.gov.finances.sgci.repository.TransfertCreditRepository;
import mr.gov.finances.sgci.repository.TvaDeductibleStockRepository;
import mr.gov.finances.sgci.web.dto.CertificatUtilisationEligibilityDto;
import mr.gov.finances.sgci.web.dto.CreateUtilisationCreditRequest;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class UtilisationCreditEligibilityHelper {

    private static final Set<StatutCertificat> STATUTS_ELIGIBLES = EnumSet.of(
            StatutCertificat.OUVERT, StatutCertificat.MODIFIE);

    private final ClotureCreditRepository clotureCreditRepository;
    private final TransfertCreditRepository transfertCreditRepository;
    private final TvaDeductibleStockRepository tvaStockRepository;

    public void assertCertificatEligible(CertificatCredit certificat, TypeUtilisation type) {
        CertificatUtilisationEligibilityDto eval = evaluate(certificat, type, null);
        if (!eval.isEligible()) {
            String message = eval.getMotifs() != null && !eval.getMotifs().isEmpty()
                    ? String.join(" ; ", eval.getMotifs())
                    : "Certificat non éligible pour une utilisation de crédit";
            if (eval.isTransfertExecute() && type == TypeUtilisation.DOUANIER) {
                throw ApiException.conflict(ApiErrorCode.BUSINESS_RULE_VIOLATION, message);
            }
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, message);
        }
    }

    public void assertSoldesDouaneProposes(CertificatCredit certificat,
                                           List<CreateUtilisationCreditRequest.LigneBulletinRequest> lignes) {
        SoldesProposes soldes = computeSoldesProposesFromRequests(lignes);
        assertSoldesDouaneProposesInternal(certificat, soldes.montantCordon(), soldes.tvaImport());
    }

    public void assertSoldesDouaneProposesFromEntities(CertificatCredit certificat,
                                                       List<LigneBulletinLiquidation> lignes) {
        SoldesProposes soldes = computeSoldesProposesFromEntities(lignes);
        assertSoldesDouaneProposesInternal(certificat, soldes.montantCordon(), soldes.tvaImport());
    }

    public CertificatUtilisationEligibilityDto evaluate(CertificatCredit certificat,
                                                        TypeUtilisation type,
                                                        List<CreateUtilisationCreditRequest.LigneBulletinRequest> lignes) {
        List<String> motifs = new ArrayList<>();
        StatutCertificat statut = certificat != null ? certificat.getStatut() : null;
        Instant dateValidite = certificat != null ? certificat.getDateValidite() : null;
        boolean expire = isExpired(dateValidite);
        boolean clotureEnCours = isClotureEnCours(certificat);
        boolean transfertExecute = certificat != null && isTransfertExecute(certificat.getId());

        if (certificat == null) {
            motifs.add("Certificat introuvable");
        } else {
            if (statut == StatutCertificat.CLOTURE || statut == StatutCertificat.ANNULE) {
                motifs.add("Certificat clôturé ou annulé");
            } else if (statut == null || !STATUTS_ELIGIBLES.contains(statut)) {
                motifs.add("Le crédit doit être OUVERT ou MODIFIE pour créer une utilisation (statut actuel: "
                        + statut + ")");
            }
            if (expire) {
                motifs.add("Date de validité du certificat dépassée");
            }
            if (clotureEnCours) {
                motifs.add("Une proposition de clôture est en cours sur ce certificat");
            }
            if (type == TypeUtilisation.DOUANIER && transfertExecute) {
                motifs.add("Transfert exécuté — utilisations douanières interdites sur ce certificat");
            }
            if (type == TypeUtilisation.DOUANIER && lignes != null && !lignes.isEmpty()) {
                SoldesProposes soldes = computeSoldesProposesFromRequests(lignes);
                collectSoldesMotifs(certificat, soldes.montantCordon(), soldes.tvaImport(), motifs);
            }
        }

        BigDecimal soldeCordon = certificat != null && certificat.getSoldeCordon() != null
                ? certificat.getSoldeCordon() : BigDecimal.ZERO;
        BigDecimal tvaImport = resolveQuotaTvaImport(certificat);
        if (tvaImport == null) {
            tvaImport = BigDecimal.ZERO;
        }
        BigDecimal soldeTva = certificat != null && certificat.getSoldeTVA() != null
                ? certificat.getSoldeTVA() : BigDecimal.ZERO;

        return CertificatUtilisationEligibilityDto.builder()
                .eligible(motifs.isEmpty())
                .statutCertificat(statut)
                .motifs(motifs)
                .soldeCordon(soldeCordon)
                .tvaImportationDouane(tvaImport)
                .soldeTVA(soldeTva)
                .transfertExecute(transfertExecute)
                .clotureEnCours(clotureEnCours)
                .dateValidite(dateValidite)
                .expire(expire)
                .build();
    }

    private void assertSoldesDouaneProposesInternal(CertificatCredit certificat,
                                                    BigDecimal montantCordonRequis,
                                                    BigDecimal tvaImportRequis) {
        List<String> motifs = new ArrayList<>();
        collectSoldesMotifs(certificat, montantCordonRequis, tvaImportRequis, motifs);
        if (!motifs.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, String.join(" ; ", motifs));
        }
    }

    private void collectSoldesMotifs(CertificatCredit certificat,
                                     BigDecimal montantCordonRequis,
                                     BigDecimal tvaImportRequis,
                                     List<String> motifs) {
        BigDecimal soldeCordon = certificat.getSoldeCordon() != null ? certificat.getSoldeCordon() : BigDecimal.ZERO;
        if (montantCordonRequis.compareTo(BigDecimal.ZERO) > 0 && soldeCordon.compareTo(montantCordonRequis) < 0) {
            motifs.add("Solde cordon insuffisant (disponible=" + soldeCordon + ", requis=" + montantCordonRequis + ")");
        }
        BigDecimal quotaTva = resolveQuotaTvaImport(certificat);
        if (quotaTva != null
                && tvaImportRequis.compareTo(BigDecimal.ZERO) > 0
                && quotaTva.compareTo(tvaImportRequis) < 0) {
            motifs.add("Quota TVA import insuffisant (disponible=" + quotaTva + ", requis=" + tvaImportRequis + ")");
        }
    }

    private BigDecimal resolveQuotaTvaImport(CertificatCredit certificat) {
        if (certificat.getTvaImportationDouane() != null) {
            return certificat.getTvaImportationDouane();
        }
        if (certificat.getTvaImportationDouaneAccordee() != null) {
            return certificat.getTvaImportationDouaneAccordee();
        }
        return null;
    }

    private record SoldesProposes(BigDecimal montantCordon, BigDecimal tvaImport) {}

    private SoldesProposes computeSoldesProposesFromRequests(
            List<CreateUtilisationCreditRequest.LigneBulletinRequest> lignes) {
        BigDecimal montantCordon = BigDecimal.ZERO;
        BigDecimal tvaImport = BigDecimal.ZERO;
        if (lignes == null) {
            return new SoldesProposes(montantCordon, tvaImport);
        }
        for (CreateUtilisationCreditRequest.LigneBulletinRequest l : lignes) {
            if (l == null || l.getAffectation() != AffectationTaxe.AU_CI) {
                continue;
            }
            BigDecimal val = l.getValeurTaxe() != null ? l.getValeurTaxe() : BigDecimal.ZERO;
            if (val.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (isTvaCode(l.getCodeTaxe())) {
                tvaImport = tvaImport.add(val);
            } else {
                montantCordon = montantCordon.add(val);
            }
        }
        return new SoldesProposes(montantCordon, tvaImport);
    }

    private SoldesProposes computeSoldesProposesFromEntities(List<LigneBulletinLiquidation> lignes) {
        BigDecimal montantCordon = BigDecimal.ZERO;
        BigDecimal tvaImport = BigDecimal.ZERO;
        if (lignes == null) {
            return new SoldesProposes(montantCordon, tvaImport);
        }
        for (LigneBulletinLiquidation l : lignes) {
            AffectationTaxe aff = l.getAffectationEntreprise() != null ? l.getAffectationEntreprise() : l.getAffectation();
            if (aff != AffectationTaxe.AU_CI) {
                continue;
            }
            BigDecimal val = l.getValeurTaxe() != null ? l.getValeurTaxe() : BigDecimal.ZERO;
            if (val.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (isTvaCode(l.getCodeTaxe())) {
                tvaImport = tvaImport.add(val);
            } else {
                montantCordon = montantCordon.add(val);
            }
        }
        return new SoldesProposes(montantCordon, tvaImport);
    }

    private boolean isTvaCode(String codeTaxe) {
        return codeTaxe != null && "TVA".equalsIgnoreCase(codeTaxe.trim());
    }

    private boolean isExpired(Instant dateValidite) {
        return dateValidite != null && dateValidite.isBefore(Instant.now());
    }

    private boolean isClotureEnCours(CertificatCredit certificat) {
        if (certificat == null || certificat.getId() == null) {
            return false;
        }
        return clotureCreditRepository.findByCertificatCreditId(certificat.getId())
                .map(c -> c.getDateCloture() == null)
                .orElse(false);
    }

    private boolean isTransfertExecute(Long certificatCreditId) {
        if (certificatCreditId == null) {
            return false;
        }
        boolean transfertExecute = transfertCreditRepository.existsByCertificatCreditIdAndStatut(
                certificatCreditId, StatutTransfert.TRANSFERE);
        boolean traceStockTransfert = tvaStockRepository.existsByCertificatCreditIdAndSource(
                        certificatCreditId, TvaDeductibleStockSource.TRANSFERT_CREDIT)
                || tvaStockRepository.existsByCertificatCreditIdAndUtilisationDouaneIsNull(certificatCreditId);
        return transfertExecute || traceStockTransfert;
    }
}
