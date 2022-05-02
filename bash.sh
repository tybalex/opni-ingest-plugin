IMAGE_NAME=tybalex/opensearch:local4
docker build . -t $IMAGE_NAME

docker push $IMAGE_NAME
