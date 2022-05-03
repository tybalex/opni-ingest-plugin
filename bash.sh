IMAGE_NAME=tybalex/opensearch:demo
docker build . -t $IMAGE_NAME

docker push $IMAGE_NAME
