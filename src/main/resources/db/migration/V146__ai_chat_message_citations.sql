ALTER TABLE edushift.ai_chat_messages
    ADD COLUMN citations jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD CONSTRAINT chk_ai_chat_message_citations_array
        CHECK (jsonb_typeof(citations) = 'array');
