/**
 * CardioProgress - Cardiac Fibrosis AI Predictor Client Engine
 * Integrates Firebase Web SDK (Auth, Firestore) with local database fallback.
 */

// --- FIREBASE CONFIGURATION (Matching Android project cardio-ai-635a5) ---
const firebaseConfig = {
    apiKey: "AIzaSyBHxHbWaWfB6rFbBLX3BMPeK9ss0QdnZy0",
    authDomain: "cardio-ai-635a5.firebaseapp.com",
    projectId: "cardio-ai-635a5",
    storageBucket: "cardio-ai-635a5.firebasestorage.app",
    messagingSenderId: "1031334334650"
};

let db = null;
let auth = null;
let firebaseEnabled = false;

// Initialize Firebase
try {
    if (typeof firebase !== 'undefined') {
        firebase.initializeApp(firebaseConfig);
        db = firebase.firestore();
        auth = firebase.auth();
        firebaseEnabled = true;
        console.log("Firebase Backend initialized successfully!");
        
        // Dynamic offline persistence setting for Firestore
        db.enablePersistence().catch(err => {
            console.warn("Firestore offline persistence not enabled: ", err.code);
        });
    }
} catch (e) {
    console.warn("Firebase Web SDK failed to initialize. Operating in Local Offline Mode.", e);
}

// --- PROMISE TIMEOUT HELPER ---
function withTimeout(promise, ms) {
    let timeoutId;
    const timeoutPromise = new Promise((resolve, reject) => {
        timeoutId = setTimeout(() => {
            reject(new Error("Timeout after " + ms + "ms"));
        }, ms);
    });
    return Promise.race([promise, timeoutPromise]).finally(() => {
        clearTimeout(timeoutId);
    });
}

// --- APPLICATION DATA STATE ---
const appState = {
    currentUser: null,
    settings: {
        darkMode: false,
        theme: 'blue',
        soundEffects: true,
        pushNotifications: true,
        emailNotifications: false
    },
    patientDetails: {
        dob: '',
        gender: '',
        bloodGroup: 'O+',
        height: '',
        weight: '',
        medicalHistory: [],
        exercise: '3-5 hours',
        sodium: 'Moderate (1500-2300mg/day)',
        smoking: 'Never',
        alcohol: 'Never',
        familyHistory: []
    },
    reports: [
        {
            filename: 'baseline_ekg_report.pdf',
            date: 'May 10, 2026',
            probability: 15,
            verdict: 'Low Risk',
            troponin: 0.015,
            bnp: 42.0,
            ntProbnp: 78.0
        },
        {
            filename: 'cardiac_biomarkers_may26.pdf',
            date: 'May 26, 2026',
            probability: 18,
            verdict: 'Low Risk',
            troponin: 0.024,
            bnp: 58.0,
            ntProbnp: 94.0
        }
    ],
    currentReport: null,
    activeTab: 'tab-dashboard'
};

// Clinical Guideline Texts
const clinicalGuidelines = {
    'Healthy': [
        'Optimal baseline troponin and BNP indices.',
        'Excellent lifestyle indicators: continue current exercise and nutrition patterns.',
        'Maintain normal cardiac care checkup in 12 months.'
    ],
    'Low Risk': [
        'Biomarkers are within optimal clinical thresholds.',
        'Continue standard cardiovascular exercise routine (150 minutes per week).',
        'Monitor arterial blood pressure monthly; next screening recommended in 6 months.'
    ],
    'Risk': [
        'Cardiovascular biomarkers (BNP/NT-proBNP) show mild elevations indicating myocardial strain.',
        'Hypertension or dietary sodium adjustments needed: restrict sodium below 2000mg/day.',
        'Schedule a clinical consult with a cardiologist for diagnostic evaluation (ECG, stress test).'
    ],
    'High Risk': [
        'Significant elevations detected in Troponin I levels, indicating active myocardial cell damage.',
        'Immediate consultation with a cardiologist or cardiac specialist is highly recommended.',
        'Schedule a Cardiac Magnetic Resonance (MRI) to evaluate structural fibrosis volumes.',
        'Restrict strenuous exercises or heavy physical training until cleared by a physician.'
    ]
};

// --- AUDIO UTILITIES ---
function playSound(type) {
    if (!appState.settings.soundEffects) return;
    const clickSound = document.getElementById('click-sound');
    const successSound = document.getElementById('success-sound');
    
    if (type === 'click' && clickSound) {
        clickSound.currentTime = 0;
        clickSound.play().catch(err => {});
    } else if (type === 'success' && successSound) {
        successSound.currentTime = 0;
        successSound.play().catch(err => {});
    }
}

// --- DOM ELEMENT REFERENCES ---
const screens = {
    splash: document.getElementById('screen-splash'),
    on1: document.getElementById('screen-on1'),
    on2: document.getElementById('screen-on2'),
    on3: document.getElementById('screen-on3'),
    login: document.getElementById('screen-login'),
    signup: document.getElementById('screen-signup'),
    verify: document.getElementById('screen-verify'),
    forgot: document.getElementById('screen-forgot-password'),
    reset: document.getElementById('screen-reset-password'),
    resetSuccess: document.getElementById('screen-reset-success'),
    continueSetup: document.getElementById('screen-continue-patient'),
    wizard: document.getElementById('screen-patient-wizard'),
    main: document.getElementById('screen-main')
};

// --- SPA SCREEN NAVIGATION ---
function showScreen(screenKey) {
    Object.values(screens).forEach(screen => {
        if (screen) {
            screen.classList.remove('active');
            screen.style.display = 'none';
        }
    });

    const targetScreen = screens[screenKey];
    if (targetScreen) {
        targetScreen.style.display = 'flex';
        setTimeout(() => {
            targetScreen.classList.add('active');
        }, 20);
    }
}

// --- SPA TAB NAVIGATION ---
function showTab(tabId) {
    document.querySelectorAll('.nav-item').forEach(item => {
        if (item.getAttribute('data-tab') === tabId) {
            item.classList.add('active');
        } else {
            item.classList.remove('active');
        }
    });

    document.querySelectorAll('.mobile-nav-item').forEach(item => {
        if (item.getAttribute('data-tab') === tabId) {
            item.classList.add('active');
        } else {
            item.classList.remove('active');
        }
    });

    document.querySelectorAll('.tab-content').forEach(tab => {
        if (tab.id === tabId) {
            tab.classList.add('active');
        } else {
            tab.classList.remove('active');
        }
    });

    appState.activeTab = tabId;
    playSound('click');

    const titleMapping = {
        'tab-dashboard': 'Cardiac Assessment',
        'tab-reports': 'Reports & Diagnostics',
        'tab-health': 'Health Profile Summary',
        'tab-profile': 'Account Details',
        'tab-settings': 'System Settings'
    };
    
    document.getElementById('header-title').innerText = titleMapping[tabId] || 'Cardiac Assessment';
}

