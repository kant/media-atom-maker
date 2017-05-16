#!/usr/bin/env bash

nvm_installed() {
  if [ -d '/usr/local/opt/nvm' ] || [ -d "$HOME/.nvm" ]; then
    true
  else
    false
  fi
}

nvm_available() {
  type -t nvm > /dev/null
}

source_nvm() {
  if ! nvm_available; then
    [ -e "/usr/local/opt/nvm/nvm.sh" ] && source /usr/local/opt/nvm/nvm.sh
  fi
  if ! nvm_available; then
    [ -e "$HOME/.nvm/nvm.sh" ] && source $HOME/.nvm/nvm.sh
  fi
}

# if nvm is installed, install the node version required and use it
if nvm_installed; then
  if ! nvm_available; then
    source_nvm
  fi
  nvm install
fi

printf "\n\rRemoving compiled css file... \n\r\n\r"
rm public/video-ui/build/main.css 2> /dev/null
printf "\n\rStarting Webpack Dev Server... \n\r\n\r"
yarn run client-dev &
printf "\n\rStarting Play App... \n\r\n\r"
JS_ASSET_HOST=https://video-assets.local.dev-gutools.co.uk/assets/ ./sbt $@ app/run
