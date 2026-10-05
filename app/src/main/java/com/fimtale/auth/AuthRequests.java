package com.fimtale.auth;

/** Request bodies from ft-front/schema/openapi.json. */
public final class AuthRequests {
    private AuthRequests() {}

    public static final class PasswordLogin {
        public final String username, email, password;
        public PasswordLogin(String account, String password) {
            this.username = account.contains("@") ? null : account;
            this.email = account.contains("@") ? account : null;
            this.password = password;
        }
    }

    public static final class Email {
        public final String email;
        public Email(String email) { this.email = email; }
    }

    public static final class Registration {
        public final String username, email, password;
        public Registration(String username, String email, String password) {
            this.username = username; this.email = email; this.password = password;
        }
    }
}