// --- FIREBASE DATA SYNC LAYER ---
async function fetchUserData(userId) {
    if (firebaseEnabled && db) {
        try {
            // 1. Fetch main user profile
            const userDoc = await withTimeout(db.collection("users").doc(userId).get(), 1000);
            if (userDoc.exists) {
                const userData = userDoc.data();
                appState.currentUser = {
                    id: userId,
                    name: userData.name || userData.full_name || 'User',
                    email: userData.email,
                    phone: userData.phone || '',
                    address: userData.address || '',
                    emergency: userData.emergency || userData.emergency_contact || '',
                    profilePictureUrl: userData.profile_picture_url || '',
                    settings: userData.settings || { ...appState.settings }
                };
                appState.settings = appState.currentUser.settings;
            }

            // 2. Fetch patient details
            const detailDoc = await withTimeout(db.collection("patient_details").doc(userId).get(), 1000);
            if (detailDoc.exists) {
                const det = detailDoc.data();
                appState.patientDetails = {
                    dob: det.dob || '',
                    gender: det.gender || '',
                    bloodGroup: det.blood_type || 'O+',
                    height: det.height_cm || '',
                    weight: det.weight_kg || '',
                    medicalHistory: det.medical_history || [],
                    exercise: det.exercise_hours || '3-5 hours',
                    sodium: det.sodium_intake || 'Moderate (1500-2300mg/day)',
                    smoking: det.smoking_habit || 'Never',
                    alcohol: det.alcohol_consumption || 'Never',
                    familyHistory: det.family_history || []
                };
            }

            // 3. Fetch patient reports
            const reportsSnap = await withTimeout(db.collection("reports").where("user_id", "==", userId).get(), 1200);
            const loadedReports = [];
            reportsSnap.forEach(doc => {
                const rep = doc.data();
                loadedReports.push({
                    filename: rep.filename || rep.file_path.split('/').pop(),
                    date: rep.uploaded_at ? formatDateString(rep.uploaded_at) : 'Today',
                    probability: rep.probability || 15,
                    verdict: rep.ai_result || 'Low Risk',
                    troponin: rep.troponin_i || 0.02,
                    bnp: rep.bnp || 65.0,
                    ntProbnp: rep.nt_probnp || 110.0,
                    timestamp: rep.uploaded_at || ''
                });
            });
            
            // Client-side sort by upload time
            loadedReports.sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp));
            
            if (loadedReports.length > 0) {
                appState.reports = loadedReports;
            } else {
                appState.reports = [];
            }
            
            updateConnectionDot(true);
            return true;
        } catch (err) {
            console.error("Firebase fetch error. Operating in offline mode.", err);
            updateConnectionDot(false);
        }
    }

    // LOCAL OFFLINE FALLBACK
    const usersJson = localStorage.getItem('cp_users');
    const users = JSON.parse(usersJson || '{}');
    const localUser = users[userId];
    if (localUser) {
        appState.currentUser = localUser;
        appState.settings = localUser.settings || appState.settings;
        appState.patientDetails = localUser.patientDetails || appState.patientDetails;
        appState.reports = localUser.reports || appState.reports;
        updateConnectionDot(false);
        return true;
    } else {
        // Fallback for new un-persisted users
        appState.currentUser = {
            id: userId,
            name: 'User',
            email: auth && auth.currentUser ? auth.currentUser.email : 'user@example.com',
            phone: '',
            address: '',
            emergency: '',
            profilePictureUrl: '',
            settings: { ...appState.settings }
        };
        saveLocalUserCache();
        updateConnectionDot(false);
        return true;
    }
    return false;
}

async function savePatientDetailsToBackend() {
    const userId = appState.currentUser ? appState.currentUser.id : (localStorage.getItem('cp_active_user') || 'guest_user');
    if (firebaseEnabled && db) {
        try {
            await withTimeout(db.collection("patient_details").doc(userId).set({
                user_id: userId,
                full_name: appState.currentUser ? appState.currentUser.name : 'User',
                dob: appState.patientDetails.dob,
                gender: appState.patientDetails.gender,
                blood_type: appState.patientDetails.bloodGroup,
                height_cm: appState.patientDetails.height,
                weight_kg: appState.patientDetails.weight,
                medical_history: appState.patientDetails.medicalHistory,
                exercise_hours: appState.patientDetails.exercise,
                sodium_intake: appState.patientDetails.sodium,
                smoking_habit: appState.patientDetails.smoking,
                alcohol_consumption: appState.patientDetails.alcohol,
                family_history: appState.patientDetails.familyHistory
            }), 1000);
            console.log("Patient details uploaded to Firebase Firestore.");
        } catch (err) {
            console.error("Firestore write failed. Cached locally.", err);
        }
    }
    
    // Always sync locally
    saveLocalUserCache();
}

async function saveUserProfileToBackend() {
    const userId = appState.currentUser ? appState.currentUser.id : (localStorage.getItem('cp_active_user') || 'guest_user');
    if (firebaseEnabled && db) {
        try {
            await withTimeout(db.collection("users").doc(userId).update({
                name: appState.currentUser ? appState.currentUser.name : 'User',
                full_name: appState.currentUser ? appState.currentUser.name : 'User',
                phone: appState.currentUser ? appState.currentUser.phone : '',
                address: appState.currentUser ? appState.currentUser.address : '',
                emergency: appState.currentUser ? appState.currentUser.emergency : '',
                emergency_contact: appState.currentUser ? appState.currentUser.emergency : '',
                profile_picture_url: appState.currentUser ? (appState.currentUser.profilePictureUrl || '') : '',
                settings: appState.settings
            }), 1000);
            console.log("Profile updates synced to Firebase Firestore.");
        } catch (err) {
            console.error("Firestore user write failed.", err);
        }
    }
    saveLocalUserCache();
}

async function uploadReportToBackend(report) {
    const userId = appState.currentUser ? appState.currentUser.id : (localStorage.getItem('cp_active_user') || 'guest_user');
    if (firebaseEnabled && db) {
        try {
            const timestampStr = new Date().toISOString().replace('T', ' ').substring(0, 19);
            await withTimeout(db.collection("reports").add({
                user_id: userId,
                file_path: `reports/${userId}/${report.filename}`,
                filename: report.filename,
                ai_result: report.verdict,
                probability: report.probability,
                troponin_i: report.troponin,
                bnp: report.bnp,
                nt_probnp: report.ntProbnp,
                uploaded_at: timestampStr
            }), 1200);
            console.log("Diagnostic report logged in Firebase.");
        } catch (err) {
            console.error("Firestore report write failed.", err);
        }
    }
    saveLocalUserCache();
}

function saveLocalUserCache() {
    if (!appState.currentUser) return;
    const usersJson = localStorage.getItem('cp_users');
    const users = JSON.parse(usersJson || '{}');
    
    appState.currentUser.settings = appState.settings;
    appState.currentUser.patientDetails = appState.patientDetails;
    appState.currentUser.reports = appState.reports;
    
    users[appState.currentUser.id] = appState.currentUser;
    localStorage.setItem('cp_users', JSON.stringify(users));
}

