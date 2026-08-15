package mr.gov.finances.sgci.service.archive;

import mr.gov.finances.sgci.web.dto.archive.LigneTaxeArchiveDto;
import mr.gov.finances.sgci.web.dto.archive.ReleveArchiveDto;
import mr.gov.finances.sgci.web.dto.archive.UtilisationArchiveDto;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lecture d'un relevé de crédit d'impôt exporté par l'ancienne application (feuille
 * « EXPORT GENERIQUE »).
 *
 * <p>Le fichier est une <b>mise en page de rapport</b>, pas un tableau régulier : les blocs sont
 * donc localisés par <b>ancrages textuels</b> (« Crédit douanier : », « Utililisations du crédit
 * d'impot », « Total utilisation »), jamais par numéro de ligne — l'export varie selon le nombre
 * d'utilisations et de transferts.
 *
 * <p>Règle métier déduite de l'export et vérifiée arithmétiquement : sur une ligne douanière, les
 * taxes {@code DD, TVA, RS, PC, PSC, TCO, TT} totalisent exactement le montant imputé au crédit
 * (affectation {@code AU_CI}), tandis que {@code TTI, RIF, IMF} en sont exclues et restent à la
 * charge de l'entreprise ({@code A_PAYER}).
 */
@Component
public class ReleveArchiveParser {

    /** Taxes imputées sur le crédit d'impôt. */
    private static final List<String> TAXES_AU_CI = List.of("DD", "TVA", "RS", "PC", "PSC", "TCO", "TT");
    /** Taxes hors crédit, réglées comptant par l'entreprise. */
    private static final List<String> TAXES_A_PAYER = List.of("TTI", "RIF", "IMF");

    private static final DateTimeFormatter JJ_MM_AAAA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public ReleveArchiveDto parse(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le fichier est obligatoire");
        }
        try (InputStream in = fichier.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION, "Le classeur ne contient aucune feuille");
            }
            return lireFeuille(sheet, fichier.getOriginalFilename());
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw ApiException.badRequest(ApiErrorCode.BUSINESS_RULE_VIOLATION,
                    "Fichier illisible : format Excel attendu (.xls ou .xlsx). Détail : " + e.getMessage());
        }
    }

    private ReleveArchiveDto lireFeuille(Sheet sheet, String nomFichier) {
        ReleveArchiveDto dto = new ReleveArchiveDto();
        dto.setNomFichier(nomFichier);
        dto.setAnomalies(new ArrayList<>());

        dto.setNif(extraireApresPrefixe(sheet, "NIF"));
        dto.setReferenceMarche(extraireReferenceMarche(sheet));
        dto.setNumeroCredit(valeurSousEntete(sheet, "N° Credit"));
        dto.setDateCredit(dateSousEntete(sheet, "Date", "N° Credit"));
        dto.setMontantMarche(montantSousEntete(sheet, "Montant M"));

        dto.setCreditDouanier(montantApresLibelle(sheet, "Crédit douanier :", 0));
        dto.setCreditInterieur(montantApresLibelle(sheet, "Crédit intérieur :", 0));
        dto.setMontantCreditImpot(montantApresLibelle(sheet, "Montant du crédit d'impot :", 0));
        dto.setTransfertCredit(montantApresLibelle(sheet, "Transfert de crédit :", 0));
        dto.setTotalTransfertSortant(lireTotalTransfertSortant(sheet));

        // « Solde après utilisation » réutilise les mêmes libellés que les crédits initiaux :
        // on repart de la ligne du bloc pour ne pas retomber sur la première occurrence.
        int ligneSolde = trouverLigne(sheet, "Solde après utilisation");
        if (ligneSolde >= 0) {
            dto.setSoldeDouanierDeclare(montantApresLibelle(sheet, "Crédit douanier :", ligneSolde));
            dto.setSoldeInterieurDeclare(montantApresLibelle(sheet, "Crédit intérieur :", ligneSolde));
        }

        dto.setUtilisations(lireUtilisations(sheet, dto));
        controlerCoherence(dto);
        return dto;
    }

    // ------------------------------------------------------------------
    // Bloc des utilisations
    // ------------------------------------------------------------------

    private List<UtilisationArchiveDto> lireUtilisations(Sheet sheet, ReleveArchiveDto dto) {
        List<UtilisationArchiveDto> utilisations = new ArrayList<>();

        // « quittance » n'apparaît que dans l'en-tête du bloc des utilisations : c'est l'ancre la
        // plus sûre, celui des transferts porte les mêmes intitulés Date / Libellé.
        int ligneEntete = trouverLigne(sheet, "quittance");
        if (ligneEntete < 0) {
            dto.getAnomalies().add("Bloc des utilisations introuvable : l'en-tête « N° quittance » est absent.");
            return utilisations;
        }

        // Position des colonnes relevée sur la ligne d'en-tête, pour ne dépendre
        // d'aucun décalage fixe (l'export cale les blocs différemment selon les cas).
        Map<String, Integer> colonnes = indexerEntete(sheet.getRow(ligneEntete));
        Integer colDate = colonne(colonnes, "DATE");
        Integer colLibelle = colonne(colonnes, "LIBELLE");
        Integer colTvaInterieur = colonne(colonnes, "TVA INTERIEUR");
        Integer colDouane = colonne(colonnes, "DOUANE");
        Integer colQuittance = colonne(colonnes, "N QUITTANCE", "QUITTANCE");

        if (colDate == null || colLibelle == null || colDouane == null || colTvaInterieur == null) {
            dto.getAnomalies().add("En-tête du bloc des utilisations incomplet : colonnes attendues "
                    + "Date, Libellé, TVA intérieur, Douane.");
            return utilisations;
        }

        for (int i = ligneEntete + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            if (contientTexte(row, "Total utilisation")) {
                break;  // fin du bloc
            }
            String libelle = texte(row.getCell(colLibelle));
            if (libelle.isBlank()) {
                continue;
            }

            BigDecimal montantTva = montant(row.getCell(colTvaInterieur));
            BigDecimal montantDouane = montant(row.getCell(colDouane));
            boolean douaniere = montantDouane != null && montantDouane.signum() != 0;

            UtilisationArchiveDto u = new UtilisationArchiveDto();
            u.setLibelle(libelle);
            u.setNumeroQuittance(colQuittance != null ? emptyToNull(texte(row.getCell(colQuittance))) : null);
            u.setDate(date(row.getCell(colDate)));
            u.setDouaniere(douaniere);
            u.setMontant(douaniere ? montantDouane : montantTva);

            if (u.getMontant() == null || u.getMontant().signum() == 0) {
                dto.getAnomalies().add("Utilisation « " + libelle + " » ignorée : aucun montant renseigné.");
                continue;
            }
            if (douaniere) {
                u.setLignesTaxe(lireLignesTaxe(row, colonnes));
                u.setTotalPrisEnCharge(sommeParAffectation(u.getLignesTaxe(), "AU_CI"));
                u.setTotalAPayer(sommeParAffectation(u.getLignesTaxe(), "A_PAYER"));
            } else {
                u.setLignesTaxe(List.of());
            }
            utilisations.add(u);
        }
        return utilisations;
    }

    /** Une ligne de bulletin par taxe renseignée, l'affectation découlant du groupe de la taxe. */
    private List<LigneTaxeArchiveDto> lireLignesTaxe(Row row, Map<String, Integer> colonnes) {
        List<LigneTaxeArchiveDto> lignes = new ArrayList<>();
        ajouterLignes(lignes, row, colonnes, TAXES_AU_CI, "AU_CI");
        ajouterLignes(lignes, row, colonnes, TAXES_A_PAYER, "A_PAYER");
        return lignes;
    }

    private void ajouterLignes(List<LigneTaxeArchiveDto> cible, Row row, Map<String, Integer> colonnes,
                               List<String> codes, String affectation) {
        for (String code : codes) {
            Integer col = colonnes.get(code);
            if (col == null) {
                continue;
            }
            BigDecimal valeur = montant(row.getCell(col));
            if (valeur == null || valeur.signum() == 0) {
                continue;   // une taxe à zéro n'est pas une ligne de bulletin
            }
            LigneTaxeArchiveDto ligne = new LigneTaxeArchiveDto();
            ligne.setCodeTaxe(code);
            ligne.setValeur(valeur);
            ligne.setAffectation(affectation);
            cible.add(ligne);
        }
    }

    private BigDecimal sommeParAffectation(List<LigneTaxeArchiveDto> lignes, String affectation) {
        return lignes.stream()
                .filter(l -> affectation.equals(l.getAffectation()))
                .map(LigneTaxeArchiveDto::getValeur)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ------------------------------------------------------------------
    // Contrôles de cohérence — rapportés, jamais bloquants à la lecture
    // ------------------------------------------------------------------

    private void controlerCoherence(ReleveArchiveDto dto) {
        BigDecimal totalDouane = dto.getUtilisations().stream()
                .filter(UtilisationArchiveDto::isDouaniere)
                .map(UtilisationArchiveDto::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalInterieur = dto.getUtilisations().stream()
                .filter(u -> !u.isDouaniere())
                .map(UtilisationArchiveDto::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        dto.setTotalUtilisationsDouane(totalDouane);
        dto.setTotalUtilisationsInterieur(totalInterieur);

        // Sur une ligne douanière, la somme des taxes AU_CI doit égaler le montant imputé.
        for (UtilisationArchiveDto u : dto.getUtilisations()) {
            if (u.isDouaniere() && u.getTotalPrisEnCharge() != null
                    && u.getTotalPrisEnCharge().compareTo(u.getMontant()) != 0) {
                dto.getAnomalies().add("Utilisation « " + u.getLibelle() + " » : la somme des taxes prises en charge ("
                        + u.getTotalPrisEnCharge().toPlainString() + ") diffère du montant imputé ("
                        + u.getMontant().toPlainString() + ").");
            }
        }

        // Côté douanier, c'est l'intégralité du reliquat qui sort du crédit (part TVA transférée vers
        // l'intérieur + droits soldés), et non le seul montant affiché en « Transfert de crédit ».
        BigDecimal sortie = dto.getTotalTransfertSortant() != null
                ? dto.getTotalTransfertSortant()
                : dto.getTransfertCredit();
        dto.setSoldeDouanierCalcule(soustraire(dto.getCreditDouanier(), totalDouane, sortie));

        // Côté intérieur, seule la part transférable vient abonder le crédit.
        dto.setSoldeInterieurCalcule(
                additionner(dto.getCreditInterieur(), dto.getTransfertCredit(), totalInterieur.negate()));

        signalerEcart(dto, "douanier", dto.getSoldeDouanierDeclare(), dto.getSoldeDouanierCalcule());
        signalerEcart(dto, "intérieur", dto.getSoldeInterieurDeclare(), dto.getSoldeInterieurCalcule());
    }

    private void signalerEcart(ReleveArchiveDto dto, String libelle, BigDecimal declare, BigDecimal calcule) {
        if (declare == null || calcule == null || declare.compareTo(calcule) == 0) {
            return;
        }
        dto.getAnomalies().add("Solde " + libelle + " : le relevé indique " + declare.toPlainString()
                + " alors que le calcul (crédit − utilisations ± transfert) donne " + calcule.toPlainString()
                + ". Le montant retenu à l'import est celui du relevé.");
    }

    private BigDecimal soustraire(BigDecimal base, BigDecimal... aRetirer) {
        if (base == null) {
            return null;
        }
        BigDecimal r = base;
        for (BigDecimal v : aRetirer) {
            if (v != null) {
                r = r.subtract(v);
            }
        }
        return r;
    }

    private BigDecimal additionner(BigDecimal base, BigDecimal... aAjouter) {
        if (base == null) {
            return null;
        }
        BigDecimal r = base;
        for (BigDecimal v : aAjouter) {
            if (v != null) {
                r = r.add(v);
            }
        }
        return r;
    }

    // ------------------------------------------------------------------
    // Utilitaires de lecture
    // ------------------------------------------------------------------

    /** Indexe les intitulés d'une ligne d'en-tête (normalisés) vers leur numéro de colonne. */
    private Map<String, Integer> indexerEntete(Row row) {
        Map<String, Integer> index = new LinkedHashMap<>();
        if (row == null) {
            return index;
        }
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            String libelle = normaliser(texte(row.getCell(c)));
            if (!libelle.isBlank()) {
                index.putIfAbsent(libelle, c);
            }
        }
        return index;
    }

    /**
     * Référence du marché, débarrassée de son libellé d'en-tête.
     * « Marché public N° 2021 S.H.C.C.W.Z. N°11 » donne « 2021 S.H.C.C.W.Z. N°11 ».
     * Le motif tolère l'accent de « Marché » et les variantes du symbole numéro, sans toucher
     * au « N°11 » final qui appartient à la référence.
     */
    private String extraireReferenceMarche(Sheet sheet) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                String brut = texte(cell).trim();
                if (!normaliser(brut).startsWith("MARCHE PUBLIC")) {
                    continue;
                }
                String reference = brut.replaceFirst("(?iu)^\\s*march\\S*\\s+public\\s+n[^\\s\\w]?\\s*", "");
                return emptyToNull(reference.trim());
            }
        }
        return null;
    }

    /**
     * Total sorti du crédit douanier, lu dans le bloc « Détailles transfert » qui le ventile par taxe.
     *
     * <p>La colonne {@code TVADD} y reprend la valeur de {@code TVA} : elle est exclue du cumul pour
     * ne pas compter deux fois la part transférable. Le total ainsi obtenu couvre à la fois la part
     * versée au crédit intérieur et les droits soldés sans transfert.
     */
    private BigDecimal lireTotalTransfertSortant(Sheet sheet) {
        // Ancrage sur le titre du bloc, pas sur « TVADD » : ce libellé figure aussi dans l'en-tête
        // « Totaux » situé plus haut, dont les lignes « Initiaux » et « Solde » seraient sommées à tort.
        int ligneTitre = trouverLigneTransfert(sheet);
        if (ligneTitre < 0) {
            return null;
        }
        int ligneEntete = ligneTitre + 1;
        if (sheet.getRow(ligneEntete) == null) {
            return null;
        }
        Map<String, Integer> colonnes = indexerEntete(sheet.getRow(ligneEntete));
        BigDecimal total = BigDecimal.ZERO;
        boolean auMoinsUneValeur = false;

        for (int i = ligneEntete + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                break;   // le bloc s'arrête à la première ligne vide
            }
            BigDecimal ligne = BigDecimal.ZERO;
            boolean renseignee = false;
            for (String code : TAXES_AU_CI) {   // DD, TVA, RS, PC, PSC, TCO, TT — TVADD volontairement exclu
                Integer col = colonnes.get(code);
                if (col == null) {
                    continue;
                }
                BigDecimal valeur = montant(row.getCell(col));
                if (valeur != null) {
                    ligne = ligne.add(valeur);
                    renseignee = true;
                }
            }
            if (!renseignee) {
                break;
            }
            total = total.add(ligne);
            auMoinsUneValeur = true;
        }
        return auMoinsUneValeur ? total : null;
    }

    /** Texte d'une cellule dont le contenu commence par un préfixe (ex. « NIF 90600034 »). */
    private String extraireApresPrefixe(Sheet sheet, String prefixe) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                String t = texte(cell).trim();
                if (t.regionMatches(true, 0, prefixe, 0, prefixe.length()) && t.length() > prefixe.length()) {
                    return t.substring(prefixe.length()).trim();
                }
            }
        }
        return null;
    }

    /** Valeur placée juste sous un intitulé de colonne. */
    private String valeurSousEntete(Sheet sheet, String entete) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (normaliser(texte(cell)).equals(normaliser(entete))) {
                    Row suivante = sheet.getRow(row.getRowNum() + 1);
                    return suivante != null ? emptyToNull(texte(suivante.getCell(cell.getColumnIndex()))) : null;
                }
            }
        }
        return null;
    }

    private BigDecimal montantSousEntete(Sheet sheet, String entete) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (normaliser(texte(cell)).startsWith(normaliser(entete))) {
                    Row suivante = sheet.getRow(row.getRowNum() + 1);
                    return suivante != null ? montant(suivante.getCell(cell.getColumnIndex())) : null;
                }
            }
        }
        return null;
    }

    private Instant dateSousEntete(Sheet sheet, String entete, String enteteVoisin) {
        for (Row row : sheet) {
            if (!contientTexte(row, enteteVoisin)) {
                continue;   // ancre sur la bonne ligne d'en-tête, « Date » étant trop fréquent
            }
            for (Cell cell : row) {
                if (normaliser(texte(cell)).equals(normaliser(entete))) {
                    Row suivante = sheet.getRow(row.getRowNum() + 1);
                    return suivante != null ? date(suivante.getCell(cell.getColumnIndex())) : null;
                }
            }
        }
        return null;
    }

    /** Premier montant non vide à droite d'un libellé, à partir de la ligne indiquée. */
    private BigDecimal montantApresLibelle(Sheet sheet, String libelle, int aPartirDeLaLigne) {
        String cible = normaliser(libelle);
        for (int i = Math.max(0, aPartirDeLaLigne); i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            for (Cell cell : row) {
                if (!normaliser(texte(cell)).startsWith(cible)) {
                    continue;
                }
                for (int c = cell.getColumnIndex() + 1; c < row.getLastCellNum(); c++) {
                    BigDecimal v = montant(row.getCell(c));
                    if (v != null) {
                        return v;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Ligne du titre du bloc de transfert. L'export l'orthographie « Détailles transfert » (sic) ;
     * la recherche porte donc sur la présence conjointe de « DETAIL » et « TRANSFERT » afin de
     * résister à une correction ultérieure de cette coquille.
     */
    private int trouverLigneTransfert(Sheet sheet) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                String t = normaliser(texte(cell));
                if (t.contains("DETAIL") && t.contains("TRANSFERT")) {
                    return row.getRowNum();
                }
            }
        }
        return -1;
    }

    private int trouverLigne(Sheet sheet, String fragment) {
        for (Row row : sheet) {
            if (contientTexte(row, fragment)) {
                return row.getRowNum();
            }
        }
        return -1;
    }

    private boolean contientTexte(Row row, String fragment) {
        String cible = normaliser(fragment);
        for (Cell cell : row) {
            if (normaliser(texte(cell)).contains(cible)) {
                return true;
            }
        }
        return false;
    }

    private String texte(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? LocalDate.ofInstant(cell.getDateCellValue().toInstant(), ZoneOffset.UTC).format(JJ_MM_AAAA)
                    : BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> lireFormule(cell);
            default -> "";
        };
    }

    private String lireFormule(Cell cell) {
        try {
            return cell.getStringCellValue().trim();
        } catch (IllegalStateException e) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
    }

    /**
     * Montant au format de l'export : séparateur de milliers « . » et décimal « , »
     * (ex. {@code 1.200.000.000,00}), ou valeur numérique brute.
     */
    private BigDecimal montant(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        String brut = texte(cell).replace(" ", "").replace(" ", "");
        if (brut.isBlank()) {
            return null;
        }
        if (brut.contains(",")) {
            brut = brut.replace(".", "").replace(",", ".");
        }
        try {
            return new BigDecimal(brut);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Instant date(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue().toInstant();
        }
        String brut = texte(cell).trim();
        if (brut.isBlank() || brut.startsWith("00/00")) {
            return null;   // date nulle de l'ancienne application
        }
        try {
            return LocalDate.parse(brut, JJ_MM_AAAA).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Forme comparable d'un intitulé : majuscules, sans accent, ponctuation réduite à des espaces.
     *
     * <p>Indispensable car l'ancienne application n'émet pas toujours le même caractère pour le
     * symbole « numéro » ({@code °} ou {@code º} selon l'export) : un ancrage littéral échouerait
     * silencieusement. « N° quittance » et « Nº quittance » donnent tous deux {@code N QUITTANCE}.
     */
    private String normaliser(String valeur) {
        if (valeur == null) {
            return "";
        }
        String sansAccent = Normalizer.normalize(valeur, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return sansAccent.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", " ")
                .trim();
    }

    private String emptyToNull(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur;
    }

    /**
     * Numéro de colonne dont l'intitulé normalisé correspond à l'un des libellés donnés
     * (égalité d'abord, puis préfixe — « LIBELLE » retrouve ainsi « LIBELLE N U »).
     */
    private Integer colonne(Map<String, Integer> entete, String... libelles) {
        for (String libelle : libelles) {
            String cible = normaliser(libelle);
            Integer exact = entete.get(cible);
            if (exact != null) {
                return exact;
            }
            for (Map.Entry<String, Integer> e : entete.entrySet()) {
                if (e.getKey().startsWith(cible)) {
                    return e.getValue();
                }
            }
        }
        return null;
    }
}
