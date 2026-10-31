package mr.gov.finances.sgci.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le schéma hérité des pièces demandées par un rejet est remis d'aplomb.
 *
 * <p>Avant juin 2026, la pièce était une colonne ENUM {@code type_document}, dans la clé primaire.
 * Le passage à {@code code_document} l'a laissée en place ({@code ddl-auto} ne retire rien) : chaque
 * ligne y reçoit la même valeur par défaut, et un rejet réclamant deux pièces viole la clé —
 * « Conflit de données » en production, invisible sous H2 qui recrée son schéma. D'où un test de
 * l'instruction elle-même, faute de MySQL sur ce poste.
 */
class DecisionDocumentsDemandesLegacyMigrationTest {

    private static final String TABLE = "decision_correction_documents_demandes";
    private static final String PROPRIETAIRE = "decision_correction_id";

    @Test
    void schemaHerite_cleReconstruiteEtColonneRetiree() {
        String sql = DecisionDocumentsDemandesLegacyMigration.instruction(
                TABLE, PROPRIETAIRE, List.of(PROPRIETAIRE, "type_document"), true);

        assertThat(sql).isEqualTo("ALTER TABLE " + TABLE
                + " DROP PRIMARY KEY, DROP COLUMN type_document, ADD PRIMARY KEY (" + PROPRIETAIRE + ", code_document)");
    }

    @Test
    void colonneHeriteeHorsCle_seuleLaColonneEstRetiree() {
        String sql = DecisionDocumentsDemandesLegacyMigration.instruction(
                TABLE, PROPRIETAIRE, List.of(PROPRIETAIRE, "code_document"), true);

        assertThat(sql).isEqualTo("ALTER TABLE " + TABLE + " DROP COLUMN type_document");
    }

    @Test
    void sansCle_laCleEstPosee() {
        String sql = DecisionDocumentsDemandesLegacyMigration.instruction(TABLE, PROPRIETAIRE, List.of(), true);

        assertThat(sql).isEqualTo("ALTER TABLE " + TABLE
                + " DROP COLUMN type_document, ADD PRIMARY KEY (" + PROPRIETAIRE + ", code_document)");
    }

    @Test
    void schemaDejaConforme_rienNEstFait() {
        assertThat(DecisionDocumentsDemandesLegacyMigration.instruction(
                TABLE, PROPRIETAIRE, List.of(PROPRIETAIRE, "code_document"), false)).isNull();
    }

    @Test
    void lesQuatreCircuitsSontCouverts() {
        assertThat(DecisionDocumentsDemandesLegacyMigration.TABLES)
                .extracting(DecisionDocumentsDemandesLegacyMigration.TableCollection::table)
                .containsExactlyInAnyOrder(
                        "decision_correction_documents_demandes",
                        "decision_certificat_credit_documents_demandes",
                        "decision_utilisation_credit_documents_demandes",
                        "decision_transfert_credit_documents_demandes");
    }
}
