-- ============================================================================
-- Black-Coat Flyway Migration V3: Seed Extended Statutes (Marriage, Bail, BNS)
-- ============================================================================

DO $$
DECLARE
    v_dummy_vector vector(1024);
    v_hma_id UUID := 'a0000000-0000-0000-0000-000000000002';
    v_sma_id UUID := 'a0000000-0000-0000-0000-000000000003';
    v_bnss_id UUID := 'a0000000-0000-0000-0000-000000000004';
    v_bns_id UUID := 'a0000000-0000-0000-0000-000000000001';
BEGIN
    SELECT ('[' || array_to_string(ARRAY(SELECT (0.03125)::float4 FROM generate_series(1, 1024)), ',') || ']')::vector
    INTO v_dummy_vector;

    -- 1. Hindu Marriage Act, 1955
    INSERT INTO legal_documents (id, kind, title, short_name, year, source_url, license_note, corpus_version)
    VALUES (
        v_hma_id,
        'ACT',
        'The Hindu Marriage Act, 1955 (Act No. 25 of 1955)',
        'HMA',
        1955,
        'https://www.indiacode.nic.in/handle/123456789/1560',
        'Government of India Public Domain',
        'v1.0'
    ) ON CONFLICT DO NOTHING;

    -- HMA Section 8: Registration of Hindu marriages
    INSERT INTO legal_sections (document_id, section_no, heading, text, embedding, corpus_version)
    VALUES (
        v_hma_id,
        '8',
        'Registration of Hindu marriages',
        'For the purpose of facilitating the proof of Hindu marriages, the State Government may make rules providing that the parties to any such marriage may have the particulars relating to their marriage entered in such manner and subject to such conditions as may be prescribed in a Hindu Marriage Register kept for the purpose. The Hindu Marriage Register shall at all reasonable times be open for inspection, and shall be admissible as evidence of the statements made therein. The validity of any Hindu marriage shall in no way be affected by the omission to make the entry.',
        v_dummy_vector,
        'v1.0'
    ) ON CONFLICT (document_id, section_no, corpus_version) DO NOTHING;

    -- HMA Section 5: Conditions for a Hindu marriage
    INSERT INTO legal_sections (document_id, section_no, heading, text, embedding, corpus_version)
    VALUES (
        v_hma_id,
        '5',
        'Conditions for a Hindu marriage',
        'A marriage may be solemnized between any two Hindus, if the following conditions are fulfilled, namely: neither party has a spouse living at the time of the marriage; at the time of the marriage, neither party is incapable of giving a valid consent to it in consequence of unsoundness of mind; the bridegroom has completed the age of twenty-one years and the bride, the age of eighteen years; the parties are not within the degrees of prohibited relationship unless the custom or usage governing each of them permits of a marriage between the two; the parties are not sapindas of each other, unless the custom permits.',
        v_dummy_vector,
        'v1.0'
    ) ON CONFLICT (document_id, section_no, corpus_version) DO NOTHING;

    -- 2. Special Marriage Act, 1954
    INSERT INTO legal_documents (id, kind, title, short_name, year, source_url, license_note, corpus_version)
    VALUES (
        v_sma_id,
        'ACT',
        'The Special Marriage Act, 1954 (Act No. 43 of 1954)',
        'SMA',
        1954,
        'https://www.indiacode.nic.in/handle/123456789/1387',
        'Government of India Public Domain',
        'v1.0'
    ) ON CONFLICT DO NOTHING;

    -- SMA Section 15 & 16: Registration of marriages celebrated in other forms
    INSERT INTO legal_sections (document_id, section_no, heading, text, embedding, corpus_version)
    VALUES (
        v_sma_id,
        '15',
        'Registration of marriages celebrated in other forms',
        'Any marriage celebrated, whether before or after the commencement of this Act, other than a marriage solemnized under the Special Marriage Act, 1872, or under this Act, may be registered under this Chapter by a Marriage Officer in the territories to which this Act extends if a ceremony of marriage has been performed between the parties and they have been living together as husband and wife ever since; neither party has at the time of registration more than one spouse living; neither party is an idiot or a lunatic at the time of registration; the parties have completed the age of twenty-one years at the time of registration; the parties are not within the degrees of prohibited relationship.',
        v_dummy_vector,
        'v1.0'
    ) ON CONFLICT (document_id, section_no, corpus_version) DO NOTHING;

    -- 3. Bharatiya Nagarik Suraksha Sanhita, 2023 (BNSS)
    INSERT INTO legal_documents (id, kind, title, short_name, year, source_url, license_note, corpus_version)
    VALUES (
        v_bnss_id,
        'ACT',
        'The Bharatiya Nagarik Suraksha Sanhita, 2023 (Act No. 46 of 2023)',
        'BNSS',
        2023,
        'https://www.indiacode.nic.in/handle/123456789/21125',
        'Government of India Public Domain',
        'v1.0'
    ) ON CONFLICT DO NOTHING;

    -- BNSS Section 35: When police may arrest without warrant
    INSERT INTO legal_sections (document_id, section_no, heading, text, embedding, corpus_version)
    VALUES (
        v_bnss_id,
        '35',
        'When police may arrest without warrant',
        'Any police officer may without an order from a Magistrate and without a warrant, arrest any person who commits, in the presence of a police officer, a cognizable offence; or against whom a reasonable complaint has been made, or credible information has been received, or a reasonable suspicion exists that he has committed a cognizable offence punishable with imprisonment for a term which may be less than seven years or which may extend to seven years whether with or without fine, if the police officer has reason to believe that such arrest is necessary to prevent such person from committing any further offence, or for proper investigation of the offence, or to prevent such person from causing the evidence of the offence to disappear.',
        v_dummy_vector,
        'v1.0'
    ) ON CONFLICT (document_id, section_no, corpus_version) DO NOTHING;

    -- BNSS Section 479: Maximum period for which under-trial prisoner can be detained (Bail)
    INSERT INTO legal_sections (document_id, section_no, heading, text, embedding, corpus_version)
    VALUES (
        v_bnss_id,
        '479',
        'Maximum period for which under-trial prisoner can be detained',
        'Where a person has, during the period of investigation, inquiry or trial under this Sanhita of an offence under any law (not being an offence for which the punishment of death or life imprisonment has been specified as one of the punishments under that law) undergone detention for a period extending up to one-half of the maximum period of imprisonment specified for that offence under that law, he shall be released by the Court on bail: Provided that where such person is a first-time offender (who has never been convicted of any offence in the past), he shall be released on bail by the Court if he has undergone detention for the period extending up to one-third of the maximum period of imprisonment specified for that offence under that law.',
        v_dummy_vector,
        'v1.0'
    ) ON CONFLICT (document_id, section_no, corpus_version) DO NOTHING;

    -- 4. Additional BNS 2023 Sections (Right of Private Defence, Extortion, Cheating)
    -- BNS Section 34: Right of private defence
    INSERT INTO legal_sections (document_id, section_no, heading, text, embedding, corpus_version)
    VALUES (
        v_bns_id,
        '34',
        'Things done in private defence',
        'Nothing is an offence which is done in the exercise of the right of private defence. Every person has a right, subject to the restrictions, to defend his own body, and the body of any other person, against any offence affecting the human body; and the property, whether movable or immovable, of himself or of any other person, against any act which is an offence falling under the definition of theft, robbery, mischief or criminal trespass, or which is an attempt to commit theft, robbery, mischief or criminal trespass.',
        v_dummy_vector,
        'v1.0'
    ) ON CONFLICT (document_id, section_no, corpus_version) DO NOTHING;

    -- BNS Section 308: Extortion
    INSERT INTO legal_sections (document_id, section_no, heading, text, embedding, corpus_version)
    VALUES (
        v_bns_id,
        '308',
        'Extortion',
        'Whoever intentionally puts any person in fear of any injury to that person, or to any other, and thereby dishonestly induces the person so put in fear to deliver to any person any property, or valuable security or anything signed or sealed which may be converted into a valuable security, commits extortion. Whoever commits extortion shall be punished with imprisonment of either description for a term which may extend to seven years, or with fine, or with both.',
        v_dummy_vector,
        'v1.0'
    ) ON CONFLICT (document_id, section_no, corpus_version) DO NOTHING;

END $$;
