/**
 * VoiceDebugger - 语音对话调试工具
 *
 * 功能：
 * 1. 实时显示触发机制的状态
 * 2. 记录所有事件的时间线
 * 3. 参数实时调整
 * 4. 性能监控
 */
class VoiceDebugger {
    constructor(voiceManager) {
        this.voiceManager = voiceManager;
        this.events = [];
        this.metrics = {
            totalCommits: 0,
            commitsByReason: {
                isFinal: 0,
                silence_detected: 0,
                manual_button: 0,
                max_duration: 0
            },
            avgResponseTime: 0,
            responseTimes: []
        };

        this.createDebugPanel();
        this.hookIntoVoiceManager();
    }

    /**
     * 创建调试面板
     */
    createDebugPanel() {
        const panel = document.createElement('div');
        panel.id = 'voice-debugger';
        panel.style.cssText = `
            position: fixed;
            bottom: 20px;
            right: 20px;
            width: 400px;
            max-height: 600px;
            background: rgba(0, 0, 0, 0.9);
            color: #00ff00;
            font-family: 'Courier New', monospace;
            font-size: 12px;
            border-radius: 8px;
            box-shadow: 0 4px 20px rgba(0, 0, 0, 0.5);
            overflow: hidden;
            z-index: 9999;
        `;

        panel.innerHTML = `
            <div style="padding: 15px; background: #1a1a1a; border-bottom: 2px solid #00ff00;">
                <h3 style="margin: 0; color: #00ff00;">🛠️ Voice Debugger</h3>
                <button id="toggle-debugger" style="
                    position: absolute;
                    top: 10px;
                    right: 10px;
                    background: #ff4444;
                    color: white;
                    border: none;
                    padding: 5px 10px;
                    cursor: pointer;
                    border-radius: 4px;
                ">最小化</button>
            </div>

            <!-- 实时状态 -->
            <div style="padding: 10px; border-bottom: 1px solid #333;">
                <div><strong>状态:</strong> <span id="debug-state">IDLE</span></div>
                <div><strong>活跃 Turn:</strong> <span id="debug-turnid">-</span></div>
                <div><strong>字幕长度:</strong> <span id="debug-text-len">0</span></div>
                <div><strong>距上次更新:</strong> <span id="debug-time-since">0</span> ms</div>
            </div>

            <!-- 参数调整 -->
            <div style="padding: 10px; border-bottom: 1px solid #333;">
                <div style="margin-bottom: 5px;">
                    <label>silenceMs: <span id="silence-value">900</span>ms</label>
                    <input type="range" id="silence-slider" min="500" max="2000" step="100" value="900" style="width: 100%;">
                </div>
                <div style="margin-bottom: 5px;">
                    <label>minChars: <span id="minchars-value">3</span></label>
                    <input type="range" id="minchars-slider" min="1" max="10" step="1" value="3" style="width: 100%;">
                </div>
            </div>

            <!-- 统计指标 -->
            <div style="padding: 10px; border-bottom: 1px solid #333;">
                <div><strong>总提交次数:</strong> <span id="stat-total">0</span></div>
                <div style="margin-left: 20px; font-size: 11px;">
                    <div>isFinal: <span id="stat-isfinal">0</span></div>
                    <div>silence: <span id="stat-silence">0</span></div>
                    <div>manual: <span id="stat-manual">0</span></div>
                    <div>timeout: <span id="stat-timeout">0</span></div>
                </div>
                <div><strong>平均响应:</strong> <span id="stat-avg-time">0</span> ms</div>
            </div>

            <!-- 事件时间线 -->
            <div style="padding: 10px; max-height: 200px; overflow-y: auto;" id="event-timeline">
                <strong>事件时间线:</strong>
                <div id="events-list" style="margin-top: 5px; font-size: 11px;"></div>
            </div>
        `;

        document.body.appendChild(panel);

        // 绑定事件
        this.bindPanelEvents();

        // 启动实时更新
        this.startRealTimeUpdate();
    }

    /**
     * 绑定面板事件
     */
    bindPanelEvents() {
        // 最小化/展开
        let minimized = false;
        document.getElementById('toggle-debugger').addEventListener('click', () => {
            const panel = document.getElementById('voice-debugger');
            if (minimized) {
                panel.style.maxHeight = '600px';
                document.getElementById('toggle-debugger').textContent = '最小化';
            } else {
                panel.style.maxHeight = '50px';
                document.getElementById('toggle-debugger').textContent = '展开';
            }
            minimized = !minimized;
        });

        // silenceMs 调整
        document.getElementById('silence-slider').addEventListener('input', (e) => {
            const value = parseInt(e.target.value);
            document.getElementById('silence-value').textContent = value;
            this.voiceManager.config.silenceMs = value;
            this.logEvent('CONFIG', `silenceMs 调整为 ${value}ms`);
        });

        // minChars 调整
        document.getElementById('minchars-slider').addEventListener('input', (e) => {
            const value = parseInt(e.target.value);
            document.getElementById('minchars-value').textContent = value;
            this.voiceManager.config.minCharsToCommit = value;
            this.logEvent('CONFIG', `minCharsToCommit 调整为 ${value}`);
        });
    }

