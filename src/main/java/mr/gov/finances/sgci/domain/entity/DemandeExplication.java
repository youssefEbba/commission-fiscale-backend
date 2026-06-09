package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import mr.gov.finances.sgci.domain.enums.ContexteExplication;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.StatutExplication;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "demande_explication")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemandeExplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ContexteExplication contexte;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demande_correction_id")
    private DemandeCorrection demandeCorrection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificat_credit_id")
    private CertificatCredit certificatCredit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisation_credit_id")
    private UtilisationCredit utilisationCredit;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_destinataire", nullable = false, length = 32)
    private Role roleDestinataire;

    @Column(name = "message_initial", nullable = false, length = 2000)
    private String messageInitial;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private StatutExplication statut = StatutExplication.OUVERTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auteur_id", nullable = false)
    private Utilisateur auteur;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_auteur", nullable = false, length = 32)
    private Role roleAuteur;

    @Column(name = "date_ouverture", nullable = false)
    private Instant dateOuverture;

    @Column(name = "date_fermeture")
    private Instant dateFermeture;

    @OneToMany(mappedBy = "demandeExplication", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<DemandeExplicationMessage> messages = new ArrayList<>();
}
