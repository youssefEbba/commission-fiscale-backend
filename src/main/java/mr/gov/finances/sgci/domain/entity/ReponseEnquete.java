package mr.gov.finances.sgci.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mr.gov.finances.sgci.domain.enums.ProfilFeedback;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** La participation d'un usager à une enquête : une seule par personne et par enquête. */
@Entity
@Table(name = "reponse_enquete", uniqueConstraints = @UniqueConstraint(
        name = "uk_reponse_enquete_utilisateur", columnNames = {"enquete_id", "utilisateurId"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReponseEnquete {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enquete_id")
    private EnqueteSatisfaction enquete;

    @Column(nullable = false)
    private Long utilisateurId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProfilFeedback profil;

    @Column(nullable = false)
    private Instant dateReponse;

    @OneToMany(mappedBy = "reponseEnquete", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReponseQuestion> reponses = new ArrayList<>();
}
