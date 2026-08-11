---
name: es-curl-query-only
description: 使用 curl 对 Elasticsearch 执行只读查询。用于需要查询索引、文档、聚合、计数、映射或集群健康等信息的场景。遇到任何写入、更新、删除、重建索引、批量写入、脚本更新、设置变更等请求时，必须拒绝并改为只读查询方案。
---

# ES Curl Query Only

## 快速流程

1. 仅使用 `scripts/es_readonly_query.sh` 发起请求，不直接裸调 `curl`。
2. 默认连接以下开发环境（可用环境变量覆盖）：
   - `ES_BASE_URL=http://es.jdx-ka-v2-dev.building2-dev.jdt.com.cn`
   - `ES_USER=admin`
   - `ES_PASS=LOeJH2vJpcCAdg11`
3. 只允许查询端点，且只允许 `GET` 或用于搜索的 `POST`。
4. 若用户提出写操作，明确拒绝，并给出等价查询方式（例如先查询待变更文档）。

## 允许与拒绝规则

### 允许

- 文档与索引查询：`GET /{index}/_search`、`POST /{index}/_search`
- 计数：`GET /{index}/_count`、`POST /{index}/_count`
- 映射与设置读取：`GET /{index}/_mapping`、`GET /{index}/_settings`
- 文档读取：`GET /{index}/_doc/{id}`
- 集群与节点只读信息：`GET /_cluster/health`、`GET /_cat/*`

### 禁止

- `PUT`、`DELETE`、`PATCH`
- 任意 `/_bulk`、`/_reindex`、`/_update`、`/_update_by_query`
- 任意 `/_delete`、`/_delete_by_query`
- 任意创建/修改索引、模板、别名、ILM、管道、脚本、设置

## 命令用法

```bash
# 默认 GET
scripts/es_readonly_query.sh '/_cluster/health?pretty'

# 指定 POST 搜索
scripts/es_readonly_query.sh '/my-index/_search?pretty' POST '{"query":{"match_all":{}},"size":5}'
```

## 资源
所有相对路径均相对于本 SKILL.md 所在目录。

- 查询脚本：`scripts/es_readonly_query.sh`
- 示例请求：`references/query-examples.md`
