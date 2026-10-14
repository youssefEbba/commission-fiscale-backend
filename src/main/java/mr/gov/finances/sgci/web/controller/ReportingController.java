package mr.gov.finances.sgci.web.controller;

import lombok.RequiredArgsConstructor;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.ReportingService;
import mr.gov.finances.sgci.domain.enums.AxeEngagement;
import mr.gov.finances.sgci.domain.enums.BlocReporting;
import mr.gov.finances.sgci.domain.enums.Granularite;
import mr.gov.finances.sgci.service.reporting.AFermerService;
import mr.gov.finances.sgci.service.reporting.BlocagesService;
import mr.gov.finances.sgci.service.reporting.EngagementEtatService;
import mr.gov.finances.sgci.service.reporting.ProductionPeriodeService;
import mr.gov.finances.sgci.service.reporting.ReportingDossierService;
import mr.gov.finances.sgci.service.reporting.export.CsvReportWriter;
import mr.gov.finances.sgci.service.reporting.export.PdfReportWriter;
import mr.gov.finances.sgci.service.reporting.export.XlsxReportWriter;
import mr.gov.finances.sgci.service.reporting.model.ReportingDossier;
import mr.gov.finances.sgci.web.dto.reporting.AFermerDto;
import mr.gov.finances.sgci.web.dto.reporting.BlocagesDto;
import mr.gov.finances.sgci.web.dto.reporting.EngagementEtatDto;
import mr.gov.finances.sgci.web.dto.reporting.ProductionPeriodeDto;
import mr.gov.finances.sgci.web.dto.reporting.ReportingSummaryDto;
import mr.gov.finances.sgci.web.dto.reporting.TimeSeriesPointDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.List;

/**
 * Agrégats pour tableaux de bord et exports (périmètre selon le rôle).
 */
