package mr.gov.finances.sgci;

import mr.gov.finances.sgci.domain.entity.Utilisateur;
import mr.gov.finances.sgci.domain.enums.Role;
import mr.gov.finances.sgci.domain.enums.TypeEmpreinte;
import mr.gov.finances.sgci.repository.SignatureRepository;
import mr.gov.finances.sgci.repository.UtilisateurRepository;
import mr.gov.finances.sgci.security.AuthenticatedUser;
import mr.gov.finances.sgci.service.SignatureService;
import mr.gov.finances.sgci.web.dto.MesEmpreintesDto;
import mr.gov.finances.sgci.web.dto.SignatureDto;
import mr.gov.finances.sgci.web.exception.ApiErrorCode;
import mr.gov.finances.sgci.web.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Empreintes PNG : validation, coexistence signature/cachet, profil, contrôle d'accès en lecture.
 *
 * <p>Module jusqu'ici sans aucune couverture, alors qu'il porte la logique la plus testable du projet
 * (validation PNG par magic number) et qu'il devient porteur de valeur juridique.
 *
 * <p>Test au niveau service, et non HTTP : l'environnement de développement ne permet pas de démarrer
 * Tomcat ({@code Unable to establish loopback connection}), ce qui fait échouer tous les tests
 * {@code RANDOM_PORT} indépendamment du code. Les règles vérifiées ici — accès en lecture,
 * portée du type, résolution par repli — sont décidées dans le service ; le contrôleur ne fait que
 * relayer le statut et le code de l'{@link ApiException}.
 */
@SpringBootTest
@ActiveProfiles("test")
class SignatureEmpreinteIT {

    /** PNG 1×1 valide, 67 octets — aucun fichier de fixture nécessaire. */
    private static final byte[] PNG_1X1 = hex(
            "89504E470D0A1A0A0000000D49484452000000010000000108060000001F15C489"
            + "0000000A49444154789C63000100000500010D0A2DB40000000049454E44AE426082");

    @Autowired
    private SignatureService service;

    @Autowired
    private SignatureRepository signatureRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    private AuthenticatedUser president;
    private AuthenticatedUser admin;
    private AuthenticatedUser entreprise;
    private AuthenticatedUser dgb;

    @BeforeEach
    void setUp() {
        president = principal("president");
        admin = principal("admin");
        entreprise = principal("entreprise");
        dgb = principal("dgb");
    }

    // ── validation PNG ─────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void pngValide_accepte_avecDimensionsEtChecksum() {
        SignatureDto dto = deposer(president, PNG_1X1, TypeEmpreinte.SIGNATURE);