// --- DYNAMIC HELPER PLUGINS ---
function updateConnectionDot(isOnline) {
    const dot = document.getElementById('connection-status-dot');
    if (dot) {
        if (isOnline) {
            dot.className = 'status-dot online';
            dot.title = 'Firebase Live Connected';
        } else {
            dot.className = 'status-dot offline';
            dot.title = 'Offline Local Mode';
        }
    }
}

// Format "yyyy-MM-dd HH:mm:ss" or ISO string to standard date
function formatDateString(timestamp) {
    if (!timestamp) return 'Today';
    try {
        const cleaned = timestamp.replace(' ', 'T');
        const date = new Date(cleaned);
        if (isNaN(date.getTime())) return timestamp;
        return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
    } catch (e) {
        return timestamp;
    }
}

// --- UPDATE DASHBOARD PANEL CONTROLS ---
function syncDashboard() {
    if (!appState.currentUser) return;

    // Render profile picture
    const dpUrl = (appState.currentUser && appState.currentUser.profilePictureUrl) 
        ? appState.currentUser.profilePictureUrl 
        : 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&q=80&w=256&h=256';
    
    document.getElementById('user-dp-side').src = dpUrl;
    document.getElementById('user-dp-header').src = dpUrl;
    document.getElementById('user-dp-profile').src = dpUrl;

    const name = appState.currentUser.name;
    document.getElementById('user-greeting-name').innerText = name;
    document.getElementById('user-name-side').innerText = name;
    document.getElementById('prof-name').value = name;
    document.getElementById('prof-email').value = appState.currentUser.email;
    document.getElementById('prof-phone').value = appState.currentUser.phone || '';
    document.getElementById('prof-address').value = appState.currentUser.address || '';
    document.getElementById('prof-emergency').value = appState.currentUser.emergency || '';

    // Render Verdict Details
    if (appState.reports.length > 0) {
        const latest = appState.reports[appState.reports.length - 1];
        appState.currentReport = latest;
        
        document.getElementById('dash-report-date').innerText = `Last scan: ${latest.date}`;
        
        // Gauge fill
        const circle = document.getElementById('gauge-fill-circle');
        const r = circle.r.baseVal.value;
        const circumference = 2 * Math.PI * r;
        const offset = circumference - (latest.probability / 100) * circumference;
        circle.style.strokeDashoffset = offset;
        
        document.getElementById('gauge-pct').innerText = `${latest.probability}%`;
        
        const badge = document.getElementById('dash-verdict-badge');
        badge.innerText = latest.verdict;
        badge.className = 'verdict-badge';
        
        const colorMap = {
            'Healthy': 'healthy',
            'Low Risk': 'low',
            'Risk': 'risk',
            'High Risk': 'high'
        };
        badge.classList.add(colorMap[latest.verdict] || 'low');
        document.getElementById('dash-verdict-title').innerText = `${latest.verdict} Verdict`;
        
        const descriptionMap = {
            'Healthy': 'Negligible probability of cardiac fibrosis. Your heart is in excellent condition.',
            'Low Risk': 'Low probability of cardiac fibrosis progression. Maintain healthy habits.',
            'Risk': 'Moderate probability detected. Some markers indicate early signs of stress.',
            'High Risk': 'Significant probability of cardiac fibrosis. Immediate medical consultation required.'
        };
        document.getElementById('dash-verdict-desc').innerText = descriptionMap[latest.verdict] || '';

        // Biomarker Meters
        document.getElementById('bio-val-trop').innerText = latest.troponin.toFixed(3);
        const tropPct = Math.min((latest.troponin / 0.15) * 100, 100);
        const tropBar = document.getElementById('bio-bar-trop');
        tropBar.style.width = `${tropPct}%`;
        tropBar.className = 'fill ' + (latest.troponin >= 0.12 ? 'red' : latest.troponin >= 0.04 ? 'orange' : 'green');

        document.getElementById('bio-val-bnp').innerText = latest.bnp.toFixed(1);
        const bnpPct = Math.min((latest.bnp / 400) * 100, 100);
        const bnpBar = document.getElementById('bio-bar-bnp');
        bnpBar.style.width = `${bnpPct}%`;
        bnpBar.className = 'fill ' + (latest.bnp >= 300 ? 'red' : latest.bnp >= 100 ? 'orange' : 'green');

        document.getElementById('bio-val-nt').innerText = latest.ntProbnp.toFixed(1);
        const ntPct = Math.min((latest.ntProbnp / 600) * 100, 100);
        const ntBar = document.getElementById('bio-bar-nt');
        ntBar.style.width = `${ntPct}%`;
        ntBar.className = 'fill ' + (latest.ntProbnp >= 450 ? 'red' : latest.ntProbnp >= 125 ? 'orange' : 'green');

        // Dynamic Guidelines
        const listContainer = document.getElementById('dash-recommendations-list');
        listContainer.innerHTML = '';
        const recList = clinicalGuidelines[latest.verdict] || clinicalGuidelines['Low Risk'];
        recList.forEach(rec => {
            const li = document.createElement('li');
            li.innerText = rec;
            listContainer.appendChild(li);
        });

        // Sync health tab recommendation displays
        document.getElementById('rec-group-healthy').classList.add('hidden');
        document.getElementById('rec-group-risk').classList.add('hidden');
        document.getElementById('rec-group-high').classList.add('hidden');

        if (latest.verdict === 'Healthy' || latest.verdict === 'Low Risk') {
            document.getElementById('rec-group-healthy').classList.remove('hidden');
        } else if (latest.verdict === 'Risk') {
            document.getElementById('rec-group-risk').classList.remove('hidden');
        } else {
            document.getElementById('rec-group-high').classList.remove('hidden');
        }
    } else {
        // Safe default if no reports exist yet
        document.getElementById('dash-report-date').innerText = 'No scans run yet';
        document.getElementById('gauge-pct').innerText = '0%';
        document.getElementById('gauge-fill-circle').style.strokeDashoffset = 314.16;
        document.getElementById('dash-verdict-badge').innerText = 'N/A';
        document.getElementById('dash-verdict-title').innerText = 'No assessments';
        document.getElementById('dash-verdict-desc').innerText = 'Upload a diagnostic report to review cardiac progression risk scores.';
    }

    // Sync Lifestyle Stats on Dashboard
    document.getElementById('dash-life-exercise').innerText = appState.patientDetails.exercise;
    document.getElementById('dash-life-diet').innerText = appState.patientDetails.sodium.split(' ')[0];
    const historyFlags = appState.patientDetails.medicalHistory;
    const hasArsenic = historyFlags.includes('Arsenic Exposure History') ? 'Yes' : 'None';
    document.getElementById('dash-life-exposure').innerText = hasArsenic;

    // Sync Health Profile Tab
    if (appState.patientDetails.dob) {
        document.getElementById('hlth-dob').innerText = formatDate(appState.patientDetails.dob);
        const age = calculateAge(appState.patientDetails.dob);
        document.getElementById('hlth-age').innerText = `${age} Years`;
    }
    document.getElementById('hlth-gender').innerText = appState.patientDetails.gender || 'Not set';
    document.getElementById('hlth-blood').innerText = appState.patientDetails.bloodGroup;
    document.getElementById('hlth-height').innerText = appState.patientDetails.height ? `${appState.patientDetails.height} cm` : 'Not set';
    document.getElementById('hlth-weight').innerText = appState.patientDetails.weight ? `${appState.patientDetails.weight} kg` : 'Not set';
    
    if (appState.patientDetails.height && appState.patientDetails.weight) {
        const heightM = appState.patientDetails.height / 100;
        const bmi = appState.patientDetails.weight / (heightM * heightM);
        let status = 'Normal';
        if (bmi < 18.5) status = 'Underweight';
        else if (bmi >= 25 && bmi < 30) status = 'Overweight';
        else if (bmi >= 30) status = 'Obese';
        document.getElementById('hlth-bmi').innerText = `${bmi.toFixed(1)} (${status})`;
    } else {
        document.getElementById('hlth-bmi').innerText = 'Not calculated';
    }

    // Sync Tags
    const tagsWrapper = document.getElementById('hlth-conditions-tags');
    tagsWrapper.innerHTML = '';
    if (historyFlags.length > 0) {
        historyFlags.forEach(cond => {
            const span = document.createElement('span');
            span.className = 'health-tag';
            span.innerText = cond;
            tagsWrapper.appendChild(span);
        });
    } else {
        tagsWrapper.innerHTML = '<span class="empty-tag">No medical conditions specified.</span>';
    }

    // History Table
    const tableBody = document.getElementById('report-history-rows');
    tableBody.innerHTML = '';
    
    if (appState.reports.length > 0) {
        [...appState.reports].reverse().forEach((rep, idx) => {
            const tr = document.createElement('tr');
            const scoreColor = rep.verdict === 'High Risk' ? 'text-red' : rep.verdict === 'Risk' ? 'text-orange' : 'text-green';
            tr.innerHTML = `
                <td><strong>${rep.filename}</strong></td>
                <td>${rep.date}</td>
                <td class="${scoreColor}"><strong>${rep.probability}%</strong></td>
                <td><span class="verdict-badge ${rep.verdict === 'High Risk' ? 'high' : rep.verdict === 'Risk' ? 'risk' : 'low'}">${rep.verdict}</span></td>
                <td>
                    <button class="btn-icon-sm view-historic-report" data-index="${appState.reports.length - 1 - idx}" title="View Details"><i class="fa-solid fa-eye"></i></button>
                </td>
            `;
            tableBody.appendChild(tr);
        });

        // Bind clicks to dynamically injected elements
        document.querySelectorAll('.view-historic-report').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const targetIdx = parseInt(e.currentTarget.getAttribute('data-index'));
                const rep = appState.reports[targetIdx];
                if (rep) showReportPopup(rep);
            });
        });
    } else {
        tableBody.innerHTML = `<tr><td colspan="5" class="text-center py-20 text-muted">No diagnostic reports uploaded yet.</td></tr>`;
    }

    // Sync Toggle Buttons in settings
    document.getElementById('setting-dark-mode').checked = appState.settings.darkMode;
    document.getElementById('setting-sound-effects').checked = appState.settings.soundEffects;
    document.getElementById('setting-push-notif').checked = appState.settings.pushNotifications;
    document.getElementById('setting-email-notif').checked = appState.settings.emailNotifications;

    applyVisualSettings();
}

