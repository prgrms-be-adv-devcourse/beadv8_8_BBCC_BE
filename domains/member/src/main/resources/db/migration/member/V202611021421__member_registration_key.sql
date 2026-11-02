ALTER TABLE member.member ADD COLUMN registration_key varchar(64);
ALTER TABLE member.member ADD CONSTRAINT uk_member_registration_key UNIQUE (registration_key);
