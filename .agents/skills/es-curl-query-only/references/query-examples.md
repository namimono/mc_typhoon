# Elasticsearch 只读查询示例

## 1) 查看集群健康

```bash
scripts/es_readonly_query.sh '/_cluster/health?pretty'
```

## 2) 查看索引文档数量

```bash
scripts/es_readonly_query.sh '/my-index/_count?pretty'
```

## 3) 查询最近 10 条文档

```bash
scripts/es_readonly_query.sh '/my-index/_search?pretty' POST '{"size":10,"sort":[{"@timestamp":{"order":"desc"}}],"query":{"match_all":{}}}'
```

## 4) 按 ID 查询文档

```bash
scripts/es_readonly_query.sh '/my-index/_doc/123?pretty'
```

## 5) 查询字段映射

```bash
scripts/es_readonly_query.sh '/my-index/_mapping?pretty'
```
