#!/bin/sh

fuser "9002/tcp" -k
tmux new-session -d -s orbit-server
tmux send-keys -t orbit-server "java -jar target/scala-2.12/monika.jar --orbit" "C-m"