    /**
     * Hook 到 VoiceManager 的关键方法
     */
    hookIntoVoiceManager() {
        const vm = this.voiceManager;

        // Hook handleResult
        const originalHandleResult = vm.handleResult.bind(vm);
        vm.handleResult = (event) => {
            this.logEvent('ASR', '收到识别结果');
            originalHandleResult(event);
        };

        // Hook commitUtterance
        const originalCommit = vm.commitUtterance.bind(vm);
        vm.commitUtterance = (reason) => {
            const startTime = Date.now();

            this.logEvent('COMMIT', `触发提交: ${reason}`, '#ffff00');
            this.metrics.totalCommits++;
            this.metrics.commitsByReason[reason]++;

            // 记录响应时间
            const checkResponse = setInterval(() => {
                if (vm.state.activeTurnId === null) {
                    clearInterval(checkResponse);
                    const responseTime = Date.now() - startTime;
                    this.metrics.responseTimes.push(responseTime);
                    this.metrics.avgResponseTime = Math.round(
                        this.metrics.responseTimes.reduce((a, b) => a + b, 0) /
                        this.metrics.responseTimes.length
                    );
                    this.logEvent('DONE', `响应时间: ${responseTime}ms`, '#00ff00');
                }
            }, 100);

            originalCommit(reason);
        };

        // Hook handleServerMessage
        const originalHandleMsg = vm.handleServerMessage.bind(vm);
        vm.handleServerMessage = (message) => {
            const data = JSON.parse(message.body);
            this.logEvent('SERVER', `收到: ${data.type}`, '#00aaff');
            originalHandleMsg(message);
        };
    }

    /**
     * 记录事件
     */
    logEvent(type, message, color = '#00ff00') {
        const timestamp = new Date().toLocaleTimeString('zh-CN', { hour12: false });
        const event = { type, message, timestamp, color };
        this.events.unshift(event);

        // 只保留最近 50 条
        if (this.events.length > 50) {
            this.events.pop();
        }

        this.updateEventsList();
    }

    /**
     * 更新事件列表
     */
    updateEventsList() {
        const listEl = document.getElementById('events-list');
        listEl.innerHTML = this.events.map(e => `
            <div style="color: ${e.color}; margin: 2px 0;">
                [${e.timestamp}] <strong>${e.type}</strong>: ${e.message}
            </div>
        `).join('');
    }

    /**
     * 实时更新状态
     */
    startRealTimeUpdate() {
        setInterval(() => {
            const vm = this.voiceManager;
            const state = vm.state;

            // 更新状态
            document.getElementById('debug-state').textContent =
                state.isListening ? 'LISTENING' :
                state.activeTurnId ? 'WAITING_AI' : 'IDLE';

            document.getElementById('debug-turnid').textContent =
                state.activeTurnId || '-';

            const totalText = state.currentTranscript + state.partialTranscript;
            document.getElementById('debug-text-len').textContent = totalText.length;

            const timeSince = state.lastUpdateTime ?
                Date.now() - state.lastUpdateTime : 0;
            document.getElementById('debug-time-since').textContent = timeSince;

            // 更新统计
            document.getElementById('stat-total').textContent = this.metrics.totalCommits;
            document.getElementById('stat-isfinal').textContent = this.metrics.commitsByReason.isFinal;
            document.getElementById('stat-silence').textContent = this.metrics.commitsByReason.silence_detected;
            document.getElementById('stat-manual').textContent = this.metrics.commitsByReason.manual_button;
            document.getElementById('stat-timeout').textContent = this.metrics.commitsByReason.max_duration;
            document.getElementById('stat-avg-time').textContent = this.metrics.avgResponseTime;
        }, 100);
    }

    /**
     * 导出调试数据
     */
    exportDebugData() {
        const data = {
            config: this.voiceManager.config,
            metrics: this.metrics,
            events: this.events,
            timestamp: new Date().toISOString()
        };

        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `voice-debug-${Date.now()}.json`;
        a.click();
        URL.revokeObjectURL(url);

        this.logEvent('EXPORT', '调试数据已导出', '#ffaa00');
    }
}

// 导出
if (typeof module !== 'undefined' && module.exports) {
    module.exports = VoiceDebugger;
}