@RestController
@RequestMapping("/api/reporting")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReportingController {

    private final ReportingService reportingService;
    private final EngagementEtatService engagementEtatService;
    private final BlocagesService blocagesService;
    private final AFermerService aFermerService;
    private final ProductionPeriodeService productionPeriodeService;
    private final ReportingDossierService reportingDossierService;
    private final XlsxReportWriter xlsxReportWriter;
    private final CsvReportWriter csvReportWriter;
    private final PdfReportWriter pdfReportWriter;

    /**
     * Synthèse multi-domaines sur une fenêtre temporelle.
     *
     * <p>Les chiffres sont bornés au périmètre du rôle : une entreprise ne voit que ses dossiers,
     * une autorité contractante que les siens, un délégué que ses marchés. Les rôles « nationaux »
     * — Président, ADMIN_SI, DGD, DGTCP, DGI, DGB — voient tout, et sont donc les seuls à qui
     * {@code autoriteContractanteId} et {@code entrepriseId} servent à quelque chose : pour les
     * autres, le périmètre est imposé et ces paramètres sont ignorés.
     *
     * <p>La permission a été ajoutée après coup, et elle manquait vraiment : sans elle, le seul
     * rôle privé de {@code reporting.view} — COMMISSION_RELAIS — franchissait la porte, et comme
     * un relais n'a ni entreprise ni autorité rattachée, il ressortait avec les totaux nationaux.
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('reporting.view')")
    public ReportingSummaryDto summary(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long autoriteContractanteId,
            @RequestParam(required = false) Long entrepriseId
    ) {
        return reportingService.getSummary(user, from, to, autoriteContractanteId, entrepriseId);
    }

    /**
     * Série temporelle des demandes de correction (compte par mois civil).
     */
    @GetMapping("/timeseries/demandes")
    @PreAuthorize("hasAuthority('reporting.view')")
    public List<TimeSeriesPointDto> demandeTimeseries(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long autoriteContractanteId,
            @RequestParam(required = false) Long entrepriseId
    ) {
        return reportingService.getDemandeTimeseries(user, from, to, autoriteContractanteId, entrepriseId);
    }

    /**
     * L'engagement de l'État : accordé, consommé, restant, ventilé selon un axe.
     *
     * <p>Trois enveloppes distinctes — droits et taxes hors TVA, TVA à l'import, TVA intérieure —
     * parce que {@code soldeCordon} ne couvre que les deux premières lignes du récapitulatif alors
     * que {@code montantCordon} en couvre trois : les soustraire directement afficherait une
     * consommation égale à la TVA à l'import sur un dossier jamais utilisé.
     *
     * <p>Deux lignes du résultat sont des mesures de qualité de données, pas des vides à ignorer :
     * « hors rattachement » et « non renseigné ». Les masquer donnerait un tableau dont les lignes
     * ne font pas le total.
     */
    @GetMapping("/engagement")
    @PreAuthorize("hasAuthority('reporting.view')")
    public EngagementEtatDto engagement(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long autoriteContractanteId,
            @RequestParam(required = false) Long entrepriseId,
            @RequestParam(required = false) AxeEngagement axe
    ) {
        return engagementEtatService.parAxe(user, from, to, autoriteContractanteId, entrepriseId, axe);
    }

    /**
     * Où les dossiers sont arrêtés, et ce que chaque direction met à viser.
     *
     * <p>Noter l'étiquette : l'âge rendu est celui écoulé depuis la <b>dernière décision
     * enregistrée</b>, et non l'ancienneté dans le statut courant — rien ne permet encore de
     * calculer celle-là. La définition voyage dans la réponse pour atteindre l'en-tête de colonne.
     */
    @GetMapping("/blocages")
    @PreAuthorize("hasAuthority('reporting.view')")
    public BlocagesDto blocages(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long autoriteContractanteId,
            @RequestParam(required = false) Long entrepriseId,
            @RequestParam(required = false) Integer seuilJours
    ) {
        return blocagesService.blocages(user, from, to, autoriteContractanteId, entrepriseId, seuilJours);
    }

    /**
     * Ce qu'il faudrait fermer : crédits échus à solde non nul, conventions à échéance,
     * équipements déclarés sur un crédit encore ouvert, crédits dormants.
     *
     * <p>La dormance est une <b>définition</b>, pas une donnée : elle est publiée en clair dans la
     * réponse pour que personne n'hérite d'un critère qu'il n'a pas validé.
     */
    @GetMapping("/a-fermer")
    @PreAuthorize("hasAuthority('reporting.view')")
    public AFermerDto aFermer(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long autoriteContractanteId,
            @RequestParam(required = false) Long entrepriseId,
            @RequestParam(required = false) Integer horizonJours,
            @RequestParam(required = false) Integer dormanceJours
    ) {
        return aFermerService.aFermer(user, from, to, autoriteContractanteId, entrepriseId,
                horizonJours, dormanceJours);
    }

    /**
     * Les volumes et les masses financières par période, et l'évolution d'une période à l'autre.
     *
     * <p>Les périodes sans donnée sont présentes, à zéro : une série qui les saute dessine une
     * courbe continue là où il y a eu un arrêt. Et une variation vaut {@code null}, non
     * {@code -100 %}, quand la période précédente est vide — l'absence de référence n'est pas un
     * effondrement.
     */
    @GetMapping("/production")
    @PreAuthorize("hasAuthority('reporting.view')")
    public ProductionPeriodeDto production(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long autoriteContractanteId,
            @RequestParam(required = false) Long entrepriseId,
            @RequestParam(required = false) Granularite granularite
    ) {
        return productionPeriodeService.production(user, from, to, autoriteContractanteId,
                entrepriseId, granularite);
    }

    // ── les exports ────────────────────────────────────────────────────────────────────────────

    /**
     * L'état complet en classeur.
     *
     * <p>Les trois exports partagent un seul assemblage, {@link ReportingDossierService} : c'est ce
     * qui rend impossible la dérive classique où un fichier finit par contredire l'écran parce
     * qu'il a sa propre requête.
     *
     * <p>L'état est assemblé <b>avant</b> que le flux ne s'ouvre, et non pendant : la transaction
     * se termine au retour de cette méthode, alors que l'écriture dans la réponse se produit après.
     * Assembler paresseusement reviendrait à lire la base hors transaction.
     */
    @GetMapping("/export.xlsx")
    @PreAuthorize("hasAuthority('reporting.view')")
    public ResponseEntity<StreamingResponseBody> exportXlsx(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long autoriteContractanteId,
            @RequestParam(required = false) Long entrepriseId,
            @RequestParam(required = false) Set<BlocReporting> blocs
    ) {
        ReportingDossier dossier = reportingDossierService.assembler(
                user, from, to, autoriteContractanteId, entrepriseId, blocs);
        return fichier(nom("xlsx"),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                sortie -> xlsxReportWriter.ecrire(dossier, sortie));
    }

    /** Le même état en CSV, en format long — un CSV n'a pas d'onglets. */
    @GetMapping("/export.csv")
    @PreAuthorize("hasAuthority('reporting.view')")
    public ResponseEntity<StreamingResponseBody> exportCsv(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long autoriteContractanteId,
            @RequestParam(required = false) Long entrepriseId,
            @RequestParam(required = false) Set<BlocReporting> blocs
    ) {
        ReportingDossier dossier = reportingDossierService.assembler(
                user, from, to, autoriteContractanteId, entrepriseId, blocs);
        return fichier(nom("csv"), "text/csv; charset=UTF-8",
                sortie -> csvReportWriter.ecrire(dossier, sortie));
    }

    /**
     * Le même état en PDF, pour la séance.
     *
     * <p><b>Francophone seulement</b>, et c'est une limite assumée : la bibliothèque de rendu
     * n'inverse pas le sens d'écriture et ne lie pas les lettres arabes. Un PDF arabe demande une
     * couche de mise en forme supplémentaire, et c'est un travail distinct. L'écran reste bilingue.
     */
    @GetMapping("/export.pdf")
    @PreAuthorize("hasAuthority('reporting.view')")
    public ResponseEntity<StreamingResponseBody> exportPdf(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long autoriteContractanteId,
            @RequestParam(required = false) Long entrepriseId,
            @RequestParam(required = false) Set<BlocReporting> blocs
    ) {
        ReportingDossier dossier = reportingDossierService.assembler(
                user, from, to, autoriteContractanteId, entrepriseId, blocs);
        return fichier(nom("pdf"), "application/pdf",
                sortie -> pdfReportWriter.ecrire(dossier, sortie));
    }

    private static String nom(String extension) {
        return "reporting-" + DateTimeFormatter.ofPattern("yyyyMMdd")
                .withZone(ZoneId.systemDefault()).format(Instant.now()) + "." + extension;
    }

    private static ResponseEntity<StreamingResponseBody> fichier(
            String nomFichier, String typeContenu, StreamingResponseBody corps) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nomFichier).build().toString())
                .header(HttpHeaders.CONTENT_TYPE, typeContenu)
                .body(corps);
    }
}
