/**
 * Black-Coat Web Frontend
 * Standalone Signal-like Reactive Client for Streaming Legal AI
 */

(() => {
  // --------------------------------------------------------------------------
  // API Host & Dynamic Base URL Resolver
  // --------------------------------------------------------------------------
  function getApiHost() {
    const stored = localStorage.getItem('blackcoat_api_url');
    if (stored && stored.trim()) {
      return stored.trim().replace(/\/+$/, '');
    }
    if (window.__BLACKCOAT_API_URL__) {
      return String(window.__BLACKCOAT_API_URL__).trim().replace(/\/+$/, '');
    }
    const hostname = window.location.hostname;
    if (hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '') {
      return 'http://localhost:8080';
    }
    if (hostname.includes('.onrender.com')) {
      if (hostname.includes('-web.')) {
        return `https://${hostname.replace('-web.', '-api.')}`;
      }
      return 'https://black-coat-api.onrender.com';
    }
    return 'https://black-coat-api.onrender.com';
  }

  function getApiBase() {
    return `${getApiHost()}/api/v1`;
  }

  async function safeFetchJson(url, options = {}) {
    let res;
    try {
      res = await fetch(url, options);
    } catch (netErr) {
      throw new Error(`Cannot reach backend at ${url}. If your Render service is waking up, please wait ~30 seconds and retry. (${netErr.message || 'Network error'})`);
    }

    const rawText = await res.text();
    let data = null;

    if (rawText && rawText.trim().length > 0) {
      try {
        data = JSON.parse(rawText);
      } catch {
        data = { error: rawText.replace(/<[^>]*>/g, ' ').substring(0, 200).trim() };
      }
    }

    if (!res.ok) {
      let errorMsg = data && (data.error || data.message);
      if (!errorMsg) {
        if (res.status === 404) {
          errorMsg = `Endpoint not found (HTTP 404) at ${url}. Verify backend API URL configuration.`;
        } else if (res.status === 502 || res.status === 503 || res.status === 504) {
          errorMsg = `Server is currently waking up (HTTP ${res.status}). Render free instances take ~45s to spin up. Please retry in a moment.`;
        } else {
          errorMsg = `Server error HTTP ${res.status} (${res.statusText || 'Request failed'})`;
        }
      }
      throw new Error(errorMsg);
    }

    if (!data) {
      throw new Error(`Server returned an empty response (HTTP ${res.status}). Verify your backend URL.`);
    }

    return data;
  }

  // Application State
  const state = {
    token: localStorage.getItem('blackcoat_access_token') || '',
    refreshToken: localStorage.getItem('blackcoat_refresh_token') || '',
    user: JSON.parse(localStorage.getItem('blackcoat_user') || 'null'),
    isStreaming: false,
    selectedLang: 'en',
    citationsMap: new Map(), // Stores current message citations
    dailyQuotaRemaining: 10
  };

  // DOM Elements
  const serverStatus = document.getElementById('server-status');
  const statusText = document.getElementById('status-text');
  const langSelect = document.getElementById('lang-select');
  const quotaDisplay = document.getElementById('quota-display');
  const authNavContainer = document.getElementById('auth-nav-container');
  const btnOpenAuth = document.getElementById('btn-open-auth');
  const userMenu = document.getElementById('user-menu');
  const userDisplayName = document.getElementById('user-display-name');
  const btnLogout = document.getElementById('btn-logout');

  const chatContainer = document.querySelector('.chat-container');
  const chatMessages = document.getElementById('chat-messages');
  const chatWelcome = document.getElementById('chat-welcome');
  const chatForm = document.getElementById('chat-form');
  const chatInput = document.getElementById('chat-input');
  const btnSend = document.getElementById('btn-send');
  const btnMic = document.getElementById('btn-mic');
  const emergencyBox = document.getElementById('emergency-box');
  const btnCloseEmergency = document.getElementById('btn-close-emergency');

  // Modals
  const authModal = document.getElementById('auth-modal');
  const btnCloseAuth = document.getElementById('btn-close-auth');
  const tabLogin = document.getElementById('tab-login');
  const tabRegister = document.getElementById('tab-register');
  const loginForm = document.getElementById('login-form');
  const registerForm = document.getElementById('register-form');
  const authError = document.getElementById('auth-error');

  const citationModal = document.getElementById('citation-modal');
  const btnCloseCitation = document.getElementById('btn-close-citation');
  const citTag = document.getElementById('cit-tag');
  const citTitle = document.getElementById('cit-title');
  const citAct = document.getElementById('cit-act');
  const citSection = document.getElementById('cit-section');
  const citText = document.getElementById('cit-text');
  const citLink = document.getElementById('cit-link');

  // Configure API URL on server-status click
  if (serverStatus) {
    serverStatus.style.cursor = 'pointer';
    serverStatus.setAttribute('title', 'Click to view or change backend API URL');
    serverStatus.addEventListener('click', () => {
      const current = getApiHost();
      const input = prompt(
        `Backend API Endpoint URL:\n\nCurrent: ${current}\n\nEnter custom API URL (or leave blank to reset to auto):`,
        localStorage.getItem('blackcoat_api_url') || ''
      );
      if (input !== null) {
        if (input.trim()) {
          localStorage.setItem('blackcoat_api_url', input.trim().replace(/\/+$/, ''));
        } else {
          localStorage.removeItem('blackcoat_api_url');
        }
        checkServerHealth();
      }
    });
  }

  // --------------------------------------------------------------------------
  // 1. Cold Start Wake-up Check
  // --------------------------------------------------------------------------
  async function checkServerHealth() {
    const host = getApiHost();
    try {
      const res = await fetch(`${host}/actuator/health`, { method: 'GET' });
      if (res.ok) {
        serverStatus.className = 'server-status status-online';
        statusText.textContent = 'API Online (JVM)';
      } else {
        throw new Error('Not ready');
      }
    } catch {
      serverStatus.className = 'server-status status-waking';
      statusText.textContent = 'Server Waking Up...';
      setTimeout(checkServerHealth, 5000); // Poll until backend wakes up
    }
  }

  // --------------------------------------------------------------------------
  // 2. Auth & Session Management
  // --------------------------------------------------------------------------
  function updateAuthUI() {
    if (state.token && state.user) {
      btnOpenAuth.classList.add('hidden');
      userMenu.classList.remove('hidden');
      userDisplayName.textContent = state.user.displayName || state.user.email.split('@')[0];
    } else {
      btnOpenAuth.classList.remove('hidden');
      userMenu.classList.add('hidden');
    }
    quotaDisplay.textContent = `${state.dailyQuotaRemaining} / 10 queries`;
  }

  async function handleLogin(e) {
    e.preventDefault();
    authError.classList.add('hidden');
    const email = (document.getElementById('login-email').value || '').trim();
    const password = document.getElementById('login-password').value || '';

    if (!email || !password) {
      authError.textContent = 'Please enter email and password.';
      authError.classList.remove('hidden');
      return;
    }

    try {
      const data = await safeFetchJson(`${getApiBase()}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password })
      });

      setSession(data);
      authModal.classList.add('hidden');
    } catch (err) {
      authError.textContent = err.message || 'Login failed';
      authError.classList.remove('hidden');
    }
  }

  async function handleRegister(e) {
    e.preventDefault();
    authError.classList.add('hidden');
    const displayName = (document.getElementById('reg-name').value || '').trim();
    const email = (document.getElementById('reg-email').value || '').trim();
    const password = document.getElementById('reg-password').value || '';

    if (!email) {
      authError.textContent = 'Please enter a valid email address.';
      authError.classList.remove('hidden');
      return;
    }

    if (password.length < 8) {
      authError.textContent = 'Password must be at least 8 characters long.';
      authError.classList.remove('hidden');
      return;
    }

    try {
      const data = await safeFetchJson(`${getApiBase()}/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password, displayName: displayName || 'Advocate', langPref: state.selectedLang })
      });

      setSession(data);
      authModal.classList.add('hidden');
    } catch (err) {
      authError.textContent = err.message || 'Registration failed';
      authError.classList.remove('hidden');
    }
  }

  function setSession(authData) {
    state.token = authData.accessToken;
    state.refreshToken = authData.refreshToken;
    state.user = authData.user;
    localStorage.setItem('blackcoat_access_token', state.token);
    localStorage.setItem('blackcoat_refresh_token', state.refreshToken);
    localStorage.setItem('blackcoat_user', JSON.stringify(state.user));
    updateAuthUI();
  }

  function handleLogout() {
    state.token = '';
    state.refreshToken = '';
    state.user = null;
    localStorage.removeItem('blackcoat_access_token');
    localStorage.removeItem('blackcoat_refresh_token');
    localStorage.removeItem('blackcoat_user');
    updateAuthUI();
  }

  // --------------------------------------------------------------------------
  // 3. Streaming Chat Execution (fetch + Stream Reader)
  // --------------------------------------------------------------------------
  async function submitQuestion(questionText) {
    if (!questionText.trim() || state.isStreaming) return;

    if (chatWelcome) {
      chatWelcome.style.display = 'none';
    }

    // Append User Message
    appendMessage('user', questionText);
    chatInput.value = '';
    chatInput.style.height = 'auto';

    // Prepare Assistant Message Placeholder
    const assistantRow = appendMessage('assistant', '');
    const contentEl = assistantRow.querySelector('.message-text');
    const footerEl = assistantRow.querySelector('.message-footer');

    state.isStreaming = true;
    btnSend.disabled = true;

    let fullAnswer = '';

    try {
      const headers = { 'Content-Type': 'application/json' };
      if (state.token) {
        headers['Authorization'] = `Bearer ${state.token}`;
      }

      const response = await fetch(`${getApiBase()}/chat/stream`, {
        method: 'POST',
        headers,
        body: JSON.stringify({ question: questionText })
      });

      if (!response.ok) {
        throw new Error(`Server returned status ${response.status}`);
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder('utf-8');
      let buffer = '';

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop(); // Keep partial line

        let currentEvent = 'message';

        for (const line of lines) {
          const trimmed = line.trim();
          if (trimmed.startsWith('event:')) {
            currentEvent = trimmed.substring(6).trim();
          } else if (trimmed.startsWith('data:')) {
            const dataStr = trimmed.substring(5).trim();
            if (dataStr) {
              handleSseEvent(currentEvent, dataStr, contentEl, footerEl, (token) => {
                fullAnswer += token;
                renderFormattedText(contentEl, fullAnswer);
              });
            }
          }
        }
      }

      state.dailyQuotaRemaining = Math.max(0, state.dailyQuotaRemaining - 1);
      updateAuthUI();

    } catch (err) {
      contentEl.innerHTML = `<span style="color: #ef4444;">⚠️ Error: ${err.message}. Please check if the server is active.</span>`;
    } finally {
      state.isStreaming = false;
      btnSend.disabled = false;
    }
  }

  function handleSseEvent(eventName, dataStr, contentEl, footerEl, onToken) {
    try {
      const data = JSON.parse(dataStr);
      switch (eventName) {
        case 'token':
          if (data.text) {
            onToken(data.text);
          }
          break;

        case 'citations':
          if (Array.isArray(data.citations)) {
            renderCitationList(footerEl, data.citations);
          }
          break;

        case 'done':
          if (data.provider) {
            const badge = document.createElement('span');
            badge.className = 'model-badge';
            badge.textContent = `⚡ Grounded via ${data.provider} (${data.latencyMs}ms)`;
            footerEl.prepend(badge);
          }
          break;

        case 'refusal':
          contentEl.innerHTML = `<span style="color: #f59e0b;">ℹ️ ${data.message}</span>`;
          break;

        case 'error':
          contentEl.innerHTML = `<span style="color: #ef4444;">⚠️ [${data.code}] ${data.message}</span>`;
          break;
      }
    } catch {
      // Raw string fallback
      onToken(dataStr);
    }
  }

  function renderFormattedText(container, text) {
    // Replace citation tags like [S1], [S2] with interactive chips
    const formatted = text.replace(/\[S(\d+)\]/g, (match, num) => {
      return `<button class="citation-chip" data-cit="[S${num}]">[S${num}]</button>`;
    });

    container.innerHTML = formatted;
    chatMessages.scrollTop = chatMessages.scrollHeight;

    // Attach click listeners to citation chips
    container.querySelectorAll('.citation-chip').forEach(chip => {
      chip.onclick = () => openCitationModal(chip.dataset.cit);
    });
  }

  function renderCitationList(footerEl, citations) {
    if (!citations.length) return;
    
    let listEl = footerEl.querySelector('.citation-list');
    if (!listEl) {
      listEl = document.createElement('div');
      listEl.className = 'citation-list';
      footerEl.before(listEl);
    }

    citations.forEach(c => {
      state.citationsMap.set(c.tag, c);
      const chip = document.createElement('button');
      chip.className = 'citation-chip';
      chip.textContent = `${c.tag} ${c.actShortName} s.${c.sectionNo}`;
      chip.onclick = () => openCitationModal(c.tag);
      listEl.appendChild(chip);
    });
  }

  function openCitationModal(tag) {
    const cit = state.citationsMap.get(tag);
    if (!cit) return;

    citTag.textContent = cit.tag;
    citTitle.textContent = cit.heading || `Section ${cit.sectionNo}`;
    citAct.textContent = `Act: ${cit.actShortName}`;
    citSection.textContent = `Section: ${cit.sectionNo}`;
    citText.textContent = cit.textSnippet;
    citLink.href = `https://www.indiacode.nic.in`;
    citationModal.classList.remove('hidden');
  }

  function appendMessage(role, text) {
    const row = document.createElement('div');
    row.className = `message-row message-${role}`;

    if (role === 'assistant') {
      row.innerHTML = `
        <div class="message-avatar">⚖️</div>
        <div class="message-content">
          <div class="message-text">${text}</div>
          <div class="message-footer">
            <div class="message-feedback">
              <button class="btn-feedback" title="Helpful answer">👍</button>
              <button class="btn-feedback" title="Report inaccuracy">👎</button>
            </div>
          </div>
        </div>
      `;
    } else {
      row.innerHTML = `
        <div class="message-content">
          <div class="message-text">${text}</div>
        </div>
      `;
    }

    chatMessages.appendChild(row);
    chatMessages.scrollTop = chatMessages.scrollHeight;
    return row;
  }

  // --------------------------------------------------------------------------
  // 4. Voice Input (Speech-to-Text via Web Speech API)
  // --------------------------------------------------------------------------
  function setupVoiceInput() {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
      btnMic.title = 'Speech-to-text not supported in this browser';
      btnMic.style.opacity = '0.5';
      return;
    }

    const recognition = new SpeechRecognition();
    recognition.continuous = false;
    recognition.interimResults = false;
    recognition.lang = state.selectedLang === 'hi' ? 'hi-IN' : 'en-IN';

    recognition.onstart = () => {
      btnMic.classList.add('listening');
    };

    recognition.onresult = (event) => {
      const transcript = event.results[0][0].transcript;
      chatInput.value = transcript;
      chatInput.focus();
    };

    recognition.onend = () => {
      btnMic.classList.remove('listening');
    };

    recognition.onerror = () => {
      btnMic.classList.remove('listening');
    };

    btnMic.onclick = () => {
      recognition.lang = state.selectedLang === 'hi' ? 'hi-IN' : 'en-IN';
      recognition.start();
    };
  }

  // --------------------------------------------------------------------------
  // 5. Event Listeners & Initialization
  // --------------------------------------------------------------------------
  function init() {
    checkServerHealth();
    updateAuthUI();
    setupVoiceInput();

    // Language switcher
    langSelect.addEventListener('change', (e) => {
      state.selectedLang = e.target.value;
    });

    // Form submission
    chatForm.addEventListener('submit', (e) => {
      e.preventDefault();
      submitQuestion(chatInput.value);
    });

    // Enter key submits (Shift+Enter adds newline)
    chatInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        submitQuestion(chatInput.value);
      }
    });

    // Starter Prompt Chips
    document.querySelectorAll('.chip').forEach(chip => {
      chip.onclick = () => {
        submitQuestion(chip.dataset.prompt);
      };
    });

    // Modals
    btnOpenAuth.onclick = () => authModal.classList.remove('hidden');
    btnCloseAuth.onclick = () => authModal.classList.add('hidden');
    btnCloseCitation.onclick = () => citationModal.classList.add('hidden');
    btnCloseEmergency.onclick = () => emergencyBox.classList.add('hidden');

    tabLogin.onclick = () => {
      tabLogin.classList.add('active');
      tabRegister.classList.remove('active');
      loginForm.classList.remove('hidden');
      registerForm.classList.add('hidden');
    };

    tabRegister.onclick = () => {
      tabRegister.classList.add('active');
      tabLogin.classList.remove('active');
      registerForm.classList.remove('hidden');
      loginForm.classList.add('hidden');
    };

    loginForm.addEventListener('submit', handleLogin);
    registerForm.addEventListener('submit', handleRegister);
    btnLogout.addEventListener('click', handleLogout);

    // Auto-resize textarea
    chatInput.addEventListener('input', () => {
      chatInput.style.height = 'auto';
      chatInput.style.height = Math.min(chatInput.scrollHeight, 120) + 'px';
    });
  }

  document.addEventListener('DOMContentLoaded', init);
})();
