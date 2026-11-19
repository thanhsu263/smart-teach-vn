-- Students have no account: entering their access code on an assignment gives them a random
-- attempt token. Only its SHA-256 hash is stored, like access codes, so it can be revoked
-- (new access code) and looked up with one indexed read.
ALTER TABLE attempts ADD
    token_hash        NVARCHAR(64)  NULL,
    token_expires_at  DATETIME2     NULL;
GO

CREATE UNIQUE INDEX ux_attempts_token_hash ON attempts (token_hash) WHERE token_hash IS NOT NULL;

-- Starting an assignment finds the student by the hash of the code they typed.
CREATE INDEX ix_students_access_code ON students (access_code_hash);
