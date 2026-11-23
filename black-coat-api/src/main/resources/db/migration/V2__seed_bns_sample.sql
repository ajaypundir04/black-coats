-- ============================================================================
-- Black-Coat Flyway Migration V2: Seed Baseline Statutes (BNS 2023 Sample)
-- ============================================================================

DO $$
DECLARE
    v_doc_id UUID;
    v_dummy_vector vector(1024);
BEGIN
    -- Generate a normalized dummy 1024-dimension vector for baseline seeding
    -- In production, the offline ingest-cli embeds sections using BGE-M3
    SELECT ('[' || array_to_string(ARRAY(SELECT (0.03125)::float4 FROM generate_series(1, 1024)), ',') || ']')::vector
    INTO v_dummy_vector;

    -- Insert Bharatiya Nyaya Sanhita, 2023
    INSERT INTO legal_documents (id, kind, title, short_name, year, source_url, license_note, corpus_version)
    VALUES (
        'a0000000-0000-0000-0000-000000000001',
        'ACT',
        'The Bharatiya Nyaya Sanhita, 2023 (Act No. 45 of 2023)',
        'BNS',
        2023,
        'https://www.indiacode.nic.in/handle/123456789/21124',
        'Government of India Public Domain (Section 52(1)(q) of Copyright Act, 1957)',
        'v1.0'
    )
    ON CONFLICT DO NOTHING;

    -- Section 303: Theft
    INSERT INTO legal_sections (document_id, section_no, heading, text, embedding, corpus_version)
    VALUES (
        'a0000000-0000-0000-0000-000000000001',
        '303',
        'Theft',
        'Whoever, intending to take dishonestly any movable property out of the possession of any person without that person''s consent, moves that property in order to such taking, is said to commit theft. Whoever commits theft shall be punished with imprisonment of either description for a term which may extend to three years, or with fine, or with both, and in case of second or subsequent conviction of any person under this section, he shall be punished with rigorous imprisonment for a term which shall not be less than one year but which may extend to five years and with fine.',
        v_dummy_vector,
        'v1.0'
    )
    ON CONFLICT (document_id, section_no, corpus_version) DO NOTHING;

    -- Section 318: Cheating
    INSERT INTO legal_sections (document_id, section_no, heading, text, embedding, corpus_version)
    VALUES (
        'a0000000-0000-0000-0000-000000000001',
        '318',
        'Cheating',
        'Whoever, by deceiving any person, fraudulently or dishonestly induces the person so deceived to deliver any property to any person, or to consent that any person shall retain any property, or intentionally induces the person so deceived to do or omit to do anything which he would not do or omit if he were not so deceived, and which act or omission causes or is likely to cause damage or harm to that person in body, mind, reputation or property, is said to cheat.',
        v_dummy_vector,
        'v1.0'
    )
    ON CONFLICT (document_id, section_no, corpus_version) DO NOTHING;

    -- Section 103: Punishment for murder
    INSERT INTO legal_sections (document_id, section_no, heading, text, embedding, corpus_version)
    VALUES (
        'a0000000-0000-0000-0000-000000000001',
        '103',
        'Punishment for murder',
        'Whoever commits murder shall be punished with death or imprisonment for life, and shall also be liable to fine. When a group of five or more persons acting in concert commits murder on the ground of race, caste or community, sex, place of birth, language, personal belief or any other similar ground, each member of such group shall be punished with death or with imprisonment for life, and shall also be liable to fine.',
        v_dummy_vector,
        'v1.0'
    )
    ON CONFLICT (document_id, section_no, corpus_version) DO NOTHING;

    -- Section 351: Criminal intimidation
    INSERT INTO legal_sections (document_id, section_no, heading, text, embedding, corpus_version)
    VALUES (
        'a0000000-0000-0000-0000-000000000001',
        '351',
        'Criminal intimidation',
        'Whoever threatens another with any injury to his person, reputation or property, or to the person or reputation of any one in whom that person is interested, with intent to cause alarm to that person, or to cause that person to do any act which he is not legally bound to do, or to omit to do any act which that person is legally entitled to do, as the means of avoiding the execution of such threat, commits criminal intimidation.',
        v_dummy_vector,
        'v1.0'
    )
    ON CONFLICT (document_id, section_no, corpus_version) DO NOTHING;

END $$;
