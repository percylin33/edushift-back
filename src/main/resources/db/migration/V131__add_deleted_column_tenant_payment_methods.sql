ALTER TABLE edushift.tenant_payment_methods
ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT false;