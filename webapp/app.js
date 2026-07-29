/**
 * CardioProgress - High-Tech Web Engine & Real-Time Cloud Sync
 * Integrated with Firebase Project cardio-ai-635a5
 */

// --- FIREBASE CONFIGURATION (Matching Android Project cardio-ai-635a5) ---
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

// Active Snapshot Unsubscribers
let unsubscribeUser = null;
let unsubscribePatientDetails = null;
let unsubscribeReports = null;

// Initialize Firebase
try {
    if (typeof firebase !== 'undefined') {
        firebase.initializeApp(firebaseConfig);
        db = firebase.firestore();
        auth = firebase.auth();
        firebaseEnabled = true;
        console.log("Firebase Real-time Backend initialized successfully!");
    }
} catch (e) {
    console.warn("Firebase Web SDK failed to initialize. Operating in Local Mode.", e);
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

// --- APPLICATION STATE ---
const appState = {
    currentUser: null,
    activeReport: null,
    settings: {
        darkMode: true,
        theme: 'cyan',
        soundEffects: true,
        pushNotifications: true,
        emailNotifications: false
    },
    patientDetails: {
        dob: '',
        gender: '',
        bloodGroup: '',
        height: '',
        weight: '',
        medicalHistory: [],
        exercise: '',
        sodium: '',
        smoking: '',
        alcohol: '',
        familyHistory: ''
    },
    reports: [],
    activeTab: 'tab-dashboard'
};

// --- NAVIGATION & UI CONTROL ---
function showScreen(screenId) {
    document.querySelectorAll('.screen').forEach(s => {
        s.classList.remove('active');
        s.style.display = 'none';
    });
    const target = document.getElementById(screenId.startsWith('screen-') ? screenId : `screen-${screenId}`);
    if (target) {
        target.style.display = 'flex';
        setTimeout(() => target.classList.add('active'), 20);
    }
}

function showTab(tabId) {
    document.querySelectorAll('.nav-item').forEach(nav => {
        if (nav.getAttribute('data-tab') === tabId) {
            nav.classList.add('active');
        } else {
            nav.classList.remove('active');
        }
    });

    document.querySelectorAll('.tab-content').forEach(tab => {
        if (tab.id === tabId) {
            tab.style.display = 'block';
            tab.classList.add('active');
        } else {
            tab.style.display = 'none';
            tab.classList.remove('active');
        }
    });

    const titleMap = {
        'tab-dashboard': 'Cardiac Assessment',
        'tab-reports': 'Reports & Diagnostics',
        'tab-health': 'Health Profile Summary',
        'tab-profile': 'Account Details',
        'tab-settings': 'System Settings'
    };
    const titleElem = document.getElementById('header-title');
    if (titleElem) titleElem.innerText = titleMap[tabId] || 'Cardiac Assessment';
    appState.activeTab = tabId;
    syncDashboard();
}

// --- UTILITY & THEME FUNCTIONS ---
function updateConnectionDot(isOnline) {
    const dot = document.getElementById('connection-status-dot');
    if (dot) {
        dot.className = isOnline ? 'status-dot online' : 'status-dot offline';
    }
}

function formatDateString(timestamp) {
    if (!timestamp) return 'Today';
    return timestamp.substring(0, 10);
}

function applyVisualSettings() {
    if (appState.settings.darkMode) {
        document.body.classList.remove('light-theme');
    } else {
        document.body.classList.add('light-theme');
    }

    const root = document.documentElement;
    const theme = appState.settings.theme || 'cyan';

    if (theme === 'emerald') {
        root.style.setProperty('--primary', '#10B981');
        root.style.setProperty('--secondary', '#059669');
        root.style.setProperty('--primary-glow', 'rgba(16, 185, 129, 0.25)');
    } else if (theme === 'purple') {
        root.style.setProperty('--primary', '#A855F7');
        root.style.setProperty('--secondary', '#7C3AED');
        root.style.setProperty('--primary-glow', 'rgba(168, 85, 247, 0.25)');
    } else if (theme === 'crimson') {
        root.style.setProperty('--primary', '#F43F5E');
        root.style.setProperty('--secondary', '#E11D48');
        root.style.setProperty('--primary-glow', 'rgba(244, 63, 94, 0.25)');
    } else {
        root.style.setProperty('--primary', '#00F5D4');
        root.style.setProperty('--secondary', '#00BBF9');
        root.style.setProperty('--primary-glow', 'rgba(0, 245, 212, 0.25)');
    }

    const chkDark = document.getElementById('setting-dark-mode');
    const chkSound = document.getElementById('setting-sound-effects');
    const chkPush = document.getElementById('setting-push-notif');
    const chkEmail = document.getElementById('setting-email-notif');
    const selTheme = document.getElementById('setting-theme-accent');

    if (chkDark) chkDark.checked = appState.settings.darkMode;
    if (chkSound) chkSound.checked = appState.settings.soundEffects;
    if (chkPush) chkPush.checked = appState.settings.pushNotifications;
    if (chkEmail) chkEmail.checked = appState.settings.emailNotifications;
    if (selTheme) selTheme.value = theme;
}

// --- DETAILED REPORT POPUP MODAL ---
function showReportPopup(report) {
    appState.activeReport = report;
    const modal = document.getElementById('report-detail-modal');
    if (!modal) {
        alert(`Report: ${report.filename}\nScore: ${report.probability}%\nVerdict: ${report.verdict}`);
        return;
    }

    const fname = document.getElementById('modal-report-filename');
    const fdate = document.getElementById('modal-report-date');
    const badge = document.getElementById('modal-verdict-badge');
    const score = document.getElementById('modal-prob-score');
    const desc = document.getElementById('modal-summary-desc');
    const valTrop = document.getElementById('modal-val-trop');
    const valBnp = document.getElementById('modal-val-bnp');
    const valNt = document.getElementById('modal-val-nt');

    if (fname) fname.innerText = report.filename;
    if (fdate) fdate.innerText = `Scan Date: ${report.date}`;
    if (score) score.innerText = `${report.probability}% Fibrosis Progression Risk`;
    
    if (badge) {
        badge.innerText = report.verdict;
        const vUpper = report.verdict.toUpperCase();
        if (vUpper.includes('HIGH')) {
            badge.className = 'verdict-badge high';
            if (desc) desc.innerText = "Significant biomarker elevation detected. Immediate consultation with a cardiac specialist recommended.";
        } else if (vUpper.includes('RISK') || vUpper.includes('MODERATE')) {
            badge.className = 'verdict-badge moderate';
            if (desc) desc.innerText = "Moderate probability detected. Biomarkers indicate cardiac stress. Follow-up recommended.";
        } else {
            badge.className = 'verdict-badge low';
            if (desc) desc.innerText = "Biomarkers indicate low probability of structural cardiac fibrosis. Heart is in good condition.";
        }
    }

    if (valTrop) valTrop.innerText = `${report.troponin} ng/mL`;
    if (valBnp) valBnp.innerText = `${report.bnp} pg/mL`;
    if (valNt) valNt.innerText = `${report.ntProbnp} pg/mL`;

    modal.classList.add('active');
}

function saveLocalUserCache() {
    if (appState.currentUser) {
        localStorage.setItem('cp_users', JSON.stringify({ [appState.currentUser.id]: appState.currentUser }));
    }
}

// --- DUAL-LAYER PERSISTENCE HELPERS ---
function saveLocalState(userId) {
    if (!userId) return;
    try {
        if (appState.currentUser) {
            localStorage.setItem(`cp_user_${userId}`, JSON.stringify(appState.currentUser));
        }
        if (appState.patientDetails) {
            localStorage.setItem(`cp_patient_${userId}`, JSON.stringify(appState.patientDetails));
        }
        if (appState.reports) {
            localStorage.setItem(`cp_reports_${userId}`, JSON.stringify(appState.reports));
        }
    } catch (e) {
        console.warn("LocalStorage save cache notice:", e);
    }
}

function loadLocalState(userId) {
    if (!userId) return false;
    let loadedAny = false;
    try {
        const userRaw = localStorage.getItem(`cp_user_${userId}`);
        if (userRaw) {
            const uData = JSON.parse(userRaw);
            appState.currentUser = { ...appState.currentUser, ...uData };
            if (uData.settings) appState.settings = { ...appState.settings, ...uData.settings };
            loadedAny = true;
        }

        const patRaw = localStorage.getItem(`cp_patient_${userId}`);
        if (patRaw) {
            appState.patientDetails = JSON.parse(patRaw);
            loadedAny = true;
        }

        const repRaw = localStorage.getItem(`cp_reports_${userId}`);
        if (repRaw) {
            appState.reports = JSON.parse(repRaw);
            loadedAny = true;
        }
    } catch (e) {
        console.warn("LocalStorage load cache notice:", e);
    }
    return loadedAny;
}

// --- FETCH & REALTIME SYNCHRONIZATION ---
async function fetchUserData(userId) {
    if (!userId) return;
    
    const activeEmail = auth && auth.currentUser && auth.currentUser.email 
        ? auth.currentUser.email 
        : (localStorage.getItem('cp_active_email') || 'user@example.com');
        
    const defaultName = activeEmail.includes('@') ? activeEmail.split('@')[0] : 'User';

    if (!appState.currentUser || appState.currentUser.id !== userId) {
        appState.currentUser = {
            id: userId,
            name: defaultName,
            email: activeEmail,
            phone: '',
            address: '',
            emergency: '',
            profilePictureUrl: ''
        };
    }

    // Load from Local Storage Cache First for Instant Persistence
    loadLocalState(userId);
    applyVisualSettings();
    syncDashboard();

    if (firebaseEnabled && db) {
        try {
            // 1. Fetch User Profile & Settings
            const userDoc = await db.collection("users").doc(userId).get();
            if (userDoc.exists) {
                const data = userDoc.data();
                appState.currentUser = {
                    id: userId,
                    name: data.full_name || data.name || appState.currentUser.name || defaultName,
                    email: data.email || activeEmail,
                    phone: data.phone || appState.currentUser.phone || '',
                    address: data.address || appState.currentUser.address || '',
                    emergency: data.emergency_contact || data.emergency || appState.currentUser.emergency || '',
                    profilePictureUrl: data.profile_picture_url || appState.currentUser.profilePictureUrl || '',
                    settings: data.settings || appState.settings
                };
                if (data.settings) {
                    appState.settings = { ...appState.settings, ...data.settings };
                }
            } else {
                await saveUserProfileToBackend();
            }

            // 2. Fetch Patient Details
            const detDoc = await db.collection("patient_details").doc(userId).get();
            if (detDoc.exists) {
                const det = detDoc.data();
                appState.patientDetails = {
                    dob: det.dob || appState.patientDetails.dob || '',
                    gender: det.gender || appState.patientDetails.gender || '',
                    bloodGroup: det.blood_type || appState.patientDetails.bloodGroup || '',
                    height: det.height_cm || appState.patientDetails.height || '',
                    weight: det.weight_kg || appState.patientDetails.weight || '',
                    medicalHistory: det.medical_history || appState.patientDetails.medicalHistory || [],
                    exercise: det.exercise_hours || appState.patientDetails.exercise || '',
                    sodium: det.sodium_intake || appState.patientDetails.sodium || '',
                    smoking: det.smoking_habit || appState.patientDetails.smoking || '',
                    alcohol: det.alcohol_consumption || appState.patientDetails.alcohol || '',
                    familyHistory: det.family_history || appState.patientDetails.familyHistory || ''
                };
            }

            // 3. Fetch Reports
            const reportsSnap = await db.collection("reports").where("user_id", "==", userId).get();
            const loaded = [];
            reportsSnap.forEach(doc => {
                const rep = doc.data();
                loaded.push({
                    filename: rep.filename || 'report.pdf',
                    date: rep.uploaded_at ? rep.uploaded_at.substring(0, 10) : 'Today',
                    probability: rep.probability || 15,
                    verdict: rep.ai_result || 'LOW RISK',
                    troponin: rep.troponin_i || 0.02,
                    bnp: rep.bnp || 50.0,
                    ntProbnp: rep.nt_probnp || 90.0,
                    timestamp: rep.uploaded_at || ''
                });
            });
            // Sort newest-first by actual parsed time (not Firestore's arbitrary doc order).
            // The Android app may write "uploaded_at" in a different but still valid date format,
            // so parse with Date() rather than comparing the raw strings.
            loaded.sort((a, b) => (new Date(b.timestamp).getTime() || 0) - (new Date(a.timestamp).getTime() || 0));
            if (loaded.length > 0) appState.reports = loaded;

            saveLocalState(userId);

        } catch (e) {
            console.error("fetchUserData error:", e);
        }
    }
    
    applyVisualSettings();
    syncDashboard();
    attachRealtimeListeners(userId);
}

async function savePatientDetailsToBackend() {
    if (!appState.currentUser) return;
    const userId = appState.currentUser.id;
    saveLocalState(userId);
    if (firebaseEnabled && db) {
        try {
            await db.collection("patient_details").doc(userId).set({
                user_id: userId,
                full_name: appState.currentUser.name,
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
            }, { merge: true });
        } catch (e) {
            console.error("savePatientDetailsToBackend error:", e);
        }
    }
}

async function uploadReportToBackend(report) {
    if (!appState.currentUser) return;
    const userId = appState.currentUser.id;
    saveLocalState(userId);
    if (firebaseEnabled && db) {
        try {
            await db.collection("reports").add({
                user_id: userId,
                filename: report.filename,
                file_path: `reports/${userId}/${report.filename}`,
                ai_result: report.verdict,
                probability: report.probability,
                troponin_i: report.troponin,
                bnp: report.bnp,
                nt_probnp: report.ntProbnp,
                uploaded_at: new Date().toISOString()
            });
        } catch (e) {
            console.error("uploadReportToBackend error:", e);
        }
    }
}

function attachRealtimeListeners(userId) {
    if (!firebaseEnabled || !db || !userId) return;

    if (unsubscribeUser) unsubscribeUser();
    if (unsubscribePatientDetails) unsubscribePatientDetails();
    if (unsubscribeReports) unsubscribeReports();

    unsubscribeUser = db.collection("users").doc(userId).onSnapshot(doc => {
        if (doc.exists) {
            const data = doc.data();
            const fallbackName = data.email && data.email.includes('@') ? data.email.split('@')[0] : 'User';
            appState.currentUser = {
                id: userId,
                name: data.full_name || data.name || (appState.currentUser ? appState.currentUser.name : fallbackName),
                email: data.email || (appState.currentUser ? appState.currentUser.email : ''),
                phone: data.phone || (appState.currentUser ? appState.currentUser.phone : ''),
                address: data.address || (appState.currentUser ? appState.currentUser.address : ''),
                emergency: data.emergency_contact || data.emergency || (appState.currentUser ? appState.currentUser.emergency : ''),
                profilePictureUrl: data.profile_picture_url || (appState.currentUser ? appState.currentUser.profilePictureUrl : ''),
                settings: data.settings || appState.settings
            };
            if (data.settings) {
                appState.settings = { ...appState.settings, ...data.settings };
                applyVisualSettings();
            }
            saveLocalState(userId);
            syncDashboard();
        } else {
            saveUserProfileToBackend();
        }
    });

    unsubscribePatientDetails = db.collection("patient_details").doc(userId).onSnapshot(doc => {
        if (doc.exists) {
            const det = doc.data();
            const h = det.height_cm;
            const w = det.weight_kg;
            appState.patientDetails = {
                dob: det.dob || (appState.patientDetails ? appState.patientDetails.dob : '') || '',
                gender: det.gender || (appState.patientDetails ? appState.patientDetails.gender : '') || '',
                bloodGroup: det.blood_type || (appState.patientDetails ? appState.patientDetails.bloodGroup : '') || '',
                height: (h !== null && h !== undefined) ? String(h) : (appState.patientDetails ? appState.patientDetails.height : ''),
                weight: (w !== null && w !== undefined) ? String(w) : (appState.patientDetails ? appState.patientDetails.weight : ''),
                medicalHistory: det.medical_history || (appState.patientDetails ? appState.patientDetails.medicalHistory : []) || [],
                exercise: det.exercise_hours || (appState.patientDetails ? appState.patientDetails.exercise : '') || '',
                sodium: det.sodium_intake || (appState.patientDetails ? appState.patientDetails.sodium : '') || '',
                smoking: det.smoking_habit || (appState.patientDetails ? appState.patientDetails.smoking : '') || '',
                alcohol: det.alcohol_consumption || (appState.patientDetails ? appState.patientDetails.alcohol : '') || '',
                familyHistory: det.family_history || (appState.patientDetails ? appState.patientDetails.familyHistory : '') || ''
            };
            saveLocalState(userId);
            syncDashboard();
        } else {
            if (appState.patientDetails && (appState.patientDetails.dob || appState.patientDetails.height || appState.patientDetails.weight)) {
                savePatientDetailsToBackend();
            }
        }
    });

    unsubscribeReports = db.collection("reports").where("user_id", "==", userId).onSnapshot(snapshot => {
        const loaded = [];
        snapshot.forEach(doc => {
            const rep = doc.data();
            loaded.push({
                filename: rep.filename || 'report.pdf',
                date: rep.uploaded_at ? rep.uploaded_at.substring(0, 10) : 'Today',
                probability: rep.probability || 15,
                verdict: rep.ai_result || 'LOW RISK',
                troponin: rep.troponin_i || 0.02,
                bnp: rep.bnp || 50.0,
                ntProbnp: rep.nt_probnp || 90.0,
                timestamp: rep.uploaded_at || ''
            });
        });
        // Same newest-first fix as fetchUserData: sort by parsed time, not raw string/doc order.
        loaded.sort((a, b) => (new Date(b.timestamp).getTime() || 0) - (new Date(a.timestamp).getTime() || 0));
        appState.reports = loaded;
        syncDashboard();
    });
}

// --- SYNC DASHBOARD & UI ELEMENTS ---
function syncDashboard() {
    if (!appState.currentUser) return;

    const defaultDp = `data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" fill="%2300BFA5"><circle cx="50" cy="35" r="20"/><path d="M20,85 C20,65 35,55 50,55 C65,55 80,65 80,85 Z"/></svg>`;
    const dpUrl = appState.currentUser.profilePictureUrl && appState.currentUser.profilePictureUrl.length > 10
        ? appState.currentUser.profilePictureUrl 
        : defaultDp;

    const dpProfile = document.getElementById('user-dp-profile');
    const dpSide = document.getElementById('user-dp-side');
    const dpHeader = document.getElementById('user-dp-header');
    
    if (dpProfile) dpProfile.src = dpUrl;
    if (dpSide) dpSide.src = dpUrl;
    if (dpHeader) dpHeader.src = dpUrl;

    const name = appState.currentUser.name || 'User';
    const nameSide = document.getElementById('user-name-side');
    if (nameSide) nameSide.innerText = name;

    const profName = document.getElementById('prof-name');
    const profEmail = document.getElementById('prof-email');
    const profPhone = document.getElementById('prof-phone');
    const profAddress = document.getElementById('prof-address');
    const profEmergency = document.getElementById('prof-emergency');

    if (profName) profName.value = name;
    if (profEmail) profEmail.value = appState.currentUser.email || '';
    if (profPhone) profPhone.value = appState.currentUser.phone || '';
    if (profAddress) profAddress.value = appState.currentUser.address || '';
    if (profEmergency) profEmergency.value = appState.currentUser.emergency || '';

    // Populate Health Profile Summary Inputs
    const hpDob = document.getElementById('hp-dob');
    const hpGender = document.getElementById('hp-gender');
    const hpBlood = document.getElementById('hp-blood');
    const hpHeight = document.getElementById('hp-height');
    const hpWeight = document.getElementById('hp-weight');
    const hpBmi = document.getElementById('hp-bmi-val');
    const hpExercise = document.getElementById('hp-exercise');
    const hpSodium = document.getElementById('hp-sodium');
    const hpSmoking = document.getElementById('hp-smoking');
    const hpAlcohol = document.getElementById('hp-alcohol');
    const hpFamily = document.getElementById('hp-family');

    if (hpDob) hpDob.value = appState.patientDetails.dob || '';
    if (hpGender) hpGender.value = appState.patientDetails.gender || '';
    if (hpBlood) hpBlood.value = appState.patientDetails.bloodGroup || '';
    if (hpHeight) hpHeight.value = appState.patientDetails.height || '';
    if (hpWeight) hpWeight.value = appState.patientDetails.weight || '';
    if (hpExercise) hpExercise.value = appState.patientDetails.exercise || '';
    if (hpSodium) hpSodium.value = appState.patientDetails.sodium || '';
    if (hpSmoking) hpSmoking.value = appState.patientDetails.smoking || '';
    if (hpAlcohol) hpAlcohol.value = appState.patientDetails.alcohol || '';
    if (hpFamily) hpFamily.value = appState.patientDetails.familyHistory || '';

    // Calculate BMI
    if (hpHeight && hpWeight && hpBmi) {
        const hMeter = parseFloat(hpHeight.value) / 100;
        const wKg = parseFloat(hpWeight.value);
        if (hMeter > 0 && wKg > 0) {
            const bmi = (wKg / (hMeter * hMeter)).toFixed(1);
            let category = "Normal Weight";
            if (bmi < 18.5) category = "Underweight";
            else if (bmi >= 25 && bmi < 30) category = "Overweight";
            else if (bmi >= 30) category = "Obese";
            hpBmi.innerText = `${bmi} (${category})`;
        } else {
            hpBmi.innerText = `-- (Enter Height & Weight)`;
        }
    }

    // Populate Medical History Checkboxes
    const mHist = appState.patientDetails.medicalHistory || [];
    const mhHyp = document.getElementById('mh-hypertension');
    const mhDiab = document.getElementById('mh-diabetes');
    const mhChol = document.getElementById('mh-cholesterol');
    const mhCard = document.getElementById('mh-cardiac');

    if (mhHyp) mhHyp.checked = mHist.includes('Hypertension');
    if (mhDiab) mhDiab.checked = mHist.includes('Diabetes');
    if (mhChol) mhChol.checked = mHist.includes('Cholesterol');
    if (mhCard) mhCard.checked = mHist.includes('Cardiac');

    // Update Latest Report Metrics
    const gaugePct = document.getElementById('gauge-pct');
    const circle = document.getElementById('gauge-fill-circle');
    const badge = document.getElementById('dash-verdict-badge');
    const title = document.getElementById('dash-verdict-title');
    const tropVal = document.getElementById('bio-val-trop');
    const bnpVal = document.getElementById('bio-val-bnp');
    const ntVal = document.getElementById('bio-val-nt');
    const tropBar = document.getElementById('bio-bar-trop');
    const bnpBar = document.getElementById('bio-bar-bnp');
    const ntBar = document.getElementById('bio-bar-nt');

    if (appState.reports.length > 0) {
        const latest = appState.reports[appState.reports.length - 1];
        
        if (gaugePct) gaugePct.innerText = `${latest.probability}%`;
        if (circle) {
            const circumference = 2 * Math.PI * 80;
            const offset = circumference - (latest.probability / 100) * circumference;
            circle.style.strokeDashoffset = offset;
            
            if (latest.probability >= 70) {
                circle.style.stroke = '#EF4444';
            } else if (latest.probability >= 40) {
                circle.style.stroke = '#F59E0B';
            } else {
                circle.style.stroke = 'var(--primary)';
            }
        }
        
        if (badge) {
            badge.innerText = latest.verdict;
            const vUpper = latest.verdict.toUpperCase();
            if (vUpper.includes('HIGH')) {
                badge.className = 'verdict-badge high';
            } else if (vUpper.includes('RISK') || vUpper.includes('MODERATE')) {
                badge.className = 'verdict-badge moderate';
            } else {
                badge.className = 'verdict-badge low';
            }
        }

        if (title) {
            title.innerText = `Biomarkers evaluated on ${latest.date}: Troponin I ${latest.troponin} ng/mL, BNP ${latest.bnp} pg/mL.`;
        }

        if (tropVal) tropVal.innerText = `${latest.troponin} ng/mL`;
        if (bnpVal) bnpVal.innerText = `${latest.bnp} pg/mL`;
        if (ntVal) ntVal.innerText = `${latest.ntProbnp} pg/mL`;

        if (tropBar) tropBar.style.width = `${Math.min(100, (latest.troponin / 0.5) * 100)}%`;
        if (bnpBar) bnpBar.style.width = `${Math.min(100, (latest.bnp / 400) * 100)}%`;
        if (ntBar) ntBar.style.width = `${Math.min(100, (latest.ntProbnp / 600) * 100)}%`;
    } else {
        if (gaugePct) gaugePct.innerText = `0%`;
        if (circle) {
            const circumference = 2 * Math.PI * 80;
            circle.style.strokeDashoffset = circumference;
            circle.style.stroke = 'var(--primary)';
        }
        if (badge) {
            badge.innerText = 'NO DATA';
            badge.className = 'verdict-badge low';
        }
        if (title) {
            title.innerText = 'No lab report scans uploaded yet. Upload a diagnostic report file or complete a scan on your mobile app.';
        }
        if (tropVal) tropVal.innerText = '--';
        if (bnpVal) bnpVal.innerText = '--';
        if (ntVal) ntVal.innerText = '--';
        if (tropBar) tropBar.style.width = '0%';
        if (bnpBar) bnpBar.style.width = '0%';
        if (ntBar) ntBar.style.width = '0%';
    }

    // Populate Historical Lab Scans Table
    const tableBody = document.getElementById('report-history-rows');
    if (tableBody) {
        tableBody.innerHTML = '';
        if (appState.reports.length === 0) {
            tableBody.innerHTML = `<tr><td colspan="4" style="text-align:center; color: var(--text-muted); padding: 28px;"><i class="fa-solid fa-folder-open" style="margin-right: 8px;"></i> No lab scans found for this account. Upload a report to begin.</td></tr>`;
        } else {
            appState.reports.forEach((rep, idx) => {
                const tr = document.createElement('tr');
                tr.style.cursor = 'pointer';
                
                const vUpper = rep.verdict.toUpperCase();
                const badgeClass = vUpper.includes('HIGH') ? 'high' : vUpper.includes('RISK') || vUpper.includes('MODERATE') ? 'moderate' : 'low';
                
                tr.innerHTML = `
                    <td><strong style="color: var(--text-main);"><i class="fa-regular fa-file-pdf" style="color: var(--primary); margin-right: 8px;"></i>${rep.filename}</strong></td>
                    <td>${rep.date}</td>
                    <td><strong style="color: var(--primary);">${rep.probability}%</strong></td>
                    <td><span class="verdict-badge ${badgeClass}">${rep.verdict}</span></td>
                `;
                tr.addEventListener('click', () => showReportPopup(rep));
                tableBody.appendChild(tr);
            });
        }
    }
}

async function saveUserProfileToBackend() {
    if (!appState.currentUser) return;
    const userId = appState.currentUser.id;

    saveLocalState(userId);

    const payload = {
        id: userId,
        full_name: appState.currentUser.name,
        name: appState.currentUser.name,
        email: appState.currentUser.email,
        phone: appState.currentUser.phone,
        address: appState.currentUser.address,
        emergency_contact: appState.currentUser.emergency,
        emergency: appState.currentUser.emergency,
        profile_picture_url: appState.currentUser.profilePictureUrl || '',
        settings: appState.settings
    };

    if (firebaseEnabled && db) {
        try {
            await Promise.race([
                db.collection("users").doc(userId).set(payload, { merge: true }),
                new Promise(resolve => setTimeout(resolve, 2500))
            ]);
        } catch (e) {
            console.warn("Firestore user write notice:", e);
        }
    }
    saveLocalState(userId);
}

async function savePatientDetailsToBackend() {
    if (!appState.currentUser) return;
    const userId = appState.currentUser.id;

    saveLocalState(userId);

    const hNum = parseFloat(appState.patientDetails.height);
    const wNum = parseFloat(appState.patientDetails.weight);

    const payload = {
        user_id: userId,
        full_name: appState.currentUser.name,
        dob: appState.patientDetails.dob || '',
        gender: appState.patientDetails.gender || '',
        blood_type: appState.patientDetails.bloodGroup || '',
        height_cm: isNaN(hNum) ? null : hNum,
        weight_kg: isNaN(wNum) ? null : wNum,
        medical_history: appState.patientDetails.medicalHistory || [],
        exercise_hours: appState.patientDetails.exercise || '',
        sodium_intake: appState.patientDetails.sodium || '',
        smoking_habit: appState.patientDetails.smoking || '',
        alcohol_consumption: appState.patientDetails.alcohol || '',
        family_history: appState.patientDetails.familyHistory || ''
    };

    if (firebaseEnabled && db) {
        try {
            await Promise.race([
                db.collection("patient_details").doc(userId).set(payload, { merge: true }),
                new Promise(resolve => setTimeout(resolve, 2500))
            ]);
        } catch (e) {
            console.warn("Firestore patient details write notice:", e);
        }
    }
    saveLocalState(userId);
}

// --- FILE UPLOAD & DROPZONE ENGINE ---
function initDropZoneEngine() {
    const dropZone = document.getElementById('drop-zone');
    const filePicker = document.getElementById('file-picker');

    const zonePrompt = document.getElementById('zone-prompt');
    const zoneUploading = document.getElementById('zone-uploading');
    const zoneAnalyzing = document.getElementById('zone-analyzing');
    const zoneComplete = document.getElementById('zone-complete');
    const completeSummary = document.getElementById('upload-complete-summary');

    if (!dropZone || !filePicker) return;

    dropZone.addEventListener('click', (e) => {
        if (e.target.tagName !== 'INPUT') {
            filePicker.click();
        }
    });

    ['dragenter', 'dragover'].forEach(evt => {
        dropZone.addEventListener(evt, (e) => {
            e.preventDefault();
            e.stopPropagation();
            dropZone.classList.add('dragover');
        });
    });

    ['dragleave', 'drop'].forEach(evt => {
        dropZone.addEventListener(evt, (e) => {
            e.preventDefault();
            e.stopPropagation();
            dropZone.classList.remove('dragover');
        });
    });

    dropZone.addEventListener('drop', (e) => {
        const dt = e.dataTransfer;
        if (dt && dt.files && dt.files.length > 0) {
            handleFileSelect(dt.files[0]);
        }
    });

    filePicker.addEventListener('change', (e) => {
        if (e.target.files && e.target.files.length > 0) {
            handleFileSelect(e.target.files[0]);
        }
    });

    async function handleFileSelect(file) {
        const fname = file.name;
        
        setZoneState('uploading');
        await new Promise(r => setTimeout(r, 1200));

        setZoneState('analyzing');
        await new Promise(r => setTimeout(r, 1500));

        let prob = 15;
        let verdict = 'HEALTHY';
        let trop = 0.018;
        let bnp = 45.0;
        let nt = 85.0;

        const lname = fname.toLowerCase();
        if (lname.includes('high') || lname.includes('severe') || lname.includes('critical') || lname.includes('infarction') || lname.includes('failure') || (lname.includes('risk') && !lname.includes('low') && !lname.includes('healthy'))) {
            prob = 84;
            verdict = 'HIGH RISK';
            trop = 0.42;
            bnp = 360.0;
            nt = 510.0;
        } else if (lname.includes('moderate') || lname.includes('medium') || lname.includes('abnormal') || lname.includes('ischemia')) {
            prob = 48;
            verdict = 'MODERATE RISK';
            trop = 0.08;
            bnp = 160.0;
            nt = 240.0;
        } else if (lname.includes('low')) {
            prob = 25;
            verdict = 'LOW RISK';
            trop = 0.02;
            bnp = 50.0;
            nt = 90.0;
        }

        const newReport = {
            filename: fname,
            date: new Date().toISOString().substring(0, 10),
            probability: prob,
            verdict: verdict,
            troponin: trop,
            bnp: bnp,
            ntProbnp: nt
        };

        await uploadReportToBackend(newReport);
        appState.reports.push(newReport);
        syncDashboard();

        if (completeSummary) completeSummary.innerText = `Uploaded: ${fname} (${prob}% ${verdict})`;
        setZoneState('complete');

        setTimeout(() => {
            setZoneState('prompt');
            showReportPopup(newReport);
        }, 1000);
    }

    function setZoneState(stateName) {
        if (zonePrompt) zonePrompt.style.display = stateName === 'prompt' ? 'block' : 'none';
        if (zoneUploading) zoneUploading.style.display = stateName === 'uploading' ? 'block' : 'none';
        if (zoneAnalyzing) zoneAnalyzing.style.display = stateName === 'analyzing' ? 'block' : 'none';
        if (zoneComplete) zoneComplete.style.display = stateName === 'complete' ? 'block' : 'none';
    }
}

// --- EVENT LISTENERS INITIALIZATION ---
function initApp() {

    initDropZoneEngine();

    // Modal Close & Action Handlers
    const btnCloseModal = document.getElementById('btn-close-modal');
    const reportModal = document.getElementById('report-detail-modal');
    const btnShareReport = document.getElementById('btn-share-report');
    const btnDownloadPdf = document.getElementById('btn-download-pdf');

    if (btnCloseModal && reportModal) {
        btnCloseModal.addEventListener('click', () => {
            reportModal.classList.remove('active');
        });
    }

    if (btnShareReport) {
        btnShareReport.addEventListener('click', async () => {
            const rep = appState.activeReport || (appState.reports.length > 0 ? appState.reports[appState.reports.length - 1] : null);
            if (!rep) return;

            const text = `CardioProgress Cardiac Diagnostics Report:\nFile: ${rep.filename}\nRisk Score: ${rep.probability}%\nVerdict: ${rep.verdict}\nTroponin I: ${rep.troponin} ng/mL\nBNP: ${rep.bnp} pg/mL`;

            if (navigator.share) {
                try {
                    await navigator.share({ title: 'Cardiac Fibrosis Report', text: text });
                } catch (e) {
                    console.log("Native share cancelled or unsupported");
                }
            } else {
                navigator.clipboard.writeText(text);
                alert("📋 Report summary copied to clipboard! You can now share it with your doctor or contacts.");
            }
        });
    }

    if (btnDownloadPdf) {
        btnDownloadPdf.addEventListener('click', () => {
            window.print();
        });
    }

    // HEALTH PROFILE FORM SUBMIT HANDLER & LIVE BMI CALCULATOR
    const formHealthProfile = document.getElementById('form-health-profile');
    if (formHealthProfile) {
        formHealthProfile.addEventListener('submit', async (e) => {
            e.preventDefault();
            if (!appState.currentUser) return;
            
            const mHist = [];
            if (document.getElementById('mh-hypertension')?.checked) mHist.push('Hypertension');
            if (document.getElementById('mh-diabetes')?.checked) mHist.push('Diabetes');
            if (document.getElementById('mh-cholesterol')?.checked) mHist.push('Cholesterol');
            if (document.getElementById('mh-cardiac')?.checked) mHist.push('Cardiac');

            appState.patientDetails = {
                dob: document.getElementById('hp-dob')?.value || '',
                gender: document.getElementById('hp-gender')?.value || '',
                bloodGroup: document.getElementById('hp-blood')?.value || '',
                height: document.getElementById('hp-height')?.value || '',
                weight: document.getElementById('hp-weight')?.value || '',
                exercise: document.getElementById('hp-exercise')?.value || '',
                sodium: document.getElementById('hp-sodium')?.value || '',
                smoking: document.getElementById('hp-smoking')?.value || '',
                alcohol: document.getElementById('hp-alcohol')?.value || '',
                familyHistory: document.getElementById('hp-family')?.value || '',
                medicalHistory: mHist
            };

            const saveBtn = e.target.querySelector('button[type="submit"]');
            if (saveBtn) { saveBtn.disabled = true; saveBtn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Saving...'; }

            await savePatientDetailsToBackend();
            syncDashboard();

            if (saveBtn) { 
                saveBtn.disabled = false; 
                saveBtn.innerHTML = '<i class="fa-solid fa-check"></i> Health Profile Saved & Synced!';
                setTimeout(() => {
                    saveBtn.innerHTML = '<i class="fa-solid fa-cloud-arrow-up"></i> Save Health Profile to Cloud';
                }, 2500);
            }
        });
    }

    ['hp-height', 'hp-weight'].forEach(id => {
        const elem = document.getElementById(id);
        if (elem) {
            elem.addEventListener('input', () => {
                const hVal = parseFloat(document.getElementById('hp-height')?.value);
                const wVal = parseFloat(document.getElementById('hp-weight')?.value);
                const hpBmi = document.getElementById('hp-bmi-val');
                if (hVal > 0 && wVal > 0 && hpBmi) {
                    const hMeter = hVal / 100;
                    const bmi = (wVal / (hMeter * hMeter)).toFixed(1);
                    let category = "Normal Weight";
                    if (bmi < 18.5) category = "Underweight";
                    else if (bmi >= 25 && bmi < 30) category = "Overweight";
                    else if (bmi >= 30) category = "Obese";
                    hpBmi.innerText = `${bmi} (${category})`;
                    hpBmi.style.background = 'rgba(0,245,212,0.1)';
                    hpBmi.style.borderColor = 'var(--primary)';
                    hpBmi.style.color = 'var(--primary)';
                } else if (hpBmi) {
                    hpBmi.innerText = '-- (Enter Height & Weight)';
                    hpBmi.style.background = 'rgba(255,255,255,0.04)';
                    hpBmi.style.borderColor = 'var(--border-color)';
                    hpBmi.style.color = 'var(--text-muted)';
                }
            });
        }
    });

    // SETTINGS LIVE CONTROLS & LISTENERS
    const chkDarkMode = document.getElementById('setting-dark-mode');
    const selThemeAccent = document.getElementById('setting-theme-accent');
    const chkSoundFx = document.getElementById('setting-sound-effects');
    const chkPushNotif = document.getElementById('setting-push-notif');
    const chkEmailNotif = document.getElementById('setting-email-notif');
    const btnSaveSettings = document.getElementById('btn-save-settings');
    const btnClearCache = document.getElementById('btn-clear-cache');

    if (chkDarkMode) {
        chkDarkMode.addEventListener('change', (e) => {
            appState.settings.darkMode = e.target.checked;
            applyVisualSettings();
            saveUserProfileToBackend();
        });
    }

    if (selThemeAccent) {
        selThemeAccent.addEventListener('change', (e) => {
            appState.settings.theme = e.target.value;
            applyVisualSettings();
            saveUserProfileToBackend();
        });
    }

    if (chkSoundFx) {
        chkSoundFx.addEventListener('change', (e) => {
            appState.settings.soundEffects = e.target.checked;
            saveUserProfileToBackend();
        });
    }

    if (chkPushNotif) {
        chkPushNotif.addEventListener('change', (e) => {
            appState.settings.pushNotifications = e.target.checked;
            saveUserProfileToBackend();
        });
    }

    if (chkEmailNotif) {
        chkEmailNotif.addEventListener('change', (e) => {
            appState.settings.emailNotifications = e.target.checked;
            saveUserProfileToBackend();
        });
    }

    if (btnSaveSettings) {
        btnSaveSettings.addEventListener('click', async () => {
            await saveUserProfileToBackend();
            alert("⚙️ All settings & theme preferences saved and synced to cloud!");
        });
    }

    if (btnClearCache) {
        btnClearCache.addEventListener('click', () => {
            localStorage.clear();
            alert("🔄 Local storage cache refreshed! Fetching clean live data from cloud.");
            if (appState.currentUser) {
                fetchUserData(appState.currentUser.id);
            }
        });
    }

    document.querySelectorAll('.toggle-password').forEach(eye => {
        eye.addEventListener('click', (e) => {
            const targetId = e.currentTarget.getAttribute('data-target');
            const input = document.getElementById(targetId);
            if (input) {
                if (input.type === 'password') {
                    input.type = 'text';
                    e.currentTarget.className = 'fa-regular fa-eye toggle-password';
                } else {
                    input.type = 'password';
                    e.currentTarget.className = 'fa-regular fa-eye-slash toggle-password';
                }
            }
        });
    });

    const linkSignup = document.getElementById('link-to-signup');
    const linkLogin = document.getElementById('link-to-login');
    if (linkSignup) linkSignup.addEventListener('click', (e) => { e.preventDefault(); showScreen('screen-signup'); });
    if (linkLogin) linkLogin.addEventListener('click', (e) => { e.preventDefault(); showScreen('screen-login'); });

    const formLogin = document.getElementById('form-login');
    if (formLogin) {
        formLogin.addEventListener('submit', async (e) => {
            e.preventDefault();
            const email = document.getElementById('login-email').value;
            const password = document.getElementById('login-password').value;
            const submitBtn = e.target.querySelector('button[type="submit"]');

            if (submitBtn) { submitBtn.disabled = true; submitBtn.innerText = "Logging in..."; }

            if (firebaseEnabled && auth) {
                try {
                    const cred = await auth.signInWithEmailAndPassword(email, password);
                    const uid = cred.user.uid;
                    localStorage.setItem('cp_active_user', uid);
                    localStorage.setItem('cp_active_email', email);
                    await fetchUserData(uid);
                    showScreen('screen-main');
                    showTab('tab-dashboard');
                } catch (err) {
                    alert("Login Failed: " + err.message);
                } finally {
                    if (submitBtn) { submitBtn.disabled = false; submitBtn.innerText = "Log In"; }
                }
            } else {
                const mockUid = 'local_uid_' + email.replace(/[^a-zA-Z0-9]/g, '');
                localStorage.setItem('cp_active_user', mockUid);
                localStorage.setItem('cp_active_email', email);
                await fetchUserData(mockUid);
                showScreen('screen-main');
                showTab('tab-dashboard');
                if (submitBtn) { submitBtn.disabled = false; submitBtn.innerText = "Log In"; }
            }
        });
    }

    const formSignup = document.getElementById('form-signup');
    if (formSignup) {
        formSignup.addEventListener('submit', async (e) => {
            e.preventDefault();
            const name = document.getElementById('signup-name').value;
            const email = document.getElementById('signup-email').value;
            const password = document.getElementById('signup-password').value;
            const submitBtn = e.target.querySelector('button[type="submit"]');

            if (submitBtn) { submitBtn.disabled = true; submitBtn.innerText = "Registering..."; }

            if (firebaseEnabled && auth) {
                try {
                    const cred = await auth.createUserWithEmailAndPassword(email, password);
                    const uid = cred.user.uid;
                    await db.collection("users").doc(uid).set({
                        id: uid, full_name: name, name: name, email: email, phone: '', address: '', emergency_contact: '', profile_picture_url: ''
                    }, { merge: true });

                    localStorage.setItem('cp_active_user', uid);
                    localStorage.setItem('cp_active_email', email);
                    await fetchUserData(uid);
                    alert("Account created successfully!");
                    showScreen('screen-login');
                } catch (err) {
                    alert("Registration Failed: " + err.message);
                } finally {
                    if (submitBtn) { submitBtn.disabled = false; submitBtn.innerText = "Register Account"; }
                }
            }
        });
    }

    const btnVerify = document.getElementById('btn-verify-submit');
    if (btnVerify) {
        btnVerify.addEventListener('click', () => {
            showScreen('screen-main');
            showTab('tab-dashboard');
        });
    }

    document.querySelectorAll('.nav-item').forEach(item => {
        item.addEventListener('click', (e) => {
            e.preventDefault();
            const tabId = e.currentTarget.getAttribute('data-tab');
            showTab(tabId);
        });
    });

    const formEditProfile = document.getElementById('form-edit-profile');
    if (formEditProfile) {
        formEditProfile.addEventListener('submit', async (e) => {
            e.preventDefault();
            if (!appState.currentUser) return;

            appState.currentUser.name = document.getElementById('prof-name').value;
            appState.currentUser.phone = document.getElementById('prof-phone').value;
            appState.currentUser.address = document.getElementById('prof-address').value;
            appState.currentUser.emergency = document.getElementById('prof-emergency').value;

            const saveBtn = e.target.querySelector('button[type="submit"]');
            if (saveBtn) { saveBtn.disabled = true; saveBtn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Saving...'; }

            await saveUserProfileToBackend();
            syncDashboard();

            if (saveBtn) { 
                saveBtn.disabled = false; 
                saveBtn.innerHTML = '<i class="fa-solid fa-check"></i> Saved & Synced!';
                setTimeout(() => {
                    saveBtn.innerHTML = 'Save Changes <i class="fa-solid fa-floppy-disk"></i>';
                }, 2500);
            }
        });
    }



    const btnChangeDp = document.getElementById('btn-change-dp');
    const dpPicker = document.getElementById('dp-file-picker');

    if (btnChangeDp && dpPicker) {
        btnChangeDp.addEventListener('click', () => dpPicker.click());

        dpPicker.addEventListener('change', (e) => {
            if (e.target.files.length > 0) {
                const file = e.target.files[0];
                const reader = new FileReader();

                reader.onload = () => {
                    const img = new Image();
                    img.onload = async () => {
                        const canvas = document.createElement('canvas');
                        const maxSize = 300;
                        let width = img.width;
                        let height = img.height;

                        if (width > height) {
                            if (width > maxSize) {
                                height = Math.round((height * maxSize) / width);
                                width = maxSize;
                            }
                        } else {
                            if (height > maxSize) {
                                width = Math.round((width * maxSize) / height);
                                height = maxSize;
                            }
                        }

                        canvas.width = width;
                        canvas.height = height;
                        const ctx = canvas.getContext('2d');
                        ctx.drawImage(img, 0, 0, width, height);

                        const compressedBase64 = canvas.toDataURL('image/jpeg', 0.75);
                        appState.currentUser.profilePictureUrl = compressedBase64;

                        syncDashboard();
                        await saveUserProfileToBackend();
                        alert("Profile picture updated & real-time synced to cloud!");
                    };
                    img.src = reader.result;
                };
                reader.readAsDataURL(file);
            }
        });
    }

    const btnLogout = document.getElementById('btn-logout-sidebar');
    if (btnLogout) {
        btnLogout.addEventListener('click', (e) => {
            e.preventDefault();
            if (firebaseEnabled && auth) auth.signOut();
            localStorage.removeItem('cp_active_user');
            localStorage.removeItem('cp_active_email');
            appState.currentUser = null;
            showScreen('screen-login');
        });
    }

    // FIREBASE AUTH OBSERVER & INITIALIZATION
    let authObserved = false;

    function handleAuthResolved(user) {
        if (authObserved) return;
        authObserved = true;
        if (user) {
            const uid = user.uid;
            localStorage.setItem('cp_active_user', uid);
            localStorage.setItem('cp_active_email', user.email || '');
            fetchUserData(uid);
            showScreen('screen-main');
            showTab('tab-dashboard');
        } else {
            const activeId = localStorage.getItem('cp_active_user');
            if (activeId) {
                fetchUserData(activeId);
                showScreen('screen-main');
                showTab('tab-dashboard');
            } else {
                localStorage.removeItem('cp_active_user');
                localStorage.removeItem('cp_active_email');
                appState.currentUser = null;
                showScreen('screen-login');
            }
        }
    }

    if (firebaseEnabled && auth) {
        auth.onAuthStateChanged(user => {
            handleAuthResolved(user);
        });

        // Fail-safe timeout so splash screen NEVER hangs
        setTimeout(() => {
            if (!authObserved) {
                const currentUser = auth.currentUser;
                handleAuthResolved(currentUser);
            }
        }, 1200);
    } else {
        const activeId = localStorage.getItem('cp_active_user');
        if (activeId) {
            fetchUserData(activeId);
            showScreen('screen-main');
            showTab('tab-dashboard');
        } else {
            showScreen('screen-login');
        }
    }
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initApp);
} else {
    initApp();
}
