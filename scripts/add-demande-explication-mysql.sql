-- Fil de discussion « demande d'explication » (commission interne)
-- À exécuter sur MySQL si ddl-auto ne crée pas les tables automatiquement.

CREATE TABLE IF NOT EXISTS demande_explication (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contexte VARCHAR(32) NOT NULL,
    demande_correction_id BIGINT NULL,
    certificat_credit_id BIGINT NULL,
    utilisation_credit_id BIGINT NULL,
    role_destinataire VARCHAR(32) NOT NULL,
    message_initial VARCHAR(2000) NOT NULL,
    statut VARCHAR(16) NOT NULL,
    auteur_id BIGINT NOT NULL,
    role_auteur VARCHAR(32) NOT NULL,
    date_ouverture DATETIME(6) NOT NULL,
    date_fermeture DATETIME(6) NULL,
    CONSTRAINT fk_explication_demande FOREIGN KEY (demande_correction_id) REFERENCES demande_correction(id),
    CONSTRAINT fk_explication_certificat FOREIGN KEY (certificat_credit_id) REFERENCES certificat_credit(id),
    CONSTRAINT fk_explication_utilisation FOREIGN KEY (utilisation_credit_id) REFERENCES utilisation_credit(id),
    CONSTRAINT fk_explication_auteur FOREIGN KEY (auteur_id) REFERENCES utilisateur(id)
);

CREATE TABLE IF NOT EXISTS demande_explication_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    demande_explication_id BIGINT NOT NULL,
    message VARCHAR(2000) NOT NULL,
    auteur_id BIGINT NOT NULL,
    role_auteur VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_explication_msg_thread FOREIGN KEY (demande_explication_id) REFERENCES demande_explication(id) ON DELETE CASCADE,
    CONSTRAINT fk_explication_msg_auteur FOREIGN KEY (auteur_id) REFERENCES utilisateur(id)
);

CREATE INDEX idx_explication_demande ON demande_explication(demande_correction_id);
CREATE INDEX idx_explication_certificat ON demande_explication(certificat_credit_id);
CREATE INDEX idx_explication_utilisation ON demande_explication(utilisation_credit_id);

-- Notifications : autoriser le type DEMANDE_EXPLICATION (voir alter-notification-type-column-mysql.sql)
ALTER TABLE notification MODIFY COLUMN type VARCHAR(64) NOT NULL;
ALTER TABLE notification MODIFY COLUMN entity_type VARCHAR(64) NOT NULL;
