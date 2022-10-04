curl -XPUT localhost:9200/_ingest/pipeline/my_simple_pipeline \
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

curl -XPUT localhost:9200/my_index \
-H "Content-Type: application/json" \
-d '{
    "settings": {
        "index.default_pipeline": "my_simple_pipeline"
    }
}'

sleep 1

curl -XPOST localhost:9200/my_index/_doc \
-H "Content-Type: application/json" \
-d @example.txt

sleep 1

curl -X GET localhost:9200/my_index/_search
