// ==========================================
// 核心文本收发逻辑
// ==========================================

let currentSessionId = "";
const USER_ID = 1;

function handleEnter(event) {
    if (event.key === 'Enter') sendMessage();
}

function sendMessage() {
    const inputDom = document.getElementById('userInput');
    const text = inputDom.value.trim();
    const sendBtn = document.getElementById('sendBtn');
    if (!text) return;

    appendMessage('user', text);
    inputDom.value = '';
    sendBtn.disabled = true;

    const payload = { content: text };
    if (currentSessionId) payload.sessionId = currentSessionId;

    fetch(`http://localhost:8080/api/chat/send?uid=${USER_ID}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
    .then(res => res.json())
    .then(data => {
        if (data.status === 'success') {
            const isNewSession = (currentSessionId === "");

            currentSessionId = data.session_id;
            appendMessage('ai', data.response);

            if (isNewSession) {
                loadSessions();
            }

            if(data.emotion) {
                const emotionDom = document.getElementById('emotionDisplay');
                emotionDom.innerText = data.emotion;
                if (data.emotion === '开心') emotionDom.style.color = '#FF9800';
                else if (data.emotion === '孤独' || data.emotion === '失落') emotionDom.style.color = '#9E9E9E';
                else if (data.emotion === '身体不适') emotionDom.style.color = '#F44336';
                else if (data.emotion === '焦躁' || data.emotion === '悲伤') emotionDom.style.color = '#9C27B0';
                else emotionDom.style.color = '#4CAF50';
            }

            if (data.audioBase64) {
                const audio = new Audio("data:audio/mp3;base64," + data.audioBase64);
                audio.play();
            }
        } else {
            appendMessage('ai', '❌ 系统忙，请稍后再试。');
        }
    })
    .catch(err => {
        appendMessage('ai', '🛑 网络异常，请确认服务器已启动（Port 8080）。');
    })
    .finally(() => {
        sendBtn.disabled = false;
    });
}

// ==========================================
// 🔔 智能强提醒 & 语音播报逻辑
// ==========================================

let isShowingReminder = false;

function checkReminders() {
    if(isShowingReminder) return;

    fetch(`http://localhost:8080/api/reminders/check?uid=${USER_ID}`)
        .then(res => res.json())
        .then(tasks => {
            if(tasks && tasks.length > 0) {
                const task = tasks[0];
                showReminderAlert(task);
            }
        })
        .catch(error => console.error("轮询异常:", error));

}

function showReminderAlert(task) {
    isShowingReminder = true; // 上锁
    const modal = document.getElementById('reminderAlertModal');
    const textDom = document.getElementById('reminderAlertText');
    const completeBtn = document.getElementById('reminderCompleteBtn');

    textDom.innerHTML = `到时间啦！<br><span style="color:var(--primary-color)">【${task.task_name}】</span>`;
    const speakText = `大福提醒您，该去执行任务啦：${task.task_name}`;

    completeBtn.onclick = function() {
            finishTask(task.reid, task.repeat_type); // 调用你原有的完成接口
            closeReminderAlertModal(); // 关掉弹窗
    };

    modal.style.display = 'flex';

    fetch(`http://localhost:8080/api/voice/tts?text=${encodeURIComponent(speakText)}`)
            .then(res => res.json())
            .then(data => {
                if (data.status === 'success' && data.audioBase64) {
                    const audio = new Audio("data:audio/mp3;base64," + data.audioBase64);
                    audio.play().catch(err => {
                        console.warn("⚠️ 浏览器自动播放被拦截啦！原因：网页刚打开，还没有任何点击交互。", err);
                    });
                }
            });
}

// 关闭强提醒弹窗
function closeReminderAlertModal() {
    document.getElementById('reminderAlertModal').style.display = 'none';
    isShowingReminder = false; // 解锁，允许下一个提醒弹出
}

function appendMessage(role, text) {
    const chatBox = document.getElementById('chatBox');
    const msgDiv = document.createElement('div');
    msgDiv.className = `message ${role}`;
    msgDiv.innerHTML = `<div class="bubble">${text}</div>`;
    chatBox.appendChild(msgDiv);
    chatBox.scrollTop = chatBox.scrollHeight;
}

setInterval(checkReminders, 10000);
document.addEventListener('DOMContentLoaded', checkReminders);

// ==========================================
// 🎤 点按式录音引擎 (Web Audio API)
// ==========================================

let audioContext;
let mediaStreamSource;
let scriptProcessor;
let audioData = [];
let isRecording = false;

function toggleRecording(event) {
    if(event) event.preventDefault();

    const inputDom = document.getElementById('userInput');
    const voiceBtn = document.getElementById('voiceBtn');
    const voiceIcon = document.getElementById('voiceIcon');

    // ====== 状态 1：当前未录音，点击准备开始 ======
    if (!isRecording) {
        isRecording = true;
        audioData = [];

        // UI 切换到录音状态
        voiceBtn.classList.add('recording');
        voiceIcon.className = 'fas fa-stop-circle'; // 换成正方形停止图标
        inputDom.placeholder = "🎙️ 正在倾听... 说完请再点一次麦克风停止";
        inputDom.disabled = true; // 录音时禁用文字输入

        navigator.mediaDevices.getUserMedia({ audio: true }).then(stream => {
            audioContext = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 16000 });
            mediaStreamSource = audioContext.createMediaStreamSource(stream);
            scriptProcessor = audioContext.createScriptProcessor(4096, 1, 1);

            mediaStreamSource.connect(scriptProcessor);
            scriptProcessor.connect(audioContext.destination);

            scriptProcessor.onaudioprocess = function(e) {
                if (!isRecording) return;
                const inputData = e.inputBuffer.getChannelData(0);
                for (let i = 0; i < inputData.length; i++) {
                    let s = Math.max(-1, Math.min(1, inputData[i]));
                    audioData.push(s < 0 ? s * 0x8000 : s * 0x7FFF);
                }
            };
        }).catch(err => {
            console.error("麦克风权限被拒绝", err);
            inputDom.placeholder = "❌ 麦克风权限被拒绝，请检查浏览器设置";
            resetVoiceUI();
        });

    // ====== 状态 2：当前正在录音，点击结束并发送 ======
    } else {
        isRecording = false;

        // UI 切换到识别状态
        inputDom.placeholder = "⏳ 正在翻译您的语音...";
        resetVoiceUI();

        if (scriptProcessor) {
            scriptProcessor.disconnect();
            mediaStreamSource.disconnect();
        }

        if (audioData.length === 0) {
            inputDom.placeholder = "点击这里打字，或点击右侧麦克风说话...";
            return;
        }

        // 打包 PCM 数据发给后端
        const buffer = new Int16Array(audioData);
        const blob = new Blob([buffer], { type: 'application/octet-stream' });
        const formData = new FormData();
        formData.append("audio", blob, "record.pcm");

        fetch(`http://localhost:8080/api/chat/recognize?uid=${USER_ID}`, {
            method: 'POST',
            body: formData
        })
        .then(res => res.json())
        .then(data => {
            if (data.status === 'success' && data.text) {
                // 填入输入框并自动触发发送！
                inputDom.value = data.text;
                sendMessage();
                inputDom.placeholder = "点击这里打字，或点击右侧麦克风说话...";
            } else {
                inputDom.placeholder = "抱歉，没听清，能再说一遍吗？";
            }
        })
        .catch(err => {
            console.error(err);
            inputDom.placeholder = "网络似乎断开了...";
        });
    }
}

