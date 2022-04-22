curl -XPUT -u admin:admin --insecure https://localhost:9200/_ingest/pipeline/my_simple_pipeline \
-H "Content-Type: application/json" \
-d '{
    "processors": [
        {
            "opnipre": {
                "field": "log",
                "target_field": "masked_log"
            }
        }
    ]
}'

sleep 2

curl -XPUT -u admin:admin --insecure https://localhost:9200/my_index \
-H "Content-Type: application/json" \
-d '{
    "settings": {
        "index.default_pipeline": "my_simple_pipeline"
    }
}'

sleep 1

curl -XPOST -u admin:admin --insecure https://localhost:9200/my_index/_doc \
-H "Content-Type: application/json" \
-d '{
    "log": "I0316 12:03:47.578853       1 get.go:259] \"Starting watch\" path=\"/api/v1/namespaces/dgps/secrets\" resourceVersion=\"1035642112\" labels=\"\" fields=\"metadata.name=jenkins-master-token\"\n timeout=\"5m25s\""
}'

sleep 1

curl -X GET -u admin:admin --insecure https://localhost:9200/my_index/_search
