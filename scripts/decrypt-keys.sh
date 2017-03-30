#!/bin/sh

openssl aes-256-cbc -K $encrypted_ec1d6a7ecdae_key -iv $encrypted_ec1d6a7ecdae_iv -in keys.enc -out travis-deploy-key -d;
chmod 600 travis-deploy-key;
cp travis-deploy-key ~/.ssh/id_rsa;
