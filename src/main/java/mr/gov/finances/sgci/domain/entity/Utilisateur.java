package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.Role;

@Entity
@Table(name = "utilisateur")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autorite_contractante_id")
    private AutoriteContractante autoriteContractante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entreprise_id")
    private Entreprise entreprise;

    private String nomComplet;
    private String email;

    @Builder.Default
    private Boolean actif = true;

    /**
     * Numéro de session : chaque jeton porte celui du compte à sa création, et n'est accepté que
     * tant qu'il est le même. L'incrémenter ferme d'un coup toutes les sessions ouvertes —
     * déconnexion, changement de mot de passe. Nul sur les comptes antérieurs à la colonne : lu 0.
     */
    @Builder.Default
    @Column(name = "version_sessions")
    private Integer versionSessions = 0;

    public int versionSessionsCourante() {
        return versionSessions == null ? 0 : versionSessions;
    }

    public void revoquerSessions() {
        versionSessions = versionSessionsCourante() + 1;
    }

    /**
     * Changer le mot de passe ferme les sessions ouvertes : après un vol de session, c'est le geste
     * qui doit déconnecter le voleur. Placé ici pour qu'aucun chemin de changement ne l'oublie.
     */
    public void setPasswordHash(String passwordHash) {
        if (this.passwordHash != null && !this.passwordHash.equals(passwordHash)) {
            revoquerSessions();
        }
        this.passwordHash = passwordHash;
    }
}
