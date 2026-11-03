package mr.gov.finances.sgci.service.archive;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Construit un export de période au format de l'ancienne application (.xls, feuille « EXPORT
 * GENERIQUE »), bloc par bloc, avec la mise en page relevée sur les vrais fichiers : les vrais
 * fichiers contiennent des données de contribuables et ne sont pas versionnés.
 *
 * <p>Les montants sont écrits comme dans l'export : en texte « 1.234,56 », sauf les soldes après
 * utilisation douaniers, en nombre brut.
 */
final class ExportPeriodeFixture {

    record Utilisation(String date, String libelle, String tvaInterieur, String douane, String... taxes) {
        /** Taxes : DD, TVA, RS, PC, PSC, TCO, TT, TTI, RIF, IMF. */
    }

    record Bloc(String nif, String referenceMarche, String duree, String dateCredit, String numeroCredit,
                String montantMarche, String montantCredit, String transfert, String creditDouanier,
                String creditInterieur, String soldeDouanier, String soldeInterieur,
                String[] ligneTransfert, List<Utilisation> utilisations,
                /* totaux initiaux par taxe : DD, TVADD, RS, PC, PSC, TCO, TT */ String[] totaux) {
    }

    private final List<Bloc> blocs = new ArrayList<>();

    ExportPeriodeFixture bloc(Bloc bloc) {
        blocs.add(bloc);
        return this;
    }

