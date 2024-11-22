# Preparations
mkdir ./project_4.1
cd ./project_4.1
git clone https://github.com/32io/DUSTBIN.git
cd ./DUSTBIN/BACKEND 
docker build -t project-4.1-backend .
cd ..
cd ..
cd ..
rm ./project_4.1 -r -force
docker network create --driver bridge backend-net 

# # RUNNING 
docker run -d  --rm -p 27017:27017   --name test-mongo  -v data-vol:/data/db     mongo:latest
docker run -d  --rm --name redis-stack-server -p 6379:6379 redis/redis-stack-server:latest
# pip install -r  req.txt
docker run -d --rm --name=backend  --net=host   -e paystack_live="sk_live_87f1e81b9fc0d219a67522e90b161e6e7fcc676d" project-4.1-backend python ./app.py
docker run -d --rm --name=ngrok --net=host   -e NGROK_AUTHTOKEN=2pDuafBVsbUROJClQSkpBodzWl8_4TL3SQoXaWshLucT66bej ngrok/ngrok:latest http --url=factual-flying-scorpion.ngrok-free.app 5000
docker run   --rm --net=host  project-4.1-backend python ./client.py
## TESTING ADRUINO
# docker run --rm --net=host  project-4.1-backend python ./Adruino.py