function resetVoiceUI() {
    isRecording = false;
    const voiceBtn = document.getElementById('voiceBtn');
    const voiceIcon = document.getElementById('voiceIcon');
    const inputDom = document.getElementById('userInput');

    voiceBtn.classList.remove('recording');
    voiceIcon.className = 'fas fa-microphone';
    inputDom.disabled = false; // 恢复文字输入
}

// ==========================================
// 📝 待办事项弹窗逻辑
// ==========================================

function openTodoModal() {
    document.getElementById('todoModal').style.display = 'flex';
    loadReminders();
}

function closeTodoModal() {
    document.getElementById('todoModal').style.display = 'none';
}

function loadReminders() {
    fetch(`http://localhost:8080/api/reminders/list?uid=${USER_ID}`)
        .then(response => response.json())
        .then(data => {
            const listContainer = document.getElementById('todoList');
            const badge = document.getElementById('todoBadge');

            listContainer.innerHTML = '';

            if (data.length > 0) {
                badge.style.display = 'inline-block';
                badge.textContent = data.length;
            } else {
                badge.style.display = 'none';
                listContainer.innerHTML = '<p style="text-align:center; color:#9ca3af; padding: 20px;">老人家，目前没有待办事项哦~</p>';
                return;
            }

            data.forEach(task => {
                let repeatText = "一次性";
                let btnText = "完成任务";

                if (task.repeat_type === 1) {
                    repeatText = "每天";
                    btnText = "今日打卡";
                }
                if (task.repeat_type === 2) {
                    repeatText = "每周";
                    btnText = "本周打卡";
                }

                const li = document.createElement('li');
                li.className = 'todo-item';
                li.innerHTML = `
                    <div class="todo-info">
                        <strong>${task.task_name}</strong>
                        <span class="todo-time">⏰ 计划时间: ${task.next_remind_time} (${repeatText})</span>
                    </div>
                    <div class="action-buttons">
                        <button class="complete-btn" onclick="finishTask(${task.reid}, ${task.repeat_type})">
                            <i class="fas fa-check-circle"></i> ${btnText}
                        </button>
                        <button class="delete-btn" onclick="deleteTask(${task.reid})" title="取消提醒">
                            <i class="fas fa-trash-alt"></i>
                        </button>
                    </div>
                `;
                listContainer.appendChild(li);
            });
        })
        .catch(error => console.error("获取待办失败:", error));
}

