-- A coluna customer_id era uma cópia denormalizada do tenant, escrita apenas
-- no momento de criação do token (via BaseTenantEntity @PrePersist).
-- O escopo de tenant de um token agora é sempre derivado via JOIN com a
-- entidade de domínio (Student/Driver/Admin/ResponsibleAdult) vinculada
-- ao UserAccount do token, eliminando a possibilidade de divergência
-- entre as duas fontes.

/*ALTER TABLE push_notification_device_tokens
DROP COLUMN IF EXISTS customer_id;*/