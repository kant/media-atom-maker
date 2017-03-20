import React from 'react';
import moment from 'moment';
import _ from 'lodash';

const VIDEO_WIDTH = 320;
const VIDEO_HEIGHT = 180;

function embed(assetId, platform) {
    if(platform === "Youtube") {
        const props = {
            type: "text/html",
            width: VIDEO_WIDTH, 
            height: VIDEO_HEIGHT,
            src: `https://www.youtube.com/embed/${assetId}?showinfo=0`,
            frameBorder: 0
        };

        return <iframe {...props}></iframe>;
    } else {
        return false;
    }
}

function selector(assetId, version, selectAsset, active) {
    const classes = active ? "button__active" : "button__secondary";
    const action = active ? "Active" : "Activate";

    return <button className={classes} disabled={active} onClick={() => selectAsset(assetId, version)}>
        {action}
    </button>;
}

function readableDateTime(created) {
    return <div>
        <strong>{moment(created).format('DD/MM/YY')}</strong>
        {moment(created).format('HH:mm:ss')}
    </div>;
}

function VideoAsset({ id, platform, version, active, created, selectAsset }) {
    if(!id) {
        return <div className="upload__asset">
            <div className="upload__asset__video upload__asset__empty">
                <span>No Assets Added</span>    
            </div>
        </div>;
    }

    return <div className="upload__asset">
        <div className="upload__asset__video">{embed(id, platform)}</div>
        <div className="upload__asset__caption">
            {created ? readableDateTime(created) : <div />}
            {selector(id, version, selectAsset, active)}
        </div>
    </div>;
}

function UploadAsset({ created, message, total, progress }) {
    return <div className="upload__asset">
        <div className="upload__asset__video upload__asset__running">
            <span>{message}</span>
        </div>
        <div className="upload__asset__caption">
            {readableDateTime(created)}
            {progress === undefined ? <progress /> : <progress value={progress} max={total} />}
        </div>
    </div>;
}

export default function VideoTrail({ activeVersion, assets, selectAsset, localUpload, uploads }) {
    const squares = [];

    if(localUpload.total) {
        squares.push(<UploadAsset key="upload" message="Uploading To S3" {...localUpload} />);
    }

    uploads.forEach((upload) => {
        const hidden = _.find(upload.parts, (part) => !part.uploadedToS3);

        if(!hidden) {
            squares.push(<UploadAsset key={upload.id} message="Uploading To YouTube" created={Date.now()} />);
        }
    });

    assets.forEach((asset) => {
        squares.push(<VideoAsset key={asset.id} active={asset.version === activeVersion} selectAsset={selectAsset} {...asset} />);
    });

    return <div className="upload__assets">
        {squares.length > 0 ? squares : <VideoAsset />}
    </div>;
}