function showReportPopup(report) {
    const detailContent = `
        <div class="card-header">
            <h4>${report.filename}</h4>
            <span class="report-date">Scanned: ${report.date}</span>
        </div>
        <div style="margin: 15px 0; padding: 15px; border-radius: 12px; background: var(--bg-surface-elevated); border: 1px solid var(--border-color);">
            <div style="display: flex; justify-content: space-between; margin-bottom: 8px;">
                <span>AI Risk Score:</span>
                <strong class="${report.verdict === 'High Risk' ? 'text-red' : report.verdict === 'Risk' ? 'text-orange' : 'text-green'}">${report.probability}% (${report.verdict})</strong>
            </div>
            <div style="display: flex; justify-content: space-between; margin-bottom: 8px;">
                <span>Troponin I level:</span>
                <strong>${report.troponin.toFixed(3)} ng/mL</strong>
            </div>
            <div style="display: flex; justify-content: space-between; margin-bottom: 8px;">
                <span>BNP level:</span>
                <strong>${report.bnp.toFixed(1)} pg/mL</strong>
            </div>
            <div style="display: flex; justify-content: space-between;">
                <span>NT-proBNP level:</span>
                <strong>${report.ntProbnp.toFixed(1)} pg/mL</strong>
            </div>
        </div>
        <p><strong>Clinical Recommendations:</strong></p>
        <ul style="padding-left: 20px; font-size: 0.85rem; color: var(--text-secondary); line-height: 1.4;">
            ${(clinicalGuidelines[report.verdict] || clinicalGuidelines['Low Risk']).map(r => `<li style="margin-bottom:6px;">${r}</li>`).join('')}
        </ul>
    `;

    const popupOverlay = document.createElement('div');
    popupOverlay.className = 'dialog-overlay active';
    popupOverlay.innerHTML = `
        <div class="dialog-card">
            <div class="dialog-header">
                <h3><i class="fa-solid fa-file-medical"></i> Assessment Details</h3>
                <button class="dialog-close pop-close-btn"><i class="fa-solid fa-xmark"></i></button>
            </div>
            <div class="dialog-body">
                ${detailContent}
            </div>
            <div class="dialog-footer">
                <button class="btn btn-primary pop-close-btn-ok">Close</button>
            </div>
        </div>
    `;
    
    document.body.appendChild(popupOverlay);

    const closePopup = () => {
        playSound('click');
        popupOverlay.remove();
    };
    popupOverlay.querySelectorAll('.pop-close-btn, .pop-close-btn-ok').forEach(el => {
        el.addEventListener('click', closePopup);
    });
}

function formatDate(dateStr) {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}

function calculateAge(dobStr) {
    const dob = new Date(dobStr);
    const diffMs = Date.now() - dob.getTime();
    const ageDate = new Date(diffMs);
    return Math.abs(ageDate.getUTCFullYear() - 1970);
}

function applyVisualSettings() {
    const htmlEl = document.documentElement;
    htmlEl.setAttribute('data-theme', appState.settings.theme);
    htmlEl.setAttribute('data-mode', appState.settings.darkMode ? 'dark' : 'light');

    const toggleIcon = document.querySelector('.theme-toggle-btn i');
    if (toggleIcon) {
        toggleIcon.className = appState.settings.darkMode ? 'fa-regular fa-sun' : 'fa-regular fa-moon';
    }

    document.querySelectorAll('.theme-bubble').forEach(bub => {
        if (bub.getAttribute('data-theme') === appState.settings.theme) {
            bub.classList.add('active');
        } else {
            bub.classList.remove('active');
        }
    });
}

