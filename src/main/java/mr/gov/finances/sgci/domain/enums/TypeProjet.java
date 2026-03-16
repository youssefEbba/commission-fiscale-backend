package mr.gov.finances.sgci.domain.enums;

public enum TypeProjet {
    BTP("BTP", "Projet BTP (GTS Lot 2A)"),
    EQUIPEMENT("EQUIP", "Équipements industriels (AAOI/KFW)");

    private final String code;
    private final String description;

    TypeProjet(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