function finishTask(reid, repeatType) {
    fetch(`http://localhost:8080/api/reminders/complete?reid=${reid}`, { method: 'POST' })
        .then(response => response.text())
        .then(res => {
            if (res === 'success') {
                console.log(`任务 ${reid} 处理成功`);
                // 弹窗消失后，静默刷新左侧的待办列表和红点
                loadReminders();
            }
        });
}

function deleteTask(reid) {
    if (confirm("🚨 老人家，您确定要取消这个提醒吗？取消后就不再响了哦。")) {
        fetch(`http://localhost:8080/api/reminders/delete?reid=${reid}`, { method: 'POST' })
            .then(response => response.text())
            .then(res => {
                if (res === 'success') {
                    alert("🗑️ 已经帮您取消啦！");
                    loadReminders();
                } else {
                    alert("❌ 取消失败，请检查网络。");
                }
            });
    }
}

// ==========================================
// 🏥 健康档案逻辑
// ==========================================

function toggleDropdown() {
    const menu = document.getElementById('dropdownMenu');
    menu.style.display = menu.style.display === 'none' ? 'block' : 'none';
}

document.addEventListener('click', function(event) {
    const headerActions = document.querySelector('.header-actions');
    const menu = document.getElementById('dropdownMenu');
    if (headerActions && !headerActions.contains(event.target) && menu) {
        menu.style.display = 'none';
    }
});

function openProfileModal() {
    document.getElementById('dropdownMenu').style.display = 'none';
    document.getElementById('profileModal').style.display = 'flex';

    fetch(`http://localhost:8080/api/profile/get?uid=${USER_ID}`)
        .then(res => res.json())
        .then(data => {
            if (data.status !== 'empty') {
                document.getElementById('profileName').value = data.username || '';
                document.getElementById('profileAge').value = data.age || '';
                document.getElementById('profileGender').value = data.gender || '男';
                document.getElementById('profileDialect').value = data.dialect || '普通话';
                document.getElementById('profileMedical').value = data.medical_history || '';
                document.getElementById('profilePhysical').value = data.physical_condition || '';
                document.getElementById('profileDiet').value = data.dietary_preference || '';
                document.getElementById('profileContact').value = data.emergency_contact || '';
            }
        }).catch(err => console.error("拉取档案失败:", err));
}

