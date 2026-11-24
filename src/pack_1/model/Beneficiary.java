package pack_1.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Beneficiary entity representing saved transfer recipients.
 */
public class Beneficiary {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private long beneficiaryId;
    private final String userId;
    private final String beneficiaryAccountNo;
    private final String beneficiaryName;
    private final String nickname;
    private final String createdAt;

    public Beneficiary(String userId, String beneficiaryAccountNo, String beneficiaryName, String nickname) {
        this(0, userId, beneficiaryAccountNo, beneficiaryName, nickname, LocalDateTime.now().format(FMT));
    }

    public Beneficiary(long beneficiaryId, String userId, String beneficiaryAccountNo,
                       String beneficiaryName, String nickname, String createdAt) {
        this.beneficiaryId = beneficiaryId;
        this.userId = userId;
        this.beneficiaryAccountNo = beneficiaryAccountNo;
        this.beneficiaryName = beneficiaryName;
        this.nickname = nickname;
        this.createdAt = createdAt;
    }

    public long getBeneficiaryId() {
        return beneficiaryId;
    }

    public void setBeneficiaryId(long beneficiaryId) {
        this.beneficiaryId = beneficiaryId;
    }

    public String getUserId() {
        return userId;
    }

    public String getBeneficiaryAccountNo() {
        return beneficiaryAccountNo;
    }

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public String getNickname() {
        return nickname;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