        assertThat(dto.getType()).isEqualTo(TypeEmpreinte.SIGNATURE);
        assertThat(dto.getLargeurPx()).isEqualTo(1);
        assertThat(dto.getHauteurPx()).isEqualTo(1);
        assertThat(dto.getChecksumSha256()).isNotBlank();
        assertThat(dto.getContentType()).isEqualTo("image/png");
        assertThat(dto.getActive()).isTrue();
    }

    @Test
    @Transactional
    void contentTypeMenteur_rejete_leMagicNumberFaitFoi() {
        // Déclaré image/png, contenu JPEG : c'est le magic number qui tranche.
        byte[] faux = hex("FFD8FFE000104A46494600010100000100010000FFD9000000000000000000000000");

        assertThatThrownBy(() -> deposer(president, faux, TypeEmpreinte.SIGNATURE))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.getStatus()).isEqualTo(400);
                    assertThat(api.getCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
                });
    }

    @Test
    @Transactional
    void fichierTropVolumineux_rejete() {
        byte[] gros = new byte[1_048_577];
        System.arraycopy(PNG_1X1, 0, gros, 0, PNG_1X1.length);

        assertThatThrownBy(() -> deposer(president, gros, TypeEmpreinte.SIGNATURE))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ApiErrorCode.FILE_TOO_LARGE));
    }

    @Test
    @Transactional
    void dimensionsExcessives_rejetees() {
        byte[] large = PNG_1X1.clone();
        // IHDR : largeur sur 4 octets big-endian à l'offset 16 → 3000 px.
        large[16] = 0;
        large[17] = 0;
        large[18] = (byte) 0x0B;
        large[19] = (byte) 0xB8;

        assertThatThrownBy(() -> deposer(president, large, TypeEmpreinte.SIGNATURE))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode())
                        .isEqualTo(ApiErrorCode.VALIDATION_FAILED));
    }

    // ── coexistence signature / cachet ─────────────────────────────────────────────────────────

    @Test
    @Transactional
    void cachetEtSignatureCoexistent_etLaResolutionNeTombePas() {
        Long idSignature = deposer(president, PNG_1X1, TypeEmpreinte.SIGNATURE).getId();
        Long idCachet = deposer(president, PNG_1X1, TypeEmpreinte.CACHET).getId();

        // Le dépôt du cachet n'a pas désactivé la signature : la clé d'unicité inclut le type.
        assertThat(signatureRepository.findById(idSignature).orElseThrow().getActive()).isTrue();
        assertThat(signatureRepository.findById(idCachet).orElseThrow().getActive()).isTrue();

        // Sans type, la résolution rend la SIGNATURE — et ne tombe pas alors que deux lignes du même
        // couple (role, utilisateur) sont légitimement actives.
        SignatureDto sansType = service.getActive(null, Role.PRESIDENT, president.getUserId(), president);
        assertThat(sansType.getType()).isEqualTo(TypeEmpreinte.SIGNATURE);

        SignatureDto avecType = service.getActive(TypeEmpreinte.CACHET, Role.PRESIDENT,
                president.getUserId(), president);
        assertThat(avecType.getType()).isEqualTo(TypeEmpreinte.CACHET);
    }

    @Test
    @Transactional
    void secondDepotDeSignature_desactiveLaPremiere_maisPasLeCachet() {
        Long premiere = deposer(president, PNG_1X1, TypeEmpreinte.SIGNATURE).getId();
        Long cachet = deposer(president, PNG_1X1, TypeEmpreinte.CACHET).getId();
        Long seconde = deposer(president, PNG_1X1, TypeEmpreinte.SIGNATURE).getId();

        assertThat(signatureRepository.findById(premiere).orElseThrow().getActive()).isFalse();
        assertThat(signatureRepository.findById(seconde).orElseThrow().getActive()).isTrue();
        assertThat(signatureRepository.findById(cachet).orElseThrow().getActive()).isTrue();
    }

    @Test
    @Transactional
    void typeAbsent_valautSignature_rétrocompatibiliteDuContratExistant() {
        SignatureDto dto = deposer(president, PNG_1X1, null);

        assertThat(dto.getType()).isEqualTo(TypeEmpreinte.SIGNATURE);
    }

    // ── profil ─────────────────────────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void mesEmpreintes_rendLesDeuxEnUnAppel() {
        deposer(president, PNG_1X1, TypeEmpreinte.SIGNATURE);
        deposer(president, PNG_1X1, TypeEmpreinte.CACHET);

        MesEmpreintesDto sansContenu = service.mesEmpreintes(president, false);
        assertThat(sansContenu.getSignature()).isNotNull();
        assertThat(sansContenu.getCachet()).isNotNull();
        assertThat(sansContenu.getSignatureDataUrl()).isNull();
        assertThat(sansContenu.getCachetDataUrl()).isNull();

        MesEmpreintesDto avecContenu = service.mesEmpreintes(president, true);
        assertThat(avecContenu.getSignatureDataUrl()).startsWith("data:image/png;base64,");
        assertThat(avecContenu.getCachetDataUrl()).startsWith("data:image/png;base64,");
    }

    @Test
    @Transactional
    void mesEmpreintes_empreinteAbsente_rendNullSansErreur() {
        // Le DGB n'a déposé aucune empreinte : le front doit pouvoir le constater sans 404, pour
        // n'offrir que le chemin manuel.
        MesEmpreintesDto resp = service.mesEmpreintes(dgb, true);

        assertThat(resp.getSignature()).isNull();
        assertThat(resp.getCachet()).isNull();
        assertThat(resp.getSignatureDataUrl()).isNull();
    }

    @Test
    @Transactional
    void cachetGeneriqueDuRole_serviParRepli() {
        // Sceau institutionnel posé par l'administrateur, sans utilisateur rattaché.
        SignatureDto sceau = service.create(fichier(PNG_1X1), TypeEmpreinte.CACHET, Role.PRESIDENT,
                null, "Sceau de la commission", Boolean.TRUE, admin);
        assertThat(sceau.getUtilisateurId()).isNull();

        MesEmpreintesDto resp = service.mesEmpreintes(president, false);

        assertThat(resp.getCachet()).isNotNull();
        assertThat(resp.getCachet().getId()).isEqualTo(sceau.getId());
        assertThat(resp.isCachetGeneriqueDuRole()).isTrue();
    }

    @Test
    @Transactional
    void cachetPersonnel_lEmporteSurLeSceauDuRole() {
        service.create(fichier(PNG_1X1), TypeEmpreinte.CACHET, Role.PRESIDENT, null,
                "Sceau de la commission", Boolean.TRUE, admin);
        SignatureDto personnel = deposer(president, PNG_1X1, TypeEmpreinte.CACHET);

        MesEmpreintesDto resp = service.mesEmpreintes(president, false);

        assertThat(resp.getCachet().getId()).isEqualTo(personnel.getId());
        assertThat(resp.isCachetGeneriqueDuRole()).isFalse();
    }

    // ── contrôle d'accès en lecture ────────────────────────────────────────────────────────────

    @Test
    @Transactional
    void entreprise_nePeutPasLireLaSignatureDuPresident() {
        Long id = deposer(president, PNG_1X1, TypeEmpreinte.SIGNATURE).getId();

        assertThatThrownBy(() -> service.getContent(id, entreprise))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    // 404 et non 403 : pas d'oracle d'énumération.
                    assertThat(api.getStatus()).isEqualTo(404);
                    assertThat(api.getCode()).isEqualTo(ApiErrorCode.RESOURCE_NOT_FOUND);
                });
        assertThatThrownBy(() -> service.getBase64(id, entreprise))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @Transactional
    void entreprise_neVoitPasLaSignatureDuPresidentDansLaListe() {
        Long id = deposer(president, PNG_1X1, TypeEmpreinte.SIGNATURE).getId();

        List<SignatureDto> vues = service.list(null, null, null, null, entreprise);

        assertThat(vues).extracting(SignatureDto::getId).doesNotContain(id);
    }

    @Test
    @Transactional
    void titulaireEtAdministrateur_peuventLire() {
        Long id = deposer(president, PNG_1X1, TypeEmpreinte.SIGNATURE).getId();

        assertThat(service.getContent(id, president)).isNotEmpty();
        assertThat(service.getContent(id, admin)).isNotEmpty();
        assertThat(service.list(null, null, null, null, president))
                .extracting(SignatureDto::getId).contains(id);
    }

    @Test
    @Transactional
    void versionDesactivee_invisibleAuxTiers_maisLisibleParSonTitulaire() {
        Long remplacee = deposer(president, PNG_1X1, TypeEmpreinte.SIGNATURE).getId();
        deposer(president, PNG_1X1, TypeEmpreinte.SIGNATURE);
        assertThat(signatureRepository.findById(remplacee).orElseThrow().getActive()).isFalse();

        assertThatThrownBy(() -> service.getBase64(remplacee, entreprise))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(404));
        assertThat(service.getBase64(remplacee, president).getDataUrl())
                .startsWith("data:image/png;base64,");
    }

    @Test
    @Transactional
    void empreinteGeneriqueDeSonRole_lisible_parLeTitulaireDeLaFonction() {
        SignatureDto sceau = service.create(fichier(PNG_1X1), TypeEmpreinte.CACHET, Role.PRESIDENT,
                null, "Sceau", Boolean.TRUE, admin);

        assertThat(service.getContent(sceau.getId(), president)).isNotEmpty();
        // Mais pas par un autre rôle.
        assertThatThrownBy(() -> service.getContent(sceau.getId(), dgb))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(404));
    }

    @Test
    @Transactional
    void gestion_refuseeSurLempreinteDautrui() {
        assertThatThrownBy(() -> service.create(fichier(PNG_1X1), TypeEmpreinte.SIGNATURE,
                Role.PRESIDENT, president.getUserId(), null, Boolean.TRUE, dgb))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.getStatus()).isEqualTo(403);
                    assertThat(api.getCode()).isEqualTo(ApiErrorCode.ROLE_FORBIDDEN);
                });
    }

    // ── utilitaires ────────────────────────────────────────────────────────────────────────────

    private AuthenticatedUser principal(String username) {
        Utilisateur u = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Compte seed absent: " + username));
        return new AuthenticatedUser(u.getId(), u.getUsername(), u.getRole());
    }

    private SignatureDto deposer(AuthenticatedUser user, byte[] contenu, TypeEmpreinte type) {
        return service.createPourMoi(fichier(contenu), type, null, user);
    }

    private static MockMultipartFile fichier(byte[] contenu) {
        return new MockMultipartFile("file", "empreinte.png", "image/png", contenu);
    }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
