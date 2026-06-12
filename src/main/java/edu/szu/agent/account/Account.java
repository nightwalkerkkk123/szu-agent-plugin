package edu.szu.agent.account;

/**
 * Credential record — student ID + password.
 *
 * <p>Per ADR-0005 D1: resolved by {@link AccountResolver} from
 * three-layer lookup (process env &gt; {@code --env-file} &gt; Skill injection).
 *
 * <p>Programming technique: record (Java 16+, immutable value type).
 *
 * // 编程技术: Record(Java 16+,不可变值类型)
 *
 * @param studentId   student ID (e.g. "2023150090")
 * @param password    ehall/CAS password
 * @param displayName optional display name for logging (masked by LogMasker)
 * @since 0.1.0
 * @author 王子豪
 */
public record Account(String studentId, String password, String displayName) {

    /**
     * Compact constructor with non-null validation.
     */
    public Account {
        if (studentId == null || studentId.isBlank()) {
            throw new IllegalArgumentException("Account.studentId must not be blank");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Account.password must not be blank");
        }
        if (displayName == null) {
            displayName = studentId;
        }
    }
}