/**
 * VoiceManager - 实时语音对话端点检测管理器
 *
 * 核心功能：
 * 1. 三重触发机制：isFinal / 静默检测 / 手动按钮
 * 2. 超时兜底：25秒强制提交
 * 3. 空提交过滤：少于3字符不提交
 * 4. 智能启停：commit后停止识别，AI完成后重启
 */
class VoiceManager {
    constructor(stompClient, principalName) {
        this.stompClient = stompClient;
        this.principalName = principalName;

        // 核心参数 - 生产环境调优值
        this.config = {
            silenceMs: 900,           // 静默检测：900ms 无变化即提交
            hangoverMs: 200,          // 防抖延迟：避免误触发
            maxUtteranceMs: 25000,    // 最大发言时长：25秒强制提交
            minCharsToCommit: 3,      // 最小字符数：避免空提交
        };

        // 状态管理
        this.state = {
            recognition: null,
            isListening: false,
            currentTranscript: '',     // 当前累积的完整文本
            partialTranscript: '',     // 当前这一段的临时文本
            activeTurnId: null,        // 当前对话轮次ID
            lastUpdateTime: 0,         // 最后一次字幕更新时间
            utteranceStartTime: 0,     // 当前发言开始时间
        };

        // 定时器
        this.timers = {
            silenceDetector: null,     // 静默检测定时器
            hangoverDebounce: null,    // 防抖定时器
            maxUtteranceTimeout: null, // 最大时长超时定时器
        };

        this.initRecognition();
    }

    /**
     * 初始化 Web Speech API
     */
    initRecognition() {
        const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
        if (!SpeechRecognition) {
            console.error('当前浏览器不支持 Web Speech API');
            alert('您的浏览器不支持语音识别功能，请使用 Chrome/Edge 浏览器');
            return;
        }

        const recognition = new SpeechRecognition();
        recognition.lang = 'zh-CN';
        recognition.continuous = true;        // 持续识别
        recognition.interimResults = true;    // 返回临时结果
        recognition.maxAlternatives = 1;

        // 识别结果处理
        recognition.onresult = (event) => {
            this.handleResult(event);
        };

        // 识别开始
        recognition.onstart = () => {
            console.log('[VoiceManager] 语音识别已启动');
            this.state.isListening = true;
            this.state.utteranceStartTime = Date.now();
            this.startMaxUtteranceTimeout();
            this.updateUIState('listening');
        };

        // 识别结束
        recognition.onend = () => {
            console.log('[VoiceManager] 语音识别已停止');
            this.state.isListening = false;
            this.clearAllTimers();

            // 关键：仅在 AI 回复完成后才自动重启，避免无限循环
            // 如果是用户主动停止或等待 AI 回复，不要自动重启
            if (this.state.activeTurnId === null) {
                // 没有活跃对话时，可以自动重启（用户可能想继续说话）
                console.log('[VoiceManager] 空闲状态，2秒后自动重启识别');
                setTimeout(() => {
                    if (!this.state.isListening && this.state.activeTurnId === null) {
                        this.start();
                    }
                }, 2000);
            } else {
                console.log('[VoiceManager] 等待 AI 回复中，不自动重启');
                this.updateUIState('waiting');
            }
        };

        // 错误处理
        recognition.onerror = (event) => {
            console.error('[VoiceManager] 识别错误:', event.error);

            // 处理常见错误
            switch (event.error) {
                case 'no-speech':
                    console.log('未检测到语音，继续监听');
                    break;
                case 'audio-capture':
                    alert('无法访问麦克风，请检查权限设置');
                    break;
                case 'not-allowed':
                    alert('麦克风权限被拒绝，请在浏览器设置中允许麦克风访问');
                    break;
                case 'network':
                    console.error('网络错误，可能影响识别质量');
                    break;
                default:
                    console.error('识别服务错误:', event.error);
            }
        };

        this.state.recognition = recognition;
    }

    /**
     * 处理识别结果 - 三重触发机制的核心
     */
    handleResult(event) {
        const now = Date.now();
        let interimTranscript = '';
        let finalTranscript = '';

        // 遍历所有识别结果
        for (let i = event.resultIndex; i < event.results.length; i++) {
            const transcript = event.results[i][0].transcript;

            if (event.results[i].isFinal) {
                // 触发机制 A: isFinal 立即提交
                finalTranscript += transcript;
            } else {
                interimTranscript += transcript;
            }
        }

        // 更新临时字幕
        if (interimTranscript) {
            this.state.partialTranscript = interimTranscript;
            this.state.lastUpdateTime = now;
            this.updateSubtitle(this.state.currentTranscript + interimTranscript, false);

            // 触发机制 B: 启动静默检测
            this.startSilenceDetection();
        }

        // 触发机制 A: 收到 isFinal 立即提交
        if (finalTranscript) {
            console.log('[VoiceManager] 收到 isFinal，立即提交:', finalTranscript);
            this.state.currentTranscript += finalTranscript;
            this.state.partialTranscript = '';
            this.commitUtterance('isFinal');
        }
    }

