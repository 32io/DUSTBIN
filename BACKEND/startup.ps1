docker run -d  --rm -p 27017:27017   --name test-mongo  -v data-vol:/data/db     mongo:latest
docker run -d  --rm --name redis-stack-server -p 6379:6379 redis/redis-stack-server:latest
docker run -d --rm --net=host  -e NGROK_AUTHTOKEN=25nb1V9UOcddKkZPMlNUONlbuXh_2JnMtSTbyX3takrzW8u3H ngrok/ngrok:latest http --url=factual-flying-scorpion.ngrok-free.app 5000
pip install -r  req.txt
python ./app.py