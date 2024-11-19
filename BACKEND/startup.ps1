mkdir ./project_4.1
cd ./project_4.1
git clone https://github.com/32io/DUSTBIN.git
cd ./BACKEND 
docker build -t project-4.1-backend
rm ./project_4.1 -r -force
docker run -d  --rm -p 27017:27017   --name test-mongo  -v data-vol:/data/db     mongo:latest
docker run -d  --rm --name redis-stack-server -p 6379:6379 redis/redis-stack-server:latest
docker run -d --rm --net=host  -e NGROK_AUTHTOKEN=25nb1V9UOcddKkZPMlNUONlbuXh_2JnMtSTbyX3takrzW8u3H ngrok/ngrok:latest http --url=factual-flying-scorpion.ngrok-free.app 5000
# pip install -r  req.txt
docker run -e paystack_live="sk_live_fb4d3354cf70d3bcb800682a0836b93b9b155d34" project-4.1-backend python ./app.py
# 