    /**
     * 触发机制 B: 静默检测 - 900ms 无变化强制提交
     */
    startSilenceDetection() {
        // 清除旧的定时器
        if (this.timers.hangoverDebounce) {
            clearTimeout(this.timers.hangoverDebounce);
        }

        // 防抖：200ms 后再启动真正的静默检测
        this.timers.hangoverDebounce = setTimeout(() => {
            this.clearTimer('silenceDetector');

            this.timers.silenceDetector = setTimeout(() => {
                const timeSinceLastUpdate = Date.now() - this.state.lastUpdateTime;

                if (timeSinceLastUpdate >= this.config.silenceMs) {
                    console.log('[VoiceManager] 静默检测触发 (900ms 无变化)');

                    // 合并临时字幕到完整文本
                    if (this.state.partialTranscript) {
                        this.state.currentTranscript += this.state.partialTranscript;
                        this.state.partialTranscript = '';
                    }

                    this.commitUtterance('silence_detected');
                }
            }, this.config.silenceMs);
        }, this.config.hangoverMs);
    }

    /**
     * 触发机制 C 的支持: 最大发言时长超时
     */
    startMaxUtteranceTimeout() {
        this.clearTimer('maxUtteranceTimeout');

        this.timers.maxUtteranceTimeout = setTimeout(() => {
            console.log('[VoiceManager] 最大发言时长 25 秒已到，强制提交');

            if (this.state.partialTranscript) {
                this.state.currentTranscript += this.state.partialTranscript;
                this.state.partialTranscript = '';
            }

            this.commitUtterance('max_duration');
        }, this.config.maxUtteranceMs);
    }

    /**
     * 提交发言到后端
     */
    commitUtterance(reason) {
        const text = this.state.currentTranscript.trim();

        // 空提交过滤：少于 3 个字符不提交
        if (text.length < this.config.minCharsToCommit) {
            console.log(`[VoiceManager] 文本过短 (${text.length} 字符)，跳过提交`);
            this.reset();
            return;
        }

        // 生成新的 turnId
        const turnId = this.generateUUID();
        this.state.activeTurnId = turnId;

        console.log(`[VoiceManager] 提交发言 [${reason}]:`, text);

        // 关键：提交后立即停止识别，避免 AI 回复被误识别
        this.stop();

        // 通过 STOMP 发送到后端
        this.stompClient.send('/app/interview/commit', {}, JSON.stringify({
            type: 'commit',
            turnId: turnId,
            text: text,
            reason: reason,
            timestamp: Date.now()
        }));

        // 更新 UI
        this.updateSubtitle(text, true);
        this.updateUIState('waiting');

        // 重置累积文本
        this.state.currentTranscript = '';
        this.state.partialTranscript = '';
    }

    /**
     * 触发机制 C: 手动"我说完了"按钮
     */
    manualCommit() {
        console.log('[VoiceManager] 用户手动点击"我说完了"');

        // 合并所有文本
        if (this.state.partialTranscript) {
            this.state.currentTranscript += this.state.partialTranscript;
            this.state.partialTranscript = '';
        }

        this.commitUtterance('manual_button');
    }

    /**
     * 取消当前对话
     */
    cancel() {
        if (!this.state.activeTurnId) {
            console.log('[VoiceManager] 没有活跃对话，无需取消');
            return;
        }

        console.log('[VoiceManager] 取消当前对话:', this.state.activeTurnId);

        this.stompClient.send('/app/interview/cancel', {}, JSON.stringify({
            type: 'cancel',
            turnId: this.state.activeTurnId,
            timestamp: Date.now()
        }));

        this.reset();
        this.start(); // 取消后重新开始监听
    }

    /**
     * 处理服务器推送的消息
     */
    handleServerMessage(message) {
        const data = JSON.parse(message.body);

        // 只处理当前活跃 turnId 的消息，避免串流
        if (data.turnId !== this.state.activeTurnId) {
            console.warn('[VoiceManager] 收到非活跃 turnId 的消息，忽略:', data.turnId);
            return;
        }

        switch (data.type) {
            case 'ai_token':
                this.appendAIToken(data.token);
                break;

            case 'ai_done':
                console.log('[VoiceManager] AI 回复完成');
                this.handleAIDone(data);
                break;

            case 'ai_error':
                console.error('[VoiceManager] AI 错误:', data.error);
                this.handleAIError(data);
                break;

            case 'asr_partial':
                // 可选：服务器端 ASR 的临时结果
                this.updateSubtitle(data.text, false);
                break;

            default:
                console.warn('[VoiceManager] 未知消息类型:', data.type);
        }
    }

