## OrzRepacker

### I18n 多语言配置方法
创建 config.ini 文件
```ini
# zh_CN / en_US
language = zh_CN
```

### MCP 连接方式

将配置里的
`spring.main.web-application-type=none`
改为
`servlet`
启用 web 端口。

**端口**：默认从 **10012** 起自动顺延（10012–10029）。由 `ensure-mcp.ps1` 选择空闲端口并写入 `mcp-runtime/endpoint.json`。

#### 推荐工作流

1. 启动 MCP（若未运行）：
   ```bat
   ensure-mcp.bat
   ```
   或 `powershell -File ensure-mcp.ps1`

2. 读取当前端点：
   - `mcp-runtime/endpoint.json` — 首选（`url` 或 `port`）
   - `mcp-runtime/active-port.txt` — 纯端口号
   - 均缺失时回退 `http://127.0.0.1:10012/mcp`

3. Cursor 配置（ensure-mcp 可自动同步 `~/.cursor/mcp.json`）：
   ```json
   {
     "mcpServers": {
       "orange-wz": {
         "type": "http",
         "url": "http://127.0.0.1:10012/mcp"
       }
     }
   }
   ```
   实际 URL 以 `endpoint.json` 为准。

#### 部署 live IMG 模式

改客户端 `Data\*.img` 时：

1. 关闭客户端（`BeiDou.exe` / `MapleStory.exe`）
2. 复制 live → staging 路径 → `load_files`
3. `mutate_nodes`（`set_png` 可用 `filePath` 指向本地 PNG，或 `base64Png`）
4. `save_as` 到 **不同** output 路径 → `unload_all`
5. `os.replace(output, live)` 原子替换

**禁止** `save_as` 回写与 loaded 根相同的路径。

BeiDou Python 辅助：`BeiDou-Server_S9/tools/mcp_client.py`（`resolve_mcp_url`、`kill_game_client`、`deploy_replaced_img`）。

详见 `mcp-runtime/README.txt`。
