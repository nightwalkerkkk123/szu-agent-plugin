package edu.szu.agent.account;

/**
 * Thrown when credentials cannot be resolved for a given student ID.
 *
 * <p>Per ADR-0005 D1: the three-layer lookup (process env &gt;
 * {@code --env-file} &gt; Skill injection) exhausted all sources
 * without finding a password.
 *
 * @since 0.6.0
 * @author 王子豪
 */
public class AccountResolutionException extends RuntimeException {

    private final String studentId;

    public AccountResolutionException(String studentId) {
        super("No credential found for student ID: " + studentId
            + " (checked: process env, --env-file, Skill injection)");
        this.studentId = studentId;
    }

    public String studentId() {
        return studentId;
    }
}