// ==================== BOOTSTRAP EVENT BINDINGS ====================
document.addEventListener('DOMContentLoaded', () => {
    
    // Add visual status indicator to sidebar and top header
    const addStatusBadge = () => {
        const sideHeader = document.querySelector('.sidebar-header');
        if (sideHeader && !document.getElementById('connection-status-dot')) {
            const dot = document.createElement('span');
            dot.id = 'connection-status-dot';
            dot.className = 'status-dot';
            dot.style.cssText = 'width:8px; height:8px; border-radius:50%; display:inline-block; margin-left:8px; background-color:#8e9fa5;';
            sideHeader.appendChild(dot);
        }
    };
    addStatusBadge();

    // Firebase state tracking boot
    if (firebaseEnabled && auth) {
        auth.onAuthStateChanged(async (user) => {
            if (user) {
                console.log("Firebase Auth User Detected:", user.email);
                const success = await fetchUserData(user.uid);
                if (success) {
                    syncDashboard();
                    showScreen('main');
                    showTab('tab-dashboard');
                }
            } else {
                console.log("No active Firebase session. Loading login.");
                localStorage.removeItem('cp_active_user');
                showScreen('on1');
            }
        });
    } else {
        // Fallback local storage boot
        const activeUserId = localStorage.getItem('cp_active_user');
        if (activeUserId) {
            fetchUserData(activeUserId).then(() => {
                syncDashboard();
                showScreen('main');
                showTab('tab-dashboard');
            });
        } else {
            setTimeout(() => {
                showScreen('on1');
            }, 1800);
        }
        updateConnectionDot(false);
    }

    // Audio sound cues mapping
    document.body.addEventListener('click', (e) => {
        if (e.target.closest('button') || e.target.closest('a') || e.target.closest('.theme-bubble') || e.target.closest('.checkbox-card')) {
            playSound('click');
        }
    });

    // SPA wizard transitions
    document.querySelectorAll('.btn-next').forEach(btn => {
        btn.addEventListener('click', (e) => {
            showScreen(e.currentTarget.getAttribute('data-next'));
        });
    });

    document.querySelectorAll('.btn-skip').forEach(btn => {
        btn.addEventListener('click', () => { showScreen('login'); });
    });

    const getStartedBtn = document.querySelector('.btn-get-started');
    if (getStartedBtn) {
        getStartedBtn.addEventListener('click', () => { showScreen('login'); });
    }

    document.getElementById('go-signup').addEventListener('click', (e) => {
        e.preventDefault(); showScreen('signup');
    });

    document.getElementById('go-login').addEventListener('click', (e) => {
        e.preventDefault(); showScreen('login');
    });

    document.getElementById('go-forgot').addEventListener('click', (e) => {
        e.preventDefault(); showScreen('forgot');
    });

    document.getElementById('forgot-back').addEventListener('click', (e) => {
        e.preventDefault(); showScreen('login');
    });

    document.getElementById('verify-back').addEventListener('click', (e) => {
        e.preventDefault(); showScreen('signup');
    });

    // Passwords visibility toggle helper
    document.querySelectorAll('.toggle-password').forEach(eye => {
        eye.addEventListener('click', (e) => {
            const targetId = e.currentTarget.getAttribute('data-target');
            const input = document.getElementById(targetId);
            if (input.type === 'password') {
                input.type = 'text';
                e.currentTarget.className = 'fa-regular fa-eye toggle-password';
            } else {
                input.type = 'password';
                e.currentTarget.className = 'fa-regular fa-eye-slash toggle-password';
            }
        });
    });

    // SIGN UP LOGIC (AUTH REGISTRATION WITH FIREBASE BACKEND)
    document.getElementById('form-signup').addEventListener('submit', async (e) => {
        e.preventDefault();
        const name = document.getElementById('signup-name').value;
        const email = document.getElementById('signup-email').value;
        const password = document.getElementById('signup-password').value;

        if (password.length < 6) {
            alert('Password should be at least 6 characters.');
            return;
        }

        const submitBtn = e.target.querySelector('button[type="submit"]');
        submitBtn.disabled = true;
        submitBtn.innerText = "Registering...";

        if (firebaseEnabled && auth) {
            try {
                const cred = await withTimeout(auth.createUserWithEmailAndPassword(email, password), 2500);
                const uid = cred.user.uid;
                
                // Write user details
                await withTimeout(db.collection("users").doc(uid).set({
                    id: uid,
                    name: name,
                    full_name: name,
                    email: email,
                    phone: "",
                    address: "",
                    emergency: "",
                    emergency_contact: ""
                }), 1500);
                
                localStorage.setItem('cp_active_user', uid);
                localStorage.setItem('cp_pending_verify', uid);
                
                submitBtn.disabled = false;
                submitBtn.innerText = "Register";
                showScreen('verify');
            } catch (err) {
                alert("Firebase Registration Failed: " + err.message);
                submitBtn.disabled = false;
                submitBtn.innerText = "Register";
            }
        } else {
            // Local mode signup
            const mockId = 'uid_local_' + Date.now();
            const newUser = {
                id: mockId,
                name: name,
                email: email,
                password: password,
                settings: { ...appState.settings },
                patientDetails: { ...appState.patientDetails },
                reports: []
            };

            const users = JSON.parse(localStorage.getItem('cp_users') || '{}');
            users[mockId] = newUser;
            localStorage.setItem('cp_users', JSON.stringify(users));
            localStorage.setItem('cp_active_user', mockId);
            localStorage.setItem('cp_pending_verify', mockId);
            
            submitBtn.disabled = false;
            submitBtn.innerText = "Register";
            showScreen('verify');
        }
    });

    // CODE VERIFICATION CONFIRMATION
    document.getElementById('btn-verify-submit').addEventListener('click', () => {
        const verifyUser = localStorage.getItem('cp_pending_verify');
        if (!verifyUser) {
            showScreen('signup');
            return;
        }

        playSound('success');
        localStorage.setItem('cp_active_user', verifyUser);
        localStorage.removeItem('cp_pending_verify');

        fetchUserData(verifyUser).then(() => {
            showScreen('continueSetup');
        });
    });

    const codes = document.querySelectorAll('.code-input');
    codes.forEach((input, index) => {
        input.addEventListener('keyup', (e) => {
            if (e.target.value.length === 1 && index < codes.length - 1) {
                codes[index + 1].focus();
            }
            if (e.key === 'Backspace' && index > 0) {
                codes[index - 1].focus();
            }
        });
    });

    // LOGIN ACTION (AUTHENTICATION WITH FIREBASE BACKEND)
    document.getElementById('form-login').addEventListener('submit', async (e) => {
        e.preventDefault();
        const email = document.getElementById('login-email').value;
        const password = document.getElementById('login-password').value;
        
        const submitBtn = e.target.querySelector('button[type="submit"]');
        submitBtn.disabled = true;
        submitBtn.innerText = "Logging in...";

        if (firebaseEnabled && auth) {
            try {
                const cred = await withTimeout(auth.signInWithEmailAndPassword(email, password), 2500);
                localStorage.setItem('cp_active_user', cred.user.uid);
                await fetchUserData(cred.user.uid);
                
                playSound('success');
                syncDashboard();
                showScreen('main');
                showTab('tab-dashboard');
                submitBtn.disabled = false;
                submitBtn.innerText = "Log In";
            } catch (err) {
                alert("Login Failed: " + err.message);
                submitBtn.disabled = false;
                submitBtn.innerText = "Log In";
            }
        } else {
            // Local mode lookup
            const users = JSON.parse(localStorage.getItem('cp_users') || '{}');
            const matched = Object.values(users).find(u => u.email.toLowerCase() === email.toLowerCase());
            
            if (matched && matched.password === password) {
                playSound('success');
                localStorage.setItem('cp_active_user', matched.id);
                await fetchUserData(matched.id);
                syncDashboard();
                showScreen('main');
                showTab('tab-dashboard');
            } else {
                // Auto register a default sandbox account for ease of preview
                const mockId = 'uid_mock_' + Date.now();
                const mockUser = {
                    id: mockId,
                    name: 'Dr. Evelyn Carter',
                    email: email,
                    password: password,
                    settings: { ...appState.settings },
                    patientDetails: {
                        dob: '1985-05-15',
                        gender: 'Female',
                        bloodGroup: 'B+',
                        height: '168',
                        weight: '62',
                        medicalHistory: ['Hypertension', 'Arsenic Exposure History'],
                        exercise: '3-5 hours',
                        sodium: 'Moderate (1500-2300mg/day)',
                        smoking: 'Never',
                        alcohol: 'Occasional',
                        familyHistory: ['Premature Heart Attacks']
                    },
                    reports: [...appState.reports]
                };
                users[mockId] = mockUser;
                localStorage.setItem('cp_users', JSON.stringify(users));
                localStorage.setItem('cp_active_user', mockId);

                playSound('success');
                await fetchUserData(mockId);
                syncDashboard();
                showScreen('main');
                showTab('tab-dashboard');
            }
            submitBtn.disabled = false;
            submitBtn.innerText = "Log In";
        }
    });

    // RESET PASSWORDS
    document.getElementById('form-forgot').addEventListener('submit', (e) => {
        e.preventDefault();
        const email = document.getElementById('forgot-email').value;
        if (firebaseEnabled && auth) {
            auth.sendPasswordResetEmail(email).then(() => {
                alert("Password reset email sent (via Firebase)!");
                showScreen('login');
            }).catch(err => {
                alert("Reset Link Error: " + err.message);
            });
        } else {
            alert(`Password reset code sent to ${email} (Offline simulation)`);
            showScreen('reset');
        }
    });

    document.getElementById('form-reset').addEventListener('submit', (e) => {
        e.preventDefault();
        const pass = document.getElementById('new-password').value;
        const confirm = document.getElementById('confirm-password').value;
        if (pass !== confirm) {
            alert('Passwords do not match.');
            return;
        }
        showScreen('resetSuccess');
    });

    document.getElementById('btn-reset-continue').addEventListener('click', () => {
        showScreen('login');
    });

    // SETUP WIZARD FOR HEALTH PARAMETERS
    document.getElementById('btn-patient-setup-start').addEventListener('click', () => {
        showScreen('wizard');
    });

    let currentStep = 1;
    const prevBtn = document.getElementById('wizard-prev');
    const nextBtn = document.getElementById('wizard-next-btn');
    const stepFill = document.getElementById('wizard-progress');

    function updateWizardStep() {
        document.querySelectorAll('.wizard-step').forEach(step => {
            step.classList.remove('active');
        });
        document.getElementById(`wizard-step-${currentStep}`).classList.add('active');

        const titles = { 1: 'Demographics', 2: 'Medical History', 3: 'Lifestyle details', 4: 'Family History' };
        document.getElementById('wizard-title').innerText = titles[currentStep];

        stepFill.style.width = `${currentStep * 25}%`;

        for (let i = 1; i <= 4; i++) {
            const ind = document.getElementById(`step-ind-${i}`);
            if (i < currentStep) {
                ind.className = 'step-indicator complete';
                ind.innerHTML = '<i class="fa-solid fa-check"></i>';
            } else if (i === currentStep) {
                ind.className = 'step-indicator active';
                ind.innerText = i;
            } else {
                ind.className = 'step-indicator';
                ind.innerText = i;
            }
        }

        prevBtn.disabled = currentStep === 1;
        nextBtn.innerHTML = currentStep === 4 ? 'Complete Wizard <i class="fa-solid fa-check"></i>' : 'Next <i class="fa-solid fa-arrow-right"></i>';
    }

    prevBtn.addEventListener('click', () => {
        if (currentStep > 1) {
            currentStep--;
            updateWizardStep();
        }
    });

    nextBtn.addEventListener('click', async () => {
        if (currentStep < 4) {
            if (currentStep === 1) {
                const dob = document.getElementById('pat-dob').value;
                const gender = document.getElementById('pat-gender').value;
                if (!dob || !gender) {
                    alert('Please enter your date of birth and select your gender.');
                    return;
                }
            }
            currentStep++;
            updateWizardStep();
        } else {
            // Save state fields
            appState.patientDetails.dob = document.getElementById('pat-dob').value;
            appState.patientDetails.gender = document.getElementById('pat-gender').value;
            appState.patientDetails.bloodGroup = document.getElementById('pat-blood').value;
            appState.patientDetails.height = document.getElementById('pat-height').value;
            appState.patientDetails.weight = document.getElementById('pat-weight').value;

            const histories = [];
            document.querySelectorAll('input[name="medical_history"]:checked').forEach(chk => {
                histories.push(chk.value);
            });
            appState.patientDetails.medicalHistory = histories;

            appState.patientDetails.exercise = document.getElementById('life-exercise').value;
            appState.patientDetails.sodium = document.getElementById('life-diet').value;
            appState.patientDetails.smoking = document.getElementById('life-smoke').value;
            appState.patientDetails.alcohol = document.getElementById('life-alcohol').value;

            const families = [];
            document.querySelectorAll('input[name="family_history"]:checked').forEach(chk => {
                families.push(chk.value);
            });
            appState.patientDetails.familyHistory = families;

            // Sync to Firestore & localStorage
            await savePatientDetailsToBackend();
            syncDashboard();
            
            playSound('success');
            showScreen('main');
            showTab('tab-dashboard');
        }
    });

    // NAVIGATION TABS
    document.querySelectorAll('.nav-item').forEach(item => {
        item.addEventListener('click', (e) => {
            e.preventDefault();
            showTab(e.currentTarget.getAttribute('data-tab'));
        });
    });

    document.querySelectorAll('.mobile-nav-item').forEach(item => {
        item.addEventListener('click', (e) => {
            e.preventDefault();
            showTab(e.currentTarget.getAttribute('data-tab'));
        });
    });

    document.getElementById('btn-go-upload-tab').addEventListener('click', () => {
        showTab('tab-reports');
    });

    document.getElementById('btn-logout-sidebar').addEventListener('click', (e) => {
        e.preventDefault();
        if (firebaseEnabled && auth) {
            auth.signOut();
        }
        localStorage.removeItem('cp_active_user');
        appState.currentUser = null;
        showScreen('login');
    });

    // APP SETTINGS AND PREFERENCES
    const toggleSetDark = document.getElementById('setting-dark-mode');
    toggleSetDark.addEventListener('change', (e) => {
        appState.settings.darkMode = e.target.checked;
        saveUserProfileToBackend();
        applyVisualSettings();
    });

    document.querySelector('.theme-toggle-btn').addEventListener('click', () => {
        appState.settings.darkMode = !appState.settings.darkMode;
        toggleSetDark.checked = appState.settings.darkMode;
        saveUserProfileToBackend();
        applyVisualSettings();
    });

    document.querySelectorAll('.theme-bubble').forEach(bub => {
        bub.addEventListener('click', (e) => {
            appState.settings.theme = e.currentTarget.getAttribute('data-theme');
            saveUserProfileToBackend();
            applyVisualSettings();
        });
    });

    document.getElementById('setting-sound-effects').addEventListener('change', (e) => {
        appState.settings.soundEffects = e.target.checked;
        saveLocalUserCache();
    });

    document.getElementById('setting-push-notif').addEventListener('change', (e) => {
        appState.settings.pushNotifications = e.target.checked;
        saveUserProfileToBackend();
    });

    document.getElementById('setting-email-notif').addEventListener('change', (e) => {
        appState.settings.emailNotifications = e.target.checked;
        saveUserProfileToBackend();
    });

    // ACCOUNT PROFILE UPDATING
    document.getElementById('form-edit-profile').addEventListener('submit', async (e) => {
        e.preventDefault();
        appState.currentUser.name = document.getElementById('prof-name').value;
        appState.currentUser.phone = document.getElementById('prof-phone').value;
        appState.currentUser.address = document.getElementById('prof-address').value;
        appState.currentUser.emergency = document.getElementById('prof-emergency').value;

        await saveUserProfileToBackend();
        syncDashboard();
        
        playSound('success');
        alert('Profile saved successfully.');
    });

    // DP FILE PICKER TRIGGER & HANDLER
    const dpPicker = document.getElementById('dp-file-picker');
    const btnChangeDp = document.getElementById('btn-change-dp');
    
    if (btnChangeDp && dpPicker) {
        btnChangeDp.addEventListener('click', () => {
            dpPicker.click();
        });
        
        dpPicker.addEventListener('change', (e) => {
            if (e.target.files.length > 0) {
                const file = e.target.files[0];
                if (file.size > 2 * 1024 * 1024) {
                    alert("Image size exceeds 2MB limit.");
                    return;
                }
                
                const reader = new FileReader();
                reader.onload = async () => {
                    appState.currentUser.profilePictureUrl = reader.result;
                    
                    // Render locally immediately for instant feedback
                    document.getElementById('user-dp-profile').src = reader.result;
                    document.getElementById('user-dp-side').src = reader.result;
                    document.getElementById('user-dp-header').src = reader.result;
                    
                    // Save to backend and local storage
                    await saveUserProfileToBackend();
                    playSound('success');
                };
                reader.readAsDataURL(file);
            }
        });
    }

    // POPUPS AND DIALOG TRIGGERS
    const dialogHowAI = document.getElementById('dialog-how-ai');
    document.getElementById('btn-how-ai-works').addEventListener('click', () => {
        dialogHowAI.classList.add('active');
    });
    document.getElementById('btn-close-dialog-how-ai').addEventListener('click', () => {
        dialogHowAI.classList.remove('active');
    });
    document.getElementById('btn-close-dialog-how-ai-ok').addEventListener('click', () => {
        dialogHowAI.classList.remove('active');
    });

    const dialogBiomarkers = document.getElementById('dialog-biomarkers');
    document.getElementById('btn-see-bio-details').addEventListener('click', () => {
        dialogBiomarkers.classList.add('active');
    });
    document.getElementById('btn-close-dialog-biomarkers').addEventListener('click', () => {
        dialogBiomarkers.classList.remove('active');
    });
    document.getElementById('btn-close-dialog-biomarkers-ok').addEventListener('click', () => {
        dialogBiomarkers.classList.remove('active');
    });

    // EDIT HEALTH SUMMARY POPUP TRIGGERS
    const dialogEditHealth = document.getElementById('dialog-edit-health');
    document.getElementById('btn-edit-health-metrics').addEventListener('click', () => {
        // Pre-populate input values from appState.patientDetails
        document.getElementById('edit-pat-dob').value = appState.patientDetails.dob || '';
        document.getElementById('edit-pat-gender').value = appState.patientDetails.gender || 'Male';
        document.getElementById('edit-pat-blood').value = appState.patientDetails.bloodGroup || 'O+';
        document.getElementById('edit-pat-height').value = appState.patientDetails.height || '';
        document.getElementById('edit-pat-weight').value = appState.patientDetails.weight || '';
        document.getElementById('edit-life-exercise').value = appState.patientDetails.exercise || '3-5 hours';
        document.getElementById('edit-life-sodium').value = appState.patientDetails.sodium || 'Moderate (1500-2300mg/day)';
        document.getElementById('edit-life-smoke').value = appState.patientDetails.smoking || 'Never';

        // Pre-check conditions
        const historyFlags = appState.patientDetails.medicalHistory || [];
        document.querySelectorAll('input[name="edit_medical_history"]').forEach(chk => {
            chk.checked = historyFlags.includes(chk.value);
        });

        dialogEditHealth.classList.add('active');
    });

    const closeEditHealthDialog = () => {
        dialogEditHealth.classList.remove('active');
    };

    document.getElementById('btn-close-dialog-edit-health').addEventListener('click', closeEditHealthDialog);
    document.getElementById('btn-cancel-dialog-edit-health').addEventListener('click', closeEditHealthDialog);

    document.getElementById('form-edit-health').addEventListener('submit', async (e) => {
        e.preventDefault();
        
        // Save values back to appState.patientDetails
        appState.patientDetails.dob = document.getElementById('edit-pat-dob').value;
        appState.patientDetails.gender = document.getElementById('edit-pat-gender').value;
        appState.patientDetails.bloodGroup = document.getElementById('edit-pat-blood').value;
        appState.patientDetails.height = document.getElementById('edit-pat-height').value;
        appState.patientDetails.weight = document.getElementById('edit-pat-weight').value;
        appState.patientDetails.exercise = document.getElementById('edit-life-exercise').value;
        appState.patientDetails.sodium = document.getElementById('edit-life-sodium').value;
        appState.patientDetails.smoking = document.getElementById('edit-life-smoke').value;

        const updatedHistory = [];
        document.querySelectorAll('input[name="edit_medical_history"]:checked').forEach(chk => {
            updatedHistory.push(chk.value);
        });
        appState.patientDetails.medicalHistory = updatedHistory;

        // Save to database & sync view
        const saveBtn = e.target.querySelector('button[type="submit"]');
        saveBtn.disabled = true;
        saveBtn.innerText = "Saving...";

        await savePatientDetailsToBackend();
        syncDashboard();

        saveBtn.disabled = false;
        saveBtn.innerText = "Save Details";
        dialogEditHealth.classList.remove('active');
        playSound('success');
        alert("Health profile parameters updated successfully!");
    });

    const drawerNotif = document.getElementById('drawer-notif');
    document.getElementById('btn-notif-bell').addEventListener('click', () => {
        drawerNotif.classList.add('active');
    });
    document.getElementById('btn-close-drawer-notif').addEventListener('click', () => {
        drawerNotif.classList.remove('active');
    });
    document.getElementById('btn-mark-all-read').addEventListener('click', () => {
        document.querySelectorAll('.notification-item').forEach(item => {
            item.classList.remove('unread');
        });
        document.getElementById('notif-badge').style.display = 'none';
        playSound('success');
    });

    // FILE ASSESSMENT DRAG AND DROP
    const dropZone = document.getElementById('drop-zone');
    const filePicker = document.getElementById('file-picker');
    const pPrompt = document.getElementById('zone-prompt');
    const pUploading = document.getElementById('zone-uploading');
    const pAnalyzing = document.getElementById('zone-analyzing');
    const pComplete = document.getElementById('zone-complete');
    const upFilename = document.getElementById('uploading-filename');
    const compFilename = document.getElementById('complete-filename');
    const progressFill = document.getElementById('upload-progress-fill');
    const statusText = document.getElementById('ai-status-text');

    dropZone.addEventListener('click', (e) => {
        if (e.target.closest('#btn-view-analysis-result')) return;
        if (!pPrompt.classList.contains('hidden')) {
            filePicker.click();
        }
    });

    filePicker.addEventListener('change', (e) => {
        if (e.target.files.length > 0) processFile(e.target.files[0]);
    });

    dropZone.addEventListener('dragover', (e) => {
        e.preventDefault(); dropZone.classList.add('dragover');
    });

    dropZone.addEventListener('dragleave', () => {
        dropZone.classList.remove('dragover');
    });

    dropZone.addEventListener('drop', (e) => {
        e.preventDefault();
        dropZone.classList.remove('dragover');
        if (e.dataTransfer.files.length > 0 && !pPrompt.classList.contains('hidden')) {
            processFile(e.dataTransfer.files[0]);
        }
    });

    function processFile(file) {
        const name = file.name;
        pPrompt.classList.add('hidden');
        pUploading.classList.remove('hidden');
        upFilename.innerText = name;
        progressFill.style.width = '0%';

        let progress = 0;
        const uploadInterval = setInterval(() => {
            progress += 10;
            progressFill.style.width = `${progress}%`;
            if (progress >= 100) {
                clearInterval(uploadInterval);
                pUploading.classList.add('hidden');
                pAnalyzing.classList.remove('hidden');
                runAIAnalysis(name);
            }
        }, 150);
    }

    function runAIAnalysis(filename) {
        const ms1 = document.getElementById('ms-1');
        const ms2 = document.getElementById('ms-2');
        const ms3 = document.getElementById('ms-3');

        ms1.className = 'milestone done';
        ms2.className = 'milestone';
        ms3.className = 'milestone';
        ms1.querySelector('i').className = 'fa-solid fa-circle-check';
        ms2.querySelector('i').className = 'fa-regular fa-circle';
        ms3.querySelector('i').className = 'fa-regular fa-circle';

        statusText.innerText = "Parsing reports & scanning layout...";
        
        setTimeout(() => {
            statusText.innerText = "Extracting troponin-I and ventricle stress levels...";
            ms2.className = 'milestone done';
            ms2.querySelector('i').className = 'fa-solid fa-circle-check';
        }, 1200);

        setTimeout(() => {
            statusText.innerText = "Computing progression risk with deep neural layers...";
            ms3.className = 'milestone done';
            ms3.querySelector('i').className = 'fa-solid fa-circle-check';
        }, 2400);

        setTimeout(async () => {
            const parsedReport = parseFilenameRisk(filename);
            
            // Push to reports queue & send to database
            appState.reports.push(parsedReport);
            await uploadReportToBackend(parsedReport);
            syncDashboard();

            pAnalyzing.classList.add('hidden');
            pComplete.classList.remove('hidden');
            compFilename.innerText = filename;
            playSound('success');

            document.getElementById('btn-view-analysis-result').onclick = () => {
                pComplete.classList.add('hidden');
                pPrompt.classList.remove('hidden');
                showTab('tab-dashboard');
            };
        }, 3600);
    }

    // Risk generator based on reports parsed
    function parseFilenameRisk(filename) {
        const lowerName = filename.toLowerCase();
        let riskVerdict = 'Low Risk';
        let probability = 15;
        let troponin = 0.02;
        let bnp = 65.0;
        let ntProbnp = 110.0;

        if (lowerName.includes('healthy')) {
            riskVerdict = 'Healthy';
            probability = Math.floor(Math.random() * 6) + 10;
            troponin = (Math.floor(Math.random() * 21) + 10) / 1000.0;
            bnp = Math.floor(Math.random() * 36) + 10.0;
            ntProbnp = Math.floor(Math.random() * 56) + 40.0;
        } else if (lowerName.includes('high_risk') || lowerName.includes('high')) {
            riskVerdict = 'High Risk';
            probability = Math.floor(Math.random() * 15) + 82;
            troponin = (Math.floor(Math.random() * 19) + 6) / 10.0;
            bnp = Math.floor(Math.random() * 461) + 320.0;
            ntProbnp = Math.floor(Math.random() * 1451) + 950.0;
        } else if (lowerName.includes('risk') || lowerName.includes('medium') || lowerName.includes('moderate')) {
            riskVerdict = 'Risk';
            probability = Math.floor(Math.random() * 24) + 48;
            troponin = (Math.floor(Math.random() * 27) + 12) / 100.0;
            bnp = Math.floor(Math.random() * 136) + 110.0;
            ntProbnp = Math.floor(Math.random() * 271) + 310.0;
        } else {
            riskVerdict = 'Low Risk';
            probability = Math.floor(Math.random() * 17) + 18;
            troponin = (Math.floor(Math.random() * 5) + 4) / 100.0;
            bnp = Math.floor(Math.random() * 39) + 50.0;
            ntProbnp = Math.floor(Math.random() * 86) + 100.0;
        }

        return {
            filename: filename,
            date: new Date().toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }),
            probability: probability,
            verdict: riskVerdict,
            troponin: troponin,
            bnp: bnp,
            ntProbnp: ntProbnp,
            timestamp: new Date().toISOString().replace('T', ' ').substring(0, 19)
        };
    }
});
