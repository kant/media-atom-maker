#!/usr/bin/env bash

if [ $# -ne 3 ]
then
  echo "usage: $0 <STACK_NAME> <PANDA_AWS_PROFILE> <DOMAIN>"
  exit 1
fi

STACK_NAME=$1
PANDA_AWS_PROFILE=$2
DOMAIN=$3

# always use the media-service account
export AWS_DEFAULT_PROFILE=media-service

function get_resource() {
    aws cloudformation describe-stack-resources \
        --stack-name ${STACK_NAME} \
        | jq ".StackResources[] | select(.LogicalResourceId == \"$1\") | .PhysicalResourceId" | tr -d '"'
}

LIVE_STREAM_NAME=$(get_resource "MediaAtomLiveKinesisStream")
PREVIEW_STREAM_NAME=$(get_resource "MediaAtomPreviewKinesisStream")

PREVIEW_REINDEX_STREAM_NAME=${PREVIEW_STREAM_NAME}
PUBLISHED_REINDEX_STREAM_NAME=${LIVE_STREAM_NAME}

DYNAMO_TABLE=$(get_resource "MediaAtomsDynamoTable")
DYNAMO_PUBLISHED_TABLE=$(get_resource "PublishedMediaAtomsDynamoTable")

sed -e "s/{DOMAIN}/${DOMAIN}/g" \
    -e "s/{PANDA_PROFILE}/${PANDA_AWS_PROFILE}/g" \
    -e "s/{AWS_PROFILE}/${AWS_DEFAULT_PROFILE}/g" \
    -e "s/{LIVE_STREAM_NAME}/${LIVE_STREAM_NAME}/g" \
    -e "s/{PREVIEW_STREAM_NAME}/${PREVIEW_STREAM_NAME}/g" \
    -e "s/{PREVIEW_REINDEX_STREAM_NAME}/${PREVIEW_REINDEX_STREAM_NAME}/g" \
    -e "s/{PUBLISHED_REINDEX_STREAM_NAME}/${PUBLISHED_REINDEX_STREAM_NAME}/g" \
    -e "s/{DYNAMO_TABLE}/${DYNAMO_TABLE}/g" \
    -e "s/{DYNAMO_PUBLISHED_TABLE}/${DYNAMO_PUBLISHED_TABLE}/g" \
    ../conf/reference.conf > ../conf/application.conf

aws s3 cp s3://atom-maker-dist/conf/youtube-DEV.conf ../conf/youtube-DEV.conf
aws s3 cp s3://atom-maker-dist/conf/capi-DEV.conf ../conf/capi-DEV.conf

# clean up after ourselves
unset AWS_DEFAULT_PROFILE