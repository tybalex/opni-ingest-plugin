IMAGE_NAME=tybalex/opensearch:4.2
docker build . -t $IMAGE_NAME

docker push $IMAGE_NAME
