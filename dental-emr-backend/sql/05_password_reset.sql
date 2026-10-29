USE dental_emr;

DROP TABLE IF EXISTS PASSWORD_RESET;

CREATE TABLE PASSWORD_RESET (
    UserID     INT PRIMARY KEY,           -- one active code per user; a new request replaces the old one
    CodeHash   VARCHAR(200) NOT NULL,
    ExpiresAt  BIGINT NOT NULL,           -- epoch millis (avoids timezone issues)
    Attempts   INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_reset_user FOREIGN KEY (UserID) REFERENCES USER_ACCOUNT(UserID) ON DELETE CASCADE
) ENGINE=InnoDB;