    MockMultipartFile fichier(String nom) {
        try (Workbook wb = new HSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("EXPORT GENERIQUE");
            int r = 0;
            for (Bloc b : blocs) {
                r = ecrire(sheet, r, b) + 1;
            }
            wb.write(out);
            return new MockMultipartFile("fichier", nom, "application/vnd.ms-excel", out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private int ecrire(Sheet s, int r, Bloc b) {
        set(s, r++, 0, "NIF " + b.nif());
        set(s, r++, 0, "Marché public N° " + b.referenceMarche());
        set(s, r++, 0, "Durée " + b.duree());
        set(s, r, 0, "Date"); set(s, r, 1, "N° Credit"); set(s, r, 2, "Montant M"); set(s, r++, 8, "Activités");
        set(s, r, 0, b.dateCredit()); set(s, r, 1, b.numeroCredit()); set(s, r++, 2, b.montantMarche());
        set(s, r++, 5, "Initiaux ");
        String[] totaux = {"DD : ", "TVADD : ", "RS : ", "PC : ", "PSC : ", "TCO : ", "TT : "};
        set(s, r, 3, "Totaux:");
        for (int i = 0; i < totaux.length; i++) {
            set(s, r, 4 + i, totaux[i]);
        }
        r++;
        // Ligne des totaux initiaux par taxe, sous l'en-tête « Totaux: ».
        for (int i = 0; b.totaux() != null && i < b.totaux().length; i++) {
            set(s, r, 4 + i, b.totaux()[i]);
        }
        r++;
        set(s, r++, 3, "Solde = ");
        r++;
        set(s, r, 3, "Montant du crédit d'impot : "); set(s, r, 4, b.montantCredit());
        set(s, r, 7, "Transfert de crédit : "); set(s, r++, 8, b.transfert());
        r++;
        set(s, r, 3, "Crédit douanier : "); set(s, r++, 4, b.creditDouanier());
        set(s, r, 3, "Crédit intérieur : "); set(s, r++, 4, b.creditInterieur());
        r++;
        set(s, r, 3, "Solde après utilisation : "); set(s, r++, 7, "Détailles transfert");
        set(s, r, 3, "Crédit douanier : "); set(s, r, 4, b.soldeDouanier());
        String[] enteteTransfert = {"Date", "Libellé", "TVADD", "DD", "TVA", "RS", "PC", "PSC", "TCO", "TT"};
        for (int i = 0; i < enteteTransfert.length; i++) {
            set(s, r, 6 + i, enteteTransfert[i]);
        }
        r++;
        set(s, r, 3, "Crédit intérieur : "); set(s, r, 4, b.soldeInterieur());
        // Ligne du transfert : date, libellé, TVADD, DD, TVA, RS, PC, PSC, TCO, TT.
        for (int i = 0; i < b.ligneTransfert().length; i++) {
            set(s, r, 6 + i, b.ligneTransfert()[i]);
        }
        r++;
        r += 2;
        set(s, r++, 6, "Utililisations du crédit d'impot");
        String[] entete = {"Date", "N° quittance", "Libellé | N°U", "TVA intérieur", "Douane",
                "DD", "TVA", "RS", "PC", "PSC", "TCO", "TT", "TTI", "RIF", "IMF"};
        for (int i = 0; i < entete.length; i++) {
            set(s, r, 3 + i, entete[i]);
        }
        r++;
        for (Utilisation u : b.utilisations()) {
            set(s, r, 3, u.date());
            set(s, r, 5, u.libelle());
            set(s, r, 6, u.tvaInterieur());
            set(s, r, 7, u.douane());
            for (int i = 0; i < u.taxes().length; i++) {
                set(s, r, 8 + i, u.taxes()[i]);
            }
            r++;
        }
        set(s, r++, 5, "Total utilisation = ");
        return r;
    }

    private static void set(Sheet s, int r, int c, String v) {
        if (v == null) {
            return;
        }
        Row row = s.getRow(r) != null ? s.getRow(r) : s.createRow(r);
        row.createCell(c).setCellValue(v);
    }

    // ------------------------------------------------------------------
    // Blocs types, tirés des vrais exports (montants vérifiés à la main)
    // ------------------------------------------------------------------

    private static final String[] SANS_TRANSFERT = {"//", "", "", "0,00", "0,00", "0,00", "0,00", "0,00", "0,00", "0,00"};

    /** Crédit avec transfert : le reliquat douanier sort en entier, la part TVA abonde l'intérieur. */
    static Bloc avecTransfert(String nif, String numeroCredit, String referenceMarche) {
        return new Bloc(nif, referenceMarche, "75 JOURS", "26/01/2023", numeroCredit, "14.260.063,00",
                "3.703.190,08", "1.024.880,00", "2.250.380,00", "1.452.810,08", "0", "2.430.379,73",
                new String[]{"00/00/0000", "", "1024880", "1.036.000,00", "1.024.880,00", "51.800,00",
                        "25.900,00", "51.800,00", "60.000,00", "0,00"},
                List.of(new Utilisation("04/04/2023", "UT 1 | 1/" + numeroCredit, "31862.07", ""),
                        new Utilisation("04/04/2023", "UT 2 | 2/" + numeroCredit, "15448.28", "")),
                new String[]{"1.036.000,00", "1.024.880,00", "51.800,00", "25.900,00", "51.800,00", "60.000,00", "0,00"});
    }

    /** Crédit consommé en douane, sans transfert ; solde douanier écrit en nombre brut. */
    static Bloc douanier(String nif, String numeroCredit, String referenceMarche) {
        return new Bloc(nif, referenceMarche, "07 MOIS", "08/01/2026", numeroCredit, "13.500.000,00",
                "3.846.280,57", "0,00", "2.716.362,45", "1.129.918,12", "1999332.09", "1.129.918,12",
                SANS_TRANSFERT,
                List.of(new Utilisation("04/03/2026", "UT 1 | 1/" + numeroCredit, "", "717030.36",
                        "340.632,00", "333.819,36", "17.031,60", "8.515,80", "17.031,60", "0,00", "0,00",
                        "11.400,00", "300,00", "41.727,42")),
                new String[]{"1.205.739,85", "1.262.672,30", "64.380,12", "32.190,06", "64.380,12", "87.000,00", "0,00"});
    }

    /** Crédit intérieur dont le solde affiché ne cadre pas avec le calcul (anomalie attendue). */
    static Bloc interieurEcart(String nif, String numeroCredit, String referenceMarche) {
        return new Bloc(nif, referenceMarche, "30 JOURS", "11/03/2026", numeroCredit, "9.044.444,00",
                "500.000,00", "0,00", "0,00", "500.000,00", "0", "450.000,00",
                SANS_TRANSFERT,
                List.of(new Utilisation("26/03/2026", "UT 1 | 1/" + numeroCredit, "100000", "")),
                new String[]{"0,00", "0,00", "0,00", "0,00", "0,00", "0,00", "0,00"});
    }
}
