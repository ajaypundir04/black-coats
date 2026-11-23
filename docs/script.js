/**
 * Black-Coat GitHub Pages Interactive Simulator
 */

(() => {
  const demos = {
    theft: {
      query: "What is the punishment for theft under Bharatiya Nyaya Sanhita?",
      provider: "Gemini 1.5 Flash • 580ms latency",
      answer: "Under **Section 303** of the Bharatiya Nyaya Sanhita, 2023 [S1], whoever dishonestly takes movable property without consent commits theft.\n\nPunishment includes imprisonment extending up to **three years**, or fine, or both. For a second or subsequent conviction, rigorous imprisonment shall not be less than one year extending up to five years with fine.",
      citations: [
        { tag: "[S1]", act: "BNS 2023", section: "303", heading: "Theft" }
      ],
      emergency: null
    },
    arrest: {
      query: "What are my immediate legal rights if arrested by police without a warrant?",
      provider: "Groq Llama-3.3-70B • 610ms latency",
      answer: "Under Indian criminal procedure and constitutional guarantees:\n1. **Right to Grounds:** The police officer must immediately inform you of the full particulars of the offense.\n2. **Right to Legal Counsel:** You have the statutory right to consult and be defended by a legal practitioner of your choice.\n3. **Magistrate Production:** Must be produced before the nearest Judicial Magistrate within **24 hours** of arrest (excluding travel time).",
      citations: [
        { tag: "[S1]", act: "BNSS 2023", section: "35", heading: "Arrest by Police" },
        { tag: "[S2]", act: "Constitution of India", section: "Art. 22", heading: "Protection against arrest" }
      ],
      emergency: "🚨 Immediate Emergency & Legal Aid Notice: If facing illegal custody, call NALSA 24x7 Free Legal Aid at 15100 or dial 112 immediately. Under Section 12 of the Legal Services Authorities Act, arrested persons are entitled to free legal aid."
    },
    cheating: {
      query: "How is cheating defined under Section 318 of BNS?",
      provider: "Gemini 1.5 Flash • 540ms latency",
      answer: "Under **Section 318** of BNS, 2023 [S1], cheating involves fraudulently or dishonestly deceiving any person to induce them to deliver property or consent to retain property, or to do an act causing harm in body, mind, reputation, or property.",
      citations: [
        { tag: "[S1]", act: "BNS 2023", section: "318", heading: "Cheating" }
      ],
      emergency: null
    },
    refusal: {
      query: "What is the penalty for using quantum teleportation under Indian traffic laws?",
      provider: "Refusal Gate Triggered (0ms LLM tokens wasted)",
      answer: "⚠️ **Refusal Gate Activated:**\nI could not find relevant statutory provisions in the legal corpus regarding quantum teleportation in Indian traffic law. \n\nTo prevent legal hallucinations, Black-Coat only generates answers grounded in verified Indian statutes. Your daily quota reservation has been refunded.",
      citations: [],
      emergency: null
    }
  };

  const simQuery = document.getElementById('sim-query');
  const simAnswer = document.getElementById('sim-answer');
  const simCitations = document.getElementById('sim-citations');
  const simEmergency = document.getElementById('sim-emergency');
  const simStatus = document.getElementById('sim-status');
  const simButtons = document.querySelectorAll('.sim-btn');

  let currentTypingTimer = null;

  function runSimulation(key) {
    const data = demos[key];
    if (!data) return;

    // Reset UI
    if (currentTypingTimer) clearInterval(currentTypingTimer);
    simQuery.textContent = data.query;
    simStatus.textContent = `Provider: ${data.provider}`;
    simAnswer.textContent = "";
    simCitations.innerHTML = "";
    simEmergency.classList.add('hidden');
    simEmergency.textContent = "";

    // Typewriter token streaming simulation
    const tokens = data.answer.split(" ");
    let i = 0;

    currentTypingTimer = setInterval(() => {
      if (i < tokens.length) {
        simAnswer.innerHTML += (i === 0 ? "" : " ") + tokens[i];
        i++;
      } else {
        clearInterval(currentTypingTimer);
        currentTypingTimer = null;

        // Render Citations
        if (data.citations.length > 0) {
          data.citations.forEach(c => {
            const chip = document.createElement('span');
            chip.className = 'sim-citation-chip';
            chip.textContent = `${c.tag} ${c.act} s.${c.section} (${c.heading})`;
            simCitations.appendChild(chip);
          });
        }

        // Render Emergency box if present
        if (data.emergency) {
          simEmergency.textContent = data.emergency;
          simEmergency.classList.remove('hidden');
        }
      }
    }, 40);
  }

  simButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      simButtons.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      runSimulation(btn.dataset.sim);
    });
  });

  // Run first simulation on load
  runSimulation('theft');
})();
