# 运行本地模型测试说明

## 步骤1：启动text-generation-webui服务

**方法1（推荐）**：
```bash
cd d:\dev\AI_Interview\tools\text-generation-webui
.\start_windows.bat --api --model llama-2-7b-chat.Q4_K_M.gguf
```

**方法2（如果方法1失败）**：
```bash
cd d:\dev\AI_Interview\tools\text-generation-webui
python server.py --api --listen --model llama-2-7b-chat.Q4_K_M.gguf
```

**等待服务启动**：
- 看到 "Running on local URL: http://127.0.0.1:7860" 或 "API is running on port 5000"
- 测试API：浏览器打开 http://localhost:5000/api 或 http://localhost:7860

## 步骤2：运行evaluation测试

在**另一个终端**中：
```bash
cd d:\dev\AI_Interview\ai-interview-project\eval
python run_eval.py --mode local --local-url http://localhost:5000/api/v1/generate --local-model llama-2-7b-chat
```

**如果API端口不同**（如7860）：
```bash
python run_eval.py --mode local --local-url http://localhost:7860/api/v1/generate --local-model llama-2-7b-chat
```

## 步骤3：查看结果

- 结果保存在 `results/` 目录
- 会生成新的 `eval_report_*.md` 和 `eval_results_*.csv`
- 对比GPT-4o-mini和Llama-2的差异

## 预期时间

- 服务启动：1-2分钟（首次加载模型）
- 运行23个测试：15-25分钟（取决于CPU/GPU）
- 总计：约20-30分钟

## 常见问题

**Q: 端口被占用**
A: 修改启动命令 `--listen-port 5001`

**Q: 模型加载失败**
A: 检查 `tools/text-generation-webui/models/` 下是否有 `llama-2-7b-chat.Q4_K_M.gguf`

**Q: API调用超时**
A: Llama-2在CPU上较慢，修改 `run_eval.py` 中的 `timeout=120` 改为 `timeout=300`
