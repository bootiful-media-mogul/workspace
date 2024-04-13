#!/usr/bin/env bash
HERE="$(cd  `dirname $0 ` && pwd )"
GH_ORG=git@github.com:bootiful-media-mogul
CLONE_DIR=$HOME/code/mogul
mkdir -p $CLONE_DIR

for i in mogul-audio-processor authorization-service mogul-gateway mogul-client mogul-service workspace pipeline; do
    FULL_CLONE_DIR=$CLONE_DIR/$i
    if [ -d "$FULL_CLONE_DIR" ]; then
      echo the directory "$FULL_CLONE_DIR" already exists
      cd "$FULL_CLONE_DIR"
      git pull || echo "couldn't git pull the latest revisions in $FULL_CLONE_DIR "
    else
      echo "$FULL_CLONE_DIR does exist."
      git clone  $GH_ORG/${i}.git "$FULL_CLONE_DIR"
    fi
done


cp "$HERE"/new-envrc "$CLONE_DIR"/.envrc
cp "$HERE"/compose.yaml "$CLONE_DIR"/compose.yaml