    /**
     * AI 回复完成 - 重启语音识别
     */
    handleAIDone(data) {
        this.state.activeTurnId = null;
        this.updateUIState('idle');

        // AI 回复完成后，延迟 1 秒重启识别，避免抢话
        setTimeout(() => {
            if (!this.state.isListening) {
                console.log('[VoiceManager] AI 回复完成，重启语音识别');
                this.start();
            }
        }, 1000);
    }

    /**
     * AI 错误 - 重启语音识别
     */
    handleAIError(data) {
        this.state.activeTurnId = null;
        this.showError(data.error || '服务错误，请重试');
        this.updateUIState('error');

        // 错误后也要重启识别
        setTimeout(() => {
            if (!this.state.isListening) {
                this.start();
            }
        }, 2000);
    }

    /**
     * 启动语音识别
     */
    start() {
        if (this.state.isListening) {
            console.log('[VoiceManager] 已在监听中');
            return;
        }

        if (!this.state.recognition) {
            console.error('[VoiceManager] 语音识别未初始化');
            return;
        }

        try {
            this.state.recognition.start();
        } catch (e) {
            console.error('[VoiceManager] 启动识别失败:', e);
        }
    }

    /**
     * 停止语音识别
     */
    stop() {
        if (!this.state.isListening) {
            return;
        }

        try {
            this.state.recognition.stop();
        } catch (e) {
            console.error('[VoiceManager] 停止识别失败:', e);
        }
    }

    /**
     * 重置状态
     */
    reset() {
        this.state.currentTranscript = '';
        this.state.partialTranscript = '';
        this.state.activeTurnId = null;
        this.state.lastUpdateTime = 0;
        this.clearAllTimers();
    }

    /**
     * 清除所有定时器
     */
    clearAllTimers() {
        Object.keys(this.timers).forEach(key => this.clearTimer(key));
    }

    clearTimer(name) {
        if (this.timers[name]) {
            clearTimeout(this.timers[name]);
            this.timers[name] = null;
        }
    }

    /**
     * 生成 UUID (RFC4122 v4)
     */
    generateUUID() {
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
            const r = Math.random() * 16 | 0;
            const v = c === 'x' ? r : (r & 0x3 | 0x8);
            return v.toString(16);
        });
    }

    // ==================== UI 更新方法（需根据实际页面调整）====================

    /**
     * 更新字幕显示
     */
    updateSubtitle(text, isFinal) {
        const subtitleEl = document.getElementById('subtitle');
        if (subtitleEl) {
            subtitleEl.textContent = text;
            subtitleEl.className = isFinal ? 'final' : 'interim';
        }
    }

    /**
     * 追加 AI token
     */
    appendAIToken(token) {
        const aiResponseEl = document.getElementById('ai-response');
        if (aiResponseEl) {
            aiResponseEl.textContent += token;
            // 自动滚动到底部
            aiResponseEl.scrollTop = aiResponseEl.scrollHeight;
        }
    }

    /**
     * 更新 UI 状态
     */
    updateUIState(state) {
        const statusEl = document.getElementById('voice-status');
        const doneBtn = document.getElementById('done-btn');
        const cancelBtn = document.getElementById('cancel-btn');

        if (statusEl) {
            const states = {
                listening: { text: '🎤 正在听...', color: '#4CAF50' },
                waiting: { text: '⏳ AI 思考中...', color: '#FF9800' },
                idle: { text: '💤 空闲', color: '#9E9E9E' },
                error: { text: '❌ 错误', color: '#F44336' }
            };

            const s = states[state] || states.idle;
            statusEl.textContent = s.text;
            statusEl.style.color = s.color;
        }

        if (doneBtn) {
            doneBtn.disabled = (state !== 'listening');
        }

        if (cancelBtn) {
            cancelBtn.disabled = (state !== 'waiting');
        }
    }

    /**
     * 显示错误
     */
    showError(message) {
        const errorEl = document.getElementById('error-message');
        if (errorEl) {
            errorEl.textContent = message;
            errorEl.style.display = 'block';

            setTimeout(() => {
                errorEl.style.display = 'none';
            }, 5000);
        } else {
            console.error('[VoiceManager] 错误:', message);
        }
    }
}

// 导出供其他模块使用
if (typeof module !== 'undefined' && module.exports) {
    module.exports = VoiceManager;
}
