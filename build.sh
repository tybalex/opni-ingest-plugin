IMAGE_NAME=tybalex/opensearch
docker build . -t $IMAGE_NAME

docker push $IMAGE_NAME