function closeProfileModal() {
    document.getElementById('profileModal').style.display = 'none';
}

function saveProfile() {
    const payload = {
        uid: USER_ID,
        name: document.getElementById('profileName').value,
        age: document.getElementById('profileAge').value,
        gender: document.getElementById('profileGender').value,
        dialect: document.getElementById('profileDialect').value,
        medical_history: document.getElementById('profileMedical').value,
        physical_condition: document.getElementById('profilePhysical').value,
        dietary_preference: document.getElementById('profileDiet').value,
        emergency_contact: document.getElementById('profileContact').value
    };

    fetch('http://localhost:8080/api/profile/save', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    })
    .then(res => res.text())
    .then(res => {
        if (res === 'success') {
            alert('✅ 档案保存成功！大福已经把老人家的情况记在心里啦。');
            closeProfileModal();
            initUserInfo();
        } else {
            alert('❌ 保存失败，请检查后端是否正常运行。');
        }
    });
}

// ==========================================
// 👤 用户信息初始化逻辑
// ==========================================
function initUserInfo() {
    console.log("开始向后端拉取用户信息...");
    fetch(`http://localhost:8080/api/profile/get?uid=${USER_ID}`)
        .then(res => res.json())
        .then(data => {
            console.log("后端成功返回的数据是：", data);
            if(data && data.username) {
                const lastName = data.username.charAt(0);
                const title = (data.gender === '女') ? '奶奶' : '爷爷';
                const callName = lastName + title;
                document.getElementById('userNameDisplay').innerText = callName;
            } else {
                document.getElementById('userNameDisplay').innerText = '爷爷/奶奶';
            }
        })
        .catch(err => {
            console.error("拉取信息报错啦:", err);
            document.getElementById('userNameDisplay').innerText = '爷爷/奶奶';
        });
}

document.addEventListener('DOMContentLoaded', () => {
    initUserInfo();
    loadReminders();
    checkReminders();
    loadSessions();
});

// ==========================================
// 🗂️ 多轮会话 (历史记录) 逻辑
// ==========================================

function loadSessions() {
    fetch(`http://localhost:8080/api/session/list?uid=${USER_ID}`)
        .then(res => res.json())
        .then(data => {
            const list = document.getElementById('sessionList');
            list.innerHTML = '';
            data.forEach(session => {
                const li = document.createElement('li');
                li.className = 'session-item';
                if (session.session_id === currentSessionId) li.classList.add('active');

                li.innerText = session.title || '与大福的聊天';
                li.onclick = () => loadHistory(session.session_id);
                list.appendChild(li);
            });
        })
        .catch(err => console.error("拉取历史会话失败", err));
}

function loadHistory(sessionId) {
    currentSessionId = sessionId;
    loadSessions();

    fetch(`http://localhost:8080/api/session/history?sessionId=${sessionId}`)
        .then(res => res.json())
        .then(data => {
            const chatBox = document.getElementById('chatBox');
            chatBox.innerHTML = '';

            data.forEach(msg => {
                const roleClass = (msg.role === 'assistant' || msg.role === 'ai') ? 'ai' : 'user';
                appendMessage(roleClass, msg.content);
            });
        });
}

function startNewChat() {
    currentSessionId = "";
    loadSessions();

    const chatBox = document.getElementById('chatBox');
    chatBox.innerHTML = `
        <div class="message ai">
            <div class="bubble">爷爷/奶奶您好，我是您的智能助手大福。今天心情怎么样？有什么我可以帮您的吗？</div>
        </div>
    `;
}