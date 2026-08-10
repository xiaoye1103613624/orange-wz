---
name: maple-wz-resource-ops
description: >-
  冒险岛 v083 客户端 WZ/IMG 资源操作规范与注意事项（orange-wz MCP）。
  在 load/copy/paste/save、注入图标、跨 Item/Character/String 同步、并发写、进图前校验时使用。
---

# 冒险岛 WZ/IMG 资源操作规范

面向 **orange-wz MCP**（及 wzimg）。**禁止** `Copy-Item`/`robocopy` 裸拷 `.img`。

## 历史痛点（必须规避）

| 痛点 | 后果 | 正确做法 |
|------|------|----------|
| 裸拷/手改 `.img` | CRC、「不正确的游戏数据」、`0x80030002` | MCP `load→copy→paste→save` |
| 网页图标 ARGB8888 注入 | 客户端拒识/花屏 | **ARGB4444**（`pngFormat` 显式或默认） |
| `config.ini` UTF-8 BOM | `0xc0000142` | 无 BOM ANSI/UTF-8 |
| copy 与 paste 分两步跨请求 | 剪贴板被其它调用清空/覆盖 | **`copy_paste_nodes`** 原子操作 |
| 多会话同路径并发写 | 半包落盘、互相覆盖 | `load_files` 默认 **exclusive** 租约 |
| 只改 Item 不改 String/Character | 无名称/无外观/穿不上 | `analyze_resource_links` + 双端同步 |
| 空/极小 `.img` 壳进 live | 启动红 | 源包体积校验（如 Consume `0243` ≫ 29KB） |
| 批量 mutate 中途失败 | 半套资源不一致 | 关键批次 `continueOnError=false`；结束 `list_dirty_roots`→`save_dirty_roots` |
| includePng 拉整树 | 堆爆 | `get_node_tree_json` 默认 `includePng=false` |
| MCP 挂了回退裸拷 | 再次损坏 live | **停手**启动 MCP |

## 标准工作流

### 安全合并 / 恢复

1. `load_files` 已知好源 + live（`exclusive=true`）
2. 仅补缺：`copy_paste_nodes` + `strategy=SKIP`；换坏包：先验 PKG1 再 `OVERWRITE`
3. `verify_canvas_formats`（默认抓 ARGB8888）
4. `analyze_resource_links`（相关 ID）
5. `save_dirty_roots` 或按根 `save_node`（可 `clearCache=true`）
6. 客户端启动验证；勿留 quarantine 目录在 live `Data\`

### 新增物品 / 装备最小集合

- 消耗品：Item Consume + String Consume + icon/iconRaw(ARGB4444)
- 装备：Character 外观 + Item Equip + String Eqp +（服务端 XML/DB）
- 宣称完成前：`!item` 有名有图

### 并发模型

- **读**：会话内 `lockRead`，多查询可并行
- **写**：会话内写锁串行；跨会话同路径由 **根租约** 互斥
- 外部脚本：扫描可并行，**写入必须单 writer**（或各用独立会话且路径不重叠）
- 换钥：`batch_convert_key` 内部并行 worker，不占剪贴板

### 内存

- 大循环后：`clear_cache` / `save_node(..., clearCache=true)` / `unload_all`
- `copy_paste_nodes` 默认 `releaseSourceCache=true`
- 会话空闲 30 分钟自动回收

## 新增 / 推荐 MCP 工具

| 工具 | 用途 |
|------|------|
| `copy_paste_nodes` | 原子复制粘贴 |
| `list_dirty_roots` / `save_dirty_roots` | 脏根清单与批量保存 |
| `analyze_resource_links` | ID 跨包关联与断链 |
| `verify_canvas_formats` | 扫描危险 PNG 格式 |
| `mutate_nodes` + `continueOnError` | 批次容错（默认关闭） |
| `load_files` + `exclusive` | 跨会话独占路径（默认开） |

## 路径速查

- MCP：`E:\pro\orange-wz`（`ensure-mcp.ps1` → `:10002`）
- live：`...\BeiDou-Client_1`；S8：`...\BeiDou-Client_S8`
- 参考：V16 / `_backup_*` / `_img_merge_backup`

## 检查清单

- [ ] 未使用文件系统裸拷 `.img`
- [ ] 图标 ARGB4444
- [ ] Item/Character/String（及脚本/服务端）齐套
- [ ] UOL/_outlink 可解析或已烘焙
- [ ] dirty 已全部 save
- [ ] 客户端能启动、进图、刷物可见
