-- Demande de réinitialisation de mot de passe (validation admin)
CREATE TABLE IF NOT EXISTS demande_reset_password (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    utilisateur_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    statut VARCHAR(32) NOT NULL,
    date_creation DATETIME(6) NOT NULL,
    date_traitement DATETIME(6) NULL,
    traite_par_id BIGINT NULL,
    motif_refus VARCHAR(1000) NULL,
    CONSTRAINT fk_drp_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateur(id),
    CONSTRAINT fk_drp_traite_par FOREIGN KEY (traite_par_id) REFERENCES utilisateur(id)
);

CREATE INDEX idx_drp_statut ON demande_reset_password(statut);
CREATE INDEX idx_drp_utilisateur_statut ON demande_reset_password(utilisateur_id, statut);
