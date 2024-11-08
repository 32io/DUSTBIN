docker run -d  --rm -p 27017:27017   --name test-mongo  -v data-vol:/data/db     mongo:latest
docker run -d  --rm --name redis-stack-server -p 6379:6379 redis/redis-stack